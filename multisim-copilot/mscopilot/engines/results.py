"""Simulation result containers, data-file parsing and measurements."""
from __future__ import annotations

import csv
import math
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from mscopilot.plan import Analysis, Measurement


@dataclass
class MeasurementResult:
    name: str
    type: str
    signal: str
    value: float | None
    unit: str = ""
    expected: float | None = None
    tolerance_pct: float | None = None
    status: str = "info"  # pass | fail | info | error
    note: str = ""


@dataclass
class AnalysisResult:
    analysis: Analysis
    scale_name: str = ""
    scale: np.ndarray | None = None
    data: dict[str, np.ndarray] = field(default_factory=dict)  # complex for AC, real otherwise
    measurements: list[MeasurementResult] = field(default_factory=list)
    plots: list[Path] = field(default_factory=list)
    screenshots: list[Path] = field(default_factory=list)
    csv_path: Path | None = None
    error: str = ""
    source: str = "ngspice"

    def op_values(self) -> dict[str, float]:
        if self.analysis.kind != "op":
            return {}
        return {k: float(np.real(v[0])) for k, v in self.data.items() if len(v)}

    def signal(self, name: str) -> np.ndarray:
        key = _find_key(self.data, name)
        if key is None:
            raise KeyError(f"Signal {name} was not recorded (have: {', '.join(self.data) or 'none'})")
        return self.data[key]


def _norm(sig: str) -> str:
    return "".join(sig.lower().split())


def _find_key(data: dict, name: str) -> str | None:
    n = _norm(name)
    for k in data:
        if _norm(k) == n:
            return k
    # ngspice writes currents as i(v1) or v1#branch
    m = n
    if m.startswith("i(") and m.endswith(")"):
        alt = m[2:-1] + "#branch"
        for k in data:
            if _norm(k) == alt:
                return k
    return None


# ---------------------------------------------------------------- parsing
def parse_wrdata(path: Path, analysis_kind: str) -> tuple[str, np.ndarray, dict[str, np.ndarray]]:
    """Parse an ngspice ``wrdata`` file written with wr_singlescale + wr_vecnames.

    The first column is the scale (time/frequency/sweep); each real vector takes one column
    and each complex vector two (re, im) under the same repeated header name.
    """
    text = Path(path).read_text(encoding="utf-8", errors="replace").strip().splitlines()
    if not text:
        raise ValueError(f"{path} is empty")
    header = text[0].split()
    rows = [list(map(float, ln.split())) for ln in text[1:] if ln.strip()]
    arr = np.array(rows, dtype=float) if rows else np.zeros((0, len(header)))
    scale_name = header[0]
    scale = arr[:, 0] if arr.size else np.array([])
    data: dict[str, np.ndarray] = {}
    i = 1
    while i < len(header):
        name = header[i]
        if i + 1 < len(header) and header[i + 1] == name:
            data[name] = arr[:, i] + 1j * arr[:, i + 1]
            i += 2
        else:
            data[name] = arr[:, i]
            i += 1
    return scale_name, scale, data


def write_csv(result: AnalysisResult, path: Path) -> Path:
    path = Path(path)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        cols = [result.scale_name or "index"]
        series = []
        for k, v in result.data.items():
            if np.iscomplexobj(v):
                cols += [f"{k} mag_dB", f"{k} phase_deg"]
                series += [20 * np.log10(np.maximum(np.abs(v), 1e-30)), np.degrees(np.angle(v))]
            else:
                cols.append(k)
                series.append(v)
        w.writerow(cols)
        n = len(result.scale) if result.scale is not None else (len(series[0]) if series else 0)
        for j in range(n):
            row = [result.scale[j] if result.scale is not None and len(result.scale) else j]
            row += [s[j] for s in series]
            w.writerow([f"{x:.9g}" for x in row])
    result.csv_path = path
    return path


# ---------------------------------------------------------------- measurements
def _interp(x: np.ndarray, y: np.ndarray, at: float, log_x: bool = False) -> float:
    if log_x:
        return float(np.interp(math.log10(at), np.log10(x), y))
    return float(np.interp(at, x, y))


def _crossing(x: np.ndarray, y: np.ndarray, level: float, log_x: bool = False, start: int = 0) -> float | None:
    for j in range(max(start, 1), len(y)):
        y0, y1 = y[j - 1], y[j]
        if (y0 - level) * (y1 - level) <= 0 and y0 != y1:
            frac = (level - y0) / (y1 - y0)
            if log_x:
                lx = math.log10(x[j - 1]) + frac * (math.log10(x[j]) - math.log10(x[j - 1]))
                return 10**lx
            return float(x[j - 1] + frac * (x[j] - x[j - 1]))
    return None


