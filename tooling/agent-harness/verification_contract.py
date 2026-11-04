#!/usr/bin/env python3
"""Independent acceptance/verification contract authoring for Agentic SDD (Showcase; validation from agent_harness,
AH5-13).

The feature spec states intended behavior. This module creates a second, separately-authored
verification contract using the spec *plus* accepted ADRs, architecture invariants and existing
behavior so the implementation is not evaluated only against criteria written by the same artifact.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import time
import uuid
from typing import Any

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
SCHEMA = HERE / 'schemas' / 'verification-contract.schema.json'
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import runner as r  # noqa: E402
import telemetry  # noqa: E402
import trust  # noqa: E402

from agent_harness import verification_contract as _impl  # noqa: E402
from agent_harness import feature_transitions as _transitions  # noqa: E402
from agent_harness import contract_authoring as _contract_authoring  # noqa: E402

# AH5-13: the contract validation is the library's. Showcase binds its repository root, its constitution path and its
# active-feature layout, looked up per call (tests patch REPO). Authoring (generate) is in agent_harness.contract_authoring
# (the generate package), which reads these bindings through `_NS`.
VALID_REQUIREMENT, VALID_ORIGINS, VALID_SOURCE_TYPES = (
    _impl.VALID_REQUIREMENT, _impl.VALID_ORIGINS, _impl.VALID_SOURCE_TYPES)
die, sha, load_json, requirement, criterion_ids = (
    _impl.die, _impl.sha, _impl.load_json, _impl.requirement, _impl.criterion_ids)

CONSTITUTION = _contract_authoring.CONSTITUTION


def repo_root() -> pathlib.Path:
    return _contract_authoring.repo_root(ns=_NS)


def is_active_feature_dir(feature_dir: pathlib.Path) -> bool:
    return _contract_authoring.is_active_feature_dir(feature_dir, ns=_NS)


def input_hashes(feature_dir: pathlib.Path) -> dict[str, str]:
    return _contract_authoring.input_hashes(feature_dir, ns=_NS)


def validate_contract(feature_dir: pathlib.Path, *, require: bool | None = None) -> list[str]:
    return _contract_authoring.validate_contract(feature_dir, require=require, ns=_NS)


def _git_path(cwd: pathlib.Path, *args: str) -> pathlib.Path | None:
    return _contract_authoring._git_path(cwd, *args, ns=_NS)


def binding_repo_root(feature_dir: pathlib.Path) -> pathlib.Path:
    return _contract_authoring.binding_repo_root(feature_dir, ns=_NS)


def contract_binding(feature_dir: pathlib.Path, *, require: bool | None = None) -> tuple[list[str], dict[str, Any] | None]:
    return _contract_authoring.contract_binding(feature_dir, require=require, ns=_NS)


def prompt(feature_dir: pathlib.Path, worktree: pathlib.Path) -> str:
    return _contract_authoring.prompt(feature_dir, worktree, ns=_NS)


def generate(feature_dir: pathlib.Path, args: argparse.Namespace) -> pathlib.Path:
    return _contract_authoring.generate(feature_dir, args, ns=_NS)


def main() -> None:
    return _contract_authoring.main(ns=_NS)


# The library's contract_authoring reads this module's names through `_NS`; checked once, here, before any operation (a
# missing name refuses the import).
_NS = _contract_authoring.bind(_transitions.ModuleNamespace(globals()))


if __name__ == '__main__':
    main()
