#!/usr/bin/env python3
"""Decision-map multi-session discovery for large/foggy engineering efforts.

This is a repo-native decision-map layer that sits *before* spec-driven implementation.
It produces decisions, not production code. Once the map converges, `to-spec` collapses
those decisions into a buildable feature spec/plan. After the normal design gate passes,
`to-tasks` creates the executable task DAG consumed by orchestrate.py.
"""
from __future__ import annotations

import argparse
import contextlib
import datetime as dt
import fcntl
import hashlib
import json
import os
import pathlib
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import uuid
from typing import Any, Iterator

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import harness as h  # noqa: E402
import runner as r  # noqa: E402
import design as d  # noqa: E402
import telemetry  # noqa: E402
import trust  # noqa: E402
import verification_contract as vc  # noqa: E402
import repository_state_lock  # noqa: E402
import operational_state  # noqa: E402
from agent_harness import feature_transitions as _transitions  # noqa: E402
from agent_harness import decision_map as _decision_map  # noqa: E402

RESULT_SCHEMA = HERE / 'schemas' / 'decision-map-result.schema.json'
HANDOFF_SCHEMA = HERE / 'schemas' / 'decision-map-handoff.schema.json'
TASKS_SCHEMA = HERE / 'schemas' / 'decision-map-tasks-result.schema.json'

MAP_FILE = _decision_map.MAP_FILE
PROTOCOL_VERSION = _decision_map.PROTOCOL_VERSION
SUPPORTED_PROTOCOL_VERSIONS = _decision_map.SUPPORTED_PROTOCOL_VERSIONS
VALID_LEDGER_TYPES = _decision_map.VALID_LEDGER_TYPES
VALID_LEDGER_STATUSES = _decision_map.VALID_LEDGER_STATUSES
TERMINAL_FOG_STATUSES = _decision_map.TERMINAL_FOG_STATUSES
VALID_DECISION_TYPES = _decision_map.VALID_DECISION_TYPES
VALID_STATUSES = _decision_map.VALID_STATUSES



def die(message: str) -> None:
    return _decision_map.die(message, ns=_NS)


def load_json(path: pathlib.Path) -> dict[str, Any]:
    return _decision_map.load_json(path, ns=_NS)


def atomic_json(path: pathlib.Path, value: dict[str, Any]) -> None:
    return _decision_map.atomic_json(path, value, ns=_NS)


def map_path(map_dir: pathlib.Path) -> pathlib.Path:
    return _decision_map.map_path(map_dir, ns=_NS)


def state_repo_root() -> pathlib.Path | None:
    return _decision_map.state_repo_root(ns=_NS)


def state_root(epic: str) -> pathlib.Path:
    return _decision_map.state_root(epic, ns=_NS)


def _lifecycle_lock(root: pathlib.Path) -> Iterator[None]:
    return _decision_map._lifecycle_lock(root, ns=_NS)


def _ensure_map_lock(root: pathlib.Path) -> pathlib.Path:
    return _decision_map._ensure_map_lock(root, ns=_NS)


def _validate_map_lock(path: pathlib.Path, fd: int | None) -> tuple[int, int]:
    return _decision_map._validate_map_lock(path, fd, ns=_NS)


def locked_claim_state(epic: str) -> Iterator[None]:
    return _decision_map.locked_claim_state(epic, ns=_NS)


def locked_map(map_dir: pathlib.Path) -> Iterator[dict[str, Any]]:
    return _decision_map.locked_map(map_dir, ns=_NS)


def iso_now() -> str:
    return _decision_map.iso_now(ns=_NS)


def parse_time(value: str | None) -> dt.datetime | None:
    return _decision_map.parse_time(value, ns=_NS)


def load_map(map_dir: pathlib.Path) -> dict[str, Any]:
    return _decision_map.load_map(map_dir, ns=_NS)