def transfer(result: AnalysisResult, signal: str) -> np.ndarray:
    """signal / reference (or the signal itself if there is no reference) for AC data."""
    sig = result.signal(signal)
    ref_name = result.analysis.reference_signal
    if ref_name and _norm(ref_name) != _norm(signal):
        ref = result.signal(ref_name)
        with np.errstate(divide="ignore", invalid="ignore"):
            return np.where(np.abs(ref) > 0, sig / ref, 0)
    return sig


def compute_measurement(result: AnalysisResult, m: Measurement) -> MeasurementResult:
    mr = MeasurementResult(m.name, m.type, m.signal, None, m.unit or "", m.expected, m.tolerance_pct)
    try:
        x = result.scale
        kind = result.analysis.kind
        if kind == "ac":
            h = transfer(result, m.signal)
            mag_db = 20 * np.log10(np.maximum(np.abs(h), 1e-30))
            phase = np.degrees(np.unwrap(np.angle(h)))
            if m.type == "gain_db_at":
                mr.value = _interp(x, mag_db, m.at, log_x=True)
            elif m.type == "phase_at":
                mr.value = _interp(x, phase, m.at, log_x=True)
            elif m.type == "max_gain_db":
                mr.value = float(mag_db.max())
            elif m.type == "f_3db":
                peak = int(np.argmax(mag_db))
                level = mag_db[peak] - 3.0103
                f = _crossing(x, mag_db, level, log_x=True, start=peak + 1)
                if f is None and peak > 0:  # high-pass: crossing below the peak
                    f = _crossing(x[: peak + 1], mag_db[: peak + 1], level, log_x=True)
                if f is None:
                    mr.note = "no -3 dB crossing inside the sweep"
                mr.value = f
            elif m.type == "value_at":
                mr.value = _interp(x, np.abs(h), m.at, log_x=True)
            elif m.type in ("max", "min"):
                mr.value = float(np.abs(h).max() if m.type == "max" else np.abs(h).min())
            else:
                raise ValueError(f"{m.type} is not meaningful for an AC analysis")
        else:
            y = np.real(result.signal(m.signal))
            if kind == "op" or m.type == "dc_value":
                mr.value = float(y[0]) if kind == "op" else float(np.mean(y))
            elif m.type == "max":
                mr.value = float(y.max())
            elif m.type == "min":
                mr.value = float(y.min())
            elif m.type == "vpp":
                mr.value = float(y.max() - y.min())
            elif m.type == "vrms":
                mr.value = float(np.sqrt(np.trapezoid(y**2, x) / (x[-1] - x[0]))) if len(x) > 1 else float(abs(y[0]))
            elif m.type == "vavg":
                mr.value = float(np.trapezoid(y, x) / (x[-1] - x[0])) if len(x) > 1 else float(y[0])
            elif m.type == "value_at":
                mr.value = _interp(x, y, m.at)
            elif m.type == "frequency":
                mr.value = _frequency(x, y)
                if mr.value is None:
                    mr.note = "fewer than two cycles in the window"
            elif m.type == "rise_time":
                lo, hi = y.min(), y.max()
                t10 = _crossing(x, y, lo + 0.1 * (hi - lo))
                t90 = _crossing(x, y, lo + 0.9 * (hi - lo))
                mr.value = (t90 - t10) if (t10 is not None and t90 is not None and t90 > t10) else None
                if mr.value is None:
                    mr.note = "no clean 10-90 % rising edge"
            else:
                raise ValueError(f"{m.type} is not meaningful for a {kind} analysis")
    except Exception as e:
        mr.status, mr.note = "error", str(e)
        return mr
    mr.status = judge(mr)
    return mr


def _frequency(t: np.ndarray, y: np.ndarray) -> float | None:
    mid = (y.max() + y.min()) / 2
    s = np.sign(y - mid)
    idx = np.where((s[:-1] < 0) & (s[1:] >= 0))[0]
    if len(idx) < 3:
        return None
    times = [t[j] + (mid - y[j]) * (t[j + 1] - t[j]) / (y[j + 1] - y[j]) for j in idx]
    periods = np.diff(times)
    return float(1.0 / np.median(periods))


def judge(mr: MeasurementResult) -> str:
    if mr.value is None:
        return "error" if mr.expected is not None else "info"
    if mr.expected is None:
        return "info"
    pct = abs(mr.tolerance_pct if mr.tolerance_pct is not None else 5.0)
    # A percentage of zero is meaningless, so for a zero target the tolerance is absolute
    # (in the measurement's own unit, e.g. ±0.5 dB or ±10 mV written as 0.5 / 0.01).
    band = pct if mr.expected == 0 else abs(mr.expected) * pct / 100
    return "pass" if abs(mr.value - mr.expected) <= band else "fail"
