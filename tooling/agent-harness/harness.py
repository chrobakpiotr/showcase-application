#!/usr/bin/env python3
"""Repo-native orchestration core for agentic Spec-Driven Development.

The harness deliberately contains no model SDK. It owns deterministic coordination only:
feature validation, DAG scheduling, task leases, immutable packets, retry/escalation,
state/spec drift detection, specialist routing, and isolated git worktrees.

Model processes are launched by runner.py, which keeps provider-specific concerns out of this core.
"""
from __future__ import annotations

import argparse
import contextlib
import copy
import datetime as dt
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Any, Iterator

HERE = pathlib.Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import trust  # noqa: E402
import verification_contract as vc  # noqa: E402

STATE_DIR = pathlib.Path('.agent-state')
WORKTREE_ROOT_SUFFIX = '-agent-worktrees'
VALID_ROLES = {'architect', 'builder', 'evaluator', 'integration', 'specialist'}
RISK_TO_REVIEWERS = {
    'architecture': ('architecture-reviewer',),
    'api': ('architecture-reviewer',),
    'messaging': ('messaging-reviewer',),
    'persistence': ('persistence-reviewer',),
    'concurrency': ('concurrency-reviewer',),
    'security': ('security-reviewer',),
    'ai': ('ai-reviewer', 'security-reviewer'),
    'infra': ('platform-reviewer',),
    'observability': ('platform-reviewer',),
    'frontend': ('frontend-reviewer',),
    'performance': ('performance-reviewer',),
}
FEATURE_ID_RE = re.compile(r'^[A-Za-z0-9][A-Za-z0-9._-]*$')
TASK_ID_RE = re.compile(r'^T-[A-Z0-9][A-Z0-9._-]*$')
PROFILE_RE = re.compile(r'^[a-z][a-z0-9-]*$')
AC_RE = re.compile(r'\bAC-[A-Z0-9._-]+\b')
DEFAULT_LEASE_TTL_SECONDS = 1800
DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 60
VALID_TEST_POLICIES = {'legacy', 'risk-driven'}
VALID_TEST_MODES = {'red-green-refactor', 'existing-suite', 'not-applicable'}
# Bump only when persisted lifecycle state semantics/schema become incompatible.
# Implementation and documentation changes are recorded separately for audit.
STATE_SCHEMA_VERSION = 1


try:  # Unix/macOS/Linux - the primary targets for this repository.
    import fcntl  # type: ignore
except ImportError:  # pragma: no cover - Windows fallback simply loses cross-process locking.
    fcntl = None


def die(message: str, code: int = 2) -> None:
    print(f'ERROR: {message}', file=sys.stderr)
    raise SystemExit(code)


def load_json(path: pathlib.Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding='utf-8'))
    except FileNotFoundError:
        die(f'missing file: {path}')
    except json.JSONDecodeError as exc:
        die(f'invalid JSON in {path}: {exc}')
    if not isinstance(value, dict):
        die(f'expected JSON object in {path}')
    return value


def feature_files(feature_dir: pathlib.Path) -> tuple[pathlib.Path, pathlib.Path, pathlib.Path]:
    return feature_dir / 'spec.md', feature_dir / 'plan.md', feature_dir / 'tasks.json'


def task_index(doc: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {t['id']: t for t in doc.get('tasks', []) if isinstance(t, dict) and isinstance(t.get('id'), str)}


def repo_root() -> pathlib.Path:
    proc = subprocess.run(
        ['git', 'rev-parse', '--show-toplevel'], capture_output=True, text=True, check=False
    )
    if proc.returncode != 0:
        die('this command must run inside a Git repository')
    return pathlib.Path(proc.stdout.strip()).resolve()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def feature_fingerprint(feature_dir: pathlib.Path) -> str:
    digest = hashlib.sha256()
    paths = list(feature_files(feature_dir))
    paths.extend([
        feature_dir / 'design.json',
        feature_dir / 'design' / 'gate.json',
        feature_dir / 'verification-contract.json',
        feature_dir / 'wayfinder-handoff.json',
    ])
    for path in paths:
        if not path.exists():
            continue
        try:
            name = str(path.relative_to(feature_dir))
        except ValueError:
            name = path.name
        digest.update(name.encode())
        digest.update(b'\0')
        digest.update(path.read_bytes())
        digest.update(b'\0')
    return digest.hexdigest()


def feature_repo_base(feature_dir: pathlib.Path) -> pathlib.Path:
    resolved = feature_dir.resolve()
    if resolved.parent.name == 'specs' and resolved.parent.parent.name == 'docs':
        return resolved.parent.parent.parent
    return pathlib.Path.cwd().resolve()


def protocol_files(feature_dir: pathlib.Path) -> list[pathlib.Path]:
    root = feature_repo_base(feature_dir)
    candidates = [
        root / 'AGENTS.md',
        root / 'CLAUDE.md',
        root / 'tooling' / 'agent-harness' / 'harness.py',
        root / 'tooling' / 'agent-harness' / 'runner.py',
        root / 'tooling' / 'agent-harness' / 'orchestrate.py',
        root / 'tooling' / 'agent-harness' / 'verification_sandbox.py',
        root / 'tooling' / 'agent-harness' / 'telemetry.py',
        root / 'tooling' / 'agent-harness' / 'control_plane.py',
        root / 'tooling' / 'agent-harness' / 'design.py',
        root / 'tooling' / 'agent-harness' / 'wayfinder.py',
        root / 'tooling' / 'agent-harness' / 'verification_contract.py',
        root / 'tooling' / 'agent-harness' / 'trust.py',
        root / 'tooling' / 'agent-harness' / 'eval.py',
        root / 'tooling' / 'agent-harness' / 'schemas' / 'wayfinder-result.schema.json',
        root / 'tooling' / 'agent-harness' / 'schemas' / 'wayfinder-handoff.schema.json',
        root / 'tooling' / 'agent-harness' / 'schemas' / 'wayfinder-tasks-result.schema.json',
        root / 'tooling' / 'agent-harness' / 'schemas' / 'task-result.schema.json',
        root / 'tooling' / 'agent-harness' / 'schemas' / 'design-result.schema.json',
        root / 'tooling' / 'agent-harness' / 'schemas' / 'verification-contract.schema.json',
        root / '.github' / 'workflows' / 'agentic-sdd.yml',
    ]
    for directory in (root / 'docs' / 'agentic-sdd', root / '.claude' / 'agents'):
        if directory.exists():
            candidates.extend(sorted(p for p in directory.rglob('*.md') if p.is_file()))
    eval_dir = root / 'tooling' / 'agent-harness' / 'evals'
    if eval_dir.exists():
        candidates.extend(sorted(p for p in eval_dir.glob('*.json') if p.is_file()))
    # Deduplicate while retaining deterministic path order.
    return sorted({p.resolve() for p in candidates if p.exists()}, key=lambda p: str(p))


def protocol_fingerprint(feature_dir: pathlib.Path) -> str:
    root = feature_repo_base(feature_dir)
    digest = hashlib.sha256()
    for path in protocol_files(feature_dir):
        try:
            rel = path.relative_to(root)
        except ValueError:
            rel = path
        digest.update(str(rel).encode())
        digest.update(b'\0')
        digest.update(path.read_bytes())
        digest.update(b'\0')
    return digest.hexdigest()


def protocol_version(feature_dir: pathlib.Path) -> int:
    """Compatibility contract for persisted task lifecycle state."""
    return STATE_SCHEMA_VERSION


def safe_relative_pattern(pattern: str) -> bool:
    if not pattern or pathlib.PurePath(pattern).is_absolute():
        return False
    normalized = pattern.replace('\\', '/').strip()
    if normalized in {'.', './', '*', '**', '**/*'}:
        return False
    segments = [part for part in normalized.split('/') if part not in {'', '.'}]
    if not segments or '..' in segments:
        return False
    forbidden_roots = {'.git', '.agent-state', '.agent-runs'}
    return segments[0] not in forbidden_roots


def builder_pattern_has_concrete_root(pattern: str) -> bool:
    normalized = pattern.replace('\\', '/').lstrip('./')
    first = normalized.split('/', 1)[0]
    return bool(first) and not any(char in first for char in '*?[')


def pattern_static_prefix(pattern: str) -> str:
    normalized = pattern.replace('\\', '/').lstrip('./')
    parts: list[str] = []
    for part in normalized.split('/'):
        if any(char in part for char in '*?['):
            break
        if part:
            parts.append(part)
    return '/'.join(parts)


def patterns_may_overlap(left: str, right: str) -> bool:
    # Conservative by design: false positives force an explicit dependency, while false negatives
    # would let two builders race on the same file. Exact paths are handled precisely; glob-vs-glob
    # uses the non-wildcard prefix as the safe approximation.
    import fnmatch

    left_glob = any(char in left for char in '*?[')
    right_glob = any(char in right for char in '*?[')
    if not left_glob and not right_glob:
        return left == right
    if not left_glob:
        return fnmatch.fnmatch(left, right)
    if not right_glob:
        return fnmatch.fnmatch(right, left)
    lp = pattern_static_prefix(left)
    rp = pattern_static_prefix(right)
    if not lp or not rp:
        return True
    return lp == rp or lp.startswith(rp + '/') or rp.startswith(lp + '/')


def transitive_dependencies(idx: dict[str, dict[str, Any]], tid: str) -> set[str]:
    found: set[str] = set()
    stack = list(idx.get(tid, {}).get('depends_on', []))
    while stack:
        dep = stack.pop()
        if dep in found:
            continue
        found.add(dep)
        stack.extend(idx.get(dep, {}).get('depends_on', []))
    return found


def is_active_feature_dir(feature_dir: pathlib.Path) -> bool:
    root = feature_repo_base(feature_dir)
    try:
        rel = feature_dir.resolve().relative_to(root)
    except ValueError:
        return False
    return len(rel.parts) == 3 and rel.parts[:2] == ('docs', 'specs')


def design_gate_errors(feature_dir: pathlib.Path, spec: pathlib.Path, plan: pathlib.Path) -> list[str]:
    config_path = feature_dir / 'design.json'
    if not config_path.exists():
        return []
    errors: list[str] = []
    try:
        config = json.loads(config_path.read_text(encoding='utf-8'))
    except json.JSONDecodeError as exc:
        return [f'invalid JSON in {config_path}: {exc}']
    if not isinstance(config, dict):
        return ['design.json must contain a JSON object']
    required = config.get('required_for_orchestration', True)
    if not isinstance(required, bool):
        errors.append('design.json.required_for_orchestration must be boolean')
        return errors
    for field in ('grill', 'prototype', 'architecture_grill'):
        value = config.get(field, 'auto')
        if value not in {'auto', 'always', 'off'}:
            errors.append(f'design.json.{field} must be one of auto/always/off')
    if not required or not is_active_feature_dir(feature_dir):
        return errors
    gate_path = feature_dir / 'design' / 'gate.json'
    if not gate_path.exists():
        errors.append('design preflight gate is required before orchestration; run tooling/agent-harness/design.py')
        return errors
    try:
        gate = json.loads(gate_path.read_text(encoding='utf-8'))
    except json.JSONDecodeError as exc:
        errors.append(f'invalid JSON in {gate_path}: {exc}')
        return errors
    if not isinstance(gate, dict):
        return errors + ['design/gate.json must contain a JSON object']
    if gate.get('schema_version') != 1:
        errors.append('design/gate.json schema_version must be 1')
    if gate.get('feature') != feature_dir.name:
        errors.append('design/gate.json feature must match feature directory')
    if gate.get('decision') != 'pass':
        errors.append('design/gate.json decision must be pass')
    expected = {
        'spec_sha256': sha256_bytes(spec.read_bytes()),
        'plan_sha256': sha256_bytes(plan.read_bytes()),
        'design_config_sha256': sha256_bytes(config_path.read_bytes()),
    }
    for field, digest in expected.items():
        if gate.get(field) != digest:
            errors.append(f'design preflight gate is stale: {field} no longer matches current input')
    stages = gate.get('stages')
    if not isinstance(stages, dict):
        errors.append('design/gate.json stages must be an object')
    else:
        for stage in ('spec_grill', 'prototype', 'architecture_grill'):
            record = stages.get(stage)
            if not isinstance(record, dict) or record.get('status') not in {'pass', 'skipped', 'waived'}:
                errors.append(f'design/gate.json stage {stage} must be pass/skipped/waived')
    return errors


def validate(feature_dir: pathlib.Path) -> list[str]:
    errors: list[str] = []
    spec, plan, tasks_path = feature_files(feature_dir)
    for path in (spec, plan, tasks_path):
        if not path.exists():
            errors.append(f'missing required file: {path}')
    if errors:
        return errors

    errors.extend(design_gate_errors(feature_dir, spec, plan))
    errors.extend(vc.validate_contract(feature_dir))

    try:
        doc = json.loads(tasks_path.read_text(encoding='utf-8'))
    except json.JSONDecodeError as exc:
        return [f'invalid JSON in {tasks_path}: {exc}']
    if not isinstance(doc, dict):
        return ['tasks.json must contain a JSON object']

    feature = doc.get('feature')
    if not isinstance(feature, str) or not feature.strip():
        errors.append('tasks.json.feature must be a non-empty string')
    else:
        if not FEATURE_ID_RE.fullmatch(feature):
            errors.append(f'tasks.json.feature has unsafe/invalid format: {feature!r}')
        if feature != feature_dir.name:
            errors.append(f'tasks.json.feature must match feature directory name: {feature!r} != {feature_dir.name!r}')

    test_policy = doc.get('test_policy', 'legacy')
    if test_policy not in VALID_TEST_POLICIES:
        errors.append(f'test_policy must be one of {sorted(VALID_TEST_POLICIES)}')

    max_parallel = doc.get('max_parallel', 4)
    if not isinstance(max_parallel, int) or not 1 <= max_parallel <= 8:
        errors.append('max_parallel must be an integer between 1 and 8')
    max_rework = doc.get('max_rework_attempts', 2)
    if not isinstance(max_rework, int) or not 0 <= max_rework <= 5:
        errors.append('max_rework_attempts must be an integer between 0 and 5')

    lease_ttl = doc.get('lease_ttl_seconds', DEFAULT_LEASE_TTL_SECONDS)
    if not isinstance(lease_ttl, int) or not 60 <= lease_ttl <= 86400:
        errors.append('lease_ttl_seconds must be an integer between 60 and 86400')
    heartbeat_interval = doc.get('heartbeat_interval_seconds', DEFAULT_HEARTBEAT_INTERVAL_SECONDS)
    if not isinstance(heartbeat_interval, int) or not 5 <= heartbeat_interval <= 300:
        errors.append('heartbeat_interval_seconds must be an integer between 5 and 300')
    if isinstance(lease_ttl, int) and isinstance(heartbeat_interval, int) and heartbeat_interval * 2 >= lease_ttl:
        errors.append('heartbeat_interval_seconds must be less than half of lease_ttl_seconds')

    tasks = doc.get('tasks')
    if not isinstance(tasks, list) or not tasks:
        return errors + ['tasks.json must contain a non-empty tasks array']

    ids: list[str] = []
    required = (
        'id', 'title', 'objective', 'role', 'depends_on', 'allowed_paths',
        'risk_tags', 'acceptance_criteria', 'verification'
    )
    list_fields = ('depends_on', 'allowed_paths', 'risk_tags', 'acceptance_criteria', 'verification')
    for i, task in enumerate(tasks):
        where = f'tasks[{i}]'
        if not isinstance(task, dict):
            errors.append(f'{where} must be an object')
            continue
        for name in required:
            if name not in task:
                errors.append(f'{where} missing {name}')
        tid = task.get('id')
        if isinstance(tid, str):
            ids.append(tid)
            if not TASK_ID_RE.fullmatch(tid):
                errors.append(f'{where}.id has invalid format: {tid!r}')
        else:
            errors.append(f'{where}.id must be a string')
        if not isinstance(task.get('title'), str) or not task.get('title', '').strip():
            errors.append(f'{where}.title must be a non-empty string')
        if not isinstance(task.get('objective'), str) or not task.get('objective', '').strip():
            errors.append(f'{where}.objective must be a non-empty string')
        if task.get('role') not in VALID_ROLES:
            errors.append(f'{where}.role must be one of {sorted(VALID_ROLES)}')
        if 'agent_profile' in task:
            profile = task.get('agent_profile')
            if not isinstance(profile, str) or not PROFILE_RE.fullmatch(profile):
                errors.append(f'{where}.agent_profile must match {PROFILE_RE.pattern}')
        for name in list_fields:
            if name in task and not isinstance(task[name], list):
                errors.append(f'{where}.{name} must be an array')
        patterns = task.get('allowed_paths', []) if isinstance(task.get('allowed_paths'), list) else []
        for pattern in patterns:
            if not isinstance(pattern, str) or not safe_relative_pattern(pattern):
                errors.append(f'{where}.allowed_paths contains unsafe path pattern: {pattern!r}')
            elif task.get('role') == 'builder' and not builder_pattern_has_concrete_root(pattern):
                errors.append(f'{where}.builder allowed_paths must start with a concrete repo path: {pattern!r}')
        for risk in task.get('risk_tags', []) if isinstance(task.get('risk_tags'), list) else []:
            if not isinstance(risk, str) or not risk.strip():
                errors.append(f'{where}.risk_tags must contain non-empty strings')
        criteria = task.get('acceptance_criteria', []) if isinstance(task.get('acceptance_criteria'), list) else []
        if not criteria:
            errors.append(f'{where}.acceptance_criteria must not be empty')
        for command in task.get('verification', []) if isinstance(task.get('verification'), list) else []:
            if not isinstance(command, str) or not command.strip():
                errors.append(f'{where}.verification must contain non-empty command strings')
        if task.get('role') == 'builder' and test_policy == 'risk-driven':
            mode = task.get('test_mode')
            seam = task.get('test_seam')
            if mode not in VALID_TEST_MODES:
                errors.append(f'{where}.test_mode must be one of {sorted(VALID_TEST_MODES)} when test_policy=risk-driven')
            if not isinstance(seam, str) or not seam.strip():
                errors.append(f'{where}.test_seam must explain the observable seam or why tests are not applicable')

    duplicates = sorted({x for x in ids if ids.count(x) > 1})
    if duplicates:
        errors.append(f'duplicate task ids: {duplicates}')

    known = set(ids)
    idx = task_index(doc)
    for tid, task in idx.items():
        for dep in task.get('depends_on', []):
            if not isinstance(dep, str):
                errors.append(f'{tid} has a non-string dependency')
            elif dep not in known:
                errors.append(f'{tid} depends on unknown task {dep}')
            elif dep == tid:
                errors.append(f'{tid} depends on itself')

    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(tid: str, chain: list[str]) -> None:
        if tid in visiting:
            errors.append('dependency cycle: ' + ' -> '.join(chain + [tid]))
            return
        if tid in visited or tid not in idx:
            return
        visiting.add(tid)
        for dep in idx[tid].get('depends_on', []):
            if isinstance(dep, str):
                visit(dep, chain + [tid])
        visiting.remove(tid)
        visited.add(tid)

    for tid in idx:
        visit(tid, [])

    spec_criteria = set(AC_RE.findall(spec.read_text(encoding='utf-8')))
    verification_criteria = vc.criterion_ids(feature_dir)
    accepted_criteria = spec_criteria | verification_criteria
    for tid, task in idx.items():
        for criterion in task.get('acceptance_criteria', []):
            if not isinstance(criterion, str):
                errors.append(f'{tid} has a non-string acceptance criterion')
            elif criterion not in accepted_criteria:
                errors.append(f'{tid} references criterion absent from spec.md and verification-contract.json: {criterion}')

        profiles = [task.get('agent_profile') or task.get('role'), *reviewers(task)]
        for profile in profiles:
            if not isinstance(profile, str) or not PROFILE_RE.fullmatch(profile):
                continue
            profile_path = feature_repo_base(feature_dir) / 'docs' / 'agentic-sdd' / 'agents' / f'{profile}.md'
            if not profile_path.exists():
                errors.append(f'{tid} selects missing agent profile: {profile} ({profile_path})')

    # Detect write collisions only for tasks that can actually be concurrent (neither depends on the other).
    ancestors = {tid: transitive_dependencies(idx, tid) for tid in idx}
    for pos, a in enumerate(tasks):
        if not isinstance(a, dict) or not isinstance(a.get('id'), str):
            continue
        for b in tasks[pos + 1:]:
            if not isinstance(b, dict) or not isinstance(b.get('id'), str):
                continue
            aid, bid = a['id'], b['id']
            if aid in ancestors.get(bid, set()) or bid in ancestors.get(aid, set()):
                continue
            if a.get('role') == b.get('role') == 'builder':
                overlaps = sorted(
                    {f'{left} <-> {right}' for left in a.get('allowed_paths', []) for right in b.get('allowed_paths', [])
                     if isinstance(left, str) and isinstance(right, str) and patterns_may_overlap(left, right)}
                )
                if overlaps:
                    errors.append(f'parallel builders {aid} and {bid} have overlapping allowed_paths {overlaps}')

    evaluators = [t for t in tasks if isinstance(t, dict) and t.get('role') == 'evaluator']
    builders = [t for t in tasks if isinstance(t, dict) and t.get('role') == 'builder']
    if builders and not evaluators:
        errors.append('at least one evaluator task is required when builder tasks exist')
    if evaluators and builders:
        covered = set().union(*(set(t.get('depends_on', [])) for t in evaluators))
        uncovered = sorted(t['id'] for t in builders if t.get('id') not in covered)
        if uncovered:
            errors.append(f'builder tasks not directly covered by an evaluator dependency: {uncovered}')
        evaluator_criteria = set().union(*(set(t.get('acceptance_criteria', [])) for t in evaluators))
        uncovered_criteria = sorted(accepted_criteria - evaluator_criteria)
        if uncovered_criteria:
            errors.append(f'spec acceptance criteria not covered by an evaluator (including verification-contract criteria): {uncovered_criteria}')

    integrations = [t for t in tasks if isinstance(t, dict) and t.get('role') == 'integration']
    if integrations and evaluators:
        evaluator_ids = {t['id'] for t in evaluators}
        for task in integrations:
            if not evaluator_ids.intersection(task.get('depends_on', [])):
                errors.append(f'integration task {task.get("id")} must depend on an evaluator')

    return errors


def state_key(feature_dir: pathlib.Path) -> str:
    return sha256_bytes(f'{git_common_dir(feature_dir)}\0{feature_dir.name}'.encode())[:20]


def git_common_dir(feature_dir: pathlib.Path) -> pathlib.Path:
    """Return Git's canonical common directory for this checkout/worktree."""
    result = subprocess.run(
        ['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'],
        cwd=feature_repo_base(feature_dir), capture_output=True, text=True, check=False,
    )
    if result.returncode != 0:
        die('cannot resolve the common Git directory for lifecycle state')
    common = pathlib.Path(result.stdout.strip()).resolve()
    if not common.is_dir():
        die(f'Git common directory does not exist: {common}')
    return common


def runtime_state_dir(feature_dir: pathlib.Path) -> pathlib.Path:
    if STATE_DIR.is_absolute():
        return STATE_DIR
    # Store lifecycle state next to the common Git directory so all linked worktrees
    # resolve the same location while preserving <main-checkout>/.agent-state.
    common = git_common_dir(feature_dir)
    return common.parent / STATE_DIR


def state_path(feature_dir: pathlib.Path) -> pathlib.Path:
    return runtime_state_dir(feature_dir) / f'{feature_dir.name}-{state_key(feature_dir)}.json'


def lock_path(feature_dir: pathlib.Path) -> pathlib.Path:
    return runtime_state_dir(feature_dir) / f'{feature_dir.name}-{state_key(feature_dir)}.lock'


def initial_state(feature_dir: pathlib.Path, doc: dict[str, Any]) -> dict[str, Any]:
    return {
        'state_version': 2,
        'protocol_version': protocol_version(feature_dir),
        'feature': doc.get('feature', feature_dir.name),
        'fingerprint': feature_fingerprint(feature_dir),
        'protocol_fingerprint': protocol_fingerprint(feature_dir),
        'created_at': dt.datetime.now(dt.timezone.utc).isoformat(),
        'tasks': {
            t['id']: {'status': 'pending', 'attempts': 0}
            for t in doc['tasks'] if isinstance(t, dict) and isinstance(t.get('id'), str)
        },
    }


def load_state(feature_dir: pathlib.Path, doc: dict[str, Any]) -> dict[str, Any]:
    runtime_state_dir(feature_dir).mkdir(parents=True, exist_ok=True)
    with lock_path(feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        state = _load_state_unlocked(feature_dir, doc)
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
    return state


def _resolve_state_unlocked(feature_dir: pathlib.Path, doc: dict[str, Any]) -> tuple[dict[str, Any], bool]:
    """Resolve canonical or exact historical main-checkout state under the shared lock."""
    canonical = state_path(feature_dir)
    legacy = main_legacy_state_path(feature_dir)
    candidates: list[tuple[pathlib.Path, dict[str, Any], bool]] = []
    for path, is_legacy in ((canonical, False), (legacy, True)):
        if path.exists():
            state = load_json(path)
            validate_state_identity(feature_dir, doc, state)
            candidates.append((path, state, is_legacy))

    if len(candidates) > 1:
        first_path, first_state, _ = candidates[0]
        for other_path, other_state, _ in candidates[1:]:
            if other_state != first_state:
                die(
                    'conflicting valid lifecycle states exist at '
                    f'{first_path} and {other_path}; preserve both and resolve the conflict explicitly'
                )
        # Byte-equivalent duplicate state resolves deterministically to canonical.
        return candidates[0][1], False
    if candidates:
        _, state, is_legacy = candidates[0]
        return state, is_legacy

    return initial_state(feature_dir, doc), False


def _load_state_unlocked(feature_dir: pathlib.Path, doc: dict[str, Any]) -> dict[str, Any]:
    state, legacy_path = _resolve_state_unlocked(feature_dir, doc)
    if legacy_path:
        validate_loaded_state(feature_dir, doc, state)
    elif not state_path(feature_dir).exists():
        # Fresh state is returned to the caller and persisted only after its lifecycle
        # operation succeeds. A validation error must never leave a shadow authority.
        return state
    else:
        validate_loaded_state(feature_dir, doc, state)
        legacy = main_legacy_state_path(feature_dir)
        if legacy.exists():
            # Resolver established byte-equivalent identity; remove the redundant exact
            # historical copy only after canonical compatibility validation succeeds.
            legacy.unlink()
    return state


def validate_state_identity(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any]) -> None:
    expected = feature_fingerprint(feature_dir)
    if state.get('fingerprint') != expected:
        die('feature spec/plan/tasks fingerprint differs; refusing lifecycle state adoption/migration')
    if state.get('feature', feature_dir.name) != doc.get('feature', feature_dir.name):
        die('runtime state logical feature identity differs from tasks.json; refusing lifecycle state adoption/migration')
    expected_ids = set(task_index(doc))
    tasks = state.get('tasks')
    if not isinstance(tasks, dict) or set(tasks) != expected_ids:
        die('runtime state task set differs from tasks.json; refusing lifecycle state adoption/migration')


def validate_loaded_state(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any]) -> None:
    validate_state_identity(feature_dir, doc, state)
    expected = feature_fingerprint(feature_dir)
    expected_version = protocol_version(feature_dir)
    stored_version = state.get('protocol_version')
    if stored_version is None and state.get('state_version') == 2:
        die(
            'runtime state predates explicit protocol versioning; run '
            f'`harness.py migrate-state {feature_dir}` to validate and upgrade it'
        )
    if stored_version != expected_version:
        die(
            f'runtime state protocol/schema version {stored_version!r} is incompatible with '
            f'current version {expected_version}; use `harness.py migrate-state {feature_dir}` '
            'when a supported migration exists, otherwise revise/re-plan deliberately'
        )
    expected_ids = set(task_index(doc))
    actual_ids = set(state.get('tasks', {}))
    if expected_ids != actual_ids:
        die('runtime state task set differs from tasks.json; reset the feature state')


