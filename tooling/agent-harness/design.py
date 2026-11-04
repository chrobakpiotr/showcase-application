#!/usr/bin/env python3
"""Pre-implementation design loop for the Agentic SDD protocol.

Runs adversarial specification grilling, conditional disposable technical prototypes/bake-offs,
and a second architecture-plan grill before executable task orchestration is allowed. Durable
findings live with the feature; provider logs and scratch worktrees remain local/runtime-only.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import datetime as dt
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import threading
import time
import uuid
from dataclasses import dataclass
from typing import Any

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import harness as h  # noqa: E402
import runner as r  # noqa: E402
import telemetry  # noqa: E402
import trust  # noqa: E402
from agent_harness import feature_transitions as _transitions  # noqa: E402
from agent_harness import design as _design  # noqa: E402

DESIGN_SCHEMA = HERE / 'schemas' / 'design-result.schema.json'

VALID_MODES = _design.VALID_MODES
RISK_ORDER = _design.RISK_ORDER
DESIGN_PROTOCOL_VERSION = _design.DESIGN_PROTOCOL_VERSION
_WORKTREE_LOCK = _design._WORKTREE_LOCK

StagePlan = _design.StagePlan


def die(message: str) -> None:
    return _design.die(message, ns=_NS)


def load_json(path: pathlib.Path) -> dict[str, Any]:
    return _design.load_json(path, ns=_NS)


def file_sha256(path: pathlib.Path) -> str:
    return _design.file_sha256(path, ns=_NS)


def risk_level(spec_text: str) -> str:
    return _design.risk_level(spec_text, ns=_NS)


def validate_config(config: dict[str, Any]) -> list[str]:
    return _design.validate_config(config, ns=_NS)


def load_config(feature_dir: pathlib.Path) -> dict[str, Any]:
    return _design.load_config(feature_dir, ns=_NS)


def effective_mode(config: dict[str, Any], field: str, override: str | None) -> str:
    return _design.effective_mode(config, field, override, ns=_NS)


def stage_plan(feature_dir: pathlib.Path, config: dict[str, Any], args: argparse.Namespace) -> StagePlan:
    return _design.stage_plan(feature_dir, config, args, ns=_NS)


def require_waiver(plan: StagePlan, args: argparse.Namespace) -> None:
    return _design.require_waiver(plan, args, ns=_NS)


def normalize_recommendation(raw: Any, fallback_index: int) -> dict[str, Any] | None:
    return _design.normalize_recommendation(raw, fallback_index, ns=_NS)


def validate_design_result(result: dict[str, Any]) -> None:
    return _design.validate_design_result(result, ns=_NS)


def design_worktree_root(feature: str) -> pathlib.Path:
    return _design.design_worktree_root(feature, ns=_NS)


def design_base(feature_dir: pathlib.Path, feature: str) -> str:
    return _design.design_base(feature_dir, feature, ns=_NS)


def create_worktree(feature: str, label: str, base: str) -> pathlib.Path:
    return _design.create_worktree(feature, label, base, ns=_NS)


def remove_worktree(target: pathlib.Path) -> None:
    return _design.remove_worktree(target, ns=_NS)


def profile_text(worktree: pathlib.Path, profile: str) -> str:
    return _design.profile_text(worktree, profile, ns=_NS)


def run_provider(
    *, feature: str, stage: str, prompt: str, worktree: pathlib.Path, profile: str,
    args: argparse.Namespace, read_only: bool,
) -> tuple[dict[str, Any], pathlib.Path]:
    return _design.run_provider(feature=feature, stage=stage, prompt=prompt, worktree=worktree, profile=profile, args=args, read_only=read_only, ns=_NS)


def prompt_for(profile: str, worktree: pathlib.Path, body: str) -> str:
    return _design.prompt_for(profile, worktree, body, ns=_NS)


def run_spec_grill(feature_dir: pathlib.Path, spec: str, plan: str, base: str, args: argparse.Namespace) -> dict[str, Any]:
    return _design.run_spec_grill(feature_dir, spec, plan, base, args, ns=_NS)


def run_architecture_grill(
    feature_dir: pathlib.Path, spec: str, plan: str, prototype_summary: str, base: str, args: argparse.Namespace
) -> dict[str, Any]:
    return _design.run_architecture_grill(feature_dir, spec, plan, prototype_summary, base, args, ns=_NS)


def candidate_label(question_id: str, candidate_id: str) -> str:
    return _design.candidate_label(question_id, candidate_id, ns=_NS)


def run_prototype_candidate(
    feature_dir: pathlib.Path, spec: str, plan: str, question: dict[str, Any], candidate: dict[str, Any], base: str,
    args: argparse.Namespace,
) -> dict[str, Any]:
    return _design.run_prototype_candidate(feature_dir, spec, plan, question, candidate, base, args, ns=_NS)


def run_prototype_evaluator(
    feature_dir: pathlib.Path, question: dict[str, Any], candidates: list[dict[str, Any]], base: str,
    args: argparse.Namespace,
) -> dict[str, Any]:
    return _design.run_prototype_evaluator(feature_dir, question, candidates, base, args, ns=_NS)


def write_durable(path: pathlib.Path, value: dict[str, Any]) -> None:
    return _design.write_durable(path, value, ns=_NS)


def blockers(result: dict[str, Any]) -> bool:
    return _design.blockers(result, ns=_NS)


def main() -> None:
    return _design.main(ns=_NS)


# The library's design reads this module's names through `_NS`; checked once, here, before any operation (a
# missing name refuses the import).
_NS = _design.bind(_transitions.ModuleNamespace(globals()))


if __name__ == '__main__':
    main()
