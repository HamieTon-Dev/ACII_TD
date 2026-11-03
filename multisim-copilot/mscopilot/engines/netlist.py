"""Turn a Plan into SPICE decks.

Two decks are written per run:
- ``<name>.cir``: the plain circuit with SPICE dot-commands (.op/.ac/.tran). This is the
  file to open in Multisim (File > Open, file type "SPICE Netlist") - Multisim converts it
  into a schematic you can save as .ms14.
- ``<name>_ngspice.cir``: the same circuit with an ngspice ``.control`` block that writes
  every analysis to a data file the report generator reads.
"""
from __future__ import annotations

import re

from mscopilot.plan import Analysis, Plan

_END_RE = re.compile(r"^\s*\.end\s*$", re.I | re.M)
_ANALYSIS_RE = re.compile(r"^\s*\.(op|ac|tran|dc|noise|tf|four|disto|pz|sens)\b.*$", re.I | re.M)


def clean_netlist(netlist: str, title: str) -> str:
    """Strip analysis/.end lines (they are generated) and make sure a title line exists."""
    body = _END_RE.sub("", netlist)
    body = _ANALYSIS_RE.sub("", body)
    lines = [ln.rstrip() for ln in body.strip().splitlines()]
    if not lines or not lines[0].lstrip().startswith("*"):
        lines.insert(0, f"* {title}")
    return "\n".join(lines)


def analysis_card(a: Analysis) -> str:
    if a.kind == "op":
        return ".op"
    if a.kind == "ac":
        return f".ac dec {int(a.points_per_decade or 50)} {a.f_start:g} {a.f_stop:g}"
    if a.kind == "tran":
        start = f" {a.t_start:g}" if a.t_start else ""
        # max step = t_step keeps the waveform smooth enough for scope-style plots
        return f".tran {a.t_step:g} {a.t_stop:g}{start} {a.t_step:g}"
    if a.kind == "dc":
        return f".dc {a.dc_source} {a.dc_start:g} {a.dc_stop:g} {a.dc_step:g}"
    raise ValueError(a.kind)


def _control_line(a: Analysis) -> str:
    card = analysis_card(a)[1:]  # same syntax without the dot inside .control
    return card


def _vectors(a: Analysis) -> list[str]:
    vecs = [s.strip() for s in a.signals]
    if a.reference_signal and a.reference_signal.strip() not in vecs:
        vecs.append(a.reference_signal.strip())
    # measurement signals must be recorded too
    for m in a.measurements:
        s = m.signal.strip()
        if s and s not in vecs:
            vecs.append(s)
    return vecs


def multisim_deck(plan: Plan) -> str:
    """Circuit + analysis dot-commands, for importing into Multisim / any SPICE tool."""
    out = [clean_netlist(plan.netlist, plan.title), "", "* Analyses requested by the plan"]
    seen = set()
    for a in plan.analyses:
        card = analysis_card(a)
        if card not in seen:
            out.append(card)
            seen.add(card)
    out.append(".end")
    return "\n".join(out) + "\n"


def ngspice_deck(plan: Plan, data_files: dict[str, str]) -> str:
    """Circuit + .control block writing each analysis to ``data_files[analysis.id]``."""
    out = [clean_netlist(plan.netlist, plan.title), "", ".control", "set wr_singlescale", "set wr_vecnames", "set noaskquit"]
    for a in plan.analyses:
        out.append(f"echo MSCOPILOT_BEGIN {a.id}")
        out.append(_control_line(a))
        out.append(f"wrdata {_quote(data_files[a.id])} {' '.join(_vectors(a))}")
        out.append(f"echo MSCOPILOT_END {a.id}")
        out.append("destroy all")
    out += ["quit", ".endc", ".end"]
    return "\n".join(out) + "\n"


def _quote(path: str) -> str:
    # ngspice accepts forward slashes on Windows. wrdata cannot take quoted paths, so the
    # caller passes relative paths without spaces.
    p = path.replace("\\", "/")
    if " " in p:
        raise ValueError(f"ngspice data path must not contain spaces: {p}")
    return p
