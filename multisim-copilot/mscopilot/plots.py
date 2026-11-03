"""Instrument-style figures: Bode plotter, oscilloscope, multimeter, DC sweep table/plot."""
from __future__ import annotations

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
from matplotlib.ticker import EngFormatter  # noqa: E402

from mscopilot.engines.results import AnalysisResult, transfer  # noqa: E402

# Scope channel colours (classic bench-scope convention: CH1 yellow, CH2 cyan, CH3 magenta, CH4 green)
SCOPE_COLORS = ["#f5d90a", "#22d3ee", "#e879f9", "#4ade80", "#fb923c", "#60a5fa"]
LIGHT_SERIES = ["#2563eb", "#dc2626", "#059669", "#7c3aed", "#d97706", "#0891b2"]
DPI = 150


def _unit_for(signal: str) -> str:
    return "A" if signal.strip().lower().startswith("i(") else "V"


def plot_bode(r: AnalysisResult, path: Path) -> Path:
    f = r.scale
    fig, (ax_m, ax_p) = plt.subplots(2, 1, figsize=(9, 6.4), sharex=True, constrained_layout=True)
    ref = r.analysis.reference_signal
    for i, sig in enumerate(s for s in r.analysis.signals if not ref or s.strip().lower() != ref.strip().lower()):
        try:
            h = transfer(r, sig)
        except KeyError:
            continue
        mag = 20 * np.log10(np.maximum(np.abs(h), 1e-30))
        ph = np.degrees(np.unwrap(np.angle(h)))
        label = f"{sig} / {ref}" if ref else sig
        c = LIGHT_SERIES[i % len(LIGHT_SERIES)]
        ax_m.semilogx(f, mag, color=c, lw=2, label=label)
        ax_p.semilogx(f, ph, color=c, lw=2, label=label)
    for m in r.measurements:
        if m.type == "f_3db" and m.value:
            for ax in (ax_m, ax_p):
                ax.axvline(m.value, color="#6b7280", ls="--", lw=1)
            ax_m.annotate(f"-3 dB @ {EngFormatter('Hz')(m.value)}", (m.value, ax_m.get_ylim()[0]), xytext=(6, 10),
                          textcoords="offset points", fontsize=9, color="#374151")
    ax_m.set_ylabel("Magnitude (dB)")
    ax_p.set_ylabel("Phase (°)")
    ax_p.set_xlabel("Frequency")
    ax_p.xaxis.set_major_formatter(EngFormatter(unit="Hz"))
    for ax in (ax_m, ax_p):
        ax.grid(True, which="both", color="#e5e7eb", lw=0.8)
        ax.spines[["top", "right"]].set_visible(False)
    ax_m.legend(frameon=False, fontsize=9)
    ax_m.set_title(f"Bode plot — {r.analysis.title}", loc="left", fontsize=12, fontweight="bold")
    fig.savefig(path, dpi=DPI)
    plt.close(fig)
    return Path(path)


def plot_scope(r: AnalysisResult, path: Path) -> Path:
    t = r.scale
    fig, ax = plt.subplots(figsize=(9.6, 6), constrained_layout=True)
    fig.patch.set_facecolor("#0b0f14")
    ax.set_facecolor("#05080b")
    span = t[-1] - t[0] if len(t) > 1 else 1.0
    channels = []
    for i, sig in enumerate(r.analysis.signals):
        try:
            y = np.real(r.signal(sig))
        except KeyError:
            continue
        c = SCOPE_COLORS[i % len(SCOPE_COLORS)]
        ax.plot(t, y, color=c, lw=1.6, label=f"CH{i + 1}  {sig}")
        channels.append((i, sig, y, c))
    ax.set_xlim(t[0], t[-1])
    lo = min((y.min() for *_, y, _ in channels), default=-1)
    hi = max((y.max() for *_, y, _ in channels), default=1)
    pad = 0.1 * (hi - lo or 1)
    ax.set_ylim(lo - pad, hi + pad)
    ax.set_xticks(np.linspace(t[0], t[-1], 11))
    ax.set_yticks(np.linspace(lo - pad, hi + pad, 9))
    ax.grid(True, color="#1f3b2f", lw=0.8)
    ax.tick_params(colors="#9ca3af", labelsize=8)
    ax.xaxis.set_major_formatter(EngFormatter(unit="s", places=1))
    ax.yaxis.set_major_formatter(EngFormatter(unit=_unit_for(channels[0][1]) if channels else "V", places=2))
    for s in ax.spines.values():
        s.set_color("#374151")
    ax.legend(loc="upper right", facecolor="#111827", edgecolor="#374151", labelcolor="#e5e7eb", fontsize=9)
    ax.set_title(f"Oscilloscope — {r.analysis.title}    timebase {EngFormatter('s')(float(f'{span / 10:.3g}'))}/div",
                 loc="left", color="#e5e7eb", fontsize=11)
    fig.savefig(path, dpi=DPI, facecolor=fig.get_facecolor())
    plt.close(fig)
    return Path(path)


