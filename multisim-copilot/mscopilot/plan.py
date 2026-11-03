"""The simulation plan: what the AI produces and what the engines execute.

A plan is plain JSON so it can be reviewed and edited by hand before anything runs.
"""
from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass, field
from pathlib import Path

ANALYSIS_KINDS = ("op", "dc", "ac", "tran")
INSTRUMENTS = ("multimeter", "bode", "oscilloscope", "table")
MEASUREMENT_TYPES = (
    "dc_value",  # value of signal in an op analysis (or the mean in others)
    "max",
    "min",
    "vpp",
    "vrms",
    "vavg",
    "frequency",  # fundamental frequency from zero crossings (tran)
    "rise_time",  # 10-90 % (tran)
    "gain_db_at",  # |sig/ref| in dB at frequency `at` (ac)
    "phase_at",  # phase(sig/ref) in degrees at frequency `at` (ac)
    "max_gain_db",  # peak gain (ac)
    "f_3db",  # first -3 dB crossing relative to the passband peak (ac)
    "value_at",  # interpolated value at sweep/time point `at`
)

_NUM = {"type": ["number", "null"]}
_STR_OR_NULL = {"type": ["string", "null"]}

# JSON schema used for AI structured output. Every property is required and nullable values
# are expressed as ["type","null"] so it works with strict structured-output modes.
PLAN_SCHEMA: dict = {
    "type": "object",
    "additionalProperties": False,
    "required": ["title", "summary", "assumptions", "netlist", "analyses", "design_notes"],
    "properties": {
        "title": {"type": "string"},
        "summary": {"type": "string"},
        "assumptions": {"type": "array", "items": {"type": "string"}},
        "netlist": {"type": "string"},
        "design_notes": {"type": "array", "items": {"type": "string"}},
        "analyses": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "id", "title", "kind", "instrument", "signals", "reference_signal",
                    "f_start", "f_stop", "points_per_decade",
                    "t_step", "t_stop", "t_start",
                    "dc_source", "dc_start", "dc_stop", "dc_step",
                    "measurements",
                ],
                "properties": {
                    "id": {"type": "string"},
                    "title": {"type": "string"},
                    "kind": {"type": "string", "enum": list(ANALYSIS_KINDS)},
                    "instrument": {"type": "string", "enum": list(INSTRUMENTS)},
                    "signals": {"type": "array", "items": {"type": "string"}},
                    "reference_signal": _STR_OR_NULL,
                    "f_start": _NUM,
                    "f_stop": _NUM,
                    "points_per_decade": {"type": ["integer", "null"]},
                    "t_step": _NUM,
                    "t_stop": _NUM,
                    "t_start": _NUM,
                    "dc_source": _STR_OR_NULL,
                    "dc_start": _NUM,
                    "dc_stop": _NUM,
                    "dc_step": _NUM,
                    "measurements": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "additionalProperties": False,
                            "required": ["name", "type", "signal", "at", "expected", "tolerance_pct", "unit"],
                            "properties": {
                                "name": {"type": "string"},
                                "type": {"type": "string", "enum": list(MEASUREMENT_TYPES)},
                                "signal": {"type": "string"},
                                "at": _NUM,
                                "expected": _NUM,
                                "tolerance_pct": _NUM,
                                "unit": {"type": "string"},
                            },
                        },
                    },
                },
            },
        },
    },
}


@dataclass
class Measurement:
    name: str
    type: str
    signal: str
    at: float | None = None
    expected: float | None = None
    tolerance_pct: float | None = None
    unit: str = ""


@dataclass
class Analysis:
    id: str
    title: str
    kind: str
    instrument: str
    signals: list[str]
    reference_signal: str | None = None
    f_start: float | None = None
    f_stop: float | None = None
    points_per_decade: int | None = None
    t_step: float | None = None
    t_stop: float | None = None
    t_start: float | None = None
    dc_source: str | None = None
    dc_start: float | None = None
    dc_stop: float | None = None
    dc_step: float | None = None
    measurements: list[Measurement] = field(default_factory=list)


