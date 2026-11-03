"""Run a plan with ngspice (SPICE engine compatible with Multisim's XSPICE core)."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from mscopilot.engines.netlist import multisim_deck, ngspice_deck
from mscopilot.engines.results import AnalysisResult, compute_measurement, parse_wrdata, write_csv
from mscopilot.plan import Plan


class SimulationError(RuntimeError):
    pass


def run_plan(plan: Plan, ngspice: str, workdir: Path, name: str = "circuit", timeout: float = 600, log=print) -> list[AnalysisResult]:
    workdir = Path(workdir)
    data_dir = workdir / "data"
    data_dir.mkdir(parents=True, exist_ok=True)

    (workdir / f"{name}.cir").write_text(multisim_deck(plan), encoding="utf-8")
    # ngspice runs with cwd=workdir and is given *relative* data paths: its wrdata command
    # cannot handle spaces in paths (e.g. C:\\Users\\John Smith\\Documents), and plan ids
    # are restricted to [A-Za-z0-9_-] so the relative names never contain one.
    data_files = {a.id: f"data/{a.id}.txt" for a in plan.analyses}
    for f in data_files.values():
        (workdir / f).unlink(missing_ok=True)
    deck_path = workdir / f"{name}_ngspice.cir"
    deck_path.write_text(ngspice_deck(plan, data_files), encoding="utf-8")

    log(f"Running ngspice on {deck_path.name} ...")
    flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
    try:
        proc = subprocess.run(
            [ngspice, "-b", str(deck_path.resolve())],
            cwd=str(workdir),
            capture_output=True,
            text=True,
            timeout=timeout,
            creationflags=flags,
        )
    except FileNotFoundError as e:
        raise SimulationError(f"ngspice not found at {ngspice}") from e
    except subprocess.TimeoutExpired as e:
        raise SimulationError(f"ngspice did not finish within {timeout:.0f}s") from e
    out = (proc.stdout or "") + "\n" + (proc.stderr or "")
    (workdir / f"{name}_ngspice.log").write_text(out, encoding="utf-8")

    results: list[AnalysisResult] = []
    for a in plan.analyses:
        r = AnalysisResult(analysis=a)
        f = workdir / data_files[a.id]
        if not f.exists() or f.stat().st_size == 0:
            r.error = "No data produced. " + _error_excerpt(out, a.id)
            results.append(r)
            continue
        try:
            r.scale_name, r.scale, r.data = parse_wrdata(f, a.kind)
            write_csv(r, data_dir / f"{a.id}.csv")
            r.measurements = [compute_measurement(r, m) for m in a.measurements]
        except Exception as e:
            r.error = f"Could not read results: {e}"
        results.append(r)
    if all(r.error for r in results):
        raise SimulationError("ngspice produced no results. Log excerpt:\n" + _error_excerpt(out, None))
    return results


def _error_excerpt(output: str, analysis_id: str | None) -> str:
    lines = output.splitlines()
    if analysis_id:
        try:
            start = next(i for i, ln in enumerate(lines) if ln.strip() == f"MSCOPILOT_BEGIN {analysis_id}")
            end = next((i for i, ln in enumerate(lines[start:], start) if ln.strip() == f"MSCOPILOT_END {analysis_id}"), len(lines))
            seg = [ln for ln in lines[start + 1 : end] if ln.strip()]
            if seg:
                return " | ".join(seg[-8:])
        except StopIteration:
            pass
    errs = [ln for ln in lines if "error" in ln.lower() or "warning" in ln.lower()]
    return "\n".join((errs or lines)[-15:])
