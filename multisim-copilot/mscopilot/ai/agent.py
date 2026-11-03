"""High-level AI tasks: plan a design, review results, revise a failing design."""
from __future__ import annotations

import json

from mscopilot.ai.prompts import (
    PLANNER_SYSTEM,
    PLANNER_USER_PREFIX,
    REVIEW_SCHEMA,
    REVIEWER_SYSTEM,
    REVISE_SUFFIX,
)
from mscopilot.ai.providers import AIError, Prompt, Provider
from mscopilot.inputs import DesignPackage
from mscopilot.plan import PLAN_SCHEMA, Plan

MAX_DOC_CHARS = 200_000


def package_text(pkg: DesignPackage) -> str:
    parts = [PLANNER_USER_PREFIX]
    if pkg.instructions.strip():
        parts.append("## Instructions typed by the engineer\n" + pkg.instructions.strip())
    for name, text in pkg.documents:
        if len(text) > MAX_DOC_CHARS:
            raise AIError(f"Document {name} is very large ({len(text):,} chars). Split it or trim it to the relevant sections.")
        parts.append(f"## Document: {name}\n{text}")
    for name, text in pkg.netlists:
        parts.append(f"## Netlist supplied by the engineer: {name}\n```spice\n{text}\n```")
    for p in pkg.multisim_files:
        parts.append(f"## Multisim circuit supplied: {p.name} (it will also be opened in Multisim; describe it from the other inputs)")
    if pkg.images:
        parts.append(f"{len(pkg.images)} image(s) are attached (schematics, plots, datasheet excerpts).")
    return "\n\n".join(parts)


def plan_design(provider: Provider, pkg: DesignPackage) -> Plan:
    data = provider.complete_json(Prompt(PLANNER_SYSTEM, package_text(pkg), pkg.images, PLAN_SCHEMA, "simulation_plan"))
    return Plan.from_dict(data)


def results_digest(plan: Plan, results) -> str:
    """Compact, model-friendly summary of a simulation run."""
    lines = [f"# Plan: {plan.title}", plan.summary, "", "## Netlist", "```spice", plan.netlist, "```", "", "## Results"]
    for r in results:
        lines.append(f"### {r.analysis.title} ({r.analysis.kind}, {r.analysis.instrument})")
        if r.error:
            lines.append(f"ERROR: {r.error}")
            continue
        for m in r.measurements:
            exp = "" if m.expected is None else f" expected {m.expected:g}{m.unit} ±{m.tolerance_pct or 0:g}%"
            val = "n/a" if m.value is None else f"{m.value:.6g}{m.unit}"
            lines.append(f"- {m.name}: {val}{exp} -> {m.status}{(' (' + m.note + ')') if m.note else ''}")
        if r.analysis.kind == "op":
            for k, v in r.op_values().items():
                lines.append(f"- {k} = {v:.6g}")
    return "\n".join(lines)


def review_results(provider: Provider, plan: Plan, results, images=None) -> dict:
    text = "Review these simulation results against the design brief summarised in the plan.\n\n" + results_digest(plan, results)
    return provider.complete_json(Prompt(REVIEWER_SYSTEM, text, images or [], REVIEW_SCHEMA, "engineering_review"))


def revise_plan(provider: Provider, pkg: DesignPackage, plan: Plan, results) -> Plan:
    text = (
        package_text(pkg)
        + "\n\n## Previous plan\n```json\n"
        + json.dumps(plan.to_dict(), indent=1)
        + "\n```\n\n"
        + REVISE_SUFFIX
        + "\n"
        + results_digest(plan, results)
    )
    data = provider.complete_json(Prompt(PLANNER_SYSTEM, text, pkg.images, PLAN_SCHEMA, "simulation_plan"))
    return Plan.from_dict(data)
