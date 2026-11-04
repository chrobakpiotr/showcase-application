#!/usr/bin/env python3
"""Parallel outer-loop orchestrator for the repo-native SDD protocol.

This process owns task leasing/heartbeats, crash recovery, worktree lifecycle, provider routing,
specialist review, bounded rework and completion checkpoints. Coding-agent processes stay inside
runner.py and are never allowed to push or mutate repository remotes.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import contextlib
import datetime as dt
import hashlib
import json
import re
import math
import pathlib
import subprocess
import sys
import threading
import uuid
from dataclasses import dataclass
from typing import Any, Iterator

HERE = pathlib.Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import harness as h  # noqa: E402
import telemetry  # noqa: E402
from machine_outcomes import exit_code, is_control_outcome  # noqa: E402
from verification.authority import prepare_task_plan  # noqa: E402
from verification.profile import load_profile  # noqa: E402
import verification_sandbox  # noqa: E402
from agent_harness import feature_transitions as _transitions  # noqa: E402
from agent_harness import orchestration as _orchestration  # noqa: E402

RUNNER = HERE / 'runner.py'
REPO = HERE.parents[1]

VERIFICATION_BLOCKAGE_OUTCOMES = _orchestration.VERIFICATION_BLOCKAGE_OUTCOMES
VERIFICATION_OUTCOMES = _orchestration.VERIFICATION_OUTCOMES
MAX_SAFE_JSON_INTEGER = _orchestration.MAX_SAFE_JSON_INTEGER
MAX_PROCESS_EXIT_CODE = _orchestration.MAX_PROCESS_EXIT_CODE

ProviderChoice = _orchestration.ProviderChoice
TaskOutcome = _orchestration.TaskOutcome
AgentPreflightRefused = _orchestration.AgentPreflightRefused


def die(message: str) -> None:
    return _orchestration.die(message, ns=_NS)


def load_result(path: pathlib.Path) -> dict[str, Any]:
    return _orchestration.load_result(path, ns=_NS)


def choice_for_role(role: str, args: argparse.Namespace) -> ProviderChoice:
    return _orchestration.choice_for_role(role, args, ns=_NS)


def review_choice(args: argparse.Namespace) -> ProviderChoice:
    return _orchestration.review_choice(args, ns=_NS)


def run_accepted_verification(
    worktree: pathlib.Path, output_dir: pathlib.Path, plan_id: str, timeout_seconds: int,
    *, expected_task_id: str, expected_task_attempt: int,
    expected_record: dict[str, Any] | None = None,
) -> tuple[dict[str, Any], subprocess.CompletedProcess[str]]:
    return _orchestration.run_accepted_verification(worktree, output_dir, plan_id, timeout_seconds, expected_task_id=expected_task_id, expected_task_attempt=expected_task_attempt, expected_record=expected_record, ns=_NS)


def owner_for(args: argparse.Namespace, task_id: str) -> str:
    return _orchestration.owner_for(args, task_id, ns=_NS)


def runner_command(
    packet: pathlib.Path,
    worktree: pathlib.Path,
    choice: ProviderChoice,
    args: argparse.Namespace,
    *,
    profile: str | None = None,
    review_existing: bool = False,
    feedback_file: pathlib.Path | None = None,
    defer_verification: bool = False,
) -> list[str]:
    return _orchestration.runner_command(packet, worktree, choice, args, profile=profile, review_existing=review_existing, feedback_file=feedback_file, defer_verification=defer_verification, ns=_NS)


def invoke_runner(cmd: list[str]) -> pathlib.Path:
    return _orchestration.invoke_runner(cmd, ns=_NS)


def invoke_preflight(cmd: list[str]) -> None:
    return _orchestration.invoke_preflight(cmd, ns=_NS)


def preflight_task_launches(
    task: dict[str, Any], packet: pathlib.Path, worktree: pathlib.Path,
    choice: ProviderChoice, args: argparse.Namespace, feedback: pathlib.Path | None,
    *, verification_resume: bool = False,
) -> None:
    return _orchestration.preflight_task_launches(task, packet, worktree, choice, args, feedback, verification_resume=verification_resume, ns=_NS)


def prepare_ready_task_for_preflight(
    feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str,
    args: argparse.Namespace, feedback: pathlib.Path | None,
) -> tuple[dict[str, Any], pathlib.Path, pathlib.Path, pathlib.Path | None]:
    return _orchestration.prepare_ready_task_for_preflight(feature_dir, doc, task_id, args, feedback, ns=_NS)


def lease_heartbeat(
    feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, owner: str
) -> Iterator[None]:
    return _orchestration.lease_heartbeat(feature_dir, doc, task_id, owner, ns=_NS)


def prior_feedback(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str) -> pathlib.Path | None:
    return _orchestration.prior_feedback(feature_dir, doc, task_id, ns=_NS)


def aggregate_success(main_path: pathlib.Path, reviews: list[tuple[str, pathlib.Path]]) -> pathlib.Path:
    return _orchestration.aggregate_success(main_path, reviews, ns=_NS)


def verification_evidence_summary(result: dict[str, Any], plan_record: dict[str, Any],
                                  process_exit_code: int) -> dict[str, Any]:
    return _orchestration.verification_evidence_summary(result, plan_record, process_exit_code, ns=_NS)


def run_started_task(
    feature_dir: pathlib.Path,
    doc: dict[str, Any],
    task: dict[str, Any],
    worktree: pathlib.Path,
    packet: pathlib.Path,
    args: argparse.Namespace,
    feedback: pathlib.Path | None,
) -> TaskOutcome:
    return _orchestration.run_started_task(feature_dir, doc, task, worktree, packet, args, feedback, ns=_NS)


def run_resumed_verification(
    feature_dir: pathlib.Path, doc: dict[str, Any], task: dict[str, Any],
    worktree: pathlib.Path, packet: pathlib.Path, args: argparse.Namespace,
    resume: dict[str, Any], task_state: dict[str, Any],
) -> TaskOutcome:
    return _orchestration.run_resumed_verification(feature_dir, doc, task, worktree, packet, args, resume, task_state, ns=_NS)


def start_ready_batch(
    feature_dir: pathlib.Path, doc: dict[str, Any], args: argparse.Namespace
) -> list[tuple[dict[str, Any], pathlib.Path, pathlib.Path, pathlib.Path | None]]:
    return _orchestration.start_ready_batch(feature_dir, doc, args, ns=_NS)


def apply_outcome(feature_dir: pathlib.Path, doc: dict[str, Any], outcome: TaskOutcome, args: argparse.Namespace) -> None:
    return _orchestration.apply_outcome(feature_dir, doc, outcome, args, ns=_NS)


def stop_on_repository_admission_blockage(
    feature_dir: pathlib.Path, doc: dict[str, Any], args: argparse.Namespace,
    manifest_path: pathlib.Path, outcomes: list[TaskOutcome], round_no: int,
    recovered_total: list[str],
) -> None:
    return _orchestration.stop_on_repository_admission_blockage(feature_dir, doc, args, manifest_path, outcomes, round_no, recovered_total, ns=_NS)


def print_plan(feature_dir: pathlib.Path, doc: dict[str, Any], args: argparse.Namespace) -> None:
    return _orchestration.print_plan(feature_dir, doc, args, ns=_NS)


def orchestration_manifest_path(feature: str, run_id: str) -> pathlib.Path:
    return _orchestration.orchestration_manifest_path(feature, run_id, ns=_NS)


def write_orchestration_manifest(path: pathlib.Path, doc: dict[str, Any], args: argparse.Namespace, **updates: Any) -> None:
    return _orchestration.write_orchestration_manifest(path, doc, args, ns=_NS, **updates)


def main() -> None:
    return _orchestration.main(ns=_NS)


# AH5-15: the library's orchestration reads this module's names through `_NS`; checked once, here, before any
# operation (a missing name refuses the import).
_NS = _orchestration.bind(_transitions.ModuleNamespace(globals()))


if __name__ == '__main__':
    main()