def plot_multimeter(r: AnalysisResult, path: Path) -> Path:
    vals = list(r.op_values().items())
    n = max(len(vals), 1)
    cols = min(3, n)
    rows = (n + cols - 1) // cols
    fig, axes = plt.subplots(rows, cols, figsize=(3.3 * cols, 1.9 * rows), squeeze=False)
    fig.patch.set_facecolor("white")
    eng_v, eng_a = EngFormatter("V", places=4), EngFormatter("A", places=4)
    for k, ax in enumerate(axes.flat):
        ax.set_axis_off()
        if k >= len(vals):
            continue
        name, v = vals[k]
        is_i = name.lower().startswith("i(") or name.lower().endswith("#branch")
        ax.add_patch(plt.Rectangle((0.03, 0.05), 0.94, 0.9, transform=ax.transAxes, facecolor="#f3c623", edgecolor="#4b5563", lw=1.5))
        ax.add_patch(plt.Rectangle((0.1, 0.32), 0.8, 0.42, transform=ax.transAxes, facecolor="#c8d5b9", edgecolor="#374151", lw=1))
        ax.text(0.5, 0.53, (eng_a if is_i else eng_v)(v), transform=ax.transAxes, ha="center", va="center",
                fontsize=15, family="monospace", fontweight="bold", color="#111827")
        ax.text(0.5, 0.82, "DC A" if is_i else "DC V", transform=ax.transAxes, ha="center", fontsize=8, color="#111827")
        ax.text(0.5, 0.15, name, transform=ax.transAxes, ha="center", fontsize=9, color="#111827")
    fig.suptitle(f"Multimeter readings — {r.analysis.title}", x=0.02, ha="left", fontsize=11, fontweight="bold")
    fig.tight_layout()
    fig.savefig(path, dpi=DPI)
    plt.close(fig)
    return Path(path)


def plot_sweep(r: AnalysisResult, path: Path) -> Path:
    x = r.scale
    fig, ax = plt.subplots(figsize=(9, 5), constrained_layout=True)
    for i, sig in enumerate(r.analysis.signals):
        try:
            y = np.real(r.signal(sig))
        except KeyError:
            continue
        ax.plot(x, y, lw=2, color=LIGHT_SERIES[i % len(LIGHT_SERIES)], label=sig)
    ax.set_xlabel(f"{r.analysis.dc_source or r.scale_name}")
    ax.grid(True, color="#e5e7eb")
    ax.spines[["top", "right"]].set_visible(False)
    ax.legend(frameon=False)
    ax.set_title(f"DC sweep — {r.analysis.title}", loc="left", fontsize=12, fontweight="bold")
    fig.savefig(path, dpi=DPI)
    plt.close(fig)
    return Path(path)


def render(r: AnalysisResult, out_dir: Path) -> list[Path]:
    if r.error or r.scale is None:
        return []
    out_dir.mkdir(parents=True, exist_ok=True)
    a = r.analysis
    target = out_dir / f"{a.id}.png"
    if a.kind == "op":
        p = plot_multimeter(r, target)
    elif a.kind == "ac":
        p = plot_bode(r, target)
    elif a.kind == "tran":
        p = plot_scope(r, target)
    else:
        p = plot_sweep(r, target)
    r.plots.append(p)
    return [p]
