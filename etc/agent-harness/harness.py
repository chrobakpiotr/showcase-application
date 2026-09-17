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
        root / 'etc' / 'agent-harness' / 'harness.py',
        root / 'etc' / 'agent-harness' / 'runner.py',
        root / 'etc' / 'agent-harness' / 'orchestrate.py',
        root / 'etc' / 'agent-harness' / 'verification_sandbox.py',
        root / 'etc' / 'agent-harness' / 'telemetry.py',
        root / 'etc' / 'agent-harness' / 'control_plane.py',
        root / 'etc' / 'agent-harness' / 'design.py',
        root / 'etc' / 'agent-harness' / 'wayfinder.py',
        root / 'etc' / 'agent-harness' / 'verification_contract.py',
        root / 'etc' / 'agent-harness' / 'trust.py',
        root / 'etc' / 'agent-harness' / 'eval.py',
        root / 'etc' / 'agent-harness' / 'schemas' / 'wayfinder-result.schema.json',
        root / 'etc' / 'agent-harness' / 'schemas' / 'wayfinder-handoff.schema.json',
        root / 'etc' / 'agent-harness' / 'schemas' / 'wayfinder-tasks-result.schema.json',
        root / 'etc' / 'agent-harness' / 'schemas' / 'task-result.schema.json',
        root / 'etc' / 'agent-harness' / 'schemas' / 'design-result.schema.json',
        root / 'etc' / 'agent-harness' / 'schemas' / 'verification-contract.schema.json',
        root / '.github' / 'workflows' / 'agentic-sdd.yml',
    ]
    for directory in (root / 'docs' / 'agentic-sdd', root / '.claude' / 'agents'):
        if directory.exists():
            candidates.extend(sorted(p for p in directory.rglob('*.md') if p.is_file()))
    eval_dir = root / 'etc' / 'agent-harness' / 'evals'
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
        errors.append('design preflight gate is required before orchestration; run etc/agent-harness/design.py')
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
    return sha256_bytes(str(feature_dir.resolve()).encode())[:20]


def runtime_state_dir() -> pathlib.Path:
    return STATE_DIR if STATE_DIR.is_absolute() else repo_root() / STATE_DIR


def state_path(feature_dir: pathlib.Path) -> pathlib.Path:
    return runtime_state_dir() / f'{feature_dir.name}-{state_key(feature_dir)}.json'


def lock_path(feature_dir: pathlib.Path) -> pathlib.Path:
    return runtime_state_dir() / f'{feature_dir.name}-{state_key(feature_dir)}.lock'