def cmd_migrate_state(args: argparse.Namespace) -> None:
    """Validate and explicitly adopt a legacy state under the current semantic version."""
    doc = load_validated(args.feature_dir)
    runtime_state_dir(args.feature_dir).mkdir(parents=True, exist_ok=True)
    with lock_path(args.feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        state, legacy_path = _resolve_state_unlocked(args.feature_dir, doc)
        if not state_path(args.feature_dir).exists() and not legacy_path:
            die(f'no authoritative lifecycle state exists for {args.feature_dir}; migration will not create state')
        validate_state_identity(args.feature_dir, doc, state)
        tasks = state['tasks']
        old_version = state.get('protocol_version')
        legacy_version = old_version is None and state.get('state_version') == 2
        if old_version == protocol_version(args.feature_dir):
            validate_loaded_state(args.feature_dir, doc, state)
            if legacy_path:
                save_state(args.feature_dir, state)
            main_legacy_state_path(args.feature_dir).unlink(missing_ok=True)
            print(f'ALREADY_CURRENT {args.feature_dir} protocol_version={old_version}')
        elif legacy_version:
            # Explicit known edge: pre-versioning state v2 -> semantic protocol v1.
            # Verify allowed lifecycle statuses and nonnegative attempt counters; preserve all
            # task metadata verbatim and never consult Git history for completion.
            valid_statuses = {'pending', 'running', 'completed', 'failed', 'escalated'}
            for task_id, entry in tasks.items():
                if not isinstance(entry, dict) or entry.get('status') not in valid_statuses:
                    die(f'invalid lifecycle entry for {task_id}; refusing migration')
                attempts = entry.get('attempts', 0)
                if not isinstance(attempts, int) or isinstance(attempts, bool) or attempts < 0:
                    die(f'invalid attempt count for {task_id}; refusing migration')
            if not isinstance(state.get('protocol_fingerprint'), str) or not state['protocol_fingerprint']:
                die('legacy runtime state has no protocol audit fingerprint; refusing migration')
            state['protocol_version'] = protocol_version(args.feature_dir)
            save_state(args.feature_dir, state)
            main_legacy_state_path(args.feature_dir).unlink(missing_ok=True)
            print(
                f'MIGRATED {args.feature_dir} protocol_version={old_version or "legacy-state-v2"}'
                f'->{state["protocol_version"]} tasks={len(tasks)} '
                f'preserved_completed={sum(1 for entry in tasks.values() if entry.get("status") == "completed")}'
            )
        else:
            die(f'no supported lifecycle-state migration from protocol version {old_version!r}')
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def legacy_state_name(feature_dir: pathlib.Path) -> str:
    # Historical key: SHA-256 of the main checkout's resolved feature path.
    repo_root = git_common_dir(feature_dir).parent
    main_feature = repo_root / 'docs' / 'specs' / feature_dir.name
    name = f'{feature_dir.name}-{sha256_bytes(str(main_feature.resolve()).encode())[:20]}.json'
    return name


def main_legacy_state_path(feature_dir: pathlib.Path) -> pathlib.Path:
    """Old main-checkout file; the only legacy snapshot safe to adopt automatically."""
    root = git_common_dir(feature_dir).parent
    return root / '.agent-state' / legacy_state_name(feature_dir)


def save_state(feature_dir: pathlib.Path, state: dict[str, Any]) -> None:
    state_dir = runtime_state_dir(feature_dir)
    state_dir.mkdir(parents=True, exist_ok=True)
    path = state_path(feature_dir)
    fd, tmp_name = tempfile.mkstemp(prefix=path.name + '.', dir=state_dir)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            json.dump(state, handle, indent=2, sort_keys=True)
            handle.write('\n')
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_name, path)
        directory_fd = os.open(state_dir, os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    finally:
        if os.path.exists(tmp_name):
            os.unlink(tmp_name)


def remove_state_locked(feature_dir: pathlib.Path) -> None:
    runtime_state_dir(feature_dir).mkdir(parents=True, exist_ok=True)
    with lock_path(feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        state_path(feature_dir).unlink(missing_ok=True)
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


@contextlib.contextmanager
def locked_state(feature_dir: pathlib.Path, doc: dict[str, Any]) -> Iterator[dict[str, Any]]:
    runtime_state_dir(feature_dir).mkdir(parents=True, exist_ok=True)
    with lock_path(feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        state = _load_state_unlocked(feature_dir, doc)
        try:
            yield state
        finally:
            save_state(feature_dir, state)
            if fcntl is not None:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)



def utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def parse_timestamp(value: Any) -> dt.datetime | None:
    if not isinstance(value, str) or not value:
        return None
    try:
        parsed = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=dt.timezone.utc)
    return parsed.astimezone(dt.timezone.utc)


def lease_ttl_seconds(doc: dict[str, Any]) -> int:
    return int(doc.get('lease_ttl_seconds', DEFAULT_LEASE_TTL_SECONDS))


def heartbeat_interval_seconds(doc: dict[str, Any]) -> int:
    return int(doc.get('heartbeat_interval_seconds', DEFAULT_HEARTBEAT_INTERVAL_SECONDS))


def refresh_lease(entry: dict[str, Any], doc: dict[str, Any], *, now: dt.datetime | None = None) -> None:
    current = now or utc_now()
    entry['heartbeat_at'] = current.isoformat()
    entry['lease_expires_at'] = (current + dt.timedelta(seconds=lease_ttl_seconds(doc))).isoformat()


def lease_expired(entry: dict[str, Any], *, now: dt.datetime | None = None) -> bool:
    if entry.get('status') != 'running':
        return False
    expires = parse_timestamp(entry.get('lease_expires_at'))
    if expires is None:
        # Legacy/invalid running state is treated as stale rather than leased forever.
        return True
    return expires <= (now or utc_now())


def heartbeat(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, owner: str) -> str:
    with locked_state(feature_dir, doc) as state:
        entry = state['tasks'].get(task_id)
        if not entry:
            die(f'unknown task {task_id}')
        owner_guard(entry, owner)
        if entry.get('status') != 'running':
            die(f'{task_id} is not running')
        if is_unrecovered_partial_claim(entry):
            die(f'CLAIM_RECOVERY_REQUIRED: {task_id} must be explicitly recovered before execution heartbeat')
        refresh_lease(entry, doc)
        return str(entry['lease_expires_at'])


def recover_stale_leases(feature_dir: pathlib.Path, doc: dict[str, Any], *, reason: str = 'lease expired') -> list[str]:
    """Recover expired task leases so a crashed orchestrator can be resumed safely.

    Dirty task worktrees are checkpointed only on that task-local branch as failed-attempt evidence.
    The checkpoint is never exposed to dependents because the task remains failed until re-executed
    and completed successfully.
    """
    recovered: list[str] = []
    now = utc_now()
    with locked_state(feature_dir, doc) as state:
        for task_id, entry in state['tasks'].items():
            if not lease_expired(entry, now=now):
                continue
            if is_unrecovered_partial_claim(entry):
                die(f'CLAIM_RECOVERY_REQUIRED: {task_id} is a stranded exceptional claim; attest with recover-claim before lease recovery')
            task = active_task_contract(feature_dir, doc, task_id, state=state)
            if not task:
                continue
            feature = str(doc.get('feature', feature_dir.name))
            target = worktree_path(feature, task_id)
            stale_commit = None
            if target.exists():
                stale_commit = checkpoint_worktree(doc, task, target, label='stale-lease')
            entry.update({
                'status': 'failed',
                'last_failure': reason,
                'lease_recovered_at': now.isoformat(),
                'last_attempt_commit': stale_commit,
            })
            entry.pop('start_origin_status', None)
            for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
                entry.pop(key, None)
            recovered.append(task_id)
    return recovered


def is_unrecovered_partial_claim(entry: dict[str, Any]) -> bool:
    return (entry.get('status') == 'running' and int(entry.get('attempts', 0)) == 4 and
            isinstance(entry.get('active_retry_authorization'), str) and
            not entry.get('claim_recovery'))


def ready_ids(doc: dict[str, Any], state: dict[str, Any], feature_dir: pathlib.Path | None = None) -> list[str]:
    max_parallel = int(doc.get('max_parallel', 4))
    running = sum(1 for entry in state['tasks'].values() if entry.get('status') == 'running')
    capacity = max(0, max_parallel - running)
    if capacity == 0:
        return []
    ready: list[str] = []
    for task in doc['tasks']:
        if not isinstance(task, dict) or not isinstance(task.get('id'), str):
            continue
        tid = task['id']
        contract_task = active_task_contract(feature_dir, doc, tid, state=state) if feature_dir is not None else task
        entry = state['tasks'][tid]
        if entry['status'] not in {'pending', 'failed'}:
            continue
        if entry.get('status') == 'failed' and int(entry.get('attempts', 0)) >= 1 + int(doc.get('max_rework_attempts', 2)) and int(entry.get('human_resume_grants', 0)) <= 0:
            if feature_dir is None or classify_retry_authorizations(feature_dir, doc, state, tid, int(entry.get('attempts', 0)))[0] != 'valid':
                continue
        if all(state['tasks'][dep]['status'] == 'completed' for dep in contract_task.get('depends_on', [])):
            ready.append(tid)
    return ready[:capacity]


def reviewers(task: dict[str, Any]) -> list[str]:
    selected: list[str] = []
    for risk in task.get('risk_tags', []):
        for reviewer in RISK_TO_REVIEWERS.get(risk, ()):
            if reviewer not in selected:
                selected.append(reviewer)
    return selected


def packet_payload(doc: dict[str, Any], task: dict[str, Any], feature_dir: pathlib.Path) -> dict[str, Any]:
    profile = task.get('agent_profile') or task['role']
    root = feature_repo_base(feature_dir)
    try:
        feature_rel = feature_dir.resolve().relative_to(root.resolve())
    except ValueError:
        die('feature directory must live inside the repository')
    payload = {
        'protocol_version': 4,
        'feature': doc.get('feature', feature_dir.name),
        'feature_fingerprint': feature_fingerprint(feature_dir),
        'semantic_contract_sha256': semantic_task_contract_sha256(feature_dir, doc, task),
        'protocol_fingerprint': protocol_fingerprint(feature_dir),
        'task': task['id'],
        'title': task['title'],
        'objective': task['objective'],
        'role': task['role'],
        'agent_profile': profile,
        'depends_on': task.get('depends_on', []),
        'allowed_paths': task.get('allowed_paths', []),
        'acceptance_criteria': task.get('acceptance_criteria', []),
        'risk_tags': task.get('risk_tags', []),
        'required_reviewers': reviewers(task),
        'verification': task.get('verification', []),
        'test_policy': doc.get('test_policy', 'legacy'),
        'test_mode': task.get('test_mode'),
        'test_seam': task.get('test_seam'),
        'lease_policy': {
            'ttl_seconds': lease_ttl_seconds(doc),
            'heartbeat_interval_seconds': heartbeat_interval_seconds(doc),
        },
        'context': {
            'agent_guide': 'AGENTS.md',
            'constitution': 'docs/agentic-sdd/constitution.md',
            'role': f'docs/agentic-sdd/agents/{profile}.md',
            'spec': str(feature_rel / 'spec.md'),
            'plan': str(feature_rel / 'plan.md'),
            'verification_contract': str(feature_rel / 'verification-contract.json') if (feature_dir / 'verification-contract.json').exists() else None,
            'adrs': 'docs/adr/',
            'architecture': 'docs/architecture/README.md',
            'messaging_contract': 'contracts/asyncapi/asyncapi.yml' if 'messaging' in task.get('risk_tags', []) else None,
        },
        'context_trust': {},
        'completion_contract': {
            'agent_must_not_commit': True,
            'agent_must_not_push': True,
            'builder_cannot_self_approve': task['role'] == 'builder',
            'modify_only_allowed_paths': True,
            'record_changed_paths': True,
            'record_commands_and_results': True,
            'record_assumptions_and_residual_risk': True,
        },
    }
    payload['context_trust'] = trust.packet_context_trust(payload['context'])
    canonical = json.dumps(payload, sort_keys=True, separators=(',', ':')).encode()
    payload['packet_sha256'] = sha256_bytes(canonical)
    return payload


def write_packet(doc: dict[str, Any], task: dict[str, Any], feature_dir: pathlib.Path,
                 *, state: dict[str, Any] | None = None) -> pathlib.Path:
    active = resolve_active_packet(feature_dir, doc, task['id'], state=state)
    if active.get('revision_id') != legacy_revision_id(feature_dir, task['id']):
        return pathlib.Path(active['path'])
    try:
        payload = packet_payload(doc, task, feature_dir)
        encoded = json.dumps(payload, indent=2, sort_keys=True) + '\n'
    except Exception as exc:
        die(f'cannot serialize task packet for {task.get("id", "unknown")}: {exc}')
    out_dir = feature_dir / 'packets'
    out = out_dir / f'{task["id"]}.json'
    if out.exists():
        try:
            existing = json.loads(out.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError) as exc:
            die(f'immutable task packet cannot be validated: {out}: {exc}')
        if not isinstance(existing, dict) or not packet_matches_semantic_contract(existing, doc, task, feature_dir):
            die(f'immutable task packet has a different semantic contract: {out}; re-plan the task before replacing it')
        # Preserve the original bytes as historical packet/provenance evidence.
        return out

    try:
        out_dir.mkdir(parents=True, exist_ok=True)
        fd, temporary = tempfile.mkstemp(prefix=f'.{out.name}.', dir=out_dir)
    except OSError as exc:
        die(f'cannot prepare immutable task packet {out}: {exc}')
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            handle.write(encoded)
            handle.flush()
            os.fsync(handle.fileno())
        # Hard-link publication is atomic and refuses to overwrite a concurrent winner.
        # A packet orphaned by a later state-save failure is harmless immutable planning
        # evidence and is reusable on the next claim.
        try:
            os.link(temporary, out)
        except FileExistsError:
            existing = json.loads(out.read_text(encoding='utf-8'))
            if not isinstance(existing, dict) or not packet_matches_semantic_contract(existing, doc, task, feature_dir):
                die(f'immutable task packet has a different semantic contract: {out}; re-plan the task before replacing it')
        dir_fd = os.open(out_dir, os.O_RDONLY)
        try:
            os.fsync(dir_fd)
        finally:
            os.close(dir_fd)
    except OSError as exc:
        die(f'cannot atomically publish task packet {out}: {exc}')
    finally:
        if temporary and os.path.exists(temporary):
            os.unlink(temporary)
    return out


def legacy_packet_path(feature_dir: pathlib.Path, task_id: str) -> pathlib.Path:
    return feature_dir / 'packets' / f'{task_id}.json'


def safe_task_id(task_id: str) -> bool:
    return bool(isinstance(task_id, str) and TASK_ID_RE.fullmatch(task_id))


def packet_revision_id(packet: dict[str, Any]) -> str:
    body = {key: value for key, value in packet.items() if key != 'packet_revision_id'}
    return 'sha256:' + sha256_bytes(json.dumps(body, sort_keys=True, separators=(',', ':')).encode())


def legacy_revision_id(feature_dir: pathlib.Path, task_id: str) -> str:
    path = legacy_packet_path(feature_dir, task_id)
    if not path.exists():
        return 'unpublished'
    if path.is_symlink():
        die('ACTIVE_PACKET_AMBIGUOUS: legacy packet path is a symlink')
    try:
        packet = json.loads(path.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError) as exc:
        die(f'ACTIVE_PACKET_AMBIGUOUS: cannot read legacy packet {path}: {exc}')
    if not isinstance(packet, dict) or not safe_task_id(task_id):
        die('ACTIVE_PACKET_AMBIGUOUS: malformed legacy packet identity')
    validate_packet_integrity(packet)
    return packet_revision_id(packet)


def validate_packet_integrity(packet: dict[str, Any]) -> None:
    stored = packet.get('packet_sha256')
    body = {key: value for key, value in packet.items() if key != 'packet_sha256'}
    if not isinstance(stored, str) or stored != sha256_bytes(json.dumps(body, sort_keys=True, separators=(',', ':')).encode()):
        die('ACTIVE_PACKET_AMBIGUOUS: packet integrity check failed')


def revision_path(feature_dir: pathlib.Path, task_id: str, revision_id: str) -> pathlib.Path:
    if not safe_task_id(task_id) or not isinstance(revision_id, str) or not re.fullmatch(r'sha256:[0-9a-f]{64}', revision_id):
        die('ACTIVE_PACKET_AMBIGUOUS: invalid task or packet revision identifier')
    root = runtime_state_dir(feature_dir) / 'packet-revisions' / feature_dir.name / task_id
    candidate = root / f'{revision_id.split(":", 1)[1]}.json'
    authority_root = runtime_state_dir(feature_dir) / 'packet-revisions'
    feature_root = authority_root / feature_dir.name
    if (root.is_symlink() or feature_root.is_symlink() or candidate.is_symlink() or
            (authority_root.exists() and authority_root.is_symlink()) or
            candidate.resolve().parent != root.resolve()):
        die('ACTIVE_PACKET_AMBIGUOUS: packet revision path escapes its task directory')
    return candidate


def resolve_active_packet(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str,
                          *, state: dict[str, Any] | None = None) -> dict[str, Any]:
    """Resolve one validated active packet; legacy tasks keep their historical path."""
    if not safe_task_id(task_id) or task_id not in task_index(doc):
        die(f'ACTIVE_PACKET_AMBIGUOUS: unknown or unsafe task identity {task_id!r}')
    if state is None:
        state = load_state(feature_dir, doc)
    entry = state['tasks'][task_id]
    validate_attempt_binding_ledger(entry)
    active_id = entry.get('active_packet_revision')
    if active_id is None:
        path = legacy_packet_path(feature_dir, task_id)
        if not path.exists():
            # Preserve lazy packet creation for tasks that have never been claimed.
            fingerprint = semantic_task_contract_sha256(feature_dir, doc, task_index(doc)[task_id])
            return {'revision_id': 'unpublished', 'contract_sha256': fingerprint,
                    'packet': None, 'path': str(path), 'legacy': True}
        if path.is_symlink():
            die('ACTIVE_PACKET_AMBIGUOUS: legacy packet path is a symlink')
        try:
            packet = json.loads(path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError) as exc:
            die(f'ACTIVE_PACKET_AMBIGUOUS: malformed legacy packet: {exc}')
        if not isinstance(packet, dict):
            die('ACTIVE_PACKET_AMBIGUOUS: legacy packet is not an object')
        validate_packet_integrity(packet)
        if packet.get('feature') != doc.get('feature', feature_dir.name) or packet.get('task') != task_id:
            die('ACTIVE_PACKET_AMBIGUOUS: legacy packet feature/task identity mismatch')
        current = packet_payload(doc, task_index(doc)[task_id], feature_dir)
        # Before replanning, semantic drift is never silently accepted.
        if not packet_matches_semantic_contract(packet, doc, task_index(doc)[task_id], feature_dir):
            die('TASK_REPLAN_REQUIRED: active legacy packet differs from the planning contract')
        return {'revision_id': packet_revision_id(packet),
                'contract_sha256': packet.get('semantic_contract_sha256', current['semantic_contract_sha256']),
                'packet': packet, 'path': str(path), 'legacy': True}
    lineage = entry.get('packet_lineage')
    if not isinstance(lineage, list) or not lineage:
        die('ACTIVE_PACKET_AMBIGUOUS: active revision has no packet lineage')
    ids: list[str] = []
    previous = None
    for relation in lineage:
        if (not isinstance(relation, dict) or relation.get('previous_revision') != previous or
                not isinstance(relation.get('revision_id'), str) or
                not re.fullmatch(r'sha256:[0-9a-f]{64}', relation['revision_id']) or
                not isinstance(relation.get('contract_sha256'), str) or
                not re.fullmatch(r'[0-9a-f]{64}', relation['contract_sha256'])):
            die('ACTIVE_PACKET_AMBIGUOUS: malformed or disconnected packet lineage')
        if not isinstance(relation.get('legacy'), bool):
            die('ACTIVE_PACKET_AMBIGUOUS: packet lineage is missing legacy identity evidence')
        previous = relation['revision_id']
        ids.append(previous)
    if len(ids) != len(set(ids)) or ids[-1] != active_id:
        die('ACTIVE_PACKET_AMBIGUOUS: packet lineage is cyclic or active pointer disagrees')
    requests = entry.get('replan_requests')
    if not isinstance(requests, list) or len(requests) != len(lineage) - 1:
        die('ACTIVE_PACKET_AMBIGUOUS: packet supersession lineage and audit requests disagree')
    seen_request_ids: set[str] = set()
    repository = str(git_common_dir(feature_dir))
    for index, request in enumerate(requests):
        old_relation, new_relation = lineage[index], lineage[index + 1]
        if (not isinstance(request, dict) or
                not isinstance(request.get('request_id'), str) or
                not re.fullmatch(r'[0-9a-f]{64}', request['request_id']) or
                request['request_id'] in seen_request_ids or
                request.get('repository') != repository or
                request.get('feature') != doc.get('feature', feature_dir.name) or
                request.get('task') != task_id or
                request.get('old_revision') != old_relation['revision_id'] or
                request.get('new_revision') != new_relation['revision_id'] or
                request.get('old_contract_sha256') != old_relation['contract_sha256'] or
                request.get('new_contract_sha256') != new_relation['contract_sha256'] or
                request.get('expected_status') != 'running' or
                not isinstance(request.get('expected_attempts'), int) or
                isinstance(request.get('expected_attempts'), bool) or request['expected_attempts'] < 1 or
                not isinstance(request.get('reason'), str) or not request['reason'].strip() or
                not isinstance(request.get('provenance'), str) or not request['provenance'] or
                parse_timestamp(request.get('committed_at')) is None):
            die('ACTIVE_PACKET_AMBIGUOUS: malformed or conflicting replan audit record')
        seen_request_ids.add(request['request_id'])
    path = revision_path(feature_dir, task_id, active_id)
    try:
        packet = json.loads(path.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError) as exc:
        die(f'ACTIVE_PACKET_AMBIGUOUS: active immutable packet is missing or malformed: {exc}')
    if not isinstance(packet, dict):
        die('ACTIVE_PACKET_AMBIGUOUS: active packet is not an object')
    validate_packet_integrity(packet)
    if packet_revision_id(packet) != active_id or packet.get('task') != task_id or packet.get('feature') != doc.get('feature', feature_dir.name):
        die('ACTIVE_PACKET_AMBIGUOUS: active packet content identity mismatch')
    last = lineage[-1]
    if packet.get('semantic_contract_sha256') != last['contract_sha256']:
        die('ACTIVE_PACKET_AMBIGUOUS: active packet contract differs from lineage')
    for relation in lineage[:-1]:
        historical_path = (legacy_packet_path(feature_dir, task_id) if relation['legacy']
                           else revision_path(feature_dir, task_id, relation['revision_id']))
        try:
            historical = json.loads(historical_path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError) as exc:
            die(f'ACTIVE_PACKET_AMBIGUOUS: historical packet in lineage is unavailable: {exc}')
        if not isinstance(historical, dict):
            die('ACTIVE_PACKET_AMBIGUOUS: historical packet in lineage is not an object')
        validate_packet_integrity(historical)
        if (packet_revision_id(historical) != relation['revision_id'] or
                historical.get('task') != task_id or
                historical.get('feature') != doc.get('feature', feature_dir.name)):
            die('ACTIVE_PACKET_AMBIGUOUS: historical packet identity differs from lineage')
        historical_contract = historical.get('semantic_contract_sha256')
        if historical_contract is None:
            historical_task = {field: historical[field] for field in TASK_CONTRACT_FIELDS
                               if field in historical}
            if 'id' not in historical_task and isinstance(historical.get('task'), str):
                historical_task['id'] = historical['task']
            historical_semantic = semantic_task_contract(
                feature_dir, doc, historical_task,
                feature_sha256=historical.get('feature_fingerprint'),
                test_policy=historical.get('test_policy', 'legacy'))
            historical_contract = sha256_bytes(json.dumps(
                historical_semantic, sort_keys=True, separators=(',', ':')).encode())
        if historical_contract != relation['contract_sha256']:
            die('ACTIVE_PACKET_AMBIGUOUS: historical contract differs from lineage')
    if entry.get('status') == 'running':
        attempts = entry.get('attempt_bindings', [])
        latest = max((x for x in attempts if isinstance(x, dict) and isinstance(x.get('attempt'), int)),
                     key=lambda x: x['attempt'], default=None) if isinstance(attempts, list) else None
        if latest is None or latest.get('packet_revision') != active_id or latest.get('contract_sha256') != last['contract_sha256']:
            die('ACTIVE_PACKET_AMBIGUOUS: running lifecycle attempt is not bound to the active packet')
    return {'revision_id': active_id, 'contract_sha256': last['contract_sha256'],
            'packet': packet, 'path': str(path), 'legacy': False}


def validate_attempt_binding_ledger(entry: dict[str, Any]) -> None:
    history = entry.get('attempt_bindings')
    if history is None:
        return
    if not isinstance(history, list):
        die('ACTIVE_PACKET_AMBIGUOUS: attempt binding ledger is malformed')
    seen: set[int] = set()
    for binding in history:
        if not isinstance(binding, dict):
            die('ACTIVE_PACKET_AMBIGUOUS: attempt binding record is not an object')
        attempt = binding.get('attempt')
        status = binding.get('binding_status')
        if (not isinstance(attempt, int) or isinstance(attempt, bool) or attempt < 1 or
                attempt > int(entry.get('attempts', 0)) or attempt in seen):
            die('ACTIVE_PACKET_AMBIGUOUS: invalid or duplicate attempt binding number')
        seen.add(attempt)
        if status == 'proven':
            if (not isinstance(binding.get('packet_revision'), str) or
                    not re.fullmatch(r'sha256:[0-9a-f]{64}', binding['packet_revision']) or
                    not isinstance(binding.get('contract_sha256'), str) or
                    not re.fullmatch(r'[0-9a-f]{64}', binding['contract_sha256'])):
                die('ACTIVE_PACKET_AMBIGUOUS: proven attempt lacks packet and contract identity')
        elif status == 'ambiguous':
            if ('packet_revision' in binding or 'contract_sha256' in binding or
                    not isinstance(binding.get('observed_active_packet_revision'), str) or
                    not re.fullmatch(r'sha256:[0-9a-f]{64}', binding['observed_active_packet_revision']) or
                    not isinstance(binding.get('observed_contract_sha256'), str) or
                    not re.fullmatch(r'[0-9a-f]{64}', binding['observed_contract_sha256']) or
                    not isinstance(binding.get('evidence'), str) or not binding['evidence'].strip()):
                die('ACTIVE_PACKET_AMBIGUOUS: ambiguous attempt binding fabricates or omits identity evidence')
        else:
            die('ACTIVE_PACKET_AMBIGUOUS: attempt binding status is unknown')


def append_attempt_binding(entry: dict[str, Any], feature_dir: pathlib.Path, doc: dict[str, Any],
                           task_id: str, packet_path: pathlib.Path) -> None:
    packet = json.loads(packet_path.read_text(encoding='utf-8'))
    revision_id = packet_revision_id(packet)
    attempt = int(entry.get('attempts', 0))
    history = entry.setdefault('attempt_bindings', [])
    if any(isinstance(item, dict) and item.get('attempt') == attempt for item in history):
        die('ACTIVE_PACKET_AMBIGUOUS: attempt already has a packet binding')
    history.append({'attempt': attempt, 'packet_revision': revision_id,
                    'contract_sha256': packet.get('semantic_contract_sha256') or
                    semantic_task_contract_sha256(feature_dir, doc, task_index(doc)[task_id]),
                    'binding_status': 'proven', 'bound_at': utc_now().isoformat()})


def active_task_contract(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str,
                         *, state: dict[str, Any] | None = None) -> dict[str, Any]:
    active = resolve_active_packet(feature_dir, doc, task_id, state=state)
    if active.get('legacy'):
        return task_index(doc)[task_id]
    packet = active['packet']
    return {
        'id': task_id, 'title': packet['title'], 'objective': packet['objective'],
        'role': packet['role'], 'agent_profile': packet.get('agent_profile', packet['role']),
        'depends_on': packet.get('depends_on', []), 'allowed_paths': packet.get('allowed_paths', []),
        'acceptance_criteria': packet.get('acceptance_criteria', []),
        'risk_tags': packet.get('risk_tags', []), 'verification': packet.get('verification', []),
        'test_mode': packet.get('test_mode'), 'test_seam': packet.get('test_seam'),
    }


def packet_matches_semantic_contract(packet: dict[str, Any], doc: dict[str, Any],
                                     task: dict[str, Any], feature_dir: pathlib.Path) -> bool:
    """Validate packet integrity and execution identity, ignoring generation provenance."""
    stored_hash = packet.get('packet_sha256')
    body = {key: value for key, value in packet.items() if key != 'packet_sha256'}
    if (not isinstance(stored_hash, str) or
            stored_hash != sha256_bytes(json.dumps(body, sort_keys=True, separators=(',', ':')).encode())):
        return False
    packet_task = {field: packet[field] for field in TASK_CONTRACT_FIELDS
                   if field in task and field in packet}
    if 'id' in task and 'id' not in packet_task and isinstance(packet.get('task'), str):
        packet_task['id'] = packet['task']
    # Older immutable packets predate semantic_contract_sha256. Their task projection is
    # sufficient when bound to the unchanged feature contract fingerprint.
    semantic = semantic_task_contract(
        feature_dir, doc, packet_task,
        feature_sha256=packet.get('feature_fingerprint'),
        test_policy=packet.get('test_policy', 'legacy'))
    packet_contract_hash = sha256_bytes(json.dumps(semantic, sort_keys=True, separators=(',', ':')).encode())
    current_contract_hash = semantic_task_contract_sha256(feature_dir, doc, task)
    if packet.get('semantic_contract_sha256') not in (None, packet_contract_hash):
        return False
    current = packet_payload(doc, task, feature_dir)
    provenance_only = {'packet_sha256', 'protocol_fingerprint', 'semantic_contract_sha256'}
    existing_contract = {key: value for key, value in packet.items() if key not in provenance_only}
    current_contract = {key: value for key, value in current.items() if key not in provenance_only}
    return packet_contract_hash == current_contract_hash and existing_contract == current_contract


def validate_evidence(path: pathlib.Path, *, require_pass: bool = False) -> list[str]:
    errors: list[str] = []
    if not path.exists():
        return [f'evidence file does not exist: {path}']
    if path.suffix != '.json':
        return ['completion evidence must be structured JSON produced by a runner or explicit verifier']
    try:
        doc = json.loads(path.read_text(encoding='utf-8'))
    except json.JSONDecodeError as exc:
        return [f'invalid evidence JSON: {exc}']
    if not isinstance(doc, dict):
        return ['evidence JSON must be an object']
    required = ('status', 'summary', 'changed_paths', 'commands', 'assumptions', 'residual_risks')
    for field in required:
        if field not in doc:
            errors.append(f'evidence JSON missing {field}')
    if doc.get('status') not in {'pass', 'fail', 'needs-human'}:
        errors.append('evidence.status must be pass, fail, or needs-human')
    if require_pass and doc.get('status') != 'pass':
        errors.append('completion evidence must have status=pass')
    if 'summary' in doc and (not isinstance(doc['summary'], str) or not doc['summary'].strip()):
        errors.append('evidence.summary must be a non-empty string')
    for field in ('changed_paths', 'commands', 'assumptions', 'residual_risks', 'findings', 'rework_tasks'):
        if field in doc:
            if not isinstance(doc[field], list):
                errors.append(f'evidence.{field} must be an array')
            elif any(not isinstance(item, str) for item in doc[field]):
                errors.append(f'evidence.{field} must contain only strings')
    return errors


def load_validated(feature_dir: pathlib.Path) -> dict[str, Any]:
    errors = validate(feature_dir)
    if errors:
        for error in errors:
            print(f'FAIL: {error}', file=sys.stderr)
        die('feature is invalid')
    return load_json(feature_dir / 'tasks.json')


def cmd_validate(args: argparse.Namespace) -> None:
    errors = validate(args.feature_dir)
    if errors:
        for error in errors:
            print(f'FAIL: {error}')
        raise SystemExit(1)
    print('PASS: feature specification and task DAG are structurally valid')


def cmd_validate_all(args: argparse.Namespace) -> None:
    root = args.specs_root
    if not root.exists():
        die(f'specs root does not exist: {root}')

    documented_dirs = sorted(
        p for p in root.iterdir()
        if p.is_dir() and (p / 'spec.md').exists()
    )
    feature_dirs = [p for p in documented_dirs if (p / 'tasks.json').exists()]
    document_only_dirs = [p for p in documented_dirs if not (p / 'tasks.json').exists()]

    for feature_dir in document_only_dirs:
        print(
            f'INFO {feature_dir}: document-only; no tasks.json; '
            'not executable by validate-all; see docs/specs/INVENTORY.md'
        )

    if not feature_dirs:
        die(f'no executable feature specifications found under {root}')

    failures = 0
    for feature_dir in feature_dirs:
        errors = validate(feature_dir)
        if errors:
            failures += 1
            print(f'FAIL {feature_dir}')
            for error in errors:
                print(f'  - {error}')
        else:
            print(f'PASS {feature_dir}')
    if failures:
        raise SystemExit(1)


def cmd_ready(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    with locked_state(args.feature_dir, doc) as state:
        ids = ready_ids(doc, state, args.feature_dir)
    if args.json:
        print(json.dumps(ids))
    else:
        print('\n'.join(ids) if ids else 'NO_READY_TASKS')


def cmd_packet(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    if args.task_id not in idx:
        die(f'unknown task {args.task_id}')
    active = resolve_active_packet(args.feature_dir, doc, args.task_id)
    if getattr(args, 'identity', False):
        print(json.dumps({'revision_id': active['revision_id'],
                          'contract_sha256': active['contract_sha256'],
                          'path': active['path']}))
    elif args.stdout:
        print(json.dumps(active['packet'], indent=2, sort_keys=True))
    elif active['packet'] is not None:
        print(active['path'])
    else:
        print(write_packet(doc, idx[args.task_id], args.feature_dir))


def cmd_reviewers(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    if args.task_id not in idx:
        die(f'unknown task {args.task_id}')
    selected = reviewers(active_task_contract(args.feature_dir, doc, args.task_id))
    print('\n'.join(selected) if selected else 'NO_SPECIALIST_REVIEWERS')


def cmd_claim(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if (entry and entry.get('status') == 'failed' and
                int(entry.get('attempts', 0)) >= 1 + int(doc.get('max_rework_attempts', 2)) and
                int(entry.get('human_resume_grants', 0)) <= 0):
            classification, grant = classify_retry_authorizations(
                args.feature_dir, doc, state, args.task_id, int(entry.get('attempts', 0)))
            if classification != 'valid':
                if classification == 'legacy-unverifiable' and grant:
                    die(f'RETRY_AUTHORIZATION_LEGACY_UNVERIFIABLE: use --supersedes {grant["id"]}')
                die(f'RETRY_AUTHORIZATION_REQUIRED: {args.task_id} has no valid retry authorization ({classification})')
        if args.task_id not in ready_ids(doc, state, args.feature_dir):
            die(f'{args.task_id} is not ready')
        # Packet validation/publication is a precondition, not part of the claim commit.
        # If it fails, locked_state's exception-saving behavior persists an unchanged state.
        active_packet = write_packet(doc, task_index(doc)[args.task_id], args.feature_dir, state=state)
        staged_state = copy.deepcopy(state)
        entry = staged_state['tasks'][args.task_id]
        consume_attempt_authorization(entry, args.task_id, doc, args.feature_dir, staged_state)
        now = utc_now()
        entry.update({
            'status': 'running',
            'owner': args.owner,
            'attempts': int(entry.get('attempts', 0)) + 1,
            'claimed_at': now.isoformat(),
        })
        append_attempt_binding(entry, args.feature_dir, doc, args.task_id, active_packet)
        refresh_lease(entry, doc, now=now)
        # This single in-memory publication is the final operation in the body. The durable
        # claim commit is locked_state's atomic state-file replacement; all preparation ran
        # against a detached copy, so exceptions before publication preserve the old state.
        state['tasks'][args.task_id] = entry
    try:
        print(f'CLAIMED {args.task_id} by {args.owner}')
    except OSError:
        # Lifecycle commit already succeeded. Do not turn an output-channel failure into
        # a non-zero claim result that falsely suggests the task was left unclaimed.
        return


def cmd_recover_claim(args: argparse.Namespace) -> None:
    """Human-attest that an already committed exceptional claim never began execution."""
    doc = load_validated(args.feature_dir)
    task = task_index(doc).get(args.task_id)
    if task is None:
        die(f'unknown task {args.task_id}')
    reason = str(args.reason or '').strip()
    operator = str(args.by or '').strip()
    if not bool(getattr(args, 'attest_no_execution_started', False)):
        die('CLAIM_RECOVERY_ATTESTATION_REQUIRED: pass --attest-no-execution-started')
    if not reason or not operator:
        die('CLAIM_RECOVERY_INVALID: --reason and --by must be non-empty')
    if args.attempt != 4:
        die('CLAIM_RECOVERY_INVALID: recovery is limited to the historical attempt-4 partial-claim incident')
    packet_path = args.feature_dir / 'packets' / f'{args.task_id}.json'
    try:
        packet = json.loads(packet_path.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError) as exc:
        die(f'CLAIM_RECOVERY_PACKET_INVALID: existing immutable packet is unavailable: {exc}')
    if not isinstance(packet, dict) or not packet_matches_semantic_contract(packet, doc, task, args.feature_dir):
        die('CLAIM_RECOVERY_PACKET_INVALID: existing packet does not match the current semantic task contract')
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if not isinstance(entry, dict):
            die('CLAIM_RECOVERY_INVALID: lifecycle entry is malformed')
        existing = entry.get('claim_recovery')
        if existing is not None:
            if (not isinstance(existing, dict) or existing.get('recovery_version') != 1 or
                    existing.get('semantic_contract_sha256') != semantic_task_contract_sha256(args.feature_dir, doc, task) or
                    existing.get('packet_identity') != packet.get('packet_sha256')):
                die('CLAIM_RECOVERY_CONFLICT: stored recovery evidence no longer matches current contract/packet')
            assertion = (existing.get('attempt'), existing.get('authorization_id'), existing.get('owner'),
                         existing.get('reason'), existing.get('operator_provenance'), existing.get('attestation'))
            requested = (args.attempt, args.authorization, args.owner, reason, operator, 'no_execution_started')
            if assertion == requested:
                print(f'ALREADY_RECOVERED {args.task_id} attempt={args.attempt}')
                return
            die('CLAIM_RECOVERY_CONFLICT: a different recovery assertion already exists for this attempt')
        if entry.get('status') != 'running' or entry.get('attempts') != args.attempt:
            die('CLAIM_RECOVERY_INVALID: exact current running status and attempt must match')
        if entry.get('owner') != args.owner:
            die('CLAIM_RECOVERY_INVALID: exact current owner does not match')
        if not is_unrecovered_partial_claim(entry):
            die('CLAIM_RECOVERY_INVALID: lifecycle entry is not an identified historical partial claim')
        claimed_at = parse_timestamp(entry.get('claimed_at'))

        def evidence_attempt(field: str, value: Any) -> tuple[str, int | None]:
            """Return (classification, attempt) for legacy and attempt-bound evidence."""
            if not value:
                return 'absent', None
            explicit = entry.get(f'{field}_attempt')
            if explicit is None and isinstance(value, dict):
                explicit = value.get('attempt')
            if isinstance(explicit, int) and not isinstance(explicit, bool):
                return ('current' if explicit == args.attempt else 'previous'), explicit
            # Legacy release markers delimit the prior attempt when they precede this
            # claim and are semantically paired with its failure/checkpoint record.
            if field == 'released_at':
                released_at = parse_timestamp(value)
                if (released_at is not None and claimed_at is not None and released_at < claimed_at and
                        entry.get('last_failure') and entry.get('last_attempt_commit')):
                    return 'previous', int(entry.get('attempts', 0)) - 1
            if field in ('last_attempt_commit', 'last_failure'):
                release_attempt = entry.get('released_at_attempt')
                released_at = parse_timestamp(entry.get('released_at'))
                if (isinstance(release_attempt, int) and release_attempt < args.attempt and
                        released_at is not None and claimed_at is not None and released_at < claimed_at):
                    return 'previous', release_attempt
                # A legacy release/failure bundle is attributable to the preceding
                # attempt only when the release boundary and claim ordering agree.
                if (release_attempt is None and released_at is not None and claimed_at is not None and
                        released_at < claimed_at and entry.get('last_failure') and entry.get('last_attempt_commit') and
                        int(entry.get('attempts', 0)) == args.attempt):
                    return 'previous', int(entry.get('attempts', 0)) - 1
            return 'ambiguous', None

        evidence_fields = (
            'checkpoint_commit', 'last_attempt_commit', 'completion_marker', 'execution_started_at',
            'execution_journal', 'verification_started_at', 'verification_journal', 'started_at',
            'completed_at', 'failed_at', 'released_at', 'lease_recovered_at', 'start_rollback_reason',
            'last_failure_evidence', 'last_failure',
        )
        for field in evidence_fields:
            value = entry.get(field)
            classification, _ = evidence_attempt(field, value)
            if classification in ('current', 'ambiguous'):
                code = 'CURRENT_ATTEMPT' if classification == 'current' else 'AMBIGUOUS'
                die(f'CLAIM_RECOVERY_{code}_EVIDENCE: lifecycle {field}')
        if entry.get('worktree'):
            die('CLAIM_RECOVERY_AMBIGUOUS_EVIDENCE: worktree')
        evidence_root = git_common_dir(args.feature_dir).parent / '.agent-runs' / str(doc.get('feature', args.feature_dir.name))
        if evidence_root.exists():
            for evidence_path in evidence_root.rglob('*.json'):
                try:
                    evidence = json.loads(evidence_path.read_text(encoding='utf-8'))
                except (OSError, json.JSONDecodeError):
                    die(f'CLAIM_RECOVERY_CONFLICT: malformed runtime evidence {evidence_path}')
                if not isinstance(evidence, dict):
                    die(f'CLAIM_RECOVERY_CONFLICT: ambiguous runtime evidence {evidence_path}')
                if evidence.get('task') == args.task_id:
                    runtime_attempt = evidence.get('attempt')
                    if not isinstance(runtime_attempt, int) or isinstance(runtime_attempt, bool):
                        die('CLAIM_RECOVERY_AMBIGUOUS_EVIDENCE: runtime record')
                    if runtime_attempt == args.attempt:
                        die('CLAIM_RECOVERY_CURRENT_ATTEMPT_EVIDENCE: runtime record')
        grants = entry.get('retry_authorizations')
        if not isinstance(grants, list):
            die('CLAIM_RECOVERY_INVALID: authorization ledger is malformed')
        matching = [g for g in grants if isinstance(g, dict) and g.get('id') == args.authorization]
        if len(matching) != 1:
            die('CLAIM_RECOVERY_INVALID: exact authorization ID must occur once in this task ledger')
        grant = matching[0]
        expected_binding = retry_binding(args.feature_dir, doc, args.task_id, 3, state=state)
        binding = grant.get('binding')
        if (grant.get('version') != 2 or not isinstance(binding, dict) or binding != expected_binding or
                binding.get('binding_version') != 2 or binding.get('expected_status') != 'failed' or
                binding.get('expected_attempts') != args.attempt - 1 or
                binding.get('repository') != str(git_common_dir(args.feature_dir)) or
                binding.get('feature') != str(doc.get('feature', args.feature_dir.name)) or
                binding.get('task') != args.task_id or
                binding.get('protocol_version') != protocol_version(args.feature_dir) or
                grant.get('consumed_at') is None or grant.get('consumed_attempt') != args.attempt or
                entry.get('active_retry_authorization') != args.authorization):
            die('CLAIM_RECOVERY_INVALID: V2 grant must bind failed attempt 3 and be consumed into attempt 4')
        if (not isinstance(grant.get('reason'), str) or not grant['reason'].strip() or
                not isinstance(grant.get('provenance'), str) or not grant['provenance']):
            die('CLAIM_RECOVERY_INVALID: authorization provenance is malformed')
        issued_at = parse_timestamp(grant.get('issued_at'))
        consumed_at = parse_timestamp(grant.get('consumed_at'))
        if issued_at is None or consumed_at is None or consumed_at < issued_at:
            die('CLAIM_RECOVERY_INVALID: authorization issue/consumption history is malformed')
        supersessions = entry.get('retry_authorization_supersessions', [])
        if not isinstance(supersessions, list) or any(
                not isinstance(rel, dict) or not isinstance(rel.get('supersedes'), str) or
                not isinstance(rel.get('authorization_id'), str) or
                not isinstance(rel.get('reason'), str) or not rel['reason'].strip() or
                not isinstance(rel.get('provenance'), str) or not rel['provenance'] or
                parse_timestamp(rel.get('issued_at')) is None or rel.get('supersedes') == rel.get('authorization_id')
                for rel in supersessions):
            die('CLAIM_RECOVERY_INVALID: supersession history is malformed or ambiguous')
        superseded_ids = {rel['supersedes'] for rel in supersessions}
        if len(superseded_ids) != len(supersessions):
            die('CLAIM_RECOVERY_INVALID: supersession history is malformed or ambiguous')
        superseding_grants = [g for g in grants if isinstance(g, dict) and g.get('supersedes') == args.authorization]
        grant_supersedes = grant.get('supersedes')
        lineage = []
        if grant_supersedes is not None:
            lineage = [rel for rel in supersessions if rel.get('supersedes') == grant_supersedes and
                       rel.get('authorization_id') == args.authorization]
            old_grants = [g for g in grants if isinstance(g, dict) and g.get('id') == grant_supersedes]
            old = old_grants[0] if len(old_grants) == 1 else {}
            old_binding = old.get('binding') if isinstance(old.get('binding'), dict) else {}
            old_required = {'repository', 'feature', 'task', 'expected_status', 'expected_attempts',
                            'feature_fingerprint', 'packet_sha256', 'protocol_version'}
            same_old_identity = all(old_binding.get(key) == expected_binding.get(key)
                                    for key in ('repository', 'feature', 'task', 'protocol_version'))
            valid_old = (
                old.get('version') == 1 and set(old_binding) == old_required and same_old_identity and
                old_binding.get('expected_status') == 'failed' and old_binding.get('expected_attempts') == 3 and
                isinstance(old_binding.get('feature_fingerprint'), str) and
                re.fullmatch(r'[0-9a-f]{64}', old_binding['feature_fingerprint']) is not None and
                isinstance(old_binding.get('packet_sha256'), str) and
                re.fullmatch(r'[0-9a-f]{64}', old_binding['packet_sha256']) is not None and
                isinstance(old.get('reason'), str) and bool(old['reason'].strip()) and
                isinstance(old.get('provenance'), str) and bool(old['provenance']) and
                parse_timestamp(old.get('issued_at')) is not None and old.get('consumed_at') is None
            )
            if (len(lineage) != 1 or len(old_grants) != 1 or not valid_old or superseding_grants):
                die('CLAIM_RECOVERY_INVALID: authorization supersession history is malformed or ambiguous')
        if (args.authorization in superseded_ids or
                any(rel.get('authorization_id') == args.authorization for rel in supersessions) != bool(lineage)):
            die('CLAIM_RECOVERY_INVALID: authorization is superseded or involved in a supersession')
        allowed_legacy_ids = {grant_supersedes} if lineage else set()
        if any(not isinstance(g, dict) or
               (g.get('version') != 2 and not (g.get('version') == 1 and g.get('id') in allowed_legacy_ids)) or
               (g is not grant and g.get('consumed_at') is not None) for g in grants):
            die('CLAIM_RECOVERY_INVALID: mixed/legacy or conflicting consumed authorization history')
        if any(g.get('consumed_at') is None and g is not grant and g.get('id') not in superseded_ids for g in grants):
            die('CLAIM_RECOVERY_INVALID: another effective unconsumed authorization is ambiguous')
        now = utc_now()
        entry['claim_recovery'] = {
            'task': args.task_id, 'attempt': args.attempt, 'authorization_id': args.authorization,
            'owner': args.owner, 'reason': reason, 'operator_provenance': operator,
            'attestation': 'no_execution_started',
            'semantic_contract_sha256': semantic_task_contract_sha256(args.feature_dir, doc, task),
            'packet_identity': packet['packet_sha256'], 'recovered_at': now.isoformat(),
            'recovery_version': 1,
        }
        refresh_lease(entry, doc, now=now)
    print(f'RECOVERED_CLAIM {args.task_id} attempt={args.attempt} owner={args.owner}')


def owner_guard(entry: dict[str, Any], owner: str) -> None:
    if entry.get('owner') and entry.get('owner') != owner:
        die(f'task is owned by {entry.get("owner")}, not {owner}')


def consume_attempt_authorization(entry: dict[str, Any], task_id: str, doc: dict[str, Any], feature_dir: pathlib.Path | None = None, state: dict[str, Any] | None = None) -> str | None:
    """Consume normal retry budget or one explicit human resume grant."""
    max_attempts = 1 + int(doc.get('max_rework_attempts', 2))
    attempts = int(entry.get('attempts', 0))
    grants = int(entry.get('human_resume_grants', 0))
    if attempts >= max_attempts:
        if entry.get('status') != 'failed':
            die(f'RETRY_AUTHORIZATION_INVALID: {task_id} must be failed before exceptional claim')
        if grants > 0:
            entry['human_resume_grants'] = grants - 1
            resolution = str(entry.get('human_resolution') or '') or None
            entry['active_human_resume'] = resolution or 'human-resolution'
            return resolution
        if feature_dir is None or state is None:
            die(f'ATTEMPTS_EXHAUSTED: {task_id} exhausted its {max_attempts} allowed attempts')
        classification, authorization = classify_retry_authorizations(feature_dir, doc, state, task_id, attempts)
        if authorization is None or classification != 'valid':
            if classification == 'legacy-unverifiable':
                die(f'RETRY_AUTHORIZATION_LEGACY_UNVERIFIABLE: {task_id} requires explicit --supersedes {authorization["id"]}')
            die(f'RETRY_AUTHORIZATION_REQUIRED: ATTEMPTS_EXHAUSTED: {task_id} exhausted its {max_attempts} allowed attempts ({classification})')
        authorization['consumed_at'] = utc_now().isoformat()
        authorization['consumed_attempt'] = attempts + 1
        entry['active_retry_authorization'] = authorization['id']
        return str(authorization['id'])
    if grants <= 0:
        return None
    if grants > 0:
        entry['human_resume_grants'] = grants - 1
        resolution = str(entry.get('human_resolution') or '') or None
        entry['active_human_resume'] = resolution or 'human-resolution'
        return resolution
    return None


def retry_binding(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, attempts: int,
                  *, state: dict[str, Any] | None = None) -> dict[str, Any]:
    active = resolve_active_packet(feature_dir, doc, task_id, state=state)
    binding = {
        'binding_version': 2,
        'repository': str(git_common_dir(feature_dir)),
        'feature': str(doc.get('feature', feature_dir.name)),
        'task': task_id,
        'expected_status': 'failed',
        'expected_attempts': attempts,
        'contract_sha256': active['contract_sha256'],
        'protocol_version': protocol_version(feature_dir),
    }
    # Revision binding is introduced lazily. Historical v2 grants against a legacy
    # packet retain their semantic V2 interpretation; grants after replan bind both.
    if not active.get('legacy'):
        binding['packet_revision'] = active['revision_id']
    return binding


TASK_CONTRACT_FIELDS = (
    'id', 'title', 'objective', 'role', 'agent_profile', 'depends_on',
    'allowed_paths', 'acceptance_criteria', 'risk_tags', 'verification',
    'test_mode', 'test_seam',
)


def semantic_task_contract(feature_dir: pathlib.Path, doc: dict[str, Any], task: dict[str, Any], *,
                           feature_sha256: str | None = None,
                           test_policy: str | None = None) -> dict[str, Any]:
    """Canonical execution contract shared by retry authorization and packet identity."""
    return {
        'feature': doc.get('feature', feature_dir.name),
        'feature_contract_sha256': feature_sha256 or feature_fingerprint(feature_dir),
        'task_contract': {key: task[key] for key in TASK_CONTRACT_FIELDS if key in task},
        'test_policy': test_policy if test_policy is not None else doc.get('test_policy', 'legacy'),
    }


def semantic_task_contract_sha256(feature_dir: pathlib.Path, doc: dict[str, Any], task: dict[str, Any], *,
                                 feature_sha256: str | None = None,
                                 test_policy: str | None = None) -> str:
    semantic = semantic_task_contract(feature_dir, doc, task, feature_sha256=feature_sha256,
                                      test_policy=test_policy)
    return sha256_bytes(json.dumps(semantic, sort_keys=True, separators=(',', ':')).encode())


def classify_retry_authorizations(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any], task_id: str, attempts: int) -> tuple[str, dict[str, Any] | None]:
    entry = state['tasks'][task_id]
    grants = entry.get('retry_authorizations', [])
    if not isinstance(grants, list):
        die('RETRY_AUTHORIZATION_INVALID: authorization ledger is malformed')
    expected = retry_binding(feature_dir, doc, task_id, attempts, state=state)
    if entry.get('status') != 'failed':
        die('RETRY_AUTHORIZATION_INVALID: task is not failed')
    relations = entry.get('retry_authorization_supersessions', [])
    if not isinstance(relations, list):
        die('RETRY_AUTHORIZATION_INVALID: supersession ledger is malformed')
    superseded_ids: set[str] = set()
    supersession_target: dict[str, str] = {}
    for relation in relations:
        if (not isinstance(relation, dict) or not isinstance(relation.get('supersedes'), str) or
                not isinstance(relation.get('authorization_id'), str) or
                not isinstance(relation.get('reason'), str) or not relation['reason'].strip() or
                not isinstance(relation.get('provenance'), str) or not relation['provenance'] or
                parse_timestamp(relation.get('issued_at')) is None):
            die('RETRY_AUTHORIZATION_INVALID: malformed supersession relation')
        if relation['supersedes'] in superseded_ids:
            die('RETRY_AUTHORIZATION_INVALID: authorization superseded more than once')
        if relation['supersedes'] == relation['authorization_id']:
            die('RETRY_AUTHORIZATION_INVALID: self-supersession is not allowed')
        superseded_ids.add(relation['supersedes'])
        supersession_target[relation['supersedes']] = relation['authorization_id']
    valid: list[dict[str, Any]] = []
    legacy: list[dict[str, Any]] = []
    consumed: list[dict[str, Any]] = []
    stale: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for grant in grants:
        required = {'version', 'id', 'binding', 'reason', 'provenance', 'issued_at', 'consumed_at'}
        if not isinstance(grant, dict) or not required.issubset(grant) or grant.get('version') not in {1, 2} or not isinstance(grant.get('binding'), dict):
            die('RETRY_AUTHORIZATION_INVALID: malformed or unknown authorization')
        if (not isinstance(grant.get('id'), str) or not grant['id'] or
                not isinstance(grant.get('reason'), str) or not grant['reason'].strip() or
                not isinstance(grant.get('provenance'), str) or not grant['provenance'] or
                parse_timestamp(grant.get('issued_at')) is None or
                (grant.get('consumed_at') is not None and parse_timestamp(grant.get('consumed_at')) is None)):
            die('RETRY_AUTHORIZATION_INVALID: malformed audit fields')
        consumed_attempt = grant.get('consumed_attempt')
        if grant.get('consumed_at') is not None and (not isinstance(consumed_attempt, int) or isinstance(consumed_attempt, bool) or consumed_attempt < 1):
            die('RETRY_AUTHORIZATION_INVALID: consumed authorization has no valid consumed_attempt')
        if grant['id'] in seen_ids:
            die('RETRY_AUTHORIZATION_INVALID: duplicate authorization ID')
        seen_ids.add(grant['id'])
        binding = grant['binding']
        binding_version = binding.get('binding_version', 1)
        if not ((binding_version == 2 and grant.get('version') == 2) or
                (binding_version == 1 and grant.get('version') == 1)):
            die('RETRY_AUTHORIZATION_INVALID: unknown authorization binding version')
        if binding_version == 2 and grant.get('version') == 2:
            required_binding = {'binding_version', 'repository', 'feature', 'task', 'expected_status',
                                'expected_attempts', 'contract_sha256', 'protocol_version'}
            allowed_binding = required_binding | {'packet_revision'}
            if (not required_binding.issubset(binding) or set(binding) - allowed_binding or
                    ('packet_revision' in binding and not re.fullmatch(r'sha256:[0-9a-f]{64}', str(binding['packet_revision']))) or
                    not isinstance(binding.get('contract_sha256'), str) or not re.fullmatch(r'[0-9a-f]{64}', binding['contract_sha256'])):
                die('RETRY_AUTHORIZATION_INVALID: malformed v2 semantic binding')
        elif binding_version == 1 and grant.get('version') == 1:
            required_binding = {'repository', 'feature', 'task', 'expected_status', 'expected_attempts',
                                'feature_fingerprint', 'packet_sha256', 'protocol_version'}
            if (set(binding) != required_binding or not isinstance(binding.get('packet_sha256'), str) or
                    not re.fullmatch(r'[0-9a-f]{64}', binding['packet_sha256'])):
                die('RETRY_AUTHORIZATION_INVALID: malformed v1 packet binding')
        if grant['id'] in superseded_ids:
            continue
        if grant.get('consumed_at') is not None:
            consumed.append(grant)
            continue
        if binding_version == 2 and grant.get('version') == 2:
            if binding == expected:
                valid.append(grant)
            else:
                stale.append(grant)
        elif binding_version == 1 and grant.get('version') == 1:
            same_identity = all(binding.get(key) == expected.get(key) for key in ('repository', 'feature', 'task'))
            if (same_identity and binding.get('expected_status') == 'failed' and
                    binding.get('expected_attempts') == attempts and
                    binding.get('protocol_version') == expected['protocol_version']):
                legacy.append(grant)
            else:
                stale.append(grant)
        else:
            die('RETRY_AUTHORIZATION_INVALID: unknown authorization binding version')
    grants_by_id = {grant['id']: grant for grant in grants if isinstance(grant, dict) and isinstance(grant.get('id'), str)}
    for old_id, new_id in supersession_target.items():
        old = grants_by_id.get(old_id)
        new = grants_by_id.get(new_id)
        if (old is None or new is None or old.get('version') != 1 or old.get('consumed_at') is not None or
                new.get('version') != 2 or new.get('supersedes') != old_id or
                new.get('binding', {}).get('repository') != expected['repository'] or
                new.get('binding', {}).get('feature') != expected['feature'] or
                new.get('binding', {}).get('task') != task_id or
                new.get('binding', {}).get('expected_status') != 'failed' or
                new.get('binding', {}).get('expected_attempts') != attempts):
            die('RETRY_AUTHORIZATION_INVALID: supersession relation does not match immutable grant history')
    if len(valid) > 1 or len(legacy) > 1 or (valid and legacy):
        die('RETRY_AUTHORIZATION_INVALID: conflicting eligible authorization history')
    if valid:
        return 'valid', valid[0]
    if legacy:
        return 'legacy-unverifiable', legacy[0]
    if consumed:
        return 'consumed', consumed[0]
    if stale:
        return 'stale', stale[0]
    if superseded_ids:
        return 'superseded', None
    return 'absent', None


def matching_retry_authorization(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any], task_id: str, attempts: int) -> dict[str, Any] | None:
    classification, grant = classify_retry_authorizations(feature_dir, doc, state, task_id, attempts)
    return grant if classification == 'valid' else None


def retry_authorization_status(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any],
                              task_id: str, attempts: int, authorization_id: str) -> str:
    entry = state['tasks'][task_id]
    grant = next((item for item in entry.get('retry_authorizations', [])
                  if isinstance(item, dict) and item.get('id') == authorization_id), None)
    if grant is None:
        return 'absent'
    if any(isinstance(rel, dict) and rel.get('supersedes') == authorization_id
           for rel in entry.get('retry_authorization_supersessions', [])):
        return 'superseded'
    if grant.get('consumed_at') is not None:
        return 'consumed'
    binding = grant.get('binding', {})
    if grant.get('version') == 1 or binding.get('binding_version', 1) == 1:
        expected = retry_binding(feature_dir, doc, task_id, attempts, state=state)
        same_identity = all(binding.get(key) == expected.get(key) for key in ('repository', 'feature', 'task'))
        return ('legacy-unverifiable' if same_identity and binding.get('expected_attempts') == attempts and
                binding.get('protocol_version') == expected['protocol_version'] else 'stale')
    expected = retry_binding(feature_dir, doc, task_id, attempts, state=state)
    return 'valid' if binding == expected else 'stale'


def human_resolution_identity(explicit: str | None) -> str:
    if explicit and explicit.strip():
        return f'recorded:{explicit.strip()}'
    name = subprocess.run(['git', 'config', 'user.name'], capture_output=True, text=True, check=False).stdout.strip()
    email = subprocess.run(['git', 'config', 'user.email'], capture_output=True, text=True, check=False).stdout.strip()
    return f'git-config:{name} <{email}>' if name or email else 'unverified-local-operator'


def cmd_authorize_retry(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    if args.task_id not in task_index(doc):
        die(f'unknown task {args.task_id}')
    reason = args.reason.strip()
    if not reason:
        die('RETRY_AUTHORIZATION_INVALID: --reason must be non-empty')
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'][args.task_id]
        attempts = int(entry.get('attempts', 0))
        maximum = 1 + int(doc.get('max_rework_attempts', 2))
        if entry.get('status') != 'failed' or attempts < maximum:
            die('RETRY_AUTHORIZATION_INVALID: task must be failed with exhausted normal attempts')
        existing = entry.get('retry_authorizations', [])
        if not isinstance(existing, list):
            die('RETRY_AUTHORIZATION_INVALID: authorization ledger is malformed')
        classification, prior = classify_retry_authorizations(args.feature_dir, doc, state, args.task_id, attempts)
        supersedes = getattr(args, 'supersedes', None)
        if supersedes is not None and (not isinstance(supersedes, str) or not supersedes.strip()):
            die('RETRY_AUTHORIZATION_SUPERSESSION_INVALID: --supersedes must be a non-empty authorization ID')
        if supersedes is not None:
            old = next((item for item in existing if isinstance(item, dict) and item.get('id') == supersedes), None)
            if old is None:
                die('RETRY_AUTHORIZATION_SUPERSESSION_INVALID: exact authorization ID was not found for this task')
            if old.get('consumed_at') is not None:
                die('RETRY_AUTHORIZATION_SUPERSESSION_INVALID: consumed authorization cannot be superseded')
            if any(rel.get('supersedes') == supersedes for rel in entry.get('retry_authorization_supersessions', []) if isinstance(rel, dict)):
                die('RETRY_AUTHORIZATION_SUPERSESSION_INVALID: authorization is already superseded')
            old_binding = old.get('binding', {})
            if (old_binding.get('repository') != str(git_common_dir(args.feature_dir)) or
                    old_binding.get('feature') != str(doc.get('feature', args.feature_dir.name)) or
                    old_binding.get('task') != args.task_id):
                die('RETRY_AUTHORIZATION_SUPERSESSION_INVALID: repository, feature, or task identity differs')
            if old_binding.get('expected_status') != 'failed' or old_binding.get('expected_attempts') != attempts:
                die('RETRY_AUTHORIZATION_SUPERSESSION_INVALID: current failed status or attempt count differs')
            if old.get('version') != 1 or old_binding.get('binding_version', 1) != 1 or classification != 'legacy-unverifiable' or not prior or prior.get('id') != supersedes:
                die('RETRY_AUTHORIZATION_SUPERSESSION_INVALID: only the current unverifiable legacy grant may be superseded')
            if classification == 'valid':
                die('RETRY_AUTHORIZATION_SUPERSESSION_INVALID: legacy authorization is not unusable')
        else:
            if classification == 'valid':
                die('RETRY_AUTHORIZATION_INVALID: a valid authorization already exists for this task state')
            if classification == 'legacy-unverifiable':
                die(f'RETRY_AUTHORIZATION_SUPERSESSION_REQUIRED: specify --supersedes {prior["id"]}')
        binding = retry_binding(args.feature_dir, doc, args.task_id, attempts, state=state)
        grant_id = hashlib.sha256(os.urandom(32)).hexdigest()
        grant = {'version': 2, 'id': grant_id, 'binding': binding,
                 'reason': reason, 'provenance': human_resolution_identity(args.by),
                 'issued_at': utc_now().isoformat(), 'consumed_at': None}
        if supersedes:
            grant['supersedes'] = supersedes
            relation = {'supersedes': supersedes, 'authorization_id': grant_id,
                        'reason': reason, 'provenance': grant['provenance'], 'issued_at': grant['issued_at']}
            entry.setdefault('retry_authorization_supersessions', []).append(relation)
        entry.setdefault('retry_authorizations', []).append(grant)
    print(f'RETRY_AUTHORIZATION_CREATED {args.task_id} id={grant["id"]}')


def replan_failpoint(_stage: str) -> None:
    """Test seam for durable transaction failure injection."""


def validate_replan_task(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str,
                         proposed: Any) -> dict[str, Any]:
    if not safe_task_id(task_id) or not isinstance(proposed, dict) or proposed.get('id') != task_id:
        die('TASK_REPLAN_NOT_ALLOWED: proposed task identity must match the target task')
    required = {'id', 'title', 'objective', 'role', 'depends_on', 'allowed_paths',
                'risk_tags', 'acceptance_criteria', 'verification'}
    if not required.issubset(proposed):
        die('TASK_REPLAN_NOT_ALLOWED: proposed task is missing required planning fields')
    if (not isinstance(proposed['title'], str) or not proposed['title'].strip() or
            not isinstance(proposed['objective'], str) or not proposed['objective'].strip()):
        die('TASK_REPLAN_NOT_ALLOWED: title and objective must be non-empty')
    if proposed.get('role') not in VALID_ROLES:
        die('TASK_REPLAN_NOT_ALLOWED: invalid task role')
    for field in ('depends_on', 'allowed_paths', 'risk_tags', 'acceptance_criteria', 'verification'):
        if not isinstance(proposed.get(field), list):
            die(f'TASK_REPLAN_NOT_ALLOWED: {field} must be an array')
    if any(not isinstance(dep, str) or dep not in task_index(doc) or dep == task_id for dep in proposed['depends_on']):
        die('TASK_REPLAN_NOT_ALLOWED: dependencies must identify other planned tasks')
    for pattern in proposed['allowed_paths']:
        if (not isinstance(pattern, str) or not safe_relative_pattern(pattern) or
                (proposed['role'] == 'builder' and not builder_pattern_has_concrete_root(pattern))):
            die('TASK_REPLAN_NOT_ALLOWED: proposed allowed_paths contains an unsafe path')
    if not proposed['acceptance_criteria'] or any(not isinstance(c, str) for c in proposed['acceptance_criteria']):
        die('TASK_REPLAN_NOT_ALLOWED: acceptance criteria must be non-empty strings')
    accepted = set(AC_RE.findall((feature_dir / 'spec.md').read_text(encoding='utf-8')))
    accepted |= vc.criterion_ids(feature_dir)
    if any(c not in accepted for c in proposed['acceptance_criteria']):
        die('TASK_REPLAN_NOT_ALLOWED: criteria must exist in the accepted spec or verification contract')
    if any(not isinstance(item, str) or not item.strip() for item in proposed['verification']):
        die('TASK_REPLAN_NOT_ALLOWED: verification entries must be non-empty strings')
    if any(not isinstance(item, str) or not item.strip() for item in proposed['risk_tags']):
        die('TASK_REPLAN_NOT_ALLOWED: risk tags must be non-empty strings')
    if 'agent_profile' in proposed and (not isinstance(proposed['agent_profile'], str) or not PROFILE_RE.fullmatch(proposed['agent_profile'])):
        die('TASK_REPLAN_NOT_ALLOWED: invalid agent profile')
    if set(proposed) - set(TASK_CONTRACT_FIELDS):
        die('TASK_REPLAN_NOT_ALLOWED: proposed task contains fields outside the accepted task model')
    replacement = copy.deepcopy(doc)
    replacement['tasks'] = [proposed if task.get('id') == task_id else task for task in doc['tasks']]
    graph = task_index(replacement)
    visiting: set[str] = set()
    visited: set[str] = set()
    def visit(tid: str) -> None:
        if tid in visiting:
            die('TASK_REPLAN_NOT_ALLOWED: proposed dependencies introduce a cycle')
        if tid in visited:
            return
        visiting.add(tid)
        for dep in graph[tid].get('depends_on', []):
            visit(dep)
        visiting.remove(tid)
        visited.add(tid)
    for tid in graph:
        visit(tid)
    builders = [task for task in graph.values() if task.get('role') == 'builder']
    evaluators = [task for task in graph.values() if task.get('role') == 'evaluator']
    if builders and not evaluators:
        die('TASK_REPLAN_NOT_ALLOWED: builder tasks require an evaluator in the plan')
    for builder in builders:
        if not any(builder['id'] in evaluator.get('depends_on', []) for evaluator in evaluators):
            die('TASK_REPLAN_NOT_ALLOWED: every builder must be directly covered by an evaluator dependency')
    if evaluators:
        evaluator_criteria = set().union(*(set(task.get('acceptance_criteria', [])) for task in evaluators))
        if not accepted.issubset(evaluator_criteria):
            die('TASK_REPLAN_NOT_ALLOWED: evaluators must cover every accepted criterion')
    integrations = [task for task in graph.values() if task.get('role') == 'integration']
    evaluator_ids = {task['id'] for task in evaluators}
    if any(not evaluator_ids.intersection(task.get('depends_on', [])) for task in integrations):
        die('TASK_REPLAN_NOT_ALLOWED: integrations must depend on an evaluator')
    if proposed['role'] == 'builder' and doc.get('test_policy', 'legacy') == 'risk-driven':
        if proposed.get('test_mode') not in VALID_TEST_MODES or not isinstance(proposed.get('test_seam'), str) or not proposed['test_seam'].strip():
            die('TASK_REPLAN_NOT_ALLOWED: risk-driven builders require a valid test mode and test seam')
    profile = proposed.get('agent_profile') or proposed['role']
    if not (feature_repo_base(feature_dir) / 'docs/agentic-sdd/agents' / f'{profile}.md').is_file():
        die('TASK_REPLAN_NOT_ALLOWED: proposed role profile does not exist')
    return proposed


def publish_revision(feature_dir: pathlib.Path, task_id: str,
                     packet: dict[str, Any]) -> tuple[str, pathlib.Path]:
    revision_id = packet_revision_id(packet)
    path = revision_path(feature_dir, task_id, revision_id)
    authority_root = runtime_state_dir(feature_dir) / 'packet-revisions'
    feature_root = authority_root / feature_dir.name
    task_root = path.parent
    if task_root.is_symlink() or feature_root.is_symlink() or (authority_root.exists() and authority_root.is_symlink()):
        die('ACTIVE_PACKET_AMBIGUOUS: symlink in immutable packet revision path')
    task_root.mkdir(parents=True, exist_ok=True)
    if task_root.resolve().parent != feature_root.resolve():
        die('ACTIVE_PACKET_AMBIGUOUS: packet revision directory escapes repository authority')
    encoded = json.dumps(packet, indent=2, sort_keys=True) + '\n'
    if path.exists():
        if path.read_text(encoding='utf-8') != encoded:
            die('PACKET_SUPERSESSION_CONFLICT: revision identity collision')
        return revision_id, path
    fd, temporary = tempfile.mkstemp(prefix=f'.{path.name}.', dir=task_root)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            handle.write(encoded)
            replan_failpoint('R2')
            handle.flush()
            replan_failpoint('R3')
            os.fsync(handle.fileno())
        try:
            os.link(temporary, path)
        except FileExistsError:
            if path.read_text(encoding='utf-8') != encoded:
                die('PACKET_SUPERSESSION_CONFLICT: revision identity collision')
        dir_fd = os.open(task_root, os.O_RDONLY)
        try:
            os.fsync(dir_fd)
        finally:
            os.close(dir_fd)
    except OSError as exc:
        die(f'PACKET_SUPERSESSION_CONFLICT: immutable packet publication failed: {exc}')
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return revision_id, path


def cmd_replan_task(args: argparse.Namespace) -> None:
    feature_dir = args.feature_dir.resolve()
    doc = load_validated(feature_dir)
    if args.task_id not in task_index(doc):
        die('TASK_REPLAN_NOT_ALLOWED: unknown task')
    reason = str(args.reason or '').strip()
    operator = str(args.by or '').strip()
    if not reason or not operator:
        die('TASK_REPLAN_NOT_ALLOWED: reason and operator provenance are required')
    source_path = pathlib.Path(args.proposed_task_file)
    try:
        resolved_source = source_path.resolve(strict=True)
        resolved_source.relative_to(feature_dir.resolve())
    except (OSError, ValueError):
        die('TASK_REPLAN_NOT_ALLOWED: proposed planning input must be inside the feature directory')
    if source_path.is_symlink() or not resolved_source.is_file():
        die('TASK_REPLAN_NOT_ALLOWED: proposed planning input must be a regular in-feature file')
    try:
        proposed = validate_replan_task(feature_dir, doc, args.task_id,
                                        json.loads(resolved_source.read_text(encoding='utf-8')))
    except (OSError, json.JSONDecodeError) as exc:
        die(f'TASK_REPLAN_NOT_ALLOWED: cannot read proposed task planning input: {exc}')
    proposed_doc = copy.deepcopy(doc)
    proposed_doc['tasks'] = [proposed if task['id'] == args.task_id else task for task in doc['tasks']]
    new_contract = semantic_task_contract_sha256(feature_dir, proposed_doc, proposed)
    repo_identity = str(git_common_dir(feature_dir))
    runtime_state_dir(feature_dir).mkdir(parents=True, exist_ok=True)
    with lock_path(feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        state = _load_state_unlocked(feature_dir, doc)
        entry = state['tasks'][args.task_id]
        request_identity = {'repository': repo_identity, 'feature': doc.get('feature', feature_dir.name),
                            'task': args.task_id, 'expected_status': args.expected_status,
                            'expected_attempts': args.expected_attempts,
                            'expected_active_packet_revision': args.expected_active_revision,
                            'expected_contract_sha256': args.expected_contract_sha256,
                            'proposed_contract_sha256': new_contract, 'proposed_task': proposed,
                            'provenance': operator, 'reason': reason, 'checkpoint': args.checkpoint}
        request_id = sha256_bytes(json.dumps(request_identity, sort_keys=True, separators=(',', ':')).encode())
        prior_requests = entry.get('replan_requests', [])
        if not isinstance(prior_requests, list):
            die('ACTIVE_PACKET_AMBIGUOUS: malformed replan request ledger')
        committed = next((r for r in prior_requests if isinstance(r, dict) and r.get('request_id') == request_id), None)
        if committed:
            print(f'ALREADY_REPLANNED {args.task_id} revision={committed["new_revision"]}')
            if fcntl is not None:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
            return
        if prior_requests:
            if entry.get('active_packet_revision') != args.expected_active_revision:
                die('STALE_ACTIVE_PACKET: a prior replan changed the active packet revision')
            die('REPLAN_CONCURRENT_CONFLICT: a different replan request is already recorded')
        if entry.get('status') == 'completed':
            die('TASK_REPLAN_NOT_ALLOWED: completed tasks cannot be replanned')
        if entry.get('status') != args.expected_status or args.expected_status != 'running':
            die('TASK_REPLAN_NOT_ALLOWED: only the CAS-bound running task transition is supported')
        attempts = entry.get('attempts', 0)
        if not isinstance(attempts, int) or isinstance(attempts, bool) or attempts != args.expected_attempts:
            die('REPLAN_CONCURRENT_CONFLICT: current attempt count differs from request')
        active = resolve_active_packet(feature_dir, doc, args.task_id, state=state)
        if active['revision_id'] != args.expected_active_revision:
            die('STALE_ACTIVE_PACKET: active packet revision changed')
        if active['contract_sha256'] != args.expected_contract_sha256:
            die('STALE_CONTRACT_FINGERPRINT: active semantic contract changed')
        if new_contract == active['contract_sha256']:
            die('TASK_REPLAN_NOT_ALLOWED: proposed contract has no semantic change')
        if active['revision_id'] == 'unpublished':
            die('TASK_REPLAN_NOT_ALLOWED: running task has no published historical packet')
        replan_failpoint('R1')
        packet = packet_payload(proposed_doc, proposed, feature_dir)
        if packet.get('semantic_contract_sha256') != new_contract:
            die('TASK_REPLAN_NOT_ALLOWED: packet fingerprint differs from proposed planning contract')
        revision_id, _packet_path = publish_revision(feature_dir, args.task_id, packet)
        replan_failpoint('R4')
        staged = copy.deepcopy(entry)
        lineage = staged.get('packet_lineage', [])
        if not isinstance(lineage, list):
            die('ACTIVE_PACKET_AMBIGUOUS: packet lineage is malformed')
        if not lineage:
            lineage.append({'previous_revision': None, 'revision_id': active['revision_id'],
                            'contract_sha256': active['contract_sha256'], 'legacy': bool(active.get('legacy'))})
        elif lineage[-1].get('revision_id') != active['revision_id']:
            die('ACTIVE_PACKET_AMBIGUOUS: lineage does not end at active packet')
        lineage.append({'previous_revision': active['revision_id'], 'revision_id': revision_id,
                        'contract_sha256': new_contract, 'legacy': False})
        staged['packet_lineage'] = lineage
        staged['active_packet_revision'] = revision_id
        attempt_history = staged.setdefault('attempt_bindings', [])
        validate_attempt_binding_ledger(staged)
        current_binding = next((record for record in attempt_history
                                if isinstance(record, dict) and record.get('attempt') == attempts), None)
        historical_binding_status = 'ambiguous'
        if (current_binding and current_binding.get('binding_status') == 'proven' and
                current_binding.get('packet_revision') == active['revision_id'] and
                current_binding.get('contract_sha256') == active['contract_sha256']):
            historical_binding_status = 'proven'
        else:
            recovery = staged.get('claim_recovery')
            recovered_packet_matches = (
                isinstance(recovery, dict) and recovery.get('attempt') == attempts and
                isinstance(active.get('packet'), dict) and
                recovery.get('packet_identity') == active['packet'].get('packet_sha256') and
                recovery.get('semantic_contract_sha256') == active['contract_sha256'])
            if recovered_packet_matches:
                historical_binding_status = 'proven'
                attempt_history.append({'attempt': attempts, 'packet_revision': active['revision_id'],
                    'contract_sha256': active['contract_sha256'], 'binding_status': 'proven',
                    'bound_at': utc_now().isoformat(), 'evidence': 'matching claim-recovery packet identity'})
            elif current_binding is None:
                evidence = 'packet revision was not recorded for this historical attempt'
                if isinstance(recovery, dict) and recovery.get('attempt') == attempts:
                    evidence = 'claim-recovery packet identity differs from the current active packet'
                attempt_history.append({'attempt': attempts, 'binding_status': 'ambiguous',
                    'observed_active_packet_revision': active['revision_id'],
                    'observed_contract_sha256': active['contract_sha256'],
                    'evidence': evidence, 'recorded_at': utc_now().isoformat()})
        if attempts > 1:
            staged['unbound_historical_attempts'] = [n for n in range(1, attempts)
                if not any(isinstance(x, dict) and x.get('attempt') == n for x in attempt_history)]
        timestamp = utc_now().isoformat()
        staged['attempt_termination'] = {'attempt': attempts, 'classification': 'REPLAN_SUPERSEDED',
            'reason_code': 'TASK_REPLAN_REQUIRED', 'reason': reason, 'terminated_at': timestamp,
            'active_revision_at_termination': active['revision_id'],
            'contract_sha256_at_termination': active['contract_sha256'],
            'historical_binding_status': historical_binding_status,
            'verification': 'NOT_RUN_BY_REPLAN'}
        if historical_binding_status == 'proven':
            staged['attempt_termination']['packet_revision'] = active['revision_id']
            staged['attempt_termination']['contract_sha256'] = active['contract_sha256']
        staged['status'] = 'failed'
        staged['replanned_at'] = timestamp
        staged['replan_requests'] = [*prior_requests, {'request_id': request_id,
            'repository': repo_identity, 'feature': doc.get('feature', feature_dir.name), 'task': args.task_id,
            'old_revision': active['revision_id'], 'new_revision': revision_id,
            'old_contract_sha256': active['contract_sha256'], 'new_contract_sha256': new_contract,
            'expected_status': 'running', 'expected_attempts': attempts, 'reason': reason,
            'provenance': operator, 'checkpoint': args.checkpoint, 'committed_at': timestamp}]
        for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
            staged.pop(key, None)
        replan_failpoint('R5')
        replan_failpoint('R6')
        state['tasks'][args.task_id] = staged
        replan_failpoint('R7')
        save_state(feature_dir, state)  # atomic lifecycle state replace is the activation commit
        replan_failpoint('R8')
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
    print(f'REPLANNED {args.task_id} revision={revision_id} attempts={attempts}')


def restore_attempt_authorization(entry: dict[str, Any]) -> None:
    """Return a consumed human grant when a start is rolled back before provider execution."""
    if entry.pop('active_human_resume', None) is not None:
        entry['human_resume_grants'] = int(entry.get('human_resume_grants', 0)) + 1
    retry_id = entry.pop('active_retry_authorization', None)
    if retry_id is not None:
        for grant in entry.get('retry_authorizations', []):
            if isinstance(grant, dict) and grant.get('id') == retry_id:
                grant['consumed_at'] = None
                grant.pop('consumed_attempt', None)
                break


def changed_paths(worktree: pathlib.Path) -> list[str]:
    tracked = subprocess.run(
        ['git', 'diff', '--name-only', 'HEAD'], cwd=worktree, capture_output=True, text=True, check=True
    ).stdout.splitlines()
    untracked = subprocess.run(
        ['git', 'ls-files', '--others', '--exclude-standard'], cwd=worktree, capture_output=True, text=True, check=True
    ).stdout.splitlines()
    return sorted(set(p for p in tracked + untracked if p))


def path_matches(path: str, patterns: list[str]) -> bool:
    import fnmatch
    return any(fnmatch.fnmatch(path, pattern) for pattern in patterns)


def checkpoint_worktree(doc: dict[str, Any], task: dict[str, Any], target: pathlib.Path, label: str = 'checkpoint') -> str:
    actual = changed_paths(target)
    patterns = [p for p in task.get('allowed_paths', []) if isinstance(p, str)]
    violations = [path for path in actual if not path_matches(path, patterns)]
    if violations:
        die(f'cannot checkpoint paths outside task allowed_paths: {violations}')

    if actual:
        subprocess.run(['git', 'add', '-A'], cwd=target, check=True)
        feature = str(doc.get('feature', 'feature'))
        message = f'agent {label} {feature} {task["id"]}'
        subprocess.run(
            [
                'git', '-c', 'user.name=Agent Harness',
                '-c', 'user.email=agent-harness@local.invalid',
                'commit', '--no-gpg-sign', '-m', message,
            ],
            cwd=target,
            check=True,
        )
    return subprocess.run(
        ['git', 'rev-parse', 'HEAD'], cwd=target, capture_output=True, text=True, check=True
    ).stdout.strip()


def cmd_complete(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    evidence = pathlib.Path(args.evidence)
    evidence_errors = validate_evidence(evidence, require_pass=True)
    if evidence_errors:
        die('; '.join(evidence_errors))

    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if not entry:
            die(f'unknown task {args.task_id}')
        owner_guard(entry, args.owner)
        if entry.get('status') != 'running':
            die(f'{args.task_id} is not running')
        task = active_task_contract(args.feature_dir, doc, args.task_id, state=state)

        feature = str(doc.get('feature', args.feature_dir.name))
        target = worktree_path(feature, args.task_id)
        checkpoint = None
        if target.exists():
            evidence_doc = load_json(evidence)
            actual = changed_paths(target)
            reported = sorted(set(str(p) for p in evidence_doc.get('changed_paths', [])))
            if reported != actual:
                die(f'evidence changed_paths differs from task worktree; reported={reported}, actual={actual}')
            checkpoint = checkpoint_worktree(doc, task, target)

        entry.update({
            'status': 'completed',
            'completed_at': utc_now().isoformat(),
            'evidence': str(evidence),
            'checkpoint_commit': checkpoint,
        })
        if entry.pop('active_human_resume', None) is not None:
            entry['last_human_resume_used_at'] = utc_now().isoformat()
        if entry.pop('active_retry_authorization', None) is not None:
            entry['last_human_resume_used_at'] = utc_now().isoformat()
        entry.pop('start_origin_status', None)
        for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
            entry.pop(key, None)
    print(f'COMPLETED {args.task_id}' + (f' checkpoint={checkpoint[:12]}' if checkpoint else ' (no worktree checkpoint)'))

def cmd_fail(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if not entry:
            die(f'unknown task {args.task_id}')
        owner_guard(entry, args.owner)
        if entry.get('status') != 'running':
            die(f'{args.task_id} is not running')
        task = active_task_contract(args.feature_dir, doc, args.task_id, state=state)
        max_attempts = 1 + int(doc.get('max_rework_attempts', 2))
        attempts = int(entry.get('attempts', 0))
        status = 'escalated' if args.escalate else 'failed'
        feature = str(doc.get('feature', args.feature_dir.name))
        target = worktree_path(feature, args.task_id)
        failed_commit = None
        if target.exists():
            failed_commit = checkpoint_worktree(doc, task, target, label='failed-attempt')
        entry.update({
            'status': status,
            'last_failure': args.reason,
            'failed_at': utc_now().isoformat(),
            'last_attempt_commit': failed_commit,
        })
        if args.evidence:
            entry['last_failure_evidence'] = str(pathlib.Path(args.evidence))
        if entry.pop('active_human_resume', None) is not None:
            entry['last_human_resume_used_at'] = utc_now().isoformat()
        if entry.pop('active_retry_authorization', None) is not None:
            entry['last_human_resume_used_at'] = utc_now().isoformat()
        entry.pop('start_origin_status', None)
        for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
            entry.pop(key, None)
    print(f'{status.upper()} {args.task_id} attempt={attempts}/{max_attempts}')

def cmd_release(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if not entry:
            die(f'unknown task {args.task_id}')
        owner_guard(entry, args.owner)
        if entry.get('status') != 'running':
            die(f'{args.task_id} is not running')
        task = active_task_contract(args.feature_dir, doc, args.task_id, state=state)
        feature = str(doc.get('feature', args.feature_dir.name))
        target = worktree_path(feature, args.task_id)
        partial_commit = None
        if target.exists():
            partial_commit = checkpoint_worktree(doc, task, target, label='released-attempt')
        entry.update({
            'status': 'failed',
            'last_failure': f'lease released: {args.reason}',
            'released_at': utc_now().isoformat(),
            'last_attempt_commit': partial_commit,
        })
        if entry.pop('active_human_resume', None) is not None:
            entry['last_human_resume_used_at'] = utc_now().isoformat()
        if entry.pop('active_retry_authorization', None) is not None:
            entry['last_human_resume_used_at'] = utc_now().isoformat()
        entry.pop('start_origin_status', None)
        for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
            entry.pop(key, None)
    print(f'RELEASED {args.task_id}')

def descendants(idx: dict[str, dict[str, Any]], target: str) -> set[str]:
    result: set[str] = set()
    changed = True
    while changed:
        changed = False
        for tid, task in idx.items():
            if tid == target or tid in result:
                continue
            deps = set(task.get('depends_on', []))
            if target in deps or deps.intersection(result):
                result.add(tid)
                changed = True
    return result


def prune_task_workspace(feature: str, task_id: str) -> None:
    target = worktree_path(feature, task_id)
    branch = f'agent/{feature}/{task_id}'
    if target.exists():
        dirty = changed_paths(target)
        if dirty:
            die(f'cannot invalidate dirty descendant worktree {target}: {dirty}')
        subprocess.run(['git', 'worktree', 'remove', str(target)], check=True)
    if subprocess.run(['git', 'show-ref', '--verify', '--quiet', f'refs/heads/{branch}']).returncode == 0:
        subprocess.run(['git', 'branch', '-D', branch], check=True)


def human_resolution_identity(explicit: str | None) -> str:
    if explicit and explicit.strip():
        return explicit.strip()
    for key in ('user.email', 'user.name'):
        proc = subprocess.run(['git', 'config', '--get', key], capture_output=True, text=True, check=False)
        value = proc.stdout.strip()
        if value:
            return value
    return 'human'


def audit_reference(value: Any, feature_dir: pathlib.Path) -> str | None:
    if value is None:
        return None
    raw = str(value).strip()
    if not raw:
        return None
    path = pathlib.Path(raw)
    if not path.is_absolute():
        return raw
    root = feature_repo_base(feature_dir)
    try:
        return str(path.resolve().relative_to(root.resolve()))
    except ValueError:
        return f'external:{path.name}'


def write_human_resolution(
    feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, *, decision: str, decided_by: str,
    prior_entry: dict[str, Any], source: pathlib.Path | None = None,
) -> pathlib.Path:
    stamp = utc_now().strftime('%Y%m%dT%H%M%S.%fZ')
    directory = feature_dir / 'evidence' / 'human-resolutions' / task_id
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / f'{stamp}.json'
    payload: dict[str, Any] = {
        'schema_version': 1,
        'feature': str(doc.get('feature', feature_dir.name)),
        'task': task_id,
        'decision': decision,
        'decided_by': decided_by,
        'resolved_at': utc_now().isoformat(),
        'action': 'retry',
        'prior_status': str(prior_entry.get('status')),
        'prior_reason': prior_entry.get('last_failure'),
        'prior_evidence': audit_reference(prior_entry.get('last_failure_evidence'), feature_dir),
        'attempts_before_resolution': int(prior_entry.get('attempts', 0)),
    }
    if source is not None:
        payload['source'] = audit_reference(source, feature_dir)
    fd, tmp_name = tempfile.mkstemp(prefix=path.name + '.', dir=directory)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            json.dump(payload, handle, indent=2, sort_keys=True)
            handle.write('\n')
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_name, path)
    finally:
        if os.path.exists(tmp_name):
            os.unlink(tmp_name)
    return path


def cmd_human_resolve(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    if args.task_id not in idx:
        die(f'unknown task {args.task_id}')
    decision = (args.decision or '').strip()
    source: pathlib.Path | None = None
    if args.decision_file:
        source = pathlib.Path(args.decision_file).resolve()
        if not source.exists() or not source.is_file():
            die(f'human decision file does not exist: {source}')
        decision = source.read_text(encoding='utf-8').strip()
    if not decision:
        die('human resolution requires non-empty --decision or --decision-file')
    decided_by = human_resolution_identity(args.by)

    preliminary = load_state(args.feature_dir, doc)
    prior = preliminary['tasks'].get(args.task_id)
    if not prior or prior.get('status') != 'escalated':
        die(f'{args.task_id} must be escalated before a human can resolve it')

    artifact = write_human_resolution(
        args.feature_dir, doc, args.task_id, decision=decision, decided_by=decided_by,
        prior_entry=dict(prior), source=source,
    )
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'][args.task_id]
        if entry.get('status') != 'escalated':
            artifact.unlink(missing_ok=True)
            die(f'{args.task_id} changed state while applying human resolution; retry deliberately')
        history = entry.setdefault('human_resolution_history', [])
        if not isinstance(history, list):
            history = []
            entry['human_resolution_history'] = history
        resolved_at = utc_now().isoformat()
        history.append({
            'artifact': str(artifact),
            'decided_by': decided_by,
            'resolved_at': resolved_at,
            'prior_reason': entry.get('last_failure'),
            'attempts': int(entry.get('attempts', 0)),
        })
        entry.update({
            'status': 'failed',
            'human_resolution': str(artifact),
            'human_resolved_at': resolved_at,
            'human_resolved_by': decided_by,
            'human_resume_grants': int(entry.get('human_resume_grants', 0)) + 1,
            'rework_evidence': str(artifact),
        })
        for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
            entry.pop(key, None)
    print(f'HUMAN_RESOLVED {args.task_id} -> retry authorized; artifact={artifact}')


def cmd_reopen(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    if args.task_id not in idx:
        die(f'unknown task {args.task_id}')
    feature = str(doc.get('feature', args.feature_dir.name))
    stale: list[str] = []

    # Reopen and descendant invalidation are one state transition. Validate every lease/status under
    # the state lock before deleting any task-local workspace, otherwise a racing worker can lose a
    # live worktree even though the reopen itself is rejected.
    with locked_state(args.feature_dir, doc) as state:
        active_idx = {tid: active_task_contract(args.feature_dir, doc, tid, state=state)
                      for tid in idx}
        stale = sorted(descendants(active_idx, args.task_id))
        target = state['tasks'][args.task_id]
        if target.get('status') != 'completed':
            die(f'{args.task_id} must be completed before it can be reopened')

        running_descendants = [tid for tid in stale if state['tasks'][tid].get('status') == 'running']
        if running_descendants:
            die(
                f'cannot reopen {args.task_id} while descendants are running: '
                f'{running_descendants}; release or recover them first'
            )

        max_attempts = 1 + int(doc.get('max_rework_attempts', 2))
        if int(target.get('attempts', 0)) >= max_attempts:
            target['status'] = 'escalated'
            target['last_failure'] = args.reason
            print(f'ESCALATED {args.task_id}: rework budget exhausted')
            return

        for tid in stale:
            prune_task_workspace(feature, tid)

        reopened_at = utc_now().isoformat()
        target.update({'status': 'failed', 'last_failure': args.reason, 'reopened_at': reopened_at})
        if args.evidence:
            target['rework_evidence'] = str(pathlib.Path(args.evidence))
        for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
            target.pop(key, None)

        for tid in stale:
            entry = state['tasks'][tid]
            history = entry.get('attempt_history')
            if not isinstance(history, list):
                history = []
            history.append({
                'invalidated_at': reopened_at,
                'invalidated_by': args.task_id,
                'prior_status': entry.get('status'),
                'attempts': int(entry.get('attempts', 0)),
                'last_attempt_commit': entry.get('last_attempt_commit'),
                'checkpoint_commit': entry.get('checkpoint_commit'),
                'completion_evidence': entry.get('completion_evidence'),
                'last_failure': entry.get('last_failure'),
            })
            entry.clear()
            entry.update({
                'status': 'pending',
                'attempts': 0,
                'invalidated_by': args.task_id,
                'invalidated_at': reopened_at,
                'attempt_history': history,
            })
    print(f'REOPENED {args.task_id}; invalidated descendants: {", ".join(stale) if stale else "none"}')


def cmd_status(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    with locked_state(args.feature_dir, doc) as state:
        state = json.loads(json.dumps(state))
    if args.json:
        print(json.dumps(state, indent=2, sort_keys=True))
        return
    now = utc_now()
    for tid, entry in state['tasks'].items():
        task = active_task_contract(args.feature_dir, doc, tid, state=state)
        owner = entry.get('owner', '-')
        lease = '-'
        if entry.get('status') == 'running':
            expires = parse_timestamp(entry.get('lease_expires_at'))
            if expires is None:
                lease = 'stale'
            else:
                remaining = max(0, int((expires - now).total_seconds()))
                lease = f'{remaining}s'
        print(
            f'{tid:9} {entry["status"]:10} {task["role"]:11} '
            f'attempts={entry.get("attempts", 0)} owner={owner} lease={lease}  {task["title"]}'
        )


def cmd_reset(args: argparse.Namespace) -> None:
    doc = load_json(args.feature_dir / 'tasks.json') if (args.feature_dir / 'tasks.json').exists() else {'feature': args.feature_dir.name, 'tasks': []}
    if args.full or args.prune_worktrees:
        feature = str(doc.get('feature', args.feature_dir.name))
        for task in doc.get('tasks', []):
            if isinstance(task, dict) and isinstance(task.get('id'), str):
                prune_task_workspace(feature, task['id'])
    if args.full or args.remove_packets:
        shutil.rmtree(args.feature_dir / 'packets', ignore_errors=True)
    remove_state_locked(args.feature_dir)
    suffix = ' + worktrees/packets' if args.full else ''
    print(f'RESET runtime state{suffix} for {args.feature_dir}')


def worktree_path(feature: str, task_id: str) -> pathlib.Path:
    root = repo_root()
    return root.parent / f'{root.name}{WORKTREE_ROOT_SUFFIX}' / feature / task_id


def bootstrap_path_allowed(path: str, feature_rel: pathlib.Path) -> bool:
    normalized = path.replace('\\', '/')
    feature_prefix = str(feature_rel).replace('\\', '/').rstrip('/') + '/'
    wayfinder_prefix = f'docs/wayfinder/{feature_rel.name}/'
    return (
        normalized in {'AGENTS.md', 'CLAUDE.md', '.gitignore', '.github/workflows/agentic-sdd.yml'}
        or normalized.startswith('.claude/agents/')
        or normalized.startswith('tooling/agent-harness/')
        or normalized.startswith('docs/agentic-sdd/')
        or normalized.startswith('docs/specs/SDD-001/')
        or normalized.startswith(wayfinder_prefix)
        or normalized.startswith(feature_prefix)
    )


def execution_base(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any]) -> str:
    existing = state.get('base_commit')
    if isinstance(existing, str) and existing:
        check = subprocess.run(['git', 'cat-file', '-e', f'{existing}^{{commit}}'], capture_output=True)
        if check.returncode != 0:
            die(f'local orchestration base commit no longer exists: {existing}; reset the feature state')
        return existing

    root = repo_root()
    try:
        feature_rel = feature_dir.resolve().relative_to(root)
    except ValueError:
        die('feature_dir must be inside the repository')

    head = subprocess.run(
        ['git', 'rev-parse', 'HEAD'], cwd=root, capture_output=True, text=True, check=True
    ).stdout.strip()
    dirty = changed_paths(root)
    unrelated = [path for path in dirty if not bootstrap_path_allowed(path, feature_rel)]
    if unrelated:
        die(
            'primary checkout has uncommitted changes outside the SDD protocol/active feature; '
            f'create a deliberate local checkpoint or stash them before parallel execution: {unrelated}'
        )
    if not dirty:
        state['base_commit'] = head
        state['base_kind'] = 'head'
        return head

    # Build an immutable commit object from HEAD plus only the SDD protocol and active feature.
    # commit-tree does not move HEAD or any user branch. The object becomes reachable only when
    # task-local branches are created from it.
    fd, index_name = tempfile.mkstemp(prefix='agent-sdd-index-')
    os.close(fd)
    os.unlink(index_name)
    env = os.environ.copy()
    env['GIT_INDEX_FILE'] = index_name
    candidates = [
        '.gitignore', 'AGENTS.md', 'CLAUDE.md', '.claude/agents', 'agent-harness',
        'docs/agentic-sdd', '.github/workflows/agentic-sdd.yml', str(feature_rel),
    ]
    candidates = [candidate for candidate in candidates if (root / candidate).exists()]
    try:
        subprocess.run(['git', 'read-tree', head], cwd=root, env=env, check=True, capture_output=True)
        if candidates:
            subprocess.run(['git', 'add', '-A', '--', *candidates], cwd=root, env=env, check=True, capture_output=True)
        tree = subprocess.run(
            ['git', 'write-tree'], cwd=root, env=env, capture_output=True, text=True, check=True
        ).stdout.strip()
        commit = subprocess.run(
            [
                'git', '-c', 'user.name=Agent Harness', '-c', 'user.email=agent-harness@local.invalid',
                'commit-tree', tree, '-p', head, '-m', f'agent orchestration base {doc.get("feature", feature_dir.name)}',
            ],
            cwd=root, env=env, capture_output=True, text=True, check=True,
        ).stdout.strip()
    finally:
        if os.path.exists(index_name):
            os.unlink(index_name)

    state['base_commit'] = commit
    state['base_kind'] = 'synthetic-local'
    return commit


def assert_worktree_protocol_current(feature_dir: pathlib.Path, target: pathlib.Path) -> None:
    root = repo_root()
    try:
        rel_feature = feature_dir.resolve().relative_to(root)
    except ValueError:
        die('feature_dir must be inside the repository')
    target_feature = target / rel_feature
    if not target_feature.exists():
        die(f'existing task worktree is missing feature specification: {target_feature}')
    if feature_fingerprint(target_feature) != feature_fingerprint(feature_dir):
        die(f'existing task worktree contains stale spec/plan/tasks: {target}')
    # Worktree code/documentation fingerprints change during ordinary harness maintenance.
    # State compatibility is governed by the explicit semantic protocol version.
    if protocol_version(target_feature) != protocol_version(feature_dir):
        die(f'existing task worktree contains incompatible lifecycle protocol: {target}')


def prepare_task_worktree(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any], task: dict[str, Any]) -> pathlib.Path:
    feature = str(doc.get('feature', feature_dir.name))
    target = worktree_path(feature, task['id'])
    if target.exists():
        dirty = changed_paths(target)
        if dirty:
            die(f'existing task worktree is dirty; fail/release/checkpoint it before reuse: {dirty}')
        assert_worktree_protocol_current(feature_dir, target)
        return target

    target.parent.mkdir(parents=True, exist_ok=True)
    branch = f'agent/{feature}/{task["id"]}'
    if subprocess.run(['git', 'show-ref', '--verify', '--quiet', f'refs/heads/{branch}']).returncode == 0:
        die(f'local task branch already exists without a worktree: {branch}; delete it deliberately before recreation')

    dep_commits: list[str] = []
    for dep in task.get('depends_on', []):
        commit = state['tasks'][dep].get('checkpoint_commit')
        if not commit:
            die(
                f'dependency {dep} has no local checkpoint commit; '
                'complete it from its task worktree before creating this dependent worktree'
            )
        if commit not in dep_commits:
            dep_commits.append(commit)

    base = dep_commits[0] if dep_commits else execution_base(feature_dir, doc, state)
    subprocess.run(['git', 'worktree', 'add', '--quiet', str(target), '-b', branch, base], check=True)
    for commit in dep_commits[1:]:
        subprocess.run(
            [
                'git', '-c', 'user.name=Agent Harness',
                '-c', 'user.email=agent-harness@local.invalid',
                'merge', '--quiet', '--no-edit', '--no-gpg-sign', commit,
            ],
            cwd=target,
            check=True,
        )
    if changed_paths(target):
        die(f'new worktree is unexpectedly dirty: {target}')

    # Parallel worktrees are reproducible only from a versioned protocol/spec baseline.
    root = repo_root()
    try:
        rel_feature = feature_dir.resolve().relative_to(root)
    except ValueError:
        die('feature_dir must be inside the repository')
    required = [
        target / rel_feature / 'spec.md',
        target / rel_feature / 'plan.md',
        target / rel_feature / 'tasks.json',
        target / 'AGENTS.md',
        target / 'docs' / 'agentic-sdd' / 'constitution.md',
        target / 'tooling' / 'agent-harness' / 'harness.py',
    ]
    missing = [str(p.relative_to(target)) for p in required if not p.exists()]
    if missing:
        subprocess.run(['git', 'worktree', 'remove', '--force', str(target)], check=False)
        subprocess.run(['git', 'branch', '-D', branch], check=False)
        die(
            'worktree base is missing SDD/spec files: '
            f'{missing}. Reset/re-plan the feature; the harness can synthesize a local base without moving your branch.'
        )
    return target


def cmd_worktree_create(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    with locked_state(args.feature_dir, doc) as state:
        if args.task_id not in ready_ids(doc, state, args.feature_dir):
            die(f'{args.task_id} is not ready')
        task = active_task_contract(args.feature_dir, doc, args.task_id, state=state)
        target = prepare_task_worktree(args.feature_dir, doc, state, task)
    print(target)


def cmd_start(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    with locked_state(args.feature_dir, doc) as state:
        if args.task_id not in ready_ids(doc, state, args.feature_dir):
            die(f'{args.task_id} is not ready')
        original = state['tasks'][args.task_id]
        task = active_task_contract(args.feature_dir, doc, args.task_id, state=state)
        # Resolve and validate the packet before consuming retry authorization or changing
        # lifecycle state. The immutable publication may safely remain orphaned on failure.
        packet = write_packet(doc, task, args.feature_dir, state=state)
        staged_state = copy.deepcopy(state)
        entry = staged_state['tasks'][args.task_id]
        if is_unrecovered_partial_claim(entry):
            die(f'CLAIM_RECOVERY_REQUIRED: {args.task_id} has an unrecovered exceptional claim')
        consume_attempt_authorization(entry, args.task_id, doc, args.feature_dir, staged_state)
        target = prepare_task_worktree(args.feature_dir, doc, staged_state, task)
        now = utc_now()
        entry.update({
            'status': 'running',
            'owner': args.owner,
            'attempts': int(entry.get('attempts', 0)) + 1,
            'claimed_at': now.isoformat(),
            'worktree': str(target),
            'start_origin_status': original.get('status'),
        })
        append_attempt_binding(entry, args.feature_dir, doc, args.task_id, packet)
        refresh_lease(entry, doc, now=now)
        state['tasks'][args.task_id] = entry
    print(json.dumps({'task': args.task_id, 'owner': args.owner, 'worktree': str(target), 'packet': str(packet)}, indent=2))

def rollback_unexecuted_start(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, owner: str, reason: str) -> None:
    """Undo a start that failed before a provider process was launched.

    This does not consume rework budget and only succeeds for a clean task worktree.
    """
    feature = str(doc.get('feature', feature_dir.name))
    target = worktree_path(feature, task_id)
    if target.exists() and changed_paths(target):
        die(f'cannot roll back unexecuted start for dirty worktree {target}')
    with locked_state(feature_dir, doc) as state:
        entry = state['tasks'][task_id]
        owner_guard(entry, owner)
        if entry.get('status') != 'running':
            die(f'{task_id} is not running')
        prior_status = entry.pop('start_origin_status', None)
        entry['status'] = prior_status if prior_status in {'pending', 'failed'} else 'pending'
        entry['attempts'] = max(0, int(entry.get('attempts', 0)) - 1)
        restore_attempt_authorization(entry)
        entry['start_rollback_reason'] = reason
        for key in ('owner', 'claimed_at', 'heartbeat_at', 'lease_expires_at', 'worktree'):
            entry.pop(key, None)


def cmd_worktree_remove(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    feature = str(doc.get('feature', args.feature_dir.name))
    target = worktree_path(feature, args.task_id)
    branch = f'agent/{feature}/{args.task_id}'
    if not target.exists():
        die(f'worktree path does not exist: {target}')
    subprocess.run(['git', 'worktree', 'remove', str(target)], check=True)
    if subprocess.run(['git', 'show-ref', '--verify', '--quiet', f'refs/heads/{branch}']).returncode == 0:
        subprocess.run(['git', 'branch', '-D', branch], check=True)
    print(f'REMOVED {target} and local branch {branch}')



def cmd_heartbeat(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    expires = heartbeat(args.feature_dir, doc, args.task_id, args.owner)
    print(f'HEARTBEAT {args.task_id} lease_expires_at={expires}')


def cmd_recover_stale(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    recovered = recover_stale_leases(args.feature_dir, doc, reason=args.reason)
    if recovered:
        print('RECOVERED ' + ','.join(recovered))
    else:
        print('NO_STALE_LEASES')



def cmd_doctor(_: argparse.Namespace) -> None:
    root = repo_root()
    checks = {
        'git': shutil.which('git'),
        'python3': shutil.which('python3'),
        'python>=3.10': sys.version_info >= (3, 10),
        'gradle-wrapper': (root / 'gradlew') if (root / 'gradlew').exists() else None,
        'codex(optional)': shutil.which('codex'),
        'claude(optional)': shutil.which('claude'),
        'verification-sandbox-module': (root / 'tooling' / 'agent-harness' / 'verification_sandbox.py') if (root / 'tooling' / 'agent-harness' / 'verification_sandbox.py').exists() else None,
        'telemetry-module': (root / 'tooling' / 'agent-harness' / 'telemetry.py') if (root / 'tooling' / 'agent-harness' / 'telemetry.py').exists() else None,
        'control-plane-module': (root / 'tooling' / 'agent-harness' / 'control_plane.py') if (root / 'tooling' / 'agent-harness' / 'control_plane.py').exists() else None,
        'design-module': (root / 'tooling' / 'agent-harness' / 'design.py') if (root / 'tooling' / 'agent-harness' / 'design.py').exists() else None,
        'design-result-schema': (root / 'tooling' / 'agent-harness' / 'schemas' / 'design-result.schema.json') if (root / 'tooling' / 'agent-harness' / 'schemas' / 'design-result.schema.json').exists() else None,
        'wayfinder-module': (root / 'tooling' / 'agent-harness' / 'wayfinder.py') if (root / 'tooling' / 'agent-harness' / 'wayfinder.py').exists() else None,
        'wayfinder-result-schema': (root / 'tooling' / 'agent-harness' / 'schemas' / 'wayfinder-result.schema.json') if (root / 'tooling' / 'agent-harness' / 'schemas' / 'wayfinder-result.schema.json').exists() else None,
        'wayfinder-handoff-schema': (root / 'tooling' / 'agent-harness' / 'schemas' / 'wayfinder-handoff.schema.json') if (root / 'tooling' / 'agent-harness' / 'schemas' / 'wayfinder-handoff.schema.json').exists() else None,
        'wayfinder-tasks-schema': (root / 'tooling' / 'agent-harness' / 'schemas' / 'wayfinder-tasks-result.schema.json') if (root / 'tooling' / 'agent-harness' / 'schemas' / 'wayfinder-tasks-result.schema.json').exists() else None,
        'verification-contract-module': (root / 'tooling' / 'agent-harness' / 'verification_contract.py') if (root / 'tooling' / 'agent-harness' / 'verification_contract.py').exists() else None,
        'verification-contract-schema': (root / 'tooling' / 'agent-harness' / 'schemas' / 'verification-contract.schema.json') if (root / 'tooling' / 'agent-harness' / 'schemas' / 'verification-contract.schema.json').exists() else None,
        'trust-module': (root / 'tooling' / 'agent-harness' / 'trust.py') if (root / 'tooling' / 'agent-harness' / 'trust.py').exists() else None,
        'eval-module': (root / 'tooling' / 'agent-harness' / 'eval.py') if (root / 'tooling' / 'agent-harness' / 'eval.py').exists() else None,
    }
    system = sys.platform
    sandbox_backend = (
        shutil.which('codex')
        or (shutil.which('bwrap') if system.startswith('linux') else None)
        or (shutil.which('sandbox-exec') if system == 'darwin' else None)
    )
    checks['strong-verification-sandbox(optional)'] = sandbox_backend
    failed_required = False
    for profile in sorted({p for profiles in RISK_TO_REVIEWERS.values() for p in profiles} | {'grill-reviewer', 'prototype-agent', 'prototype-evaluator', 'wayfinder-agent', 'wayfinder-synthesizer', 'verification-author'}):
        path = root / 'docs' / 'agentic-sdd' / 'agents' / f'{profile}.md'
        checks[f'profile:{profile}'] = path if path.exists() else None

    for name, value in checks.items():
        ok = bool(value)
        if (name in {'git', 'python3', 'python>=3.10', 'gradle-wrapper', 'verification-sandbox-module', 'telemetry-module', 'control-plane-module', 'design-module', 'design-result-schema', 'wayfinder-module', 'wayfinder-result-schema', 'wayfinder-handoff-schema', 'wayfinder-tasks-schema', 'verification-contract-module', 'verification-contract-schema', 'trust-module', 'eval-module'} or name.startswith('profile:')) and not ok:
            failed_required = True
        display = str(value) if isinstance(value, (str, pathlib.Path)) else None
        print(f'{"PASS" if ok else "MISS"}: {name}' + (f' -> {display}' if display else ''))
    if failed_required:
        raise SystemExit(1)


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description='Repo-native agentic SDD task orchestrator')
    sub = p.add_subparsers(required=True)

    s = sub.add_parser('validate')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.set_defaults(func=cmd_validate)

    s = sub.add_parser('validate-all')
    s.add_argument('specs_root', nargs='?', type=pathlib.Path, default=pathlib.Path('docs/specs'))
    s.set_defaults(func=cmd_validate_all)

    for name in ('ready', 'status'):
        s = sub.add_parser(name)
        s.add_argument('feature_dir', type=pathlib.Path)
        s.add_argument('--json', action='store_true')
        s.set_defaults(func=globals()[f'cmd_{name}'])

    s = sub.add_parser('packet')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--stdout', action='store_true')
    s.add_argument('--identity', action='store_true', help='Print active packet revision and semantic contract fingerprint')
    s.set_defaults(func=cmd_packet)

    s = sub.add_parser('reviewers')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.set_defaults(func=cmd_reviewers)

    s = sub.add_parser('start')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--owner', required=True)
    s.set_defaults(func=cmd_start)

    s = sub.add_parser('claim')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--owner', required=True)
    s.set_defaults(func=cmd_claim)

    s = sub.add_parser('recover-claim')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--attempt', type=int, required=True)
    s.add_argument('--authorization', required=True)
    s.add_argument('--owner', required=True)
    s.add_argument('--reason', required=True)
    s.add_argument('--by', required=True, help='Operator provenance label; not authenticated by the harness')
    s.add_argument('--attest-no-execution-started', action='store_true', required=True,
                   help='Explicit factual human attestation; the harness cannot infer this condition')
    s.set_defaults(func=cmd_recover_claim)

    s = sub.add_parser('complete')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--owner', required=True)
    s.add_argument('--evidence', required=True)
    s.set_defaults(func=cmd_complete)

    s = sub.add_parser('fail')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--owner', required=True)
    s.add_argument('--reason', required=True)
    s.add_argument('--evidence', help='Optional structured runner evidence for the failed attempt')
    s.add_argument('--escalate', action='store_true', help='Stop immediately for a human decision instead of retrying')
    s.set_defaults(func=cmd_fail)

    s = sub.add_parser('release')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--owner', required=True)
    s.add_argument('--reason', required=True)
    s.set_defaults(func=cmd_release)

    s = sub.add_parser('human-resolve')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    resolution = s.add_mutually_exclusive_group(required=True)
    resolution.add_argument('--decision', help='Accepted human decision that resolves the escalation within the current spec/plan')
    resolution.add_argument('--decision-file', help='Path to a text/markdown file containing the accepted human decision')
    s.add_argument('--by', help='Human identity recorded in the audit artifact; defaults to git user.email/user.name')
    s.set_defaults(func=cmd_human_resolve)

    s = sub.add_parser('authorize-retry')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--reason', required=True, help='Explicit human decision authorizing one exceptional attempt')
    s.add_argument('--by', help='Operator provenance label; not authenticated by the harness')
    s.add_argument('--supersedes', help='Exact unusable legacy authorization ID to supersede explicitly')
    s.set_defaults(func=cmd_authorize_retry)

    s = sub.add_parser('replan-task')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--expected-status', required=True)
    s.add_argument('--expected-attempts', type=int, required=True)
    s.add_argument('--expected-active-revision', required=True)
    s.add_argument('--expected-contract-sha256', required=True)
    s.add_argument('--proposed-task-file', required=True, help='Validated task-model JSON stored inside the feature folder')
    s.add_argument('--reason', required=True)
    s.add_argument('--by', required=True, help='Human/operator provenance label')
    s.add_argument('--checkpoint', help='Optional implementation checkpoint provenance')
    s.set_defaults(func=cmd_replan_task)

    s = sub.add_parser('reopen')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--reason', required=True)
    s.add_argument('--evidence', help='Evaluator evidence that explains why rework is required')
    s.set_defaults(func=cmd_reopen)

    s = sub.add_parser('heartbeat')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--owner', required=True)
    s.set_defaults(func=cmd_heartbeat)

    s = sub.add_parser('recover-stale')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('--reason', default='lease expired; recovered for orchestration resume')
    s.set_defaults(func=cmd_recover_stale)

    s = sub.add_parser('reset')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('--prune-worktrees', action='store_true', help='Remove clean task worktrees and local agent branches')
    s.add_argument('--remove-packets', action='store_true', help='Remove generated immutable packets after deliberate re-planning')
    s.add_argument('--full', action='store_true', help='Reset state, clean task worktrees/branches, and remove generated packets')
    s.set_defaults(func=cmd_reset)

    s = sub.add_parser('migrate-state')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.set_defaults(func=cmd_migrate_state)

    for name in ('worktree-create', 'worktree-remove'):
        s = sub.add_parser(name)
        s.add_argument('feature_dir', type=pathlib.Path)
        s.add_argument('task_id')
        s.set_defaults(func=globals()[f'cmd_{name.replace("-", "_")}'])

    s = sub.add_parser('doctor')
    s.set_defaults(func=cmd_doctor)
    return p


if __name__ == '__main__':
    ns = parser().parse_args()
    ns.func(ns)