@dataclass
class Plan:
    title: str
    summary: str
    netlist: str
    analyses: list[Analysis]
    assumptions: list[str] = field(default_factory=list)
    design_notes: list[str] = field(default_factory=list)

    # ---- (de)serialisation --------------------------------------------
    @classmethod
    def from_dict(cls, d: dict) -> "Plan":
        analyses = []
        for a in d.get("analyses", []):
            a = dict(a)
            ms = [Measurement(**{k: m.get(k) for k in Measurement.__dataclass_fields__ if k in m}) for m in a.pop("measurements", [])]
            known = {k: a.get(k) for k in Analysis.__dataclass_fields__ if k in a and k != "measurements"}
            analyses.append(Analysis(**known, measurements=ms))
        return cls(
            title=d.get("title", "Untitled design"),
            summary=d.get("summary", ""),
            netlist=d.get("netlist", ""),
            analyses=analyses,
            assumptions=list(d.get("assumptions", []) or []),
            design_notes=list(d.get("design_notes", []) or []),
        )

    @classmethod
    def from_json(cls, text: str) -> "Plan":
        return cls.from_dict(json.loads(text))

    @classmethod
    def load(cls, path: str | Path) -> "Plan":
        return cls.from_json(Path(path).read_text(encoding="utf-8"))

    def to_dict(self) -> dict:
        return asdict(self)

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), indent=2)

    def save(self, path: str | Path) -> Path:
        path = Path(path)
        path.write_text(self.to_json(), encoding="utf-8")
        return path

    # ---- validation ----------------------------------------------------
    def validate(self) -> list[str]:
        """Return a list of human-readable problems (empty list == runnable)."""
        problems: list[str] = []
        if not self.netlist.strip():
            problems.append("Netlist is empty.")
        if re.search(r"^\s*\.control\b", self.netlist, re.I | re.M):
            problems.append("Netlist must not contain a .control block; analyses are generated from the plan.")
        if not self.analyses:
            problems.append("Plan has no analyses.")
        seen: set[str] = set()
        for a in self.analyses:
            tag = f"Analysis '{a.id}'"
            if not re.fullmatch(r"[A-Za-z0-9_\-]+", a.id or ""):
                problems.append(f"{tag}: id must be letters/digits/_/- only.")
            if a.id in seen:
                problems.append(f"{tag}: duplicate id.")
            seen.add(a.id)
            if a.kind not in ANALYSIS_KINDS:
                problems.append(f"{tag}: unknown kind '{a.kind}'.")
            if a.instrument not in INSTRUMENTS:
                problems.append(f"{tag}: unknown instrument '{a.instrument}'.")
            if not a.signals:
                problems.append(f"{tag}: no signals to record.")
            for s in a.signals + ([a.reference_signal] if a.reference_signal else []):
                if not _SIGNAL_RE.fullmatch(s.strip()):
                    problems.append(f"{tag}: signal '{s}' must look like v(node), v(n1,n2) or i(Vsource).")
            if a.kind == "ac":
                if not (a.f_start and a.f_stop and a.f_stop > a.f_start > 0):
                    problems.append(f"{tag}: AC needs 0 < f_start < f_stop.")
            if a.kind == "tran":
                if not (a.t_step and a.t_stop and a.t_stop > a.t_step > 0):
                    problems.append(f"{tag}: transient needs 0 < t_step < t_stop.")
            if a.kind == "dc":
                if not a.dc_source or a.dc_start is None or a.dc_stop is None or not a.dc_step:
                    problems.append(f"{tag}: DC sweep needs dc_source, dc_start, dc_stop, dc_step.")
            for m in a.measurements:
                if m.type not in MEASUREMENT_TYPES:
                    problems.append(f"{tag}: measurement '{m.name}' has unknown type '{m.type}'.")
                if m.type in ("gain_db_at", "phase_at", "value_at") and m.at is None:
                    problems.append(f"{tag}: measurement '{m.name}' ({m.type}) needs 'at'.")
        return problems


_SIGNAL_RE = re.compile(r"(?i)(v\(\s*[\w.:+\-]+\s*(,\s*[\w.:+\-]+\s*)?\)|i\(\s*[\w.:+\-]+\s*\))")


def extract_json_object(text: str) -> dict:
    """Pull the first JSON object out of a model reply (handles ```json fences and prose)."""
    text = text.strip()
    fence = re.search(r"```(?:json)?\s*(\{.*\})\s*```", text, re.S)
    if fence:
        text = fence.group(1)
    start = text.find("{")
    if start < 0:
        raise ValueError("No JSON object found in AI reply.")
    depth, in_str, esc = 0, False, False
    for i in range(start, len(text)):
        ch = text[i]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return json.loads(text[start : i + 1])
    raise ValueError("Unterminated JSON object in AI reply.")