def decision_index(doc: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return _decision_map.decision_index(doc, ns=_NS)


def validate_map(doc: dict[str, Any], map_dir: pathlib.Path | None = None) -> list[str]:
    return _decision_map.validate_map(doc, map_dir, ns=_NS)


def open_fog(doc: dict[str, Any]) -> list[dict[str, Any]]:
    return _decision_map.open_fog(doc, ns=_NS)


def claim_path(epic: str, decision_id: str) -> pathlib.Path:
    return _decision_map.claim_path(epic, decision_id, ns=_NS)


def _active_claim_unlocked(doc: dict[str, Any], decision_id: str) -> dict[str, Any] | None:
    return _decision_map._active_claim_unlocked(doc, decision_id, ns=_NS)


def active_claim(doc: dict[str, Any], decision_id: str) -> dict[str, Any] | None:
    return _decision_map.active_claim(doc, decision_id, ns=_NS)


def acquire_claim(doc: dict[str, Any], decision_id: str, owner: str) -> None:
    return _decision_map.acquire_claim(doc, decision_id, owner, ns=_NS)


def release_claim(doc: dict[str, Any], decision_id: str) -> None:
    return _decision_map.release_claim(doc, decision_id, ns=_NS)


def downstream_count(doc: dict[str, Any], decision_id: str) -> int:
    return _decision_map.downstream_count(doc, decision_id, ns=_NS)


def leverage_score(doc: dict[str, Any], item: dict[str, Any]) -> int:
    return _decision_map.leverage_score(doc, item, ns=_NS)


def frontier(doc: dict[str, Any], *, include_claimed: bool = False) -> list[dict[str, Any]]:
    return _decision_map.frontier(doc, include_claimed=include_claimed, ns=_NS)


def active_blocking_assumptions(doc: dict[str, Any]) -> list[dict[str, Any]]:
    return _decision_map.active_blocking_assumptions(doc, ns=_NS)


def terminal_reconciliation_errors(doc: dict[str, Any]) -> list[str]:
    return _decision_map.terminal_reconciliation_errors(doc, ns=_NS)


def cleared(doc: dict[str, Any]) -> bool:
    return _decision_map.cleared(doc, ns=_NS)


def validate_result(result: dict[str, Any], *, allow_empty_decision: bool = False, decision_type: str | None = None) -> None:
    return _decision_map.validate_result(result, allow_empty_decision=allow_empty_decision, decision_type=decision_type, ns=_NS)


def profile_text(worktree: pathlib.Path, profile: str) -> str:
    return _decision_map.profile_text(worktree, profile, ns=_NS)


def run_provider_json(*, epic: str, stage: str, prompt: str, worktree: pathlib.Path, profile: str,
                      args: argparse.Namespace, schema: pathlib.Path, read_only: bool) -> tuple[dict[str, Any], pathlib.Path]:
    return _decision_map.run_provider_json(epic=epic, stage=stage, prompt=prompt, worktree=worktree, profile=profile, args=args, schema=schema, read_only=read_only, ns=_NS)


def map_summary(doc: dict[str, Any]) -> str:
    return _decision_map.map_summary(doc, ns=_NS)


def prompt_for_decision(doc: dict[str, Any], item: dict[str, Any], worktree: pathlib.Path) -> str:
    return _decision_map.prompt_for_decision(doc, item, worktree, ns=_NS)


def next_decision_id(doc: dict[str, Any]) -> str:
    return _decision_map.next_decision_id(doc, ns=_NS)


def next_fog_id(doc: dict[str, Any]) -> str:
    return _decision_map.next_fog_id(doc, ns=_NS)


def normalize_new_decision(doc: dict[str, Any], raw: Any, parent: str) -> dict[str, Any] | None:
    return _decision_map.normalize_new_decision(doc, raw, parent, ns=_NS)


def next_ledger_id(doc: dict[str, Any]) -> str:
    return _decision_map.next_ledger_id(doc, ns=_NS)


def add_ledger_entry(doc: dict[str, Any], raw: dict[str, Any], source_decision: str) -> str:
    return _decision_map.add_ledger_entry(doc, raw, source_decision, ns=_NS)


def apply_result(map_dir: pathlib.Path, decision_id: str, result: dict[str, Any], *, allow_needs_human: bool = False) -> None:
    return _decision_map.apply_result(map_dir, decision_id, result, allow_needs_human=allow_needs_human, ns=_NS)


def repo_base_for_map(map_dir: pathlib.Path, doc: dict[str, Any]) -> str:
    return _decision_map.repo_base_for_map(map_dir, doc, ns=_NS)


def resolve_decision(map_dir: pathlib.Path, decision_id: str, args: argparse.Namespace) -> dict[str, Any]:
    return _decision_map.resolve_decision(map_dir, decision_id, args, ns=_NS)


def chart_prompt(doc: dict[str, Any], worktree: pathlib.Path) -> str:
    return _decision_map.chart_prompt(doc, worktree, ns=_NS)


def chart(map_dir: pathlib.Path, args: argparse.Namespace) -> dict[str, Any]:
    return _decision_map.chart(map_dir, args, ns=_NS)


def spec_handoff_prompt(doc: dict[str, Any], worktree: pathlib.Path) -> str:
    return _decision_map.spec_handoff_prompt(doc, worktree, ns=_NS)


def tasks_handoff_prompt(feature_dir: pathlib.Path, worktree: pathlib.Path) -> str:
    return _decision_map.tasks_handoff_prompt(feature_dir, worktree, ns=_NS)


def handoff_worktree(epic: str, base: str, label: str) -> pathlib.Path:
    return _decision_map.handoff_worktree(epic, base, label, ns=_NS)


def cmd_to_spec(map_dir: pathlib.Path, feature_dir: pathlib.Path, args: argparse.Namespace) -> None:
    return _decision_map.cmd_to_spec(map_dir, feature_dir, args, ns=_NS)


def cmd_to_tasks(feature_dir: pathlib.Path, args: argparse.Namespace) -> None:
    return _decision_map.cmd_to_tasks(feature_dir, args, ns=_NS)


def print_status(doc: dict[str, Any]) -> None:
    return _decision_map.print_status(doc, ns=_NS)


def cmd_manual_resolve(map_dir: pathlib.Path, decision_id: str, decision: str, rationale: str) -> None:
    return _decision_map.cmd_manual_resolve(map_dir, decision_id, decision, rationale, ns=_NS)


def cmd_reconcile(map_dir: pathlib.Path) -> None:
    return _decision_map.cmd_reconcile(map_dir, ns=_NS)


def cmd_fog_disposition(map_dir: pathlib.Path, fog_id: str, status: str, reason: str) -> None:
    return _decision_map.cmd_fog_disposition(map_dir, fog_id, status, reason, ns=_NS)


def cmd_supersede(map_dir: pathlib.Path, old_id: str, statement: str, reason: str) -> None:
    return _decision_map.cmd_supersede(map_dir, old_id, statement, reason, ns=_NS)


def parser() -> argparse.ArgumentParser:
    return _decision_map.parser(ns=_NS)


def add_provider_args(p: argparse.ArgumentParser) -> None:
    return _decision_map.add_provider_args(p, ns=_NS)


def ensure_provider(args: argparse.Namespace) -> None:
    return _decision_map.ensure_provider(args, ns=_NS)


def main() -> None:
    return _decision_map.main(ns=_NS)


# The library's decision_map reads this module's names through `_NS`; checked once, here, before any operation (a
# missing name refuses the import).
_NS = _decision_map.bind(_transitions.ModuleNamespace(globals()))


if __name__ == '__main__':
    main()