def initial_state(feature_dir: pathlib.Path, doc: dict[str, Any]) -> dict[str, Any]:
    return {
        'state_version': 2,
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
    path = state_path(feature_dir)
    if not path.exists():
        return initial_state(feature_dir, doc)
    state = load_json(path)
    expected = feature_fingerprint(feature_dir)
    if state.get('fingerprint') != expected:
        die(
            'feature spec/plan/tasks changed after runtime state was created; '
            f'run `harness.py reset {feature_dir}` deliberately before continuing'
        )
    expected_protocol = protocol_fingerprint(feature_dir)
    if state.get('protocol_fingerprint') != expected_protocol:
        die(
            'agent protocol changed after runtime state was created; '
            f'run `harness.py reset {feature_dir}` deliberately before continuing'
        )
    expected_ids = set(task_index(doc))
    actual_ids = set(state.get('tasks', {}))
    if expected_ids != actual_ids:
        die('runtime state task set differs from tasks.json; reset the feature state')
    return state


def save_state(feature_dir: pathlib.Path, state: dict[str, Any]) -> None:
    state_dir = runtime_state_dir()
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
    finally:
        if os.path.exists(tmp_name):
            os.unlink(tmp_name)


@contextlib.contextmanager
def locked_state(feature_dir: pathlib.Path, doc: dict[str, Any]) -> Iterator[dict[str, Any]]:
    runtime_state_dir().mkdir(parents=True, exist_ok=True)
    with lock_path(feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        state = load_state(feature_dir, doc)
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
        refresh_lease(entry, doc)
        return str(entry['lease_expires_at'])


def recover_stale_leases(feature_dir: pathlib.Path, doc: dict[str, Any], *, reason: str = 'lease expired') -> list[str]:
    """Recover expired task leases so a crashed orchestrator can be resumed safely.

    Dirty task worktrees are checkpointed only on that task-local branch as failed-attempt evidence.
    The checkpoint is never exposed to dependents because the task remains failed until re-executed
    and completed successfully.
    """
    recovered: list[str] = []
    idx = task_index(doc)
    now = utc_now()
    with locked_state(feature_dir, doc) as state:
        for task_id, entry in state['tasks'].items():
            if not lease_expired(entry, now=now):
                continue
            task = idx.get(task_id)
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
            for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
                entry.pop(key, None)
            recovered.append(task_id)
    return recovered



def ready_ids(doc: dict[str, Any], state: dict[str, Any]) -> list[str]:
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
        entry = state['tasks'][tid]
        if entry['status'] not in {'pending', 'failed'}:
            continue
        if all(state['tasks'][dep]['status'] == 'completed' for dep in task.get('depends_on', [])):
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
            'messaging_contract': 'etc/asyncapi/asyncapi.yml' if 'messaging' in task.get('risk_tags', []) else None,
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


def write_packet(doc: dict[str, Any], task: dict[str, Any], feature_dir: pathlib.Path) -> pathlib.Path:
    payload = packet_payload(doc, task, feature_dir)
    out_dir = feature_dir / 'packets'
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / f'{task["id"]}.json'
    encoded = json.dumps(payload, indent=2, sort_keys=True) + '\n'
    if out.exists() and out.read_text(encoding='utf-8') != encoded:
        die(f'immutable task packet already exists with different content: {out}; remove it deliberately after re-planning')
    out.write_text(encoded, encoding='utf-8')
    return out


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
    feature_dirs = sorted(p for p in root.iterdir() if p.is_dir() and (p / 'tasks.json').exists())
    if not feature_dirs:
        die(f'no feature specifications found under {root}')
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
    state = load_state(args.feature_dir, doc)
    ids = ready_ids(doc, state)
    if args.json:
        print(json.dumps(ids))
    else:
        print('\n'.join(ids) if ids else 'NO_READY_TASKS')


def cmd_packet(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    if args.task_id not in idx:
        die(f'unknown task {args.task_id}')
    if args.stdout:
        print(json.dumps(packet_payload(doc, idx[args.task_id], args.feature_dir), indent=2, sort_keys=True))
    else:
        print(write_packet(doc, idx[args.task_id], args.feature_dir))


def cmd_reviewers(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    if args.task_id not in idx:
        die(f'unknown task {args.task_id}')
    selected = reviewers(idx[args.task_id])
    print('\n'.join(selected) if selected else 'NO_SPECIALIST_REVIEWERS')


def cmd_claim(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    with locked_state(args.feature_dir, doc) as state:
        if args.task_id not in ready_ids(doc, state):
            die(f'{args.task_id} is not ready')
        entry = state['tasks'][args.task_id]
        consume_attempt_authorization(entry, args.task_id, doc)
        now = utc_now()
        entry.update({
            'status': 'running',
            'owner': args.owner,
            'attempts': int(entry.get('attempts', 0)) + 1,
            'claimed_at': now.isoformat(),
        })
        refresh_lease(entry, doc, now=now)
        write_packet(doc, task_index(doc)[args.task_id], args.feature_dir)
    print(f'CLAIMED {args.task_id} by {args.owner}')


def owner_guard(entry: dict[str, Any], owner: str) -> None:
    if entry.get('owner') and entry.get('owner') != owner:
        die(f'task is owned by {entry.get("owner")}, not {owner}')


def consume_attempt_authorization(entry: dict[str, Any], task_id: str, doc: dict[str, Any]) -> str | None:
    """Consume normal retry budget or one explicit human resume grant."""
    max_attempts = 1 + int(doc.get('max_rework_attempts', 2))
    attempts = int(entry.get('attempts', 0))
    grants = int(entry.get('human_resume_grants', 0))
    if attempts >= max_attempts and grants <= 0:
        die(f'{task_id} exhausted its {max_attempts} allowed attempts')
    if grants > 0:
        entry['human_resume_grants'] = grants - 1
        resolution = str(entry.get('human_resolution') or '') or None
        entry['active_human_resume'] = resolution or 'human-resolution'
        return resolution
    return None


def restore_attempt_authorization(entry: dict[str, Any]) -> None:
    """Return a consumed human grant when a start is rolled back before provider execution."""
    if entry.pop('active_human_resume', None) is not None:
        entry['human_resume_grants'] = int(entry.get('human_resume_grants', 0)) + 1


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
        max_attempts = 1 + int(doc.get('max_rework_attempts', 2))
        attempts = int(entry.get('attempts', 0))
        status = 'escalated' if args.escalate or attempts >= max_attempts else 'failed'
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
    stale = sorted(descendants(idx, args.task_id))

    # Reopen and descendant invalidation are one state transition. Validate every lease/status under
    # the state lock before deleting any task-local workspace, otherwise a racing worker can lose a
    # live worktree even though the reopen itself is rejected.
    with locked_state(args.feature_dir, doc) as state:
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
    state = load_state(args.feature_dir, doc)
    idx = task_index(doc)
    if args.json:
        print(json.dumps(state, indent=2, sort_keys=True))
        return
    now = utc_now()
    for tid, entry in state['tasks'].items():
        task = idx[tid]
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
    path = state_path(args.feature_dir)
    lock = lock_path(args.feature_dir)
    if path.exists():
        path.unlink()
    if lock.exists():
        lock.unlink()
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
        or normalized.startswith('etc/agent-harness/')
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
    if protocol_fingerprint(target_feature) != protocol_fingerprint(feature_dir):
        die(f'existing task worktree contains stale agent protocol: {target}')


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
        target / 'etc' / 'agent-harness' / 'harness.py',
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
        if args.task_id not in ready_ids(doc, state):
            die(f'{args.task_id} is not ready')
        target = prepare_task_worktree(args.feature_dir, doc, state, task)
    print(target)


def cmd_start(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    with locked_state(args.feature_dir, doc) as state:
        if args.task_id not in ready_ids(doc, state):
            die(f'{args.task_id} is not ready')
        entry = state['tasks'][args.task_id]
        consume_attempt_authorization(entry, args.task_id, doc)
        target = prepare_task_worktree(args.feature_dir, doc, state, task)
        packet = write_packet(doc, task, args.feature_dir)
        now = utc_now()
        entry.update({
            'status': 'running',
            'owner': args.owner,
            'attempts': int(entry.get('attempts', 0)) + 1,
            'claimed_at': now.isoformat(),
            'worktree': str(target),
        })
        refresh_lease(entry, doc, now=now)
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
        entry['status'] = 'pending'
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
        'verification-sandbox-module': (root / 'etc' / 'agent-harness' / 'verification_sandbox.py') if (root / 'etc' / 'agent-harness' / 'verification_sandbox.py').exists() else None,
        'telemetry-module': (root / 'etc' / 'agent-harness' / 'telemetry.py') if (root / 'etc' / 'agent-harness' / 'telemetry.py').exists() else None,
        'control-plane-module': (root / 'etc' / 'agent-harness' / 'control_plane.py') if (root / 'etc' / 'agent-harness' / 'control_plane.py').exists() else None,
        'design-module': (root / 'etc' / 'agent-harness' / 'design.py') if (root / 'etc' / 'agent-harness' / 'design.py').exists() else None,
        'design-result-schema': (root / 'etc' / 'agent-harness' / 'schemas' / 'design-result.schema.json') if (root / 'etc' / 'agent-harness' / 'schemas' / 'design-result.schema.json').exists() else None,
        'wayfinder-module': (root / 'etc' / 'agent-harness' / 'wayfinder.py') if (root / 'etc' / 'agent-harness' / 'wayfinder.py').exists() else None,
        'wayfinder-result-schema': (root / 'etc' / 'agent-harness' / 'schemas' / 'wayfinder-result.schema.json') if (root / 'etc' / 'agent-harness' / 'schemas' / 'wayfinder-result.schema.json').exists() else None,
        'wayfinder-handoff-schema': (root / 'etc' / 'agent-harness' / 'schemas' / 'wayfinder-handoff.schema.json') if (root / 'etc' / 'agent-harness' / 'schemas' / 'wayfinder-handoff.schema.json').exists() else None,
        'wayfinder-tasks-schema': (root / 'etc' / 'agent-harness' / 'schemas' / 'wayfinder-tasks-result.schema.json') if (root / 'etc' / 'agent-harness' / 'schemas' / 'wayfinder-tasks-result.schema.json').exists() else None,
        'verification-contract-module': (root / 'etc' / 'agent-harness' / 'verification_contract.py') if (root / 'etc' / 'agent-harness' / 'verification_contract.py').exists() else None,
        'verification-contract-schema': (root / 'etc' / 'agent-harness' / 'schemas' / 'verification-contract.schema.json') if (root / 'etc' / 'agent-harness' / 'schemas' / 'verification-contract.schema.json').exists() else None,
        'trust-module': (root / 'etc' / 'agent-harness' / 'trust.py') if (root / 'etc' / 'agent-harness' / 'trust.py').exists() else None,
        'eval-module': (root / 'etc' / 'agent-harness' / 'eval.py') if (root / 'etc' / 'agent-harness' / 'eval.py').exists() else None,
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
