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
from collections import namedtuple
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
COMPLETION_EVIDENCE_SCHEMA_VERSION = 1
COMPLETION_AUTHORITY_SCHEMA_VERSION = 1
LEGACY_COMPLETION_BINDING_VERSION = 1
COMPLETION_CORRECTION_REASON = 'COMPLETION_SEMANTICALLY_INVALID'
_completion_fault_injector = None

# Packet identity values occupy distinct namespaces. Recovery v1 persisted the
# embedded checksum, while M1 revisions hash the complete normalized packet
# (including that checksum). Never infer either scheme from an unversioned hash.
PACKET_PAYLOAD_IDENTITY_SCHEME = 'canonical-packet-payload-sha256-v1'
PACKET_REVISION_IDENTITY_SCHEME = 'canonical-packet-revision-sha256-v1'
RECOVERY_PACKET_IDENTITY_SCHEMES = {1: PACKET_PAYLOAD_IDENTITY_SCHEME}
RECOVERY_RECORD_IDENTITY_SCHEME = 'canonical-recovery-record-sha256-v1'
BRIDGE_SCHEMA_VERSION = 1
BRIDGE_CLASSIFICATIONS = {'AUTOMATICALLY_PROVEN', 'HUMAN_ATTESTED', 'AMBIGUOUS'}
VERIFICATION_BLOCKAGE_OUTCOMES = {
    'busy', 'stale-input', 'environment-blocked', 'needs-human',
    'verification-blocked', 'verification-owned', 'abandoned',
}


PacketIdentity = namedtuple('PacketIdentity', ('scheme', 'value'))


def canonical_packet_payload_sha256(packet: dict[str, Any]) -> str:
    """Hash the normalized packet payload, excluding its embedded checksum."""
    body = {key: value for key, value in packet.items() if key != 'packet_sha256'}
    return sha256_bytes(json.dumps(body, sort_keys=True, separators=(',', ':')).encode())


def packet_identity_from_embedded_checksum(packet: Any) -> PacketIdentity | None:
    """Validate and name the legacy packet's canonical payload checksum."""
    if not isinstance(packet, dict):
        return None
    stored = packet.get('packet_sha256')
    if (not isinstance(stored, str) or re.fullmatch(r'[0-9a-f]{64}', stored) is None or
            stored != canonical_packet_payload_sha256(packet)):
        return None
    return PacketIdentity(PACKET_PAYLOAD_IDENTITY_SCHEME, stored)


def packet_identity_from_revision(packet: Any) -> PacketIdentity | None:
    """Name the M1 content revision only when it derives from a valid packet."""
    if packet_identity_from_embedded_checksum(packet) is None:
        return None
    return PacketIdentity(PACKET_REVISION_IDENTITY_SCHEME, packet_revision_id(packet))


def packet_identity_from_recovery(recovery: Any) -> PacketIdentity | None:
    """Infer a bare recovery identity's scheme only from its trusted record version."""
    if not isinstance(recovery, dict):
        return None
    version = recovery.get('recovery_version')
    scheme = (RECOVERY_PACKET_IDENTITY_SCHEMES.get(version)
              if isinstance(version, int) and not isinstance(version, bool) else None)
    value = recovery.get('packet_identity')
    if scheme is None or not isinstance(value, str) or re.fullmatch(r'[0-9a-f]{64}', value) is None:
        return None
    return PacketIdentity(scheme, value)


def recovery_proves_packet_binding(recovery: Any, packet: Any,
                                   revision_id: str, contract_sha256: str) -> bool:
    """Prove a recovery checksum and M1 revision identify the same packet object."""
    recovered = packet_identity_from_recovery(recovery)
    embedded = packet_identity_from_embedded_checksum(packet)
    revision = packet_identity_from_revision(packet)
    expected_revision = PacketIdentity(PACKET_REVISION_IDENTITY_SCHEME, revision_id)
    return bool(
        recovered is not None and embedded is not None and revision is not None and
        recovered == embedded and revision == expected_revision and
        isinstance(recovery, dict) and recovery.get('semantic_contract_sha256') == contract_sha256
    )


def canonical_json_sha256(value: Any) -> str:
    return sha256_bytes(json.dumps(value, sort_keys=True, separators=(',', ':')).encode())


def recovery_record_identity(recovery: Any) -> str | None:
    if not isinstance(recovery, dict):
        return None
    return f'sha256:{canonical_json_sha256(recovery)}'


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
        root / 'tooling' / 'agent-harness' / 'requirements.txt',
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


def master_implementation_closure_errors(feature_dir: pathlib.Path, spec: pathlib.Path,
                                         plan: pathlib.Path) -> list[str]:
    """Validate an explicit human implementation-authority closure.

    This does not rewrite, waive, or reinterpret design/gate.json. It is a
    narrowly scoped route for continuing implementation when a human has
    explicitly frozen a non-passing design history and authorized the
    implementation contract separately.
    """
    path = feature_dir / 'design' / 'master-closure.json'
    if not path.exists():
        return ['fresh canonical design gate is required; no master implementation closure exists']
    try:
        record = json.loads(path.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError):
        return ['master implementation closure is unreadable or invalid JSON']
    expected = {
        'spec_sha256': sha256_bytes(spec.read_bytes()),
        'plan_sha256': sha256_bytes(plan.read_bytes()),
        'design_config_sha256': sha256_bytes((feature_dir / 'design.json').read_bytes()),
    }
    required = {
        'schema_version': 1,
        'feature': feature_dir.name,
        'authority': 'master-closure',
        'status': 'implementation-authorized',
        'canonical_design_gate': 'not-pass',
        'authority_runs': 16,
    }
    errors = [f'master implementation closure {key} must equal {value!r}'
              for key, value in required.items() if record.get(key) != value]
    inputs = record.get('inputs')
    if not isinstance(inputs, dict):
        errors.append('master implementation closure inputs must be an object')
    else:
        for key, value in expected.items():
            if inputs.get(key) != value:
                errors.append(f'master implementation closure is stale: inputs.{key} does not match')
    if record.get('verification_contract_authorized') is not True:
        errors.append('master implementation closure must authorize post-implementation contract refresh')
    if record.get('commit_authorized') is not True:
        errors.append('master implementation closure must authorize coherent implementation commits')
    return errors


def validate(feature_dir: pathlib.Path) -> list[str]:
    errors: list[str] = []
    spec, plan, tasks_path = feature_files(feature_dir)
    for path in (spec, plan, tasks_path):
        if not path.exists():
            errors.append(f'missing required file: {path}')
    if errors:
        return errors

    gate_errors = design_gate_errors(feature_dir, spec, plan)
    if gate_errors:
        closure_errors = master_implementation_closure_errors(feature_dir, spec, plan)
        if closure_errors:
            errors.extend(gate_errors)
            errors.extend(closure_errors)
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
    state = {
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
    # Rebuild completion projection from immutable C1/C2 authority when the
    # mutable lifecycle snapshot was lost. Preserve the historical attempt.
    for task_id, entry in state['tasks'].items():
        c2 = repaired_completion_records(feature_dir, task_id)
        if c2:
            latest = c2[-1]
            entry.update({'status': 'completed', 'attempts': latest['attempt'],
                          'checkpoint_commit': latest['checkpoint'],
                          'completion_repair_record_id': latest['record_id']})
            continue
        c1 = completion_records(feature_dir, task_id)
        if c1:
            latest = c1[-1]
            entry.update({'status': 'completed', 'attempts': latest['attempt'],
                          'checkpoint_commit': latest['checkpoint'],
                          'completion_record_id': latest['record_id']})
    return state


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


def _read_state_unlocked_pure(feature_dir: pathlib.Path, doc: dict[str, Any]) -> dict[str, Any]:
    """Validate and read authority without adopting, migrating, cleaning, or writing it."""
    state, _legacy_path = _resolve_state_unlocked(feature_dir, doc)
    validate_loaded_state(feature_dir, doc, state)
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


def historical_feature_fingerprint(feature_dir: pathlib.Path, commit: str) -> str:
    """Recompute the feature fingerprint from an immutable Git checkpoint."""
    digest = hashlib.sha256()
    for relative in ('spec.md', 'plan.md', 'tasks.json', 'design.json',
                     'design/gate.json', 'verification-contract.json',
                     'wayfinder-handoff.json'):
        result = subprocess.run(['git', 'show', f'{commit}:docs/specs/{feature_dir.name}/{relative}'],
                                cwd=feature_repo_base(feature_dir), capture_output=True, check=False)
        if result.returncode:
            continue
        digest.update(relative.encode()); digest.update(b'\0')
        digest.update(result.stdout); digest.update(b'\0')
    return digest.hexdigest()


def cmd_reconcile_feature(args: argparse.Namespace) -> None:
    """Explicitly supersede a feature fingerprint under .agent-state CAS."""
    feature_dir = args.feature_dir.resolve()
    doc = load_validated(feature_dir)
    reason, operator = str(args.reason or '').strip(), str(args.by or '').strip()
    if not reason or not operator:
        die('FEATURE_REPLAN_REJECTED: explicit reason and operator attribution are required')
    runtime_state_dir(feature_dir).mkdir(parents=True, exist_ok=True)
    with lock_path(feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None: fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        path = state_path(feature_dir)
        if not path.is_file() or path.is_symlink():
            die('FEATURE_REPLAN_REJECTED: existing lifecycle state is required')
        state = load_json(path)
        old_fingerprint = state.get('fingerprint')
        new_fingerprint = feature_fingerprint(feature_dir)
        generation = state.get('feature_generation', 1)
        if type(generation) is not int or generation != args.expected_generation:
            die('FEATURE_REPLAN_CAS_CONFLICT: expected generation differs')
        if old_fingerprint == new_fingerprint:
            die('FEATURE_REPLAN_REJECTED: current feature fingerprint is unchanged')
        if state.get('state_version') != 2 or state.get('protocol_version') != protocol_version(feature_dir):
            die('FEATURE_REPLAN_REJECTED: unsupported lifecycle state version')
        base = state.get('base_commit')
        if not isinstance(base, str) or not re.fullmatch(r'[0-9a-f]{40}', base):
            die('FEATURE_REPLAN_REJECTED: historical base checkpoint is unavailable')
        if historical_feature_fingerprint(feature_dir, base) != old_fingerprint:
            die('FEATURE_REPLAN_REJECTED: old fingerprint cannot be proven from its immutable checkpoint')
        tasks = state.get('tasks')
        if not isinstance(tasks, dict) or set(tasks) != set(task_index(doc)):
            die('FEATURE_REPLAN_REJECTED: task identity set changed; explicit feature replacement is required')
        record = {'schema_version': 1, 'kind': 'feature-fingerprint-replan',
            'from_generation': generation, 'to_generation': generation + 1,
            'old_fingerprint': old_fingerprint, 'new_fingerprint': new_fingerprint,
            'historical_checkpoint': base, 'reason': reason, 'operator_attribution': operator,
            'invalidated_verification_authority': True,
            'preserved_task_history': True, 'recorded_at': dt.datetime.now(dt.timezone.utc).isoformat()}
        history = state.setdefault('feature_replans', [])
        if not isinstance(history, list): die('FEATURE_REPLAN_REJECTED: malformed feature replan history')
        state['feature_replans'] = [*history, record]
        prior_authority = state.get('verification_authority')
        if isinstance(prior_authority, dict) and prior_authority.get('accepted_plan_id') is not None:
            plan_history = state.setdefault('verification_plan_history', [])
            if not isinstance(plan_history, list):
                die('FEATURE_REPLAN_REJECTED: malformed verification plan history')
            plan_history.append({'prior_authority': prior_authority, 'superseded_by': None,
                'superseded_at': record['recorded_at'],
                'reason': 'feature fingerprint reconciled; prior verification authority invalidated',
                'from_generation': generation, 'to_generation': generation + 1})
        state['fingerprint'] = new_fingerprint
        state['feature_generation'] = generation + 1
        state['verification_authority'] = {'accepted_plan_id': None, 'generation': generation + 1}
        save_state(feature_dir, state)
        if fcntl is not None: fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
    print(f'REPLANNED feature={feature_dir.name} generation={generation + 1} fingerprint={new_fingerprint}')


def accept_verification_plan(feature_dir: pathlib.Path, plan_record: dict, *,
                             expected_generation: int) -> dict:
    """Bind a published immutable plan through the sole .agent-state CAS."""
    from verification.authority import validate_plan_record
    validate_plan_record(plan_record, repository=feature_dir.resolve().parents[2], reconstruct=True)
    feature_dir = feature_dir.resolve()
    doc = load_validated(feature_dir)
    if (not isinstance(plan_record, dict) or
            plan_record.get('feature_id') != doc.get('feature', feature_dir.name) or
            plan_record.get('feature_fingerprint') != feature_fingerprint(feature_dir) or
            type(plan_record.get('lifecycle_generation')) is not int or
            plan_record.get('lifecycle_generation') != expected_generation):
        die('VERIFICATION_PLAN_ACCEPTANCE_REJECTED: plan feature/generation binding differs')
    with lock_path(feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None: fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        state = _load_state_unlocked(feature_dir, doc)
        generation = state.get('feature_generation', 1)
        if generation != expected_generation:
            die('VERIFICATION_PLAN_ACCEPTANCE_CAS_CONFLICT: lifecycle generation changed')
        task_state = state.get('tasks', {}).get(plan_record.get('task_id'))
        if (not isinstance(task_state, dict) or task_state.get('status') != 'running' or
                task_state.get('attempts') != plan_record.get('task_attempt')):
            die('VERIFICATION_PLAN_ACCEPTANCE_REJECTED: task attempt is not current and running')
        current = state.get('verification_authority')
        if isinstance(current, dict) and current.get('accepted_plan_id') not in (None, plan_record['plan_id']):
            old_binding = current.get('binding')
            old_task = (state.get('tasks', {}).get(old_binding.get('task_id'))
                        if isinstance(old_binding, dict) else None)
            if (isinstance(old_task, dict) and old_task.get('status') == 'running' and
                    old_task.get('attempts') == old_binding.get('task_attempt')):
                die('VERIFICATION_PLAN_ACCEPTANCE_REPLAN_REQUIRED: prior accepted execution is still current')
            history = state.setdefault('verification_plan_history', [])
            if not isinstance(history, list):
                die('VERIFICATION_PLAN_ACCEPTANCE_REJECTED: malformed plan history')
            history.append({'prior_authority': current, 'superseded_by': plan_record['plan_id'],
                'superseded_at': dt.datetime.now(dt.timezone.utc).isoformat(),
                'reason': 'new trusted task-attempt plan accepted through lifecycle CAS'})
        binding = {key: plan_record.get(key) for key in (
            'schema_version', 'profile_id', 'plan_id', 'task_id', 'task_attempt', 'feature_fingerprint', 'lifecycle_generation', 'family',
            'profile_hash', 'policy_checkpoint', 'candidate_identity',
            'final_changed_surface_id', 'origin_binding')}
        state['verification_authority'] = {'accepted_plan_id': plan_record['plan_id'],
            'generation': generation, 'binding': binding,
            'accepted_at': dt.datetime.now(dt.timezone.utc).isoformat()}
        save_state(feature_dir, state)
        if fcntl is not None: fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
    return state['verification_authority']


def resolve_accepted_verification_plan(repository: pathlib.Path, plan_id: str) -> dict:
    """Resolve subordinate plan bytes and prove current .agent-state acceptance."""
    from verification.store import VerificationStore, StoreError
    store = VerificationStore(repository)
    record = store.load_plan_record(plan_id)
    from verification.authority import validate_plan_record
    validate_plan_record(record, repository=repository, reconstruct=True)
    feature_dir = pathlib.Path(repository) / 'docs' / 'specs' / record['feature_id']
    try:
        doc = load_validated(feature_dir)
        state = _read_state_unlocked_pure(feature_dir, doc)
    except SystemExit:
        raise StoreError('ACCEPTED_PLAN_UNAVAILABLE') from None
    current = state.get('verification_authority')
    generation = state.get('feature_generation', 1)
    task_state = state.get('tasks', {}).get(record.get('task_id'))
    expected = {key: record.get(key) for key in ('schema_version', 'profile_id', 'plan_id', 'task_id', 'task_attempt', 'feature_fingerprint',
        'lifecycle_generation', 'family', 'profile_hash', 'policy_checkpoint',
        'candidate_identity', 'final_changed_surface_id', 'origin_binding')}
    if (not isinstance(current, dict) or current.get('accepted_plan_id') != plan_id or
            current.get('generation') != generation or generation != record.get('lifecycle_generation') or
            record.get('feature_fingerprint') != feature_fingerprint(feature_dir) or
            not isinstance(task_state, dict) or task_state.get('status') != 'running' or
            task_state.get('attempts') != record.get('task_attempt') or
            current.get('binding') != expected):
        raise StoreError('ACCEPTED_PLAN_UNAVAILABLE')
    return record


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


def completion_authority_dir(feature_dir: pathlib.Path, kind: str, task_id: str) -> pathlib.Path:
    if not safe_task_id(task_id) or kind not in {
        'completion-records', 'completion-corrections', 'completion-repair-authorizations',
        'completion-repair-claims', 'completion-repair-records', 'legacy-completion-bindings',
        'completion-repair-checkpoint-bindings',
    }:
        die('COMPLETION_AUTHORITY_INVALID: unsafe authority path')
    return runtime_state_dir(feature_dir) / kind / feature_dir.name / task_id


def legacy_completion_binding_id(binding: dict[str, Any]) -> str:
    semantic = {key: value for key, value in binding.items()
                if key not in {'binding_id', 'created_at'}}
    return f'legacy-completion-binding-v1:sha256:{canonical_json_sha256(semantic)}'


def legacy_completion_source_id(source: dict[str, Any]) -> str:
    semantic = {key: value for key, value in source.items() if key != 'source_id'}
    return f'legacy-completion-source-v1:sha256:{canonical_json_sha256(semantic)}'


def publish_legacy_source(feature_dir: pathlib.Path, task_id: str,
                          source: dict[str, Any]) -> bool:
    source = dict(source)
    source['schema_version'] = 1
    source['source_id'] = legacy_completion_source_id(source)
    path = runtime_state_dir(feature_dir) / 'legacy-completion-sources' / feature_dir.name / task_id / \
        f"{source['source_id'].rsplit(':', 1)[-1]}.json"
    return publish_completion_authority(path, source)


def validate_legacy_binding_shape(binding: Any, feature_dir: pathlib.Path,
                                  task_id: str) -> bool:
    if (not isinstance(binding, dict) or binding.get('schema_version') != 1 or
            binding.get('binding_version') != LEGACY_COMPLETION_BINDING_VERSION or
            binding.get('binding_type') != 'historical-legacy-completion' or
            binding.get('binding_id') != legacy_completion_binding_id(binding) or
            binding.get('repository_id') != str(git_common_dir(feature_dir)) or
            binding.get('feature') != feature_dir.name or binding.get('task') != task_id or
            binding.get('mode') not in {'AUTO_VERIFIED', 'HUMAN_ATTESTED'} or
            type(binding.get('attempt')) is not int or binding['attempt'] < 1 or
            binding.get('historical_status') != 'completed' or
            not _valid_completion_commit(binding.get('checkpoint')) or
            binding.get('checkpoint') != binding.get('evidence_checkpoint') or
            not _valid_completion_revision(binding.get('packet_revision')) or
            not _valid_completion_hash(binding.get('contract_fingerprint')) or
            not _valid_completion_hash(binding.get('evidence_sha256')) or
            not _valid_completion_commit(binding.get('evidence_checkpoint')) or
            not isinstance(binding.get('source_material'), list) or not binding['source_material'] or
            not isinstance(binding.get('attempt_identity'), str) or
            re.fullmatch(r'sha256:[0-9a-f]{64}', binding['attempt_identity']) is None or
            any(not isinstance(source, dict) or not isinstance(source.get('identity'), str) or
                not source['identity'] or source.get('immutable') is not True or
                not _valid_completion_hash(source.get('sha256'))
                for source in binding['source_material']) or
            not isinstance(binding.get('created_at'), str) or not binding['created_at'].strip()):
        return False
    if binding['mode'] == 'HUMAN_ATTESTED':
        fields = binding.get('attested_fields')
        required = {'checkpoint': binding['checkpoint'], 'packet_revision': binding['packet_revision'],
                    'contract_fingerprint': binding['contract_fingerprint'],
                    'repository': binding['repository_id'], 'feature': binding['feature'],
                    'task': task_id, 'attempt': binding['attempt']}
        return (isinstance(binding.get('operator'), str) and bool(binding['operator'].strip()) and
                isinstance(binding.get('reason'), str) and bool(binding['reason'].strip()) and
                isinstance(fields, dict) and all(fields.get(key) == value for key, value in required.items()))
    kinds = {source.get('kind') for source in binding['source_material']
             if isinstance(source, dict) and isinstance(source.get('kind'), str)}
    return len(binding['source_material']) >= 2 and len(kinds) >= 2


def legacy_completion_bindings(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    directory = completion_authority_dir(feature_dir, 'legacy-completion-bindings', task_id)
    if not directory.exists():
        return []
    if directory.is_symlink() or not directory.is_dir():
        die('LEGACY_COMPLETION_INVALID: unsafe binding directory')
    bindings = []
    for path in sorted(directory.glob('*.json')):
        if path.is_symlink() or not path.is_file():
            die('LEGACY_COMPLETION_INVALID: binding authority is not a regular file')
        try:
            binding = json.loads(path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError) as exc:
            die(f'LEGACY_COMPLETION_INVALID: unreadable binding: {exc}')
        if path.name != 'binding.json' or not validate_legacy_binding_shape(binding, feature_dir, task_id):
            die('LEGACY_COMPLETION_INVALID: malformed or unsupported binding')
        bindings.append(binding)
    if len(bindings) > 1:
        die('LEGACY_COMPLETION_CONFLICT: multiple immutable bindings exist')
    return bindings


def legacy_binding_as_completion(feature_dir: pathlib.Path, task_id: str,
                                 binding: dict[str, Any]) -> dict[str, Any]:
    body = {
        'schema_version': COMPLETION_AUTHORITY_SCHEMA_VERSION, 'record_type': 'completion',
        'repository_id': binding['repository_id'], 'feature': binding['feature'],
        'task': task_id, 'attempt': binding['attempt'], 'checkpoint': binding['checkpoint'],
        'packet_revision': binding['packet_revision'],
        'contract_fingerprint': binding['contract_fingerprint'],
        'evidence_reference_sha256': binding['evidence_sha256'],
        'correction_id': None, 'repair_authorization_id': None,
        'legacy_binding_id': binding['binding_id'],
    }
    body['record_id'] = completion_record_id('completion', body)
    body['created_at'] = binding['created_at']
    if not validate_completion_authority_shape(body, 'completion'):
        die('LEGACY_COMPLETION_INVALID: reconstructed completion shape is invalid')
    return body


def completion_record_id(scheme: str, record: dict[str, Any]) -> str:
    semantic = {key: value for key, value in record.items() if key not in {'record_id', 'created_at'}}
    return f'{scheme}:sha256:{canonical_json_sha256(semantic)}'


REPAIR_CHECKPOINT_BINDING_SCHEME = 'completion-repair-checkpoint-binding-v1'


def repair_checkpoint_binding_id(record: dict[str, Any]) -> str:
    semantic = {key: value for key, value in record.items()
                if key not in {'record_id', 'created_at'}}
    return f'{REPAIR_CHECKPOINT_BINDING_SCHEME}:sha256:{canonical_json_sha256(semantic)}'


def repair_checkpoint_bindings(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    directory = completion_authority_dir(feature_dir, 'completion-repair-checkpoint-bindings', task_id)
    if not directory.exists():
        return []
    if directory.is_symlink() or not directory.is_dir():
        die('REPAIR_CHECKPOINT_BINDING_INVALID: authority directory is unsafe')
    records = []
    for path in sorted(directory.glob('*.json')):
        try:
            item = json.loads(path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError) as exc:
            die(f'REPAIR_CHECKPOINT_BINDING_INVALID: unreadable binding: {exc}')
        required_strings = ('historical_completion_id', 'correction_id', 'repair_authorization_id',
                            'repair_claim_id', 'owner', 'operator', 'reason')
        if (path.is_symlink() or not path.is_file() or not isinstance(item, dict) or
                item.get('schema_version') != 1 or item.get('record_type') != REPAIR_CHECKPOINT_BINDING_SCHEME or
                item.get('protocol_version') != protocol_version(feature_dir) or
                item.get('record_id') != repair_checkpoint_binding_id(item) or
                path.stem != item['record_id'].rsplit(':', 1)[-1] or
                item.get('repository_id') != str(git_common_dir(feature_dir)) or
                item.get('feature') != feature_dir.name or item.get('task') != task_id or
                any(not isinstance(item.get(key), str) or not item[key].strip() for key in required_strings) or
                type(item.get('attempt')) is not int or item['attempt'] < 1 or
                not _valid_completion_commit(item.get('checkpoint')) or
                not _valid_completion_revision(item.get('packet_revision')) or
                not _valid_completion_hash(item.get('contract_fingerprint')) or
                not isinstance(item.get('created_at'), str) or not item['created_at'].strip()):
            die('REPAIR_CHECKPOINT_BINDING_INVALID: malformed or unsupported binding')
        records.append(item)
    by_claim: dict[str, list[dict[str, Any]]] = {}
    for item in records:
        by_claim.setdefault(item['repair_claim_id'], []).append(item)
    if any(len(items) != 1 for items in by_claim.values()):
        die('REPAIR_CHECKPOINT_BINDING_CONFLICT: multiple bindings exist for repair claim')
    return records


def effective_repair_checkpoint(feature_dir: pathlib.Path, task_id: str, claim: dict[str, Any],
                                active: dict[str, Any], historical: dict[str, Any],
                                correction: dict[str, Any], authorization: dict[str, Any]) -> str:
    # Newer immutable repair claims may carry the checkpoint directly.
    checkpoint = claim.get('checkpoint')
    if _valid_completion_commit(checkpoint):
        return checkpoint
    candidates = [item for item in repair_checkpoint_bindings(feature_dir, task_id)
                  if item.get('repair_claim_id') == claim.get('record_id')]
    if not candidates:
        die('REPAIR_CHECKPOINT_BINDING_REQUIRED: legacy repair claim has no immutable checkpoint binding')
    binding, = candidates
    expected = {
        'repository_id': str(git_common_dir(feature_dir)), 'feature': feature_dir.name, 'task': task_id,
        'attempt': claim.get('attempt'), 'historical_completion_id': historical.get('record_id'),
        'correction_id': correction.get('record_id'), 'repair_authorization_id': authorization.get('record_id'),
        'repair_claim_id': claim.get('record_id'), 'owner': claim.get('owner'),
        'packet_revision': active.get('revision_id'), 'contract_fingerprint': active.get('contract_sha256'),
    }
    if any(binding.get(key) != value for key, value in expected.items()):
        die('REPAIR_CHECKPOINT_BINDING_INVALID: binding does not match current repair authority')
    return binding['checkpoint']


def _completion_record_semantics(record: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in record.items() if key != 'created_at'}


def _valid_completion_hash(value: Any) -> bool:
    return isinstance(value, str) and re.fullmatch(r'[0-9a-f]{64}', value) is not None


def _valid_completion_commit(value: Any) -> bool:
    return isinstance(value, str) and re.fullmatch(r'(?:[0-9a-f]{40}|[0-9a-f]{64})', value) is not None


def _valid_completion_revision(value: Any) -> bool:
    return value == 'unpublished' or (isinstance(value, str) and
                                      re.fullmatch(r'sha256:[0-9a-f]{64}', value) is not None)


def validate_completion_authority_shape(record: dict[str, Any], record_type: str) -> bool:
    if record_type == 'completion':
        return (type(record.get('attempt')) is int and record['attempt'] > 0 and
                _valid_completion_commit(record.get('checkpoint')) and
                _valid_completion_revision(record.get('packet_revision')) and
                _valid_completion_hash(record.get('contract_fingerprint')) and
                _valid_completion_hash(record.get('evidence_reference_sha256')) and
                ((record.get('correction_id') is None and record.get('repair_authorization_id') is None and
                  'supersedes_completion_id' not in record) or
                 (isinstance(record.get('correction_id'), str) and
                  isinstance(record.get('repair_authorization_id'), str) and
                  isinstance(record.get('supersedes_completion_id'), str))))
    if record_type == 'completion-correction':
        return (type(record.get('completed_attempt')) is int and record['completed_attempt'] > 0 and
                isinstance(record.get('original_completion_id'), str) and
                _valid_completion_commit(record.get('original_completion_checkpoint')) and
                _valid_completion_revision(record.get('packet_revision')) and
                _valid_completion_hash(record.get('contract_fingerprint')) and
                record.get('reason_code') == COMPLETION_CORRECTION_REASON and
                isinstance(record.get('reason'), str) and bool(record['reason'].strip()) and
                _valid_completion_hash(record.get('defect_evidence_sha256')) and
                isinstance(record.get('operator'), str) and bool(record['operator'].strip()))
    if record_type == 'completion-repair-authorization':
        return (type(record.get('attempt')) is int and record['attempt'] > 0 and
                isinstance(record.get('correction_id'), str) and
                isinstance(record.get('original_completion_id'), str) and
                _valid_completion_revision(record.get('packet_revision')) and
                _valid_completion_hash(record.get('contract_fingerprint')) and
                isinstance(record.get('reason'), str) and bool(record['reason'].strip()) and
                isinstance(record.get('operator'), str) and bool(record['operator'].strip()))
    if record_type == 'completion-repair-claim':
        return (type(record.get('attempt')) is int and record['attempt'] > 0 and
                isinstance(record.get('correction_id'), str) and
                isinstance(record.get('authorization_id'), str) and
                isinstance(record.get('owner'), str) and bool(record['owner'].strip()) and
                type(record.get('generation')) is int and record['generation'] > 0)
    if record_type == 'completion-repair':
        return (type(record.get('attempt')) is int and record['attempt'] > 0 and
                all(isinstance(record.get(key), str) and record[key] for key in (
                    'historical_completion_id', 'correction_id', 'repair_authorization_id',
                    'repair_claim_id', 'owner')) and
                _valid_completion_commit(record.get('checkpoint')) and
                _valid_completion_revision(record.get('packet_revision')) and
                _valid_completion_hash(record.get('contract_fingerprint')) and
                _valid_completion_hash(record.get('evidence_reference_sha256')) and
                record.get('protocol_version') == 1 and
                isinstance(record.get('created_at'), str) and bool(record['created_at']))
    return False


def publish_completion_authority(path: pathlib.Path, record: dict[str, Any]) -> bool:
    """Durably publish an immutable record. Return False for exact idempotent replay."""
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.parent.is_symlink() or path.parent.resolve().parent != path.parent.parent.resolve():
        die('COMPLETION_AUTHORITY_INVALID: authority path is not canonical')
    payload = json.dumps(record, indent=2, sort_keys=True) + '\n'
    if path.parent.name == 'completion-repair-records' and path.name.startswith('claim-'):
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        except FileExistsError:
            if path.is_symlink() or not path.is_file():
                die('COMPLETION_REPAIR_CONFLICT: claim finalization slot is unsafe')
            try:
                existing = json.loads(path.read_text(encoding='utf-8'))
            except (OSError, json.JSONDecodeError) as exc:
                die(f'COMPLETION_REPAIR_CONFLICT: unreadable claim finalization slot: {exc}')
            if (not isinstance(existing, dict) or
                    completion_record_id('completion-repair', existing) != record.get('record_id')):
                die('COMPLETION_REPAIR_CONFLICT: repair claim finalized by conflicting completion')
            return False
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            handle.write(payload); handle.flush(); os.fsync(handle.fileno())
        directory_fd = os.open(path.parent, os.O_RDONLY)
        try: os.fsync(directory_fd)
        finally: os.close(directory_fd)
        return True
    fd, tmp_name = tempfile.mkstemp(prefix=path.name + '.', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        try:
            os.link(tmp_name, path)
        except FileExistsError:
            if path.is_symlink() or not path.is_file():
                die('COMPLETION_AUTHORITY_CONFLICT: existing authority is not a regular file')
            try:
                existing = json.loads(path.read_text(encoding='utf-8'))
            except (OSError, json.JSONDecodeError) as exc:
                die(f'COMPLETION_AUTHORITY_CONFLICT: existing record is unreadable: {exc}')
            if (not isinstance(existing, dict) or
                    _completion_record_semantics(existing) != _completion_record_semantics(record)):
                die('COMPLETION_AUTHORITY_CONFLICT: immutable record identity has different content')
            return False
        directory_fd = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
        return True
    finally:
        pathlib.Path(tmp_name).unlink(missing_ok=True)


def read_completion_authority(feature_dir: pathlib.Path, kind: str, task_id: str,
                              *, record_type: str) -> list[dict[str, Any]]:
    directory = completion_authority_dir(feature_dir, kind, task_id)
    if not directory.exists():
        return []
    if directory.is_symlink() or not directory.is_dir():
        die('COMPLETION_AUTHORITY_INVALID: authority directory is unsafe')
    records = []
    for path in sorted(directory.glob('*.json')):
        if record_type == 'completion-repair' and path.name.startswith('claim-'):
            continue
        if path.is_symlink() or not path.is_file():
            die('COMPLETION_AUTHORITY_INVALID: authority record is not a regular file')
        try:
            record = json.loads(path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError) as exc:
            die(f'COMPLETION_AUTHORITY_INVALID: unreadable {record_type} record: {exc}')
        if (not isinstance(record, dict) or record.get('schema_version') != COMPLETION_AUTHORITY_SCHEMA_VERSION or
                record.get('record_type') != record_type or not isinstance(record.get('record_id'), str) or
                record.get('record_id') != completion_record_id(record_type, record) or
                path.stem != record['record_id'].rsplit(':', 1)[-1]):
            die(f'COMPLETION_AUTHORITY_INVALID: malformed or unsupported {record_type} record')
        if (record.get('repository_id') != str(git_common_dir(feature_dir)) or
                record.get('feature') != feature_dir.name or record.get('task') != task_id or
                not validate_completion_authority_shape(record, record_type)):
            die(f'COMPLETION_AUTHORITY_INVALID: {record_type} scope does not match canonical repository/task')
        records.append(record)
    return records


def _completion_failpoint(boundary: str) -> None:
    if callable(_completion_fault_injector):
        _completion_fault_injector(boundary)


def completion_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    records = read_completion_authority(feature_dir, 'completion-records', task_id,
                                        record_type='completion')
    bindings = legacy_completion_bindings(feature_dir, task_id)
    if bindings:
        reconstructed = legacy_binding_as_completion(feature_dir, task_id, bindings[0])
        if all(item.get('record_id') != reconstructed['record_id'] for item in records):
            records.append(reconstructed)
    return records


def correction_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return read_completion_authority(feature_dir, 'completion-corrections', task_id,
                                     record_type='completion-correction')


def repair_authorization_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return read_completion_authority(feature_dir, 'completion-repair-authorizations', task_id,
                                     record_type='completion-repair-authorization')


def repair_claim_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return read_completion_authority(feature_dir, 'completion-repair-claims', task_id,
                                     record_type='completion-repair-claim')


def repaired_completion_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    directory = completion_authority_dir(feature_dir, 'completion-repair-records', task_id)
    if not directory.exists():
        return []
    records = []
    for path in sorted(directory.glob('claim-*.json')):
        if path.is_symlink() or not path.is_file():
            die('COMPLETION_REPAIR_INVALID: claim finalization slot is not a regular file')
        try:
            item = json.loads(path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError) as exc:
            die(f'COMPLETION_REPAIR_INVALID: unreadable claim finalization slot: {exc}')
        if (not isinstance(item, dict) or item.get('record_type') != 'completion-repair' or
                item.get('record_id') != completion_record_id('completion-repair', item) or
                not validate_completion_authority_shape(item, 'completion-repair') or
                item.get('repository_id') != str(git_common_dir(feature_dir)) or
                item.get('feature') != feature_dir.name or item.get('task') != task_id or
                path.stem != 'claim-' + item.get('repair_claim_id', '').rsplit(':', 1)[-1]):
            die('COMPLETION_REPAIR_INVALID: malformed claim finalization slot')
        records.append(item)
    return records


def correction_is_repaired(feature_dir: pathlib.Path, task_id: str,
                           correction: dict[str, Any]) -> bool:
    authorizations = {record['record_id']: record for record in repair_authorization_records(feature_dir, task_id)}
    claims = {record['record_id']: record for record in repair_claim_records(feature_dir, task_id)}
    histories = {record['record_id']: record for record in completion_records(feature_dir, task_id)}
    for record in repaired_completion_records(feature_dir, task_id):
        if record.get('correction_id') != correction['record_id']:
            continue
        authorization = authorizations.get(record.get('repair_authorization_id'))
        claim = claims.get(record.get('repair_claim_id'))
        historical = histories.get(record.get('historical_completion_id'))
        if (authorization and authorization.get('correction_id') == correction['record_id'] and
                claim and historical and
                historical.get('record_id') == correction.get('original_completion_id') and
                claim.get('authorization_id') == authorization['record_id'] and
                claim.get('correction_id') == correction['record_id'] and
                claim.get('owner') == record.get('owner') and
                claim.get('attempt') == record.get('attempt') and
                authorization.get('task') == task_id and authorization.get('attempt') == correction.get('completed_attempt') and
                authorization.get('original_completion_id') == correction.get('original_completion_id') and
                authorization.get('repository_id') == correction.get('repository_id') and
                authorization.get('packet_revision') == correction.get('packet_revision') and
                authorization.get('contract_fingerprint') == correction.get('contract_fingerprint') and
                record.get('repository_id') == correction.get('repository_id') and
                record.get('task') == task_id and record.get('attempt') == correction.get('completed_attempt') and
                record.get('packet_revision') == correction.get('packet_revision') and
                record.get('contract_fingerprint') == correction.get('contract_fingerprint')):
            return True
    # Read pre-M4.2 repair completions for historical compatibility. New repairs
    # are published only as distinct completion-repair records above.
    for record in completion_records(feature_dir, task_id):
        if record.get('correction_id') != correction['record_id']:
            continue
        authorization = authorizations.get(record.get('repair_authorization_id'))
        if (authorization and authorization.get('correction_id') == correction['record_id'] and
                authorization.get('task') == task_id and authorization.get('attempt') == correction.get('completed_attempt') and
                record.get('attempt') == correction.get('completed_attempt') and
                record.get('supersedes_completion_id') == correction.get('original_completion_id') and
                record.get('packet_revision') == correction.get('packet_revision') and
                record.get('contract_fingerprint') == correction.get('contract_fingerprint')):
            return True
    return False


def task_has_effective_completion(feature_dir: pathlib.Path, task_id: str,
                                  entry: dict[str, Any]) -> bool:
    corrections = correction_records(feature_dir, task_id)
    if any(not correction_is_repaired(feature_dir, task_id, item) for item in corrections):
        return False
    repaired = [record for record in repaired_completion_records(feature_dir, task_id)
                if record.get('attempt') == entry.get('attempts')]
    if repaired:
        return True
    if entry.get('status') == 'completed':
        return True
    # A fresh reader can observe a durable completion record written just before the
    # lifecycle snapshot. Match the attempt before treating that crash window as complete.
    return any(record.get('attempt') == entry.get('attempts') and
               (entry.get('status') == 'running' or entry.get('status') == 'correction_required')
               for record in completion_records(feature_dir, task_id))


def effective_task_status(feature_dir: pathlib.Path, task_id: str,
                          entry: dict[str, Any]) -> str:
    corrections = correction_records(feature_dir, task_id)
    if any(not correction_is_repaired(feature_dir, task_id, item) for item in corrections):
        return 'correction_required'
    if task_has_effective_completion(feature_dir, task_id, entry):
        return 'completed'
    return str(entry.get('status', 'pending'))


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


def parse_aware_timestamp(value: Any) -> dt.datetime | None:
    """Parse only timestamps that carry an explicit timezone offset."""
    if not isinstance(value, str) or not value:
        return None
    try:
        parsed = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    except ValueError:
        return None
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        return None
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
    # Admission reconciliation always precedes the task-state lock.  Never infer
    # that an expired task lease releases a verification process that may still
    # own and mutate the same worktree.
    assert_repository_verification_drained(feature_dir)
    recovered: list[str] = []
    now = utc_now()
    with locked_state(feature_dir, doc) as state:
        for task_id, entry in state['tasks'].items():
            if not lease_expired(entry, now=now):
                continue
            if isinstance(entry.get('completion_repair_claim'), dict):
                entry['status'] = 'correction_required'
                entry.setdefault('completion_repair_claim_history', []).append({
                    **entry.pop('completion_repair_claim'), 'recovered_at': now.isoformat(),
                    'recovery_reason': reason,
                })
                for key in ('owner', 'heartbeat_at', 'lease_expires_at', 'claimed_at'):
                    entry.pop(key, None)
                recovered.append(task_id)
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


def assert_repository_verification_drained(feature_dir: pathlib.Path) -> None:
    """Refuse worktree lifecycle mutations while verification ownership is unresolved.

    The repository lock is acquired and released before any caller enters the
    lifecycle lock. VerificationStore's immutable journal is authoritative;
    process state and task lease expiry are not drainage evidence.
    """
    try:
        # Keep non-verification lifecycle/status commands importable on older
        # Python installations. A mutation still fails closed if the canonical
        # verification authority cannot be loaded.
        from verification.store import (StoreError as VerificationStoreError,
                                        VerificationStore, repository_lock)
        store = VerificationStore(feature_dir)
        with repository_lock(store.root):
            store.admit_repository_verification()
    except Exception as exc:
        if isinstance(exc, ImportError) or isinstance(exc, TypeError):
            die(f'VERIFICATION_OWNERSHIP_UNAVAILABLE: {type(exc).__name__}', code=5)
        if isinstance(exc, VerificationStoreError):
            message = str(exc)
            code = {'busy': 3, 'verification-owned': 6}.get(message, 5)
            die(f'VERIFICATION_OWNERSHIP_BLOCKED: {message}', code=code)
        die(f'VERIFICATION_OWNERSHIP_BLOCKED: {type(exc).__name__}', code=5)


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
        entry = state['tasks'][tid]
        if entry['status'] not in {'pending', 'failed'}:
            continue
        contract_task = active_task_contract(feature_dir, doc, tid, state=state) if feature_dir is not None else task
        if entry.get('status') == 'failed' and int(entry.get('attempts', 0)) >= 1 + int(doc.get('max_rework_attempts', 2)) and int(entry.get('human_resume_grants', 0)) <= 0:
            if feature_dir is None or classify_retry_authorizations(feature_dir, doc, state, tid, int(entry.get('attempts', 0)))[0] != 'valid':
                continue
        if all(task_has_effective_completion(feature_dir, dep, state['tasks'][dep])
               if feature_dir is not None else state['tasks'][dep]['status'] == 'completed'
               for dep in contract_task.get('depends_on', [])):
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
    if (not isinstance(stored, str) or re.fullmatch(r'[0-9a-f]{64}', stored) is None or
            stored != canonical_packet_payload_sha256(packet)):
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
        # Lifecycle snapshots are atomically replaced. Readers validate one snapshot
        # without creating/acquiring the repository-wide writer lock.
        state = _read_state_unlocked_pure(feature_dir, doc)
    entry = state['tasks'][task_id]
    validate_attempt_binding_ledger(entry)
    active_id = entry.get('active_packet_revision')
    if active_id is None:
        path = legacy_packet_path(feature_dir, task_id)
        if not path.exists():
            # Preserve supported lazy materialization before a running attempt exists.
            if (entry.get('status') == 'running' and
                    (entry.get('active_packet_revision') is not None or entry.get('attempt_bindings'))):
                die('ACTIVE_PACKET_AMBIGUOUS: required legacy packet is missing')
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
            # A feature-level explicit replan may supersede a legacy packet
            # only when its old feature fingerprint is exactly the prior
            # fingerprint recorded by the durable feature-replan CAS. Expose
            # it as history for cmd_replan_task; consumers still reject it
            # against the current semantic task contract.
            replans = state.get('feature_replans', [])
            prior = next((item for item in reversed(replans) if isinstance(item, dict) and
                          item.get('new_fingerprint') == feature_fingerprint(feature_dir)), None)
            if (prior is None or packet.get('feature_fingerprint') != prior.get('old_fingerprint') or
                    entry.get('status') not in {'running', 'failed'}):
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
                request.get('expected_status') not in {'running', 'failed'} or
                not isinstance(request.get('expected_attempts'), int) or
                isinstance(request.get('expected_attempts'), bool) or
                request['expected_attempts'] < (1 if request.get('expected_status') == 'running' else 0) or
                (request.get('expected_status') == 'failed' and
                 (type(request.get('expected_feature_generation')) is not int or
                  request['expected_feature_generation'] < 1)) or
                ('expected_feature_generation' in request and
                 (type(request.get('expected_feature_generation')) is not int or
                  request['expected_feature_generation'] < 1)) or
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
    active_contract = packet_bound_semantic_contract_sha256(feature_dir, packet)
    if (packet.get('semantic_contract_sha256') != active_contract or
            active_contract != last['contract_sha256']):
        die('ACTIVE_PACKET_AMBIGUOUS: active packet contract differs from lineage')
    for relation in lineage[:-1]:
        canonical_history = revision_path(feature_dir, task_id, relation['revision_id'])
        # Legacy roots are copied into immutable repository authority during replan.
        # Keep the local legacy fallback only for already-supported old lineages
        # that predate canonical historical materialization.
        historical_path = (canonical_history if canonical_history.exists() or not relation['legacy']
                           else legacy_packet_path(feature_dir, task_id))
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
        historical_contract = packet_bound_semantic_contract_sha256(feature_dir, historical)
        if (historical.get('semantic_contract_sha256') not in (None, historical_contract) or
                historical_contract != relation['contract_sha256']):
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
        elif status == 'human_attested':
            if (not isinstance(binding.get('packet_revision'), str) or
                    not re.fullmatch(r'sha256:[0-9a-f]{64}', binding['packet_revision']) or
                    not isinstance(binding.get('contract_sha256'), str) or
                    not re.fullmatch(r'[0-9a-f]{64}', binding['contract_sha256']) or
                    not isinstance(binding.get('identity_bridge_id'), str) or
                    not re.fullmatch(r'sha256:[0-9a-f]{64}', binding['identity_bridge_id']) or
                    binding.get('classification') != 'HUMAN_ATTESTED'):
                die('ACTIVE_PACKET_AMBIGUOUS: human-attested attempt lacks explicit bridge evidence')
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


def required_completion_criteria(task: dict[str, Any]) -> set[str]:
    acceptance = task.get('acceptance_criteria')
    if (not isinstance(acceptance, list) or not acceptance or
            any(not isinstance(value, str) or re.fullmatch(r'(?:AC|VC)-[A-Z0-9._-]+', value) is None
                for value in acceptance)):
        return set()
    return set(acceptance)


def completion_verification_artifact(feature_dir: pathlib.Path, value: Any) -> str:
    if not isinstance(value, str) or not value:
        raise ValueError('verification output path must be a non-empty string')
    candidate = pathlib.Path(value)
    if not candidate.is_absolute():
        candidate = feature_repo_base(feature_dir) / candidate
    root = feature_repo_base(feature_dir).resolve()
    try:
        resolved = candidate.resolve(strict=True)
    except (OSError, RuntimeError) as exc:
        raise ValueError(f'verification output cannot be safely resolved: {exc}') from exc
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise ValueError('verification output must remain inside the canonical repository') from exc
    if not resolved.is_file():
        raise ValueError('verification output must be a regular file')
    return sha256_bytes(resolved.read_bytes())


def validate_completion_proofs(evidence: dict[str, Any], task: dict[str, Any],
                               required: set[str], feature_dir: pathlib.Path) -> list[str]:
    errors: list[str] = []
    commands = evidence.get('commands')
    proofs = evidence.get('proofs')
    results = evidence.get('criterion_results')
    verification_results = evidence.get('harness_verification')
    if type(evidence.get('schema_version')) is not int or evidence.get('schema_version') != COMPLETION_EVIDENCE_SCHEMA_VERSION:
        errors.append('completion evidence schema_version must be supported version 1')
    if not required:
        errors.append('active packet must declare at least one well-formed AC/VC completion criterion')
    if not isinstance(commands, list) or not commands or any(not isinstance(x, str) or not x.strip() for x in commands):
        errors.append('completion evidence commands must be non-empty command strings')
        commands = []
    allowed_commands = task.get('verification', [])
    if any(command not in allowed_commands for command in commands):
        errors.append('completion evidence contains a command not declared by the active packet')
    if commands != allowed_commands:
        errors.append('completion evidence must include the active packet verification command sequence')
    if not isinstance(verification_results, list) or len(verification_results) != len(commands):
        errors.append('completion evidence must include a harness result for every verification command')
        verification_results = []
    verified_results: dict[int, dict[str, Any]] = {}
    for index, record in enumerate(verification_results):
        if (not isinstance(record, dict) or record.get('command') != (commands[index] if index < len(commands) else None) or
                type(record.get('exit_code')) is not int or record.get('exit_code') != 0):
            errors.append(f'verification command {index} has no successful harness execution record')
            continue
        try:
            stdout_sha = completion_verification_artifact(feature_dir, record.get('stdout'))
            stderr_sha = completion_verification_artifact(feature_dir, record.get('stderr'))
        except (OSError, ValueError) as exc:
            errors.append(f'verification command {index} output evidence is invalid: {exc}')
            continue
        if not isinstance(record.get('sandbox_backend'), str) or not record['sandbox_backend']:
            errors.append(f'verification command {index} is missing sandbox provenance')
            continue
        verified_results[index] = {
            'command': record['command'], 'exit_code': record['exit_code'],
            'stdout_sha256': stdout_sha, 'stderr_sha256': stderr_sha,
            'sandbox_backend': record['sandbox_backend'],
            'strong_isolation': record.get('strong_isolation') is True,
        }
    if not isinstance(proofs, list) or not proofs:
        errors.append('completion evidence proofs must contain command-linked results')
        proofs = []
    proof_index: dict[str, dict[str, Any]] = {}
    for proof in proofs:
        if not isinstance(proof, dict) or set(proof) != {
                'proof_id', 'command_index', 'command_sha256', 'status', 'exit_code', 'result_sha256', 'criteria'}:
            errors.append('completion proof record has an invalid shape')
            continue
        proof_id = proof.get('proof_id')
        index = proof.get('command_index')
        criteria = proof.get('criteria')
        if (not isinstance(proof_id, str) or not proof_id or proof_id in proof_index or
                type(index) is not int or index < 0 or index >= len(commands)):
            errors.append('completion proof identity or command index is invalid')
            continue
        if (proof.get('status') != 'PASS' or type(proof.get('exit_code')) is not int or proof['exit_code'] != 0 or
                not isinstance(proof.get('command_sha256'), str) or
                proof.get('command_sha256') != sha256_bytes(commands[index].encode()) or
                not isinstance(proof.get('result_sha256'), str) or
                re.fullmatch(r'[0-9a-f]{64}', proof['result_sha256']) is None):
            errors.append(f'completion proof {proof_id} is not a valid successful command receipt')
        receipt = verified_results.get(index)
        if (receipt is None or proof.get('result_sha256') != canonical_json_sha256(receipt)):
            errors.append(f'completion proof {proof_id} does not match durable harness command-output evidence')
        if (not isinstance(criteria, list) or not criteria or
                any(not isinstance(item, str) for item in criteria) or
                (all(isinstance(item, str) for item in criteria) and
                 (len(criteria) != len(set(criteria)) or any(item not in required for item in criteria)))):
            errors.append(f'completion proof {proof_id} has invalid criterion links')
        proof_index[proof_id] = proof
    if not isinstance(results, dict):
        errors.append('completion evidence criterion_results must be an object')
        results = {}
    if set(results) != required:
        missing = sorted(required - set(results))
        extra = sorted(set(results) - required)
        if missing:
            errors.append(f'completion evidence missing required criteria: {missing}')
        if extra:
            errors.append(f'completion evidence contains criteria outside the active packet: {extra}')
    for criterion in required & set(results):
        result = results[criterion]
        if not isinstance(result, dict) or set(result) != {'status', 'proof_ids'} or result.get('status') != 'PASS':
            errors.append(f'{criterion} is not marked PASS with linked command proof')
            continue
        proof_ids = result.get('proof_ids')
        if (not isinstance(proof_ids, list) or not proof_ids or
                any(not isinstance(proof_id, str) for proof_id in proof_ids) or
                (all(isinstance(proof_id, str) for proof_id in proof_ids) and
                 (len(proof_ids) != len(set(proof_ids)) or
                  any(proof_id not in proof_index or criterion not in proof_index[proof_id].get('criteria', [])
                      for proof_id in proof_ids)))):
            errors.append(f'{criterion} has no valid passing command proof')
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
    state = _read_state_unlocked_pure(args.feature_dir, doc)
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


def packet_bound_semantic_contract_sha256(feature_dir: pathlib.Path,
                                          packet: dict[str, Any]) -> str:
    """Reconstruct a materialized packet contract without consulting current planning.

    Packet payloads persist ``agent_profile`` as an execution profile, defaulting it
    to ``role`` when the planning task omits the field. That fallback is packet
    metadata, not a task-contract field. For legacy packets without a semantic hash,
    normalize that default away. Newer packets carry a semantic hash, so recompute
    both shape-valid interpretations and require the embedded hash to match one.
    This preserves explicitly supplied profiles while still validating the hash
    from packet-bound material.
    """
    feature = packet.get('feature')
    task_id = packet.get('task')
    feature_sha256 = packet.get('feature_fingerprint')
    test_policy = packet.get('test_policy', 'legacy')
    if (not isinstance(feature, str) or not feature or not isinstance(task_id, str) or
            not safe_task_id(task_id) or not isinstance(feature_sha256, str) or
            re.fullmatch(r'[0-9a-f]{64}', feature_sha256) is None or
            not isinstance(test_policy, str) or not test_policy):
        die('ACTIVE_PACKET_AMBIGUOUS: packet lacks immutable semantic contract inputs')
    task = {field: packet[field] for field in TASK_CONTRACT_FIELDS if field in packet}
    if 'id' not in task:
        task['id'] = task_id
    explicit = dict(task)
    if task.get('agent_profile') == packet.get('role'):
        task.pop('agent_profile')
    def fingerprint_for(contract_task: dict[str, Any]) -> str:
        semantic = semantic_task_contract(
            feature_dir, {'feature': feature}, contract_task,
            feature_sha256=feature_sha256, test_policy=test_policy)
        return sha256_bytes(json.dumps(semantic, sort_keys=True, separators=(',', ':')).encode())

    fingerprint = fingerprint_for(task)
    stored = packet.get('semantic_contract_sha256')
    if stored is not None:
        candidates = {fingerprint, fingerprint_for(explicit)}
        if not isinstance(stored, str) or stored not in candidates:
            die('ACTIVE_PACKET_AMBIGUOUS: packet semantic contract fingerprint is inconsistent')
        fingerprint = stored
    return fingerprint


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
                not isinstance(old.get('binding'), dict) or
                old.get('binding', {}).get('expected_attempts') !=
                    new.get('binding', {}).get('expected_attempts') or
                not isinstance(new.get('binding', {}).get('expected_attempts'), int) or
                isinstance(new.get('binding', {}).get('expected_attempts'), bool) or
                new.get('binding', {}).get('expected_attempts') < 1):
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
        entry['last_transition'] = {'kind': 'retry_authorization', 'attempts': attempts,
                                    'authorization_id': grant_id, 'at': grant['issued_at']}
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


def validate_latest_human_resolution(feature_dir: pathlib.Path, doc: dict[str, Any],
                                     task_id: str, entry: dict[str, Any]) -> dict[str, Any]:
    """Require the current failed state to be the exact audited human-resolution transition."""
    attempts = entry.get('attempts')
    history = entry.get('human_resolution_history')
    artifact_value = entry.get('human_resolution')
    transition = entry.get('last_transition')
    if (not isinstance(attempts, int) or isinstance(attempts, bool) or attempts < 0 or
            not isinstance(history, list) or not history or not isinstance(history[-1], dict) or
            not isinstance(artifact_value, str) or
            (transition is not None and (not isinstance(transition, dict) or
             transition.get('kind') != 'human_resolution' or
             type(transition.get('attempts')) is not int or transition.get('attempts') != attempts or
             transition.get('artifact') != artifact_value))):
        die('TASK_REPLAN_NOT_ALLOWED: failed task was not most recently changed by a human resolution')
    if transition is None:
        # Upgrade-safe proof for human resolutions written before last_transition existed.
        # Later supported mutations create a retry grant or consume the human grant.
        if (type(entry.get('human_resume_grants')) is not int or entry['human_resume_grants'] < 1 or
                entry.get('active_human_resume') is not None or
                entry.get('active_retry_authorization') is not None):
            die('TASK_REPLAN_NOT_ALLOWED: legacy human resolution is not the latest provable failed-task transition')
    root = (feature_dir / 'evidence' / 'human-resolutions' / task_id).resolve()
    artifact = pathlib.Path(artifact_value)
    if not artifact.is_absolute():
        artifact = feature_repo_base(feature_dir) / artifact
    expected_root = feature_dir / 'evidence' / 'human-resolutions' / task_id
    if any(path.is_symlink() for path in (
            feature_dir / 'evidence', feature_dir / 'evidence' / 'human-resolutions', expected_root)):
        die('TASK_REPLAN_NOT_ALLOWED: human-resolution audit authority path contains a symlink')
    try:
        resolved = artifact.resolve(strict=True)
        resolved.relative_to(root)
        if artifact.is_symlink() or not resolved.is_file():
            raise ValueError('audit artifact is not a regular file')
        raw = resolved.read_bytes()
        record = json.loads(raw)
    except (OSError, ValueError, json.JSONDecodeError):
        die('TASK_REPLAN_NOT_ALLOWED: human-resolution audit artifact is unavailable or outside its authority path')
    latest = history[-1]
    digest = sha256_bytes(raw)
    expected = {
        'schema_version': 1,
        'feature': doc.get('feature', feature_dir.name),
        'task': task_id,
        'action': 'retry',
        'attempts_before_resolution': attempts,
    }
    if (not isinstance(record, dict) or any(record.get(key) != value for key, value in expected.items()) or
            not isinstance(record.get('decision'), str) or not record['decision'].strip() or
            not isinstance(record.get('decided_by'), str) or not record['decided_by'].strip() or
            parse_timestamp(record.get('resolved_at')) is None or
            parse_timestamp(entry.get('human_resolved_at')) is None or
            record.get('decided_by') != entry.get('human_resolved_by') or
            latest.get('artifact') != artifact_value or type(latest.get('attempts')) is not int or
            latest.get('attempts') != attempts or
            latest.get('resolved_at') != entry.get('human_resolved_at') or
            latest.get('decided_by') != record.get('decided_by') or
            parse_timestamp(record['resolved_at']) > parse_timestamp(latest['resolved_at']) or
            (transition is not None and
             (transition.get('resolved_at') != latest.get('resolved_at') or
              transition.get('artifact_sha256') != digest))):
        die('TASK_REPLAN_NOT_ALLOWED: human-resolution audit binding does not match the current failed task state')
    if transition is None:
        resolved_at = parse_timestamp(latest['resolved_at'])
        for grant in entry.get('retry_authorizations', []):
            issued_at = parse_timestamp(grant.get('issued_at')) if isinstance(grant, dict) else None
            if issued_at is None or issued_at > resolved_at:
                die('TASK_REPLAN_NOT_ALLOWED: a retry authorization changed the task after human resolution')
        for relation in entry.get('retry_authorization_supersessions', []):
            issued_at = parse_timestamp(relation.get('issued_at')) if isinstance(relation, dict) else None
            if issued_at is None or issued_at > resolved_at:
                die('TASK_REPLAN_NOT_ALLOWED: authorization supersession changed the task after human resolution')
        used_at = parse_timestamp(entry.get('last_human_resume_used_at'))
        if used_at is not None and used_at > resolved_at:
            die('TASK_REPLAN_NOT_ALLOWED: human retry authorization was consumed after resolution')
    return {'artifact': artifact_value, 'artifact_sha256': digest, 'attempts': attempts,
            'resolved_at': record['resolved_at']}


def cmd_materialize_packet_history(args: argparse.Namespace) -> None:
    """Publish a validated canonical legacy root into immutable repository authority."""
    feature_dir = args.feature_dir.resolve()
    doc = load_validated(feature_dir)
    canonical_feature = git_common_dir(feature_dir).parent / 'docs' / 'specs' / feature_dir.name
    if feature_dir != canonical_feature.resolve():
        die('PACKET_HISTORY_MATERIALIZATION_REJECTED: run from the canonical repository feature path')
    state = _read_state_unlocked_pure(feature_dir, doc)
    entry = state['tasks'].get(args.task_id)
    lineage = entry.get('packet_lineage') if isinstance(entry, dict) else None
    if (not isinstance(lineage, list) or not lineage or not isinstance(lineage[0], dict) or
            not lineage[0].get('legacy')):
        die('PACKET_HISTORY_MATERIALIZATION_REJECTED: task has no legacy lineage root')
    # Prove the complete active chain from the canonical checkout before publishing
    # any history. This validates active bytes, every audit hop, and the legacy root.
    resolve_active_packet(feature_dir, doc, args.task_id, state=state)
    relation = lineage[0]
    path = legacy_packet_path(feature_dir, args.task_id)
    if path.is_symlink():
        die('PACKET_HISTORY_MATERIALIZATION_REJECTED: canonical legacy packet is a symlink')
    try:
        raw = path.read_bytes()
        packet = json.loads(raw)
    except (OSError, json.JSONDecodeError) as exc:
        die(f'PACKET_HISTORY_MATERIALIZATION_REJECTED: canonical legacy packet unavailable: {exc}')
    if (not isinstance(packet, dict) or packet.get('feature') != doc.get('feature', feature_dir.name) or
            packet.get('task') != args.task_id):
        die('PACKET_HISTORY_MATERIALIZATION_REJECTED: canonical legacy packet identity mismatch')
    validate_packet_integrity(packet)
    if (packet_revision_id(packet) != relation.get('revision_id') or
            packet_bound_semantic_contract_sha256(feature_dir, packet) != relation.get('contract_sha256')):
        die('PACKET_HISTORY_MATERIALIZATION_REJECTED: canonical legacy packet does not prove lineage root')
    revision, published = publish_revision(feature_dir, args.task_id, packet)
    if revision != relation['revision_id']:
        die('PACKET_HISTORY_MATERIALIZATION_REJECTED: published revision differs from lineage root')
    print(f'MATERIALIZED {args.task_id} revision={revision} raw_sha256={sha256_bytes(raw)} path={published}')


def packet_identity_bridge_path(feature_dir: pathlib.Path, task_id: str, attempt: int) -> pathlib.Path:
    if not safe_task_id(task_id) or not isinstance(attempt, int) or isinstance(attempt, bool) or attempt < 1:
        die('PACKET_IDENTITY_ATTESTATION_REJECTED: invalid task or attempt')
    root = runtime_state_dir(feature_dir) / 'packet-identity-bridges'
    feature_root = root / feature_dir.name
    task_root = feature_root / task_id
    path = task_root / f'{attempt}.json'
    if (root.is_symlink() or feature_root.is_symlink() or task_root.is_symlink() or
            path.is_symlink() or path.resolve().parent != task_root.resolve()):
        die('PACKET_IDENTITY_ATTESTATION_REJECTED: bridge path escapes repository authority')
    return path


def bridge_failpoint(_stage: str) -> None:
    """Test seam for immutable bridge publication failure injection."""


def publish_immutable_bridge(path: pathlib.Path, record: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(record, indent=2, sort_keys=True) + '\n'
    if path.exists():
        die('PACKET_IDENTITY_ATTESTATION_CONFLICT: an immutable bridge already occupies this binding')
    fd, temporary = tempfile.mkstemp(prefix=f'.{path.name}.', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            handle.write(encoded)
            bridge_failpoint('B3')
            handle.flush()
            bridge_failpoint('B4')
            os.fsync(handle.fileno())
        try:
            os.link(temporary, path)
        except FileExistsError:
            die('PACKET_IDENTITY_ATTESTATION_CONFLICT: competing bridge creation')
        directory_fd = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def _bridge_request_identity(*, repository: str, feature: str, task: str, attempt: int,
                             recovery_id: str, source_scheme: str, source_value: str,
                             target_scheme: str, target_value: str, target_revision: str,
                             semantic_fingerprint: str, protocol_version_value: int,
                             operator: str, reason: str) -> dict[str, Any]:
    return {'repository': repository, 'feature': feature, 'task': task, 'attempt': attempt,
            'recovery_record_identity': {'scheme': RECOVERY_RECORD_IDENTITY_SCHEME, 'value': recovery_id},
            'source_identity': {'scheme': source_scheme, 'value': source_value},
            'target_identity': {'scheme': target_scheme, 'value': target_value},
            'target_revision_identity': {'scheme': PACKET_REVISION_IDENTITY_SCHEME,
                                         'value': target_revision},
            'semantic_contract_sha256': semantic_fingerprint,
            'protocol_version': protocol_version_value,
            'classification': 'HUMAN_ATTESTED', 'operator': operator, 'reason': reason}


def _read_packet_identity_bridge(feature_dir: pathlib.Path, task_id: str, attempt: int,
                                 expected_request: dict[str, Any] | None = None) -> dict[str, Any] | None:
    path = packet_identity_bridge_path(feature_dir, task_id, attempt)
    if not path.exists():
        return None
    try:
        record = json.loads(path.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError):
        die('PACKET_IDENTITY_ATTESTATION_CONFLICT: stored bridge is unreadable')
    if (not isinstance(record, dict) or record.get('schema_version') != BRIDGE_SCHEMA_VERSION or
            record.get('classification') != 'HUMAN_ATTESTED' or
            record.get('bridge_id') != f"sha256:{canonical_json_sha256(record.get('request'))}" or
            not isinstance(record.get('request'), dict) or
            record.get('protocol_version') != record.get('request', {}).get('protocol_version') or
            parse_timestamp(record.get('created_at')) is None):
        die('PACKET_IDENTITY_ATTESTATION_CONFLICT: stored bridge is malformed')
    if expected_request is not None and record.get('request') != expected_request:
        return record
    return record


def classify_historical_packet_binding(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str,
                                        state: dict[str, Any], active: dict[str, Any],
                                        attempt: int) -> dict[str, Any]:
    entry = state['tasks'][task_id] if isinstance(state.get('tasks'), dict) else state
    validate_attempt_binding_ledger(entry)
    binding = next((item for item in entry.get('attempt_bindings', [])
                   if isinstance(item, dict) and item.get('attempt') == attempt), None)
    if (binding and binding.get('binding_status') == 'proven' and
            binding.get('packet_revision') == active['revision_id'] and
            binding.get('contract_sha256') == active['contract_sha256']):
        return {'classification': 'AUTOMATICALLY_PROVEN', 'bridge_id': None}
    if (binding and binding.get('binding_status') == 'proven' and
            (binding.get('packet_revision') != active['revision_id'] or
             binding.get('contract_sha256') != active['contract_sha256'])):
        return {'classification': 'AMBIGUOUS', 'bridge_id': None}
    recovery = entry.get('claim_recovery')
    if (isinstance(recovery, dict) and recovery.get('attempt') == attempt and
            recovery_proves_packet_binding(recovery, active.get('packet'), active['revision_id'],
                                           active['contract_sha256'])):
        return {'classification': 'AUTOMATICALLY_PROVEN', 'bridge_id': None}
    recovery_id = recovery_record_identity(recovery)
    packet_identity = packet_identity_from_embedded_checksum(active.get('packet'))
    revision_identity = packet_identity_from_revision(active.get('packet'))
    bridge = _read_packet_identity_bridge(feature_dir, task_id, attempt)
    if (bridge and isinstance(recovery, dict) and recovery.get('attempt') == attempt and
            packet_identity is not None and revision_identity is not None):
        request = bridge['request']
        expected = _bridge_request_identity(
            repository=str(git_common_dir(feature_dir)), feature=doc.get('feature', feature_dir.name),
            task=task_id, attempt=attempt, recovery_id=recovery_id or '',
            source_scheme=(packet_identity_from_recovery(recovery).scheme
                           if packet_identity_from_recovery(recovery) else ''),
            source_value=(packet_identity_from_recovery(recovery).value
                          if packet_identity_from_recovery(recovery) else ''),
            target_scheme=packet_identity.scheme, target_value=packet_identity.value,
            target_revision=revision_identity.value,
            semantic_fingerprint=active['contract_sha256'],
            protocol_version_value=protocol_version(feature_dir),
            operator=request.get('operator', ''), reason=request.get('reason', ''))
        if request == expected:
            if (binding and binding.get('binding_status') == 'human_attested' and
                    binding.get('identity_bridge_id') != bridge['bridge_id']):
                return {'classification': 'AMBIGUOUS', 'bridge_id': None}
            return {'classification': 'HUMAN_ATTESTED', 'bridge_id': bridge['bridge_id']}
    return {'classification': 'AMBIGUOUS', 'bridge_id': None}


def attest_packet_identity(args: argparse.Namespace) -> tuple[str, dict[str, Any]]:
    feature_dir = args.feature_dir
    doc = load_validated(feature_dir)
    operator = str(args.by or '').strip()
    reason = str(args.reason or '').strip()
    if not operator or not reason:
        die('PACKET_IDENTITY_ATTESTATION_REJECTED: operator and reason are required')
    if args.source_scheme != PACKET_PAYLOAD_IDENTITY_SCHEME or args.target_scheme != PACKET_PAYLOAD_IDENTITY_SCHEME:
        die('PACKET_IDENTITY_ATTESTATION_REJECTED: unsupported packet identity scheme')
    if args.target_revision_scheme != PACKET_REVISION_IDENTITY_SCHEME:
        die('PACKET_IDENTITY_ATTESTATION_REJECTED: unsupported target revision identity scheme')
    for value in (args.source_value, args.target_value, args.expected_semantic_fingerprint):
        if not isinstance(value, str) or re.fullmatch(r'[0-9a-f]{64}', value) is None:
            die('PACKET_IDENTITY_ATTESTATION_REJECTED: malformed SHA-256 identity')
    if re.fullmatch(r'sha256:[0-9a-f]{64}', args.target_revision) is None:
        die('PACKET_IDENTITY_ATTESTATION_REJECTED: malformed target revision identity')
    if args.expected_recovery_identity_scheme != RECOVERY_RECORD_IDENTITY_SCHEME or re.fullmatch(
            r'sha256:[0-9a-f]{64}', args.expected_recovery_identity) is None:
        die('PACKET_IDENTITY_ATTESTATION_REJECTED: malformed recovery record identity')
    if args.expected_protocol_version != protocol_version(feature_dir):
        die('PACKET_IDENTITY_ATTESTATION_REJECTED: protocol version changed')
    bridge_failpoint('B1')
    runtime_state_dir(feature_dir).mkdir(parents=True, exist_ok=True)
    with lock_path(feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        state = _read_state_unlocked_pure(feature_dir, doc)
        entry = state['tasks'].get(args.task_id)
        if not isinstance(entry, dict):
            die('PACKET_IDENTITY_ATTESTATION_REJECTED: unknown task')
        if (str(git_common_dir(feature_dir)) != args.expected_repository or
                doc.get('feature', feature_dir.name) != args.expected_feature or
                args.task_id != args.expected_task or entry.get('status') != args.expected_status or
                entry.get('status') != 'running' or entry.get('attempts') != args.expected_attempts or
                entry.get('owner') != args.expected_owner or args.expected_attempts < 1):
            die('PACKET_IDENTITY_ATTESTATION_REJECTED: lifecycle compare-and-swap failed')
        recovery = entry.get('claim_recovery')
        recovery_identity = recovery_record_identity(recovery)
        if (not isinstance(recovery, dict) or recovery.get('attempt') != args.expected_attempts or
                recovery_identity != args.expected_recovery_identity):
            die('PACKET_IDENTITY_ATTESTATION_REJECTED: recovery record compare-and-swap failed')
        existing_binding = next((item for item in entry.get('attempt_bindings', [])
                                 if isinstance(item, dict) and item.get('attempt') == args.expected_attempts), None)
        if (existing_binding and existing_binding.get('binding_status') == 'proven' and
                (existing_binding.get('packet_revision') != args.target_revision or
                 existing_binding.get('contract_sha256') != args.expected_semantic_fingerprint)):
            die('PACKET_IDENTITY_ATTESTATION_REJECTED: conflicting proven attempt binding exists')
        active = resolve_active_packet(feature_dir, doc, args.task_id, state=state)
        embedded = packet_identity_from_embedded_checksum(active.get('packet'))
        revision = packet_identity_from_revision(active.get('packet'))
        recovered = packet_identity_from_recovery(recovery)
        if (active['revision_id'] != args.target_revision or
                active['contract_sha256'] != args.expected_semantic_fingerprint or
                recovered is None or recovered.scheme != args.source_scheme or
                recovered.value != args.source_value or embedded is None or
                embedded.scheme != args.target_scheme or embedded.value != args.target_value or
                revision is None or revision.value != args.target_revision or
                recovery.get('semantic_contract_sha256') != args.expected_semantic_fingerprint):
            die('PACKET_IDENTITY_ATTESTATION_REJECTED: packet identity compare-and-swap failed')
        request = _bridge_request_identity(
            repository=args.expected_repository, feature=args.expected_feature, task=args.expected_task,
            attempt=args.expected_attempts, recovery_id=args.expected_recovery_identity,
            source_scheme=args.source_scheme, source_value=args.source_value,
            target_scheme=args.target_scheme, target_value=args.target_value,
            target_revision=args.target_revision,
            semantic_fingerprint=args.expected_semantic_fingerprint,
            protocol_version_value=args.expected_protocol_version, operator=operator, reason=reason)
        path = packet_identity_bridge_path(feature_dir, args.task_id, args.expected_attempts)
        prior = _read_packet_identity_bridge(feature_dir, args.task_id, args.expected_attempts)
        if prior:
            if prior.get('request') == request:
                if fcntl is not None:
                    fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
                return 'ALREADY_ATTESTED', prior
            die('PACKET_IDENTITY_ATTESTATION_CONFLICT: conflicting bridge already exists')
        bridge_failpoint('B2')
        bridge_id = f'sha256:{canonical_json_sha256(request)}'
        record = {'schema_version': BRIDGE_SCHEMA_VERSION,
                  'protocol_version': args.expected_protocol_version,
                  'classification': 'HUMAN_ATTESTED', 'bridge_id': bridge_id,
                  'request': request, 'created_at': utc_now().isoformat()}
        publish_immutable_bridge(path, record)
        bridge_failpoint('B5')
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
        return 'ATTESTED', record


def cmd_attest_packet_identity(args: argparse.Namespace) -> None:
    result, record = attest_packet_identity(args)
    print(f'{result} {args.task_id} bridge={record["bridge_id"]} classification=HUMAN_ATTESTED')


def cmd_packet_identity_binding(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    # Read-only: this command deliberately does not enter locked_state/save_state.
    state = _read_state_unlocked_pure(args.feature_dir, doc)
    active = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
    result = classify_historical_packet_binding(args.feature_dir, doc, args.task_id,
                                                state, active, args.attempt)
    print(json.dumps(result, sort_keys=True))


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
        current_generation = state.get('feature_generation', 1)
        expected_generation = getattr(args, 'expected_feature_generation', None)
        failed_replan = entry.get('status') == 'failed' and args.expected_status == 'failed'
        if expected_generation is None and not failed_replan:
            expected_generation = current_generation
        request_identity = {'repository': repo_identity, 'feature': doc.get('feature', feature_dir.name),
                            'task': args.task_id, 'expected_status': args.expected_status,
                            'expected_attempts': args.expected_attempts,
                            'expected_active_packet_revision': args.expected_active_revision,
                            'expected_contract_sha256': args.expected_contract_sha256,
                            'expected_feature_generation': expected_generation,
                            'proposed_contract_sha256': new_contract, 'proposed_task': proposed,
                            'provenance': operator, 'reason': reason,
                            'checkpoint': getattr(args, 'checkpoint', None)}
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
            active_revision = entry.get('active_packet_revision')
            last_request = prior_requests[-1]
            if (not isinstance(last_request, dict) or
                    last_request.get('new_revision') != active_revision):
                die('ACTIVE_PACKET_AMBIGUOUS: latest replan does not produce the active revision')
            if active_revision != args.expected_active_revision:
                die('STALE_ACTIVE_PACKET: a prior replan changed the active packet revision')
        if entry.get('status') == 'completed':
            die('TASK_REPLAN_NOT_ALLOWED: completed tasks cannot be replanned')
        if entry.get('status') != args.expected_status or args.expected_status not in {'running', 'failed'}:
            die('TASK_REPLAN_NOT_ALLOWED: only CAS-bound running tasks or human-resolved failed tasks may be replanned')
        failed_replan = entry.get('status') == 'failed'
        if failed_replan:
            if type(expected_generation) is not int or expected_generation < 1:
                die('TASK_REPLAN_NOT_ALLOWED: failed-task replan requires --expected-feature-generation')
            human_resolution = validate_latest_human_resolution(feature_dir, doc, args.task_id, entry)
            if prior_requests:
                previous_replan_at = parse_aware_timestamp(prior_requests[-1].get('committed_at'))
                resolved_at = parse_aware_timestamp(human_resolution.get('resolved_at'))
                if previous_replan_at is None or resolved_at is None or resolved_at <= previous_replan_at:
                    die('TASK_REPLAN_NOT_ALLOWED: failed task requires a human resolution newer than its latest replan')
        if type(current_generation) is not int or current_generation != expected_generation:
            die('REPLAN_CONCURRENT_CONFLICT: current feature generation differs from request')
        attempts = entry.get('attempts', 0)
        if (not isinstance(attempts, int) or isinstance(attempts, bool) or
                type(args.expected_attempts) is not int or attempts != args.expected_attempts):
            die('REPLAN_CONCURRENT_CONFLICT: current attempt count differs from request')
        active = resolve_active_packet(feature_dir, doc, args.task_id, state=state)
        if active['revision_id'] != args.expected_active_revision:
            die('STALE_ACTIVE_PACKET: active packet revision changed')
        if active['contract_sha256'] != args.expected_contract_sha256:
            die('STALE_CONTRACT_FINGERPRINT: active semantic contract changed')
        if new_contract == active['contract_sha256']:
            die('TASK_REPLAN_NOT_ALLOWED: proposed contract has no semantic change')
        if active['revision_id'] == 'unpublished':
            die('TASK_REPLAN_NOT_ALLOWED: task has no published historical packet')
        replan_failpoint('R1')
        packet = packet_payload(proposed_doc, proposed, feature_dir)
        if packet.get('semantic_contract_sha256') != new_contract:
            die('TASK_REPLAN_NOT_ALLOWED: packet fingerprint differs from proposed planning contract')
        # Publish validated legacy root bytes to the common repository authority
        # before publishing/activating a lineage that depends on them. Immutable
        # publication is idempotent and rejects identity collisions.
        if active.get('legacy') and active.get('packet') is not None:
            publish_revision(feature_dir, args.task_id, active['packet'])
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
        timestamp = utc_now().isoformat()
        if not failed_replan:
            attempt_history = staged.setdefault('attempt_bindings', [])
            validate_attempt_binding_ledger(staged)
            current_binding = next((record for record in attempt_history
                                    if isinstance(record, dict) and record.get('attempt') == attempts), None)
            binding_result = classify_historical_packet_binding(feature_dir, doc, args.task_id,
                                                                staged, active, attempts)
            historical_binding_classification = binding_result['classification']
            historical_binding_status = {
                'AUTOMATICALLY_PROVEN': 'proven', 'HUMAN_ATTESTED': 'human_attested',
                'AMBIGUOUS': 'ambiguous'}[historical_binding_classification]
            if current_binding is None and historical_binding_classification == 'AUTOMATICALLY_PROVEN':
                attempt_history.append({'attempt': attempts, 'packet_revision': active['revision_id'],
                    'contract_sha256': active['contract_sha256'], 'binding_status': 'proven',
                    'bound_at': utc_now().isoformat(),
                    'evidence': 'versioned recovery payload identity matches the active packet and revision'})
            elif current_binding is None and historical_binding_classification == 'HUMAN_ATTESTED':
                attempt_history.append({'attempt': attempts, 'packet_revision': active['revision_id'],
                    'contract_sha256': active['contract_sha256'], 'binding_status': 'human_attested',
                    'classification': 'HUMAN_ATTESTED', 'identity_bridge_id': binding_result['bridge_id'],
                    'bound_at': utc_now().isoformat(),
                    'evidence': 'explicit immutable human packet identity bridge'})
            elif (current_binding is not None and current_binding.get('binding_status') == 'ambiguous' and
                  historical_binding_classification == 'HUMAN_ATTESTED'):
                current_binding.update({'packet_revision': active['revision_id'],
                    'contract_sha256': active['contract_sha256'], 'binding_status': 'human_attested',
                    'classification': 'HUMAN_ATTESTED', 'identity_bridge_id': binding_result['bridge_id'],
                    'bound_at': utc_now().isoformat(),
                    'evidence': 'explicit immutable human packet identity bridge'})
            elif current_binding is None and historical_binding_classification == 'AMBIGUOUS':
                recovery = staged.get('claim_recovery')
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
            staged['attempt_termination'] = {'attempt': attempts, 'classification': 'REPLAN_SUPERSEDED',
                'reason_code': 'TASK_REPLAN_REQUIRED', 'reason': reason, 'terminated_at': timestamp,
                'active_revision_at_termination': active['revision_id'],
                'contract_sha256_at_termination': active['contract_sha256'],
                'historical_binding_status': historical_binding_status,
                'historical_binding_classification': historical_binding_classification,
                'verification': 'NOT_RUN_BY_REPLAN'}
            if historical_binding_status in {'proven', 'human_attested'}:
                staged['attempt_termination']['packet_revision'] = active['revision_id']
                staged['attempt_termination']['contract_sha256'] = active['contract_sha256']
            if historical_binding_classification == 'HUMAN_ATTESTED':
                staged['attempt_termination']['identity_bridge_id'] = binding_result['bridge_id']
            staged['status'] = 'failed'
            staged['replanned_at'] = timestamp
            staged['last_transition'] = {'kind': 'running_task_replan', 'attempts': attempts, 'at': timestamp}
        staged['replan_requests'] = [*prior_requests, {'request_id': request_id,
            'repository': repo_identity, 'feature': doc.get('feature', feature_dir.name), 'task': args.task_id,
            'old_revision': active['revision_id'], 'new_revision': revision_id,
            'old_contract_sha256': active['contract_sha256'], 'new_contract_sha256': new_contract,
            'expected_status': args.expected_status, 'expected_attempts': attempts,
            'expected_feature_generation': expected_generation, 'reason': reason,
            'provenance': operator, 'checkpoint': args.checkpoint,
            **({} if failed_replan else {
                'historical_binding_classification': historical_binding_classification,
                'identity_bridge_id': binding_result.get('bridge_id')}),
            'committed_at': timestamp}]
        if not failed_replan:
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


def completion_record_for_entry(feature_dir: pathlib.Path, task_id: str, entry: dict[str, Any],
                                active: dict[str, Any]) -> dict[str, Any]:
    bindings = legacy_completion_bindings(feature_dir, task_id)
    if bindings:
        binding = bindings[0]
        if binding.get('attempt') != entry.get('attempts'):
            die('LEGACY_COMPLETION_CONFLICT: binding attempt differs from lifecycle')
        return legacy_binding_as_completion(feature_dir, task_id, binding)
    if (entry.get('status') == 'completed' and
            not _valid_completion_commit(entry.get('checkpoint_commit'))):
        die('LEGACY_COMPLETION_ATTESTATION_REQUIRED: historical checkpoint is not bound')
    if entry.get('status') == 'completed':
        die('LEGACY_COMPLETION_ATTESTATION_REQUIRED: completed legacy state requires immutable binding')
    recorded_id = entry.get('completion_record_id')
    if recorded_id:
        existing = next((item for item in completion_records(feature_dir, task_id)
                         if item.get('record_id') == recorded_id), None)
        if existing:
            return existing
    evidence_ref = entry.get('evidence')
    body = {
        'schema_version': COMPLETION_AUTHORITY_SCHEMA_VERSION, 'record_type': 'completion',
        'repository_id': str(git_common_dir(feature_dir)), 'feature': feature_dir.name,
        'task': task_id, 'attempt': int(entry.get('attempts', 0)),
        'checkpoint': entry.get('checkpoint_commit'),
        'packet_revision': active['revision_id'], 'contract_fingerprint': active['contract_sha256'],
        'evidence_reference_sha256': sha256_bytes(str(evidence_ref or '').encode()),
        'correction_id': None, 'repair_authorization_id': None,
    }
    body['record_id'] = completion_record_id('completion', body)
    body['created_at'] = entry.get('completed_at') or utc_now().isoformat()
    return body


def legacy_attempt_identity(feature_dir: pathlib.Path, task_id: str,
                            entry: dict[str, Any]) -> str:
    binding = next((item for item in entry.get('attempt_bindings', [])
                    if item.get('attempt') == entry.get('attempts')), {})
    return 'sha256:' + canonical_json_sha256({
        'repository_id': str(git_common_dir(feature_dir)), 'feature': feature_dir.name,
        'task': task_id, 'attempt': entry.get('attempts'),
        'claimed_at': entry.get('claimed_at'), 'attempt_binding': binding,
    })


def classify_legacy_completion(feature_dir: pathlib.Path, doc: dict[str, Any],
                               task_id: str) -> tuple[str, dict[str, Any] | None, list[str]]:
    """Read-only classifier; AUTO requires two independent immutable source records."""
    state = _read_state_unlocked_pure(feature_dir, doc)
    entry = state.get('tasks', {}).get(task_id)
    if not isinstance(entry, dict) or entry.get('status') != 'completed':
        return 'CONFLICT', None, ['stored lifecycle status is not completed']
    binding_dir = completion_authority_dir(feature_dir, 'legacy-completion-bindings', task_id)
    if binding_dir.exists() and (binding_dir.is_symlink() or not binding_dir.is_dir()):
        return 'CONFLICT', None, ['legacy binding authority path is unsafe']
    if binding_dir.exists():
        for binding_path in binding_dir.glob('*.json'):
            try:
                candidate = json.loads(binding_path.read_text(encoding='utf-8'))
            except (OSError, json.JSONDecodeError):
                return 'CONFLICT', None, ['existing legacy binding is malformed']
            if (isinstance(candidate, dict) and
                    (candidate.get('schema_version') != 1 or
                     candidate.get('binding_version') != LEGACY_COMPLETION_BINDING_VERSION)):
                return 'UNSUPPORTED', None, ['existing legacy binding schema/version is unsupported']
    try:
        existing = read_completion_authority(feature_dir, 'completion-records', task_id,
                                             record_type='completion')
        existing_bindings = legacy_completion_bindings(feature_dir, task_id)
    except SystemExit:
        return 'CONFLICT', None, ['existing completion authority is malformed or contradictory']
    if existing or existing_bindings:
        return 'CONFLICT', None, ['canonical completion or legacy binding already exists']
    active = resolve_active_packet(feature_dir, doc, task_id, state=state)
    evidence_path = pathlib.Path(str(entry.get('evidence', '')))
    if not evidence_path.is_file() or evidence_path.is_symlink():
        return 'HUMAN_ATTESTATION_REQUIRED', None, ['historical evidence artifact is unavailable']
    try:
        evidence_bytes = evidence_path.read_bytes()
        evidence = json.loads(evidence_bytes)
    except (OSError, json.JSONDecodeError):
        return 'CONFLICT', None, ['historical evidence artifact is malformed']
    evidence_hash = sha256_bytes(evidence_bytes)
    expected = {
        'repository': str(git_common_dir(feature_dir)), 'feature': feature_dir.name,
        'task': task_id, 'attempt': entry.get('attempts'),
        'packet_revision': active['revision_id'],
        'contract_fingerprint': active['contract_sha256'],
    }
    if not isinstance(evidence, dict):
        return 'CONFLICT', None, ['historical evidence artifact is not an object']
    if any(key in evidence and evidence[key] != value for key, value in expected.items()):
        return 'CONFLICT', None, ['historical evidence scope conflicts with current immutable task identity']
    missing_scope = [key for key in expected if key not in evidence]
    evidence_checkpoint = evidence.get('checkpoint')
    if not _valid_completion_commit(evidence_checkpoint):
        return 'CONFLICT', None, ['evidence checkpoint is malformed']
    if _valid_completion_commit(entry.get('checkpoint_commit')) and entry['checkpoint_commit'] != evidence_checkpoint:
        return 'CONFLICT', None, ['lifecycle and evidence checkpoints conflict']

    sources_dir = runtime_state_dir(feature_dir) / 'legacy-completion-sources' / feature_dir.name / task_id
    sources = []
    if sources_dir.is_dir() and not sources_dir.is_symlink():
        for source_path in sorted(sources_dir.glob('*.json')):
            try:
                source_bytes = source_path.read_bytes()
                source = json.loads(source_bytes)
            except (OSError, json.JSONDecodeError):
                return 'CONFLICT', None, [f'malformed immutable source record: {source_path.name}']
            if (source_path.is_symlink() or not isinstance(source, dict) or source.get('schema_version') != 1 or
                    source.get('source_id') != legacy_completion_source_id(source) or
                    source_path.stem != source['source_id'].rsplit(':', 1)[-1] or
                    source.get('repository_id') != str(git_common_dir(feature_dir)) or
                    source.get('feature') != feature_dir.name or source.get('task') != task_id or
                    source.get('attempt') != entry.get('attempts') or
                    source.get('checkpoint') != evidence_checkpoint or
                    source.get('evidence_sha256') != evidence_hash or
                    source.get('packet_revision') != active['revision_id'] or
                    source.get('contract_fingerprint') != active['contract_sha256'] or
                    source.get('attempt_identity') != legacy_attempt_identity(feature_dir, task_id, entry) or
                    source.get('historical_status') != 'completed'):
                return 'CONFLICT', None, [f'contradictory immutable source record: {source_path.name}']
            sources.append({'identity': source['source_id'],
                            'sha256': sha256_bytes(source_bytes),
                            'kind': source.get('kind'), 'immutable': True})
    independent_kinds = {s['kind'] for s in sources if isinstance(s['kind'], str)}
    if len(sources) < 2 or len(independent_kinds) < 2:
        reasons = []
        if missing_scope:
            reasons.append('historical evidence omits task-binding fields requiring explicit attestation: ' + ', '.join(missing_scope))
        reasons.append('fewer than two independent immutable historical records corroborate completion checkpoint')
        return 'HUMAN_ATTESTATION_REQUIRED', None, reasons
    binding = {
        'schema_version': 1, 'binding_version': LEGACY_COMPLETION_BINDING_VERSION,
        'binding_type': 'historical-legacy-completion',
        'repository_id': str(git_common_dir(feature_dir)), 'feature': feature_dir.name,
        'task': task_id, 'attempt': entry['attempts'], 'historical_status': 'completed',
        'attempt_identity': legacy_attempt_identity(feature_dir, task_id, entry),
        'checkpoint': evidence_checkpoint, 'packet_revision': active['revision_id'],
        'contract_fingerprint': active['contract_sha256'], 'evidence_sha256': evidence_hash,
        'evidence_checkpoint': evidence_checkpoint, 'source_material': sources,
        'mode': 'AUTO_VERIFIED',
    }
    binding['binding_id'] = legacy_completion_binding_id(binding)
    return 'AUTO_VERIFIED', binding, []


def publish_legacy_binding(feature_dir: pathlib.Path, binding: dict[str, Any]) -> bool:
    if not validate_legacy_binding_shape(binding, feature_dir, str(binding.get('task', ''))):
        die('LEGACY_COMPLETION_INVALID: refusing to publish malformed binding')
    path = completion_authority_dir(feature_dir, 'legacy-completion-bindings', binding['task']) / 'binding.json'
    return publish_completion_authority(path, binding)


def cmd_classify_legacy_completion(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    classification, binding, reasons = classify_legacy_completion(args.feature_dir, doc, args.task_id)
    print(json.dumps({'classification': classification, 'reasons': reasons,
                      'proposed_binding': binding}, indent=2, sort_keys=True))


def cmd_bind_legacy_completion_auto(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    runtime_state_dir(args.feature_dir).mkdir(parents=True, exist_ok=True)
    with lock_path(args.feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        existing = legacy_completion_bindings(args.feature_dir, args.task_id)
        if existing:
            if existing[0].get('mode') != 'AUTO_VERIFIED':
                die('LEGACY_COMPLETION_AUTO_REJECTED: a different binding mode is already authoritative')
            print(f"ALREADY_BOUND {args.task_id} binding={existing[0]['binding_id']}")
            return
        classification, binding, reasons = classify_legacy_completion(args.feature_dir, doc, args.task_id)
        if classification != 'AUTO_VERIFIED' or binding is None:
            die('LEGACY_COMPLETION_AUTO_REJECTED: ' + '; '.join(reasons or [classification]))
        binding['created_at'] = utc_now().isoformat()
        created = publish_legacy_binding(args.feature_dir, binding)
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
    print(('BOUND' if created else 'ALREADY_BOUND') + f" {args.task_id} binding={binding['binding_id']}")


def cmd_attest_legacy_completion(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    if args.task_id not in task_index(doc):
        die(f'unknown task {args.task_id}')
    operator, reason = str(args.operator or '').strip(), str(args.reason or '').strip()
    if not operator or not reason:
        die('LEGACY_COMPLETION_ATTESTATION_INVALID: explicit operator and reason are required')
    if not args.attested_fields or not args.sources:
        die('LEGACY_COMPLETION_ATTESTATION_INVALID: explicit attested fields and source references are required')
    raw_evidence_path = pathlib.Path(args.evidence)
    if raw_evidence_path.is_symlink() or not raw_evidence_path.is_file():
        die('LEGACY_COMPLETION_ATTESTATION_INVALID: evidence must be a regular file')
    evidence_path = raw_evidence_path.resolve()
    evidence_bytes = evidence_path.read_bytes()
    evidence_hash = sha256_bytes(evidence_bytes)
    try:
        evidence = json.loads(evidence_bytes)
        attested_fields = json.loads(args.attested_fields)
        source_refs = json.loads(args.sources)
    except (json.JSONDecodeError, TypeError) as exc:
        die(f'LEGACY_COMPLETION_ATTESTATION_INVALID: malformed JSON input: {exc}')
    if (not isinstance(attested_fields, dict) or not isinstance(source_refs, list) or not source_refs or
            any(not isinstance(item, dict) or not isinstance(item.get('identity'), str) or
                not item['identity'] or item.get('immutable') is not True or
                not _valid_completion_hash(item.get('sha256'))
                for item in source_refs)):
        die('LEGACY_COMPLETION_ATTESTATION_INVALID: attested fields must be an object and sources a nonempty array')
    runtime_state_dir(args.feature_dir).mkdir(parents=True, exist_ok=True)
    with lock_path(args.feature_dir).open('a+', encoding='utf-8') as lock:
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        state = _read_state_unlocked_pure(args.feature_dir, doc)
        entry = state['tasks'][args.task_id]
        active = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
        repo_id = str(git_common_dir(args.feature_dir))
        expected_projection = {
            'stored_status': 'completed', 'attempts': entry.get('attempts'),
            'attempt_identity': legacy_attempt_identity(args.feature_dir, args.task_id, entry),
            'canonical_completion_absent': True,
            'legacy_checkpoint': entry.get('checkpoint_commit'),
            'packet_revision': active['revision_id'],
            'contract_fingerprint': active['contract_sha256'],
            'evidence_sha256': evidence_hash,
            'evidence_checkpoint': evidence.get('checkpoint') if isinstance(evidence, dict) else None,
            'repository_id': repo_id, 'feature': args.feature_dir.name, 'task': args.task_id,
        }
        for key, actual in expected_projection.items():
            # The structured precondition must explicitly contain every CAS value.
            if key not in args.expected or args.expected[key] != actual:
                die(f'LEGACY_COMPLETION_CAS_REJECTED: expected precondition mismatch for {key}')
        if entry.get('status') != 'completed':
            die('LEGACY_COMPLETION_CAS_REJECTED: lifecycle status no longer matches legacy state')
        if (_valid_completion_commit(entry.get('checkpoint_commit')) and isinstance(evidence, dict) and
                entry['checkpoint_commit'] != evidence.get('checkpoint')):
            die('LEGACY_COMPLETION_CAS_REJECTED: lifecycle and evidence checkpoints conflict')
        if read_completion_authority(args.feature_dir, 'completion-records', args.task_id,
                                     record_type='completion'):
            die('LEGACY_COMPLETION_CAS_REJECTED: canonical completion already exists')
        if not isinstance(evidence, dict):
            die('LEGACY_COMPLETION_CAS_REJECTED: evidence is not a JSON object')
        evidence_binding_values = {
            'checkpoint': evidence.get('checkpoint'), 'packet_revision': active['revision_id'],
            'contract_fingerprint': active['contract_sha256'], 'repository': repo_id,
            'feature': args.feature_dir.name, 'task': args.task_id, 'attempt': entry.get('attempts'),
        }
        if any(key in evidence and evidence[key] != value for key, value in evidence_binding_values.items()):
            die('LEGACY_COMPLETION_CAS_REJECTED: evidence scope conflicts with current task binding')
        if not set(evidence_binding_values).issubset(attested_fields) or any(
                attested_fields[key] != value for key, value in evidence_binding_values.items()):
            die('LEGACY_COMPLETION_ATTESTATION_INVALID: explicitly attested fields do not bind evidence, task and current packet')
        binding = {
            'schema_version': 1, 'binding_version': LEGACY_COMPLETION_BINDING_VERSION,
            'binding_type': 'historical-legacy-completion', 'repository_id': repo_id,
            'feature': args.feature_dir.name, 'task': args.task_id, 'attempt': entry['attempts'],
            'historical_status': 'completed',
            'attempt_identity': expected_projection['attempt_identity'],
            'checkpoint': evidence['checkpoint'], 'packet_revision': active['revision_id'],
            'contract_fingerprint': active['contract_sha256'], 'evidence_sha256': evidence_hash,
            'evidence_checkpoint': evidence['checkpoint'], 'source_material': source_refs,
            'mode': 'HUMAN_ATTESTED', 'operator': operator, 'reason': reason,
            'attested_fields': attested_fields,
        }
        binding['binding_id'] = legacy_completion_binding_id(binding)
        binding['created_at'] = utc_now().isoformat()
        created = publish_legacy_binding(args.feature_dir, binding)
        if fcntl is not None:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
    print(('BOUND' if created else 'ALREADY_BOUND') + f" {args.task_id} binding={binding['binding_id']}")


def add_completion_record(feature_dir: pathlib.Path, record: dict[str, Any]) -> None:
    path = completion_authority_dir(feature_dir, 'completion-records', record['task']) / \
        f"{record['record_id'].rsplit(':', 1)[-1]}.json"
    publish_completion_authority(path, record)


def validate_completion_binding(evidence: dict[str, Any], feature_dir: pathlib.Path, task_id: str,
                                entry: dict[str, Any], active: dict[str, Any]) -> list[str]:
    expected = {
        'repository': str(git_common_dir(feature_dir)), 'feature': feature_dir.name, 'task': task_id,
        'attempt': entry.get('attempts'), 'packet_revision': active['revision_id'],
        'contract_fingerprint': active['contract_sha256'],
    }
    errors = [f'completion evidence {key} does not match current authority'
              for key, value in expected.items() if evidence.get(key) != value]
    if (not isinstance(evidence.get('checkpoint'), str) or
            re.fullmatch(r'(?:[0-9a-f]{40}|[0-9a-f]{64})', evidence['checkpoint']) is None):
        errors.append('completion evidence checkpoint must be a full Git commit id')
    if not isinstance(evidence.get('changed_paths'), list) or any(not isinstance(p, str) for p in evidence['changed_paths']):
        errors.append('completion evidence changed_paths must be a string array')
    return errors


def require_available_completion_origin(state: dict[str, Any], task_id: str,
                                        entry: dict[str, Any]) -> None:
    """Keep origin-aware lineages out of the legacy command-proof pathway.

    Until trusted admission and terminal coverage exist, no legacy PASS tuple
    can authorize a v2 completion. Superseding a plan does not remove that
    requirement from the same task attempt. Historical readers are unaffected.
    Caller holds the lifecycle lock; no runtime lock or file mutation is needed.
    """
    authorities = [state.get('verification_authority')]
    history = state.get('verification_plan_history', [])
    if isinstance(history, list):
        authorities.extend(item.get('prior_authority') for item in history if isinstance(item, dict))
    for authority in authorities:
        if not isinstance(authority, dict):
            continue
        binding = authority.get('binding')
        plan_id = authority.get('accepted_plan_id')
        is_v2 = (isinstance(plan_id, str) and plan_id.startswith('verification-plan-v2:')) or \
            (isinstance(binding, dict) and binding.get('schema_version') == 2)
        if not is_v2:
            continue
        if (not isinstance(binding, dict) or
                not isinstance(binding.get('task_id'), str) or
                type(binding.get('task_attempt')) is not int or
                (binding['task_id'] == task_id and binding['task_attempt'] == entry.get('attempts'))):
            die('VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE: origin-qualified completion is not implemented')


def cmd_complete(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    evidence_path = pathlib.Path(args.evidence)
    evidence_errors = validate_evidence(evidence_path, require_pass=True)
    if evidence_errors:
        die('; '.join(evidence_errors))
    evidence_doc = load_json(evidence_path)

    assert_repository_verification_drained(args.feature_dir)

    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if not entry:
            die(f'unknown task {args.task_id}')
        require_available_completion_origin(state, args.task_id, entry)
        if entry.get('status') == 'completed':
            active = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
            binding_errors = validate_completion_binding(evidence_doc, args.feature_dir, args.task_id, entry, active)
            proof_errors = validate_completion_proofs(
                evidence_doc, active_task_contract(args.feature_dir, doc, args.task_id, state=state),
                required_completion_criteria(active_task_contract(
                    args.feature_dir, doc, args.task_id, state=state)), args.feature_dir)
            existing = next((item for item in completion_records(args.feature_dir, args.task_id)
                             if item.get('record_id') == entry.get('completion_record_id')), None)
            evidence_hash = sha256_bytes(evidence_path.read_bytes())
            if (not binding_errors and not proof_errors and existing and
                    existing.get('evidence_reference_sha256') == evidence_hash and
                    existing.get('checkpoint') == evidence_doc.get('checkpoint')):
                print(f'ALREADY_COMPLETED {args.task_id} checkpoint={existing["checkpoint"][:12]}')
                return
            die('COMPLETION_CONFLICT: task already has a different authoritative completion')
        owner_guard(entry, args.owner)
        repair_claim = entry.get('completion_repair_claim')
        is_repair = isinstance(repair_claim, dict)
        if is_repair:
            die('COMPLETION_REPAIR_REQUIRES_CANONICAL_OPERATION: use complete-repair')
        if entry.get('status') != 'running':
            die(f'{args.task_id} is not running')
        task = active_task_contract(args.feature_dir, doc, args.task_id, state=state)
        active = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
        binding_errors = validate_completion_binding(evidence_doc, args.feature_dir, args.task_id, entry, active)
        if binding_errors:
            die('; '.join(binding_errors))
        required = required_completion_criteria(task)
        proof_errors = validate_completion_proofs(evidence_doc, task, required, args.feature_dir)
        if proof_errors:
            die('; '.join(proof_errors))

        repair_authorization = None
        correction_id = None
        if correction_records(args.feature_dir, args.task_id):
            die('COMPLETION_CORRECTION_REQUIRED: corrected completion requires explicit repair claim')

        feature = str(doc.get('feature', args.feature_dir.name))
        target = worktree_path(feature, args.task_id)
        reported = sorted(set(evidence_doc['changed_paths']))
        if target.exists():
            actual = changed_paths(target)
            if reported != actual:
                die(f'evidence changed_paths differs from task worktree; reported={reported}, actual={actual}')
            checkpoint = checkpoint_worktree(doc, task, target)
            if changed_paths(target):
                die('COMPLETION_WORKTREE_DIRTY: checkpoint worktree is not clean')
        else:
            checkpoint = evidence_doc['checkpoint']
            if checkpoint != entry.get('checkpoint_commit'):
                die('COMPLETION_CHECKPOINT_MISMATCH: no task worktree exists and evidence checkpoint differs from lifecycle checkpoint')
            exists = subprocess.run(['git', 'cat-file', '-e', f'{checkpoint}^{{commit}}'],
                                    cwd=feature_repo_base(args.feature_dir), capture_output=True).returncode == 0
            if not exists:
                die('COMPLETION_CHECKPOINT_INVALID: checkpoint is not available in the repository')
        if evidence_doc['checkpoint'] != checkpoint:
            die('COMPLETION_CHECKPOINT_MISMATCH: evidence checkpoint differs from verified task checkpoint')

        record = {
            'schema_version': COMPLETION_AUTHORITY_SCHEMA_VERSION, 'record_type': 'completion',
            'repository_id': str(git_common_dir(args.feature_dir)), 'feature': args.feature_dir.name,
            'task': args.task_id, 'attempt': int(entry.get('attempts', 0)),
            'checkpoint': checkpoint, 'packet_revision': active['revision_id'],
            'contract_fingerprint': active['contract_sha256'],
            'evidence_reference_sha256': sha256_bytes(evidence_path.read_bytes()),
            'correction_id': None, 'repair_authorization_id': None,
        }
        record['record_id'] = completion_record_id('completion', record)
        record['created_at'] = utc_now().isoformat()
        add_completion_record(args.feature_dir, record)
        _completion_failpoint('completion-published')

        entry.update({
            'status': 'completed', 'completed_at': utc_now().isoformat(),
            'evidence': str(evidence_path), 'checkpoint_commit': checkpoint,
            'completion_record_id': record['record_id'],
        })
        if entry.pop('active_human_resume', None) is not None:
            entry['last_human_resume_used_at'] = utc_now().isoformat()
        if entry.pop('active_retry_authorization', None) is not None:
            entry['last_human_resume_used_at'] = utc_now().isoformat()
        entry.pop('start_origin_status', None)
        for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
            entry.pop(key, None)
    print(f'COMPLETED {args.task_id} checkpoint={checkpoint[:12]}')


def cmd_complete_repair(args: argparse.Namespace) -> None:
    """Publish canonical C2 for an already authorized repair on the same attempt."""
    args.feature_dir = pathlib.Path(args.feature_dir)
    doc = load_validated(args.feature_dir)
    if args.task_id not in task_index(doc) or not str(args.owner or '').strip():
        die('COMPLETION_REPAIR_INVALID: task and owner are required')
    evidence_path = pathlib.Path(args.evidence)
    evidence_errors = validate_evidence(evidence_path, require_pass=True)
    if evidence_errors:
        die('COMPLETION_EVIDENCE_INVALID: ' + '; '.join(evidence_errors))
    try:
        evidence_doc = json.loads(evidence_path.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError) as exc:
        die(f'COMPLETION_EVIDENCE_INVALID: {exc}')
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if not entry:
            die(f'unknown task {args.task_id}')
        require_available_completion_origin(state, args.task_id, entry)
        claim_projection = entry.get('completion_repair_claim')
        # A replay after publication is resolved from immutable history even if
        # the first publisher already cleared the mutable active-claim projection.
        all_repair_records = repaired_completion_records(args.feature_dir, args.task_id)
        if all_repair_records:
            existing = all_repair_records[-1]
            if isinstance(claim_projection, dict):
                existing = next((item for item in all_repair_records
                                 if item.get('repair_claim_id') == claim_projection.get('claim_id')), existing)
            active_now = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
            binding_now = validate_completion_binding(
                evidence_doc, args.feature_dir, args.task_id, entry, active_now)
            proofs_now = validate_completion_proofs(
                evidence_doc, active_task_contract(args.feature_dir, doc, args.task_id, state=state),
                required_completion_criteria(active_task_contract(args.feature_dir, doc, args.task_id, state=state)),
                args.feature_dir)
            replay_hash = sha256_bytes(evidence_path.read_bytes())
            replay_claim_id = existing.get('repair_claim_id')
            replay_claim = next((x for x in repair_claim_records(args.feature_dir, args.task_id)
                                 if x.get('record_id') == replay_claim_id), None)
            replay_authorization = next((x for x in repair_authorization_records(args.feature_dir, args.task_id)
                                         if replay_claim and x.get('record_id') == replay_claim.get('authorization_id')), None)
            replay_correction = next((x for x in correction_records(args.feature_dir, args.task_id)
                                      if replay_claim and x.get('record_id') == replay_claim.get('correction_id')), None)
            replay_historical = next((x for x in completion_records(args.feature_dir, args.task_id)
                                      if replay_correction and x.get('record_id') == replay_correction.get('original_completion_id')), None)
            replay_checkpoint = (effective_repair_checkpoint(args.feature_dir, args.task_id, replay_claim,
                                active_now, replay_historical, replay_correction, replay_authorization)
                                if all((replay_claim, replay_authorization, replay_correction, replay_historical)) else None)
            if (not binding_now and not proofs_now and evidence_doc.get('checkpoint') == replay_checkpoint and
                    existing.get('evidence_reference_sha256') == replay_hash and
                    existing.get('checkpoint') == evidence_doc.get('checkpoint')):
                print(f'ALREADY_COMPLETED_REPAIR {args.task_id} c2={existing["record_id"]}')
                return
            die('COMPLETION_REPAIR_CONFLICT: finalized claim was replayed with different evidence')
        if (entry.get('status') != 'running' or not isinstance(claim_projection, dict) or
                claim_projection.get('owner') != args.owner):
            die('COMPLETION_REPAIR_OWNER_MISMATCH: active repair claim does not match caller')
        owner_guard(entry, args.owner)
        attempt = entry.get('attempts')
        if type(attempt) is not int or attempt < 1:
            die('COMPLETION_REPAIR_ATTEMPT_INVALID: existing attempt is absent')
        active = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
        task = active_task_contract(args.feature_dir, doc, args.task_id, state=state)
        binding_errors = validate_completion_binding(evidence_doc, args.feature_dir, args.task_id, entry, active)
        claim_id = claim_projection.get('claim_id')
        claims = repair_claim_records(args.feature_dir, args.task_id)
        claim = next((item for item in claims if item.get('record_id') == claim_id), None)
        authorization_id = claim_projection.get('authorization_id')
        authorization = next((item for item in repair_authorization_records(args.feature_dir, args.task_id)
                              if item.get('record_id') == authorization_id), None)
        correction_id = claim_projection.get('correction_id')
        correction = next((item for item in correction_records(args.feature_dir, args.task_id)
                           if item.get('record_id') == correction_id), None)
        historical_id = correction.get('original_completion_id') if correction else None
        historical = next((item for item in completion_records(args.feature_dir, args.task_id)
                           if item.get('record_id') == historical_id), None)
        if not all((claim, authorization, correction, historical)):
            die('COMPLETION_REPAIR_AUTHORITY_INVALID: C1/K1/A1/claim chain is incomplete')
        if (claim.get('owner') != args.owner or claim.get('attempt') != attempt or
                claim.get('correction_id') != correction_id or claim.get('authorization_id') != authorization_id or
                authorization.get('correction_id') != correction_id or
                authorization.get('original_completion_id') != historical_id or
                authorization.get('attempt') != attempt or correction.get('completed_attempt') != attempt or
                correction.get('original_completion_id') != historical_id or
                historical.get('attempt') != attempt or
                authorization.get('packet_revision') != active['revision_id'] or
                correction.get('packet_revision') != active['revision_id'] or
                authorization.get('contract_fingerprint') != active['contract_sha256'] or
                correction.get('contract_fingerprint') != active['contract_sha256']):
            die('COMPLETION_REPAIR_AUTHORITY_INVALID: exact repair authority mismatch')
        effective_checkpoint = effective_repair_checkpoint(
            args.feature_dir, args.task_id, claim, active, historical, correction, authorization)
        if evidence_doc.get('checkpoint') != effective_checkpoint:
            binding_errors.append('REPAIR_CHECKPOINT_MISMATCH: evidence checkpoint differs from immutable repair checkpoint authority')
        if binding_errors:
            die('COMPLETION_EVIDENCE_BINDING_INVALID: ' + '; '.join(binding_errors))
        proof_errors = validate_completion_proofs(
            evidence_doc, task, required_completion_criteria(task), args.feature_dir)
        if proof_errors:
            die('COMPLETION_EVIDENCE_INVALID: ' + '; '.join(proof_errors))
        if (effective_task_status(args.feature_dir, args.task_id, entry) != 'correction_required' and
                not all_repair_records):
            die('COMPLETION_REPAIR_STATE_INVALID: task is not awaiting repair completion')
        prior = [item for item in all_repair_records
                 if item.get('repair_claim_id') == claim_id]
        evidence_hash = sha256_bytes(evidence_path.read_bytes())
        record = {
            'schema_version': COMPLETION_AUTHORITY_SCHEMA_VERSION,
            'record_type': 'completion-repair', 'protocol_version': 1,
            'repository_id': str(git_common_dir(args.feature_dir)), 'feature': args.feature_dir.name,
            'task': args.task_id, 'attempt': attempt,
            'historical_completion_id': historical_id, 'correction_id': correction_id,
            'repair_authorization_id': authorization_id, 'repair_claim_id': claim_id,
            'owner': args.owner, 'checkpoint': evidence_doc['checkpoint'],
            'evidence_reference_sha256': evidence_hash,
            'packet_revision': active['revision_id'],
            'contract_fingerprint': active['contract_sha256'],
        }
        record['record_id'] = completion_record_id('completion-repair', record)
        record['created_at'] = utc_now().isoformat()
        if prior and all(item.get('record_id') != record['record_id'] for item in prior):
            die('COMPLETION_REPAIR_CONFLICT: repair claim already finalized with different evidence')
        if prior:
            if prior[0].get('record_id') == record['record_id']:
                print(f'ALREADY_COMPLETED_REPAIR {args.task_id} c2={prior[0]["record_id"]}')
                return
            die('COMPLETION_REPAIR_CONFLICT: repair claim already finalized with different evidence')
        path = completion_authority_dir(args.feature_dir, 'completion-repair-records', args.task_id) / \
            f"{record['record_id'].rsplit(':', 1)[-1]}.json"
        claim_slot = path.parent / f"claim-{claim_id.rsplit(':', 1)[-1]}.json"
        slot_record = {key: value for key, value in record.items()
                       if key not in {'record_id', 'created_at'}}
        slot_record['record_id'] = record['record_id']
        slot_record['created_at'] = record['created_at']
        _completion_failpoint('repair-completion-before-publication')
        created = publish_completion_authority(claim_slot, slot_record)
        if not created:
            winner = json.loads(claim_slot.read_text(encoding='utf-8'))
            if winner.get('record_id') != record['record_id']:
                die('COMPLETION_REPAIR_CONFLICT: repair claim finalized by a different C2')
        _completion_failpoint('repair-completion-published')
        entry.update({
            'status': 'completed', 'completed_at': utc_now().isoformat(),
            'checkpoint_commit': record['checkpoint'], 'completion_repair_record_id': record['record_id'],
        })
        entry.setdefault('completion_repair_history', []).append({
            'repair_claim_id': claim_id, 'completion_id': record['record_id'],
            'attempt': attempt, 'completed_at': entry['completed_at'],
        })
        entry.pop('completion_repair_claim', None)
        for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
            entry.pop(key, None)
    print(('COMPLETED_REPAIR' if created else 'ALREADY_COMPLETED_REPAIR') +
          f' {args.task_id} c2={record["record_id"]}')


def cmd_bind_repair_checkpoint(args: argparse.Namespace) -> None:
    """Human-attest an immutable checkpoint for an existing legacy repair claim."""
    args.feature_dir = pathlib.Path(args.feature_dir)
    doc = load_validated(args.feature_dir)
    operator, reason, owner = (str(args.by or '').strip(), str(args.reason or '').strip(),
                               str(args.owner or '').strip())
    if not operator:
        die('REPAIR_CHECKPOINT_BINDING_INVALID: explicit operator is required')
    if not reason:
        die('REPAIR_CHECKPOINT_BINDING_INVALID: explicit reason is required')
    if not owner or not _valid_completion_commit(args.checkpoint):
        die('REPAIR_CHECKPOINT_BINDING_INVALID: owner and full checkpoint commit are required')
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if not entry or entry.get('status') != 'running' or entry.get('attempts') != args.attempt:
            die('REPAIR_CHECKPOINT_BINDING_INVALID: current task attempt/status differs')
        projection = entry.get('completion_repair_claim')
        if not isinstance(projection, dict) or projection.get('claim_id') != args.claim_id or projection.get('owner') != owner:
            die('REPAIR_CHECKPOINT_BINDING_INVALID: active repair claim/owner differs')
        active = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
        claim = next((x for x in repair_claim_records(args.feature_dir, args.task_id)
                      if x.get('record_id') == args.claim_id), None)
        authorization = next((x for x in repair_authorization_records(args.feature_dir, args.task_id)
                              if x.get('record_id') == args.authorization_id), None)
        correction = next((x for x in correction_records(args.feature_dir, args.task_id)
                           if x.get('record_id') == args.correction_id), None)
        historical_id = correction.get('original_completion_id') if correction else None
        historical = next((x for x in completion_records(args.feature_dir, args.task_id)
                           if x.get('record_id') == historical_id), None)
        if not all((claim, authorization, correction, historical)):
            die('REPAIR_CHECKPOINT_BINDING_INVALID: C1/K1/A1/claim chain is incomplete')
        if (claim.get('owner') != owner or claim.get('attempt') != args.attempt or
                historical.get('record_id') != args.historical_completion_id or
                claim.get('correction_id') != correction['record_id'] or
                claim.get('authorization_id') != authorization['record_id'] or
                authorization.get('record_id') != args.authorization_id or
                authorization.get('correction_id') != correction['record_id'] or
                authorization.get('original_completion_id') != historical['record_id'] or
                authorization.get('attempt') != args.attempt or
                correction.get('record_id') != args.correction_id or correction.get('completed_attempt') != args.attempt or
                correction.get('packet_revision') != active['revision_id'] or
                authorization.get('packet_revision') != active['revision_id'] or
                correction.get('contract_fingerprint') != active['contract_sha256'] or
                authorization.get('contract_fingerprint') != active['contract_sha256']):
            die('REPAIR_CHECKPOINT_BINDING_INVALID: exact current authority mismatch')
        if claim.get('checkpoint') is not None:
            die('REPAIR_CHECKPOINT_BINDING_INVALID: claim already has a canonical checkpoint')
        if any(x.get('repair_claim_id') == args.claim_id for x in repaired_completion_records(args.feature_dir, args.task_id)):
            die('REPAIR_CHECKPOINT_BINDING_INVALID: repair is already finalized')
        existing = [x for x in repair_checkpoint_bindings(args.feature_dir, args.task_id)
                    if x.get('repair_claim_id') == args.claim_id]
        if existing:
            if (existing[0].get('checkpoint') == args.checkpoint and existing[0].get('operator') == operator and
                    existing[0].get('reason') == reason):
                print(f'ALREADY_BOUND {args.task_id} binding={existing[0]["record_id"]}')
                return
            die('REPAIR_CHECKPOINT_BINDING_CONFLICT: repair claim already has a different binding')
        record = {
            'schema_version': 1, 'record_type': REPAIR_CHECKPOINT_BINDING_SCHEME,
            'repository_id': str(git_common_dir(args.feature_dir)), 'feature': args.feature_dir.name,
            'task': args.task_id, 'attempt': args.attempt, 'historical_completion_id': historical['record_id'],
            'correction_id': correction['record_id'], 'repair_authorization_id': authorization['record_id'],
            'repair_claim_id': claim['record_id'], 'owner': owner, 'checkpoint': args.checkpoint,
            'packet_revision': active['revision_id'], 'contract_fingerprint': active['contract_sha256'],
            'protocol_version': protocol_version(args.feature_dir), 'operator': operator, 'reason': reason,
        }
        record['record_id'] = repair_checkpoint_binding_id(record)
        record['created_at'] = utc_now().isoformat()
        path = completion_authority_dir(args.feature_dir, 'completion-repair-checkpoint-bindings', args.task_id) / \
            f"{record['record_id'].rsplit(':', 1)[-1]}.json"
        _completion_failpoint('repair-checkpoint-binding-before-publication')
        created = publish_completion_authority(path, record)
        _completion_failpoint('repair-checkpoint-binding-published')
    print(('BOUND' if created else 'ALREADY_BOUND') + f' {args.task_id} binding={record["record_id"]}')


def cmd_correct_completion(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    if args.task_id not in task_index(doc):
        die(f'unknown task {args.task_id}')
    operator = str(args.by or '').strip()
    reason = str(args.reason or '').strip()
    if not operator or not reason or args.reason_code != COMPLETION_CORRECTION_REASON:
        die('COMPLETION_CORRECTION_INVALID: explicit operator, reason and supported reason code are required')
    evidence_path = pathlib.Path(args.evidence)
    if not evidence_path.is_file() or evidence_path.is_symlink():
        die('COMPLETION_CORRECTION_INVALID: defect evidence must be an existing regular file')
    evidence_hash = sha256_bytes(evidence_path.read_bytes())
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'][args.task_id]
        active = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
        current = correction_records(args.feature_dir, args.task_id)
        if entry.get('status') not in {'completed', 'correction_required'}:
            die('COMPLETION_CORRECTION_INVALID: task has no completed lifecycle event to correct')
        completions = completion_records(args.feature_dir, args.task_id)
        current_completion = next((item for item in completions
                                   if item.get('record_id') == entry.get('completion_record_id')), None)
        existing_for_current = next((item for item in current
                                     if current_completion and
                                     item.get('original_completion_id') == current_completion['record_id']), None)
        original = current_completion
        if existing_for_current:
            original = next((item for item in completions
                             if item.get('record_id') == existing_for_current.get('original_completion_id')), None)
        if original is None:
            original = completion_record_for_entry(args.feature_dir, args.task_id, entry, active)
        original['record_id'] = completion_record_id('completion', original)
        existing_for_original = [item for item in current
                                 if item.get('original_completion_id') == original['record_id']]
        correction = {
            'schema_version': COMPLETION_AUTHORITY_SCHEMA_VERSION,
            'record_type': 'completion-correction', 'repository_id': str(git_common_dir(args.feature_dir)),
            'feature': args.feature_dir.name, 'task': args.task_id,
            'completed_attempt': int(entry.get('attempts', 0)),
            'original_completion_id': original['record_id'],
            'original_completion_checkpoint': original.get('checkpoint'),
            'packet_revision': active['revision_id'], 'contract_fingerprint': active['contract_sha256'],
            'reason_code': args.reason_code, 'reason': reason, 'defect_evidence_sha256': evidence_hash,
            'operator': operator,
        }
        correction['record_id'] = completion_record_id('completion-correction', correction)
        correction['created_at'] = utc_now().isoformat()
        if existing_for_original and all(item['record_id'] != correction['record_id'] for item in existing_for_original):
            die('COMPLETION_CORRECTION_CONFLICT: original completion already has a different correction')
        add_completion_record(args.feature_dir, original)
        path = completion_authority_dir(args.feature_dir, 'completion-corrections', args.task_id) / \
            f"{correction['record_id'].rsplit(':', 1)[-1]}.json"
        created = publish_completion_authority(path, correction)
        _completion_failpoint('correction-published')
        if not correction_is_repaired(args.feature_dir, args.task_id, correction):
            entry.update({'status': 'correction_required', 'active_completion_correction_id': correction['record_id']})
    print(('CORRECTED' if created else 'ALREADY_CORRECTED') +
          f" {args.task_id} correction={correction['record_id']}")


def cmd_authorize_completion_repair(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    if args.task_id not in task_index(doc):
        die(f'unknown task {args.task_id}')
    operator = str(args.by or '').strip()
    reason = str(args.reason or '').strip()
    if not operator or not reason:
        die('COMPLETION_REPAIR_AUTHORIZATION_INVALID: explicit operator and reason are required')
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'][args.task_id]
        correction = next((x for x in correction_records(args.feature_dir, args.task_id)
                           if x.get('record_id') == args.correction_id), None)
        active = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
        if not correction:
            die('COMPLETION_REPAIR_AUTHORIZATION_INVALID: correction is absent')
        existing = [x for x in repair_authorization_records(args.feature_dir, args.task_id)
                    if x.get('correction_id') == correction['record_id']]
        authorization = {
            'schema_version': COMPLETION_AUTHORITY_SCHEMA_VERSION,
            'record_type': 'completion-repair-authorization',
            'repository_id': str(git_common_dir(args.feature_dir)), 'feature': args.feature_dir.name,
            'task': args.task_id, 'attempt': int(entry.get('attempts', 0)),
            'correction_id': correction['record_id'],
            'original_completion_id': correction['original_completion_id'],
            'packet_revision': active['revision_id'], 'contract_fingerprint': active['contract_sha256'],
            'reason': reason, 'operator': operator,
        }
        authorization['record_id'] = completion_record_id('completion-repair-authorization', authorization)
        authorization['created_at'] = utc_now().isoformat()
        if any(item['record_id'] == authorization['record_id'] for item in existing):
            print(f"ALREADY_AUTHORIZED {args.task_id} authorization={authorization['record_id']}")
            return
        if (correction_is_repaired(args.feature_dir, args.task_id, correction) or
                correction['completed_attempt'] != entry.get('attempts') or
                correction['packet_revision'] != active['revision_id'] or
                correction['contract_fingerprint'] != active['contract_sha256']):
            die('COMPLETION_REPAIR_AUTHORIZATION_INVALID: correction is repaired or stale')
        if existing and all(item['record_id'] != authorization['record_id'] for item in existing):
            die('COMPLETION_REPAIR_AUTHORIZATION_CONFLICT: correction already has different repair authority')
        path = completion_authority_dir(args.feature_dir, 'completion-repair-authorizations', args.task_id) / \
            f"{authorization['record_id'].rsplit(':', 1)[-1]}.json"
        created = publish_completion_authority(path, authorization)
        _completion_failpoint('repair-authorization-published')
        entry['active_completion_repair_authorization'] = authorization['record_id']
    print(('AUTHORIZED' if created else 'ALREADY_AUTHORIZED') +
          f" {args.task_id} authorization={authorization['record_id']}")


def cmd_claim_completion_repair(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    if args.task_id not in task_index(doc) or not str(args.owner or '').strip():
        die('COMPLETION_REPAIR_CLAIM_INVALID: task and owner are required')
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'][args.task_id]
        authorization = next((x for x in repair_authorization_records(args.feature_dir, args.task_id)
                              if x.get('record_id') == args.authorization), None)
        correction = (next((x for x in correction_records(args.feature_dir, args.task_id)
                            if authorization and x.get('record_id') == authorization.get('correction_id')), None))
        active = resolve_active_packet(args.feature_dir, doc, args.task_id, state=state)
        if (not authorization or not correction or correction_is_repaired(args.feature_dir, args.task_id, correction) or
                authorization.get('task') != args.task_id or authorization.get('attempt') != entry.get('attempts') or
                authorization.get('packet_revision') != active['revision_id'] or
                authorization.get('contract_fingerprint') != active['contract_sha256']):
            die('COMPLETION_REPAIR_AUTHORIZATION_INVALID: exact current authority is absent')
        if entry.get('status') == 'running':
            existing = entry.get('completion_repair_claim')
            if isinstance(existing, dict) and existing.get('authorization_id') == args.authorization and existing.get('owner') == args.owner:
                print(f"ALREADY_CLAIMED {args.task_id} attempt={entry['attempts']}")
                return
            die('COMPLETION_REPAIR_ALREADY_OWNED: another repair continuation is active')
        if effective_task_status(args.feature_dir, args.task_id, entry) != 'correction_required':
            die('COMPLETION_REPAIR_CLAIM_INVALID: task is not awaiting a completion repair')
        prior_claims = read_completion_authority(args.feature_dir, 'completion-repair-claims', args.task_id,
                                                 record_type='completion-repair-claim')
        claim = next((item for item in prior_claims
                      if item.get('correction_id') == correction['record_id'] and
                      item.get('authorization_id') == authorization['record_id']), None)
        if claim and claim.get('owner') != args.owner:
            die('COMPLETION_REPAIR_ALREADY_OWNED: durable repair claim belongs to another owner')
        if claim is None:
            generation = 1 + max((int(item.get('generation', 0)) for item in prior_claims
                                  if item.get('correction_id') == correction['record_id']), default=0)
            claim = {
                'schema_version': COMPLETION_AUTHORITY_SCHEMA_VERSION,
                'record_type': 'completion-repair-claim', 'repository_id': str(git_common_dir(args.feature_dir)),
                'feature': args.feature_dir.name, 'task': args.task_id, 'attempt': int(entry.get('attempts', 0)),
                'correction_id': correction['record_id'], 'authorization_id': authorization['record_id'],
                'owner': args.owner, 'generation': generation,
            }
            claim['record_id'] = completion_record_id('completion-repair-claim', claim)
            claim['created_at'] = utc_now().isoformat()
            claim_path = completion_authority_dir(args.feature_dir, 'completion-repair-claims', args.task_id) / \
                f"{claim['record_id'].rsplit(':', 1)[-1]}.json"
            publish_completion_authority(claim_path, claim)
            _completion_failpoint('repair-claim-published')
        entry.update({
            'status': 'running', 'owner': args.owner, 'completion_repair_claim': {
                'claim_id': claim['record_id'], 'correction_id': correction['record_id'],
                'authorization_id': authorization['record_id'], 'owner': args.owner,
                'generation': claim['generation'],
            },
        })
        refresh_lease(entry, doc)
    print(f"CLAIMED_REPAIR {args.task_id} owner={args.owner} attempt={entry['attempts']}")

def cmd_fail(args: argparse.Namespace, *, control_outcome: str | None = None) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    if control_outcome is not None and control_outcome not in VERIFICATION_BLOCKAGE_OUTCOMES:
        die('invalid internal verification control outcome')
    assert_repository_verification_drained(args.feature_dir)
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if not entry:
            die(f'unknown task {args.task_id}')
        owner_guard(entry, args.owner)
        if entry.get('status') != 'running':
            die(f'{args.task_id} is not running')
        if isinstance(entry.get('completion_repair_claim'), dict):
            entry['status'] = 'correction_required'
            entry['completion_repair_last_failure'] = args.reason
            entry.setdefault('completion_repair_claim_history', []).append({
                **entry.pop('completion_repair_claim'), 'ended_at': utc_now().isoformat(),
                'result': 'failed',
            })
            for key in ('owner', 'heartbeat_at', 'lease_expires_at', 'claimed_at'):
                entry.pop(key, None)
            print(f'REPAIR_FAILED {args.task_id} attempt={entry["attempts"]}; authorization remains scoped')
            return
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
        entry['last_transition'] = {'kind': 'attempt_failed', 'attempts': attempts,
                                    'status': status, 'at': entry['failed_at']}
        if control_outcome in VERIFICATION_BLOCKAGE_OUTCOMES:
            entry['control_outcome'] = control_outcome
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
    assert_repository_verification_drained(args.feature_dir)
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'].get(args.task_id)
        if not entry:
            die(f'unknown task {args.task_id}')
        owner_guard(entry, args.owner)
        if entry.get('status') != 'running':
            die(f'{args.task_id} is not running')
        if isinstance(entry.get('completion_repair_claim'), dict):
            entry['status'] = 'correction_required'
            entry['completion_repair_last_release'] = args.reason
            entry.setdefault('completion_repair_claim_history', []).append({
                **entry.pop('completion_repair_claim'), 'ended_at': utc_now().isoformat(),
                'result': 'released',
            })
            for key in ('owner', 'heartbeat_at', 'lease_expires_at', 'claimed_at'):
                entry.pop(key, None)
            print(f'REPAIR_RELEASED {args.task_id} attempt={entry["attempts"]}')
            return
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
        entry['last_transition'] = {'kind': 'lease_released', 'attempts': entry.get('attempts', 0),
                                    'status': 'failed', 'at': entry['released_at']}
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


def prune_task_workspace(feature: str, task_id: str, *, expected_branch_commit: str | None = None,
                         guard_branch: bool = False) -> None:
    target = worktree_path(feature, task_id)
    branch = f'agent/{feature}/{task_id}'
    ensure_task_workspace_clean(feature, task_id)
    branch_ref = f'refs/heads/{branch}'
    branch_result = subprocess.run(
        ['git', 'rev-parse', '--verify', branch_ref], capture_output=True, text=True, check=False,
    )
    branch_commit = branch_result.stdout.strip() if branch_result.returncode == 0 else None
    if guard_branch and branch_commit != expected_branch_commit:
        die(f'cannot invalidate task branch {branch}: expected {expected_branch_commit}, found {branch_commit}')
    if target.exists():
        subprocess.run(['git', 'worktree', 'remove', str(target)], check=True)
    if branch_commit is not None:
        subprocess.run(['git', 'update-ref', '-d', branch_ref, branch_commit], check=True)
    elif guard_branch and subprocess.run(
            ['git', 'show-ref', '--verify', '--quiet', branch_ref], check=False).returncode == 0:
        die(f'cannot invalidate task branch {branch}: a branch appeared during cleanup')


def ensure_task_workspace_clean(feature: str, task_id: str) -> None:
    target = worktree_path(feature, task_id)
    if target.exists():
        dirty = changed_paths(target)
        ignored = subprocess.run(
            ['git', 'status', '--porcelain', '--ignored=traditional', '--untracked-files=normal'],
            cwd=target, capture_output=True, text=True, check=True,
        ).stdout.splitlines()
        dirty.extend(line[3:] for line in ignored if line.startswith('!! '))
        dirty = sorted(set(dirty))
        if dirty:
            die(f'cannot invalidate dirty descendant worktree {target}: {dirty}')


def task_workspace_snapshot(feature: str, task_id: str) -> dict[str, str | bool | None]:
    target = worktree_path(feature, task_id)
    branch = f'agent/{feature}/{task_id}'
    branch_result = subprocess.run(
        ['git', 'rev-parse', '--verify', f'refs/heads/{branch}'],
        capture_output=True, text=True, check=False,
    )
    branch_commit = branch_result.stdout.strip() if branch_result.returncode == 0 else None
    worktree_commit = None
    if target.exists():
        worktree_commit = subprocess.run(
            ['git', 'rev-parse', 'HEAD'], cwd=target, capture_output=True, text=True, check=True,
        ).stdout.strip()
    return {'branch_commit': branch_commit, 'worktree_commit': worktree_commit}


def restore_task_workspace(feature: str, task_id: str, snapshot: dict[str, str | bool | None]) -> None:
    target = worktree_path(feature, task_id)
    branch = f'agent/{feature}/{task_id}'
    branch_commit = snapshot.get('branch_commit')
    worktree_commit = snapshot.get('worktree_commit')
    branch_ref = subprocess.run(
        ['git', 'rev-parse', '--verify', f'refs/heads/{branch}'],
        capture_output=True, text=True, check=False,
    )
    current_branch_commit = branch_ref.stdout.strip() if branch_ref.returncode == 0 else None
    if branch_commit and current_branch_commit is None:
        subprocess.run(['git', 'branch', branch, str(branch_commit)], check=True)
    if worktree_commit and not target.exists():
        if branch_commit == worktree_commit and current_branch_commit in {None, branch_commit}:
            subprocess.run(['git', 'worktree', 'add', str(target), branch], check=True)
        else:
            # Preserve the exact completed worktree snapshot if another process recreated or
            # advanced its branch while rollback was in progress.
            subprocess.run(['git', 'worktree', 'add', '--detach', str(target), str(worktree_commit)], check=True)


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
    if prior.get('control_outcome') in VERIFICATION_BLOCKAGE_OUTCOMES:
        die('VERIFICATION_AUTHORIZATION_REQUIRED: task-level human resolution cannot authorize or resume a verification blockage')

    artifact = write_human_resolution(
        args.feature_dir, doc, args.task_id, decision=decision, decided_by=decided_by,
        prior_entry=dict(prior), source=source,
    )
    with locked_state(args.feature_dir, doc) as state:
        entry = state['tasks'][args.task_id]
        if entry.get('status') != 'escalated':
            artifact.unlink(missing_ok=True)
            die(f'{args.task_id} changed state while applying human resolution; retry deliberately')
        if entry.get('control_outcome') in VERIFICATION_BLOCKAGE_OUTCOMES:
            artifact.unlink(missing_ok=True)
            die('VERIFICATION_AUTHORIZATION_REQUIRED: task-level human resolution cannot authorize or resume a verification blockage')
        history = entry.setdefault('human_resolution_history', [])
        if not isinstance(history, list):
            history = []
            entry['human_resolution_history'] = history
        resolved_at = json.loads(artifact.read_text(encoding='utf-8'))['resolved_at']
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
        entry['last_transition'] = {
            'kind': 'human_resolution', 'artifact': str(artifact),
            'artifact_sha256': sha256_bytes(artifact.read_bytes()),
            'attempts': int(entry.get('attempts', 0)), 'resolved_at': resolved_at,
        }
        for key in ('owner', 'heartbeat_at', 'lease_expires_at'):
            entry.pop(key, None)
    print(f'HUMAN_RESOLVED {args.task_id} -> retry authorized; artifact={artifact}')


def reopen_dependency_graph(feature_dir: pathlib.Path, doc: dict[str, Any],
                            state: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Build reopen dependencies without resolving unrelated legacy packets."""
    graph = {}
    for tid, task in task_index(doc).items():
        validate_attempt_binding_ledger(state['tasks'][tid])
        if state['tasks'].get(tid, {}).get('active_packet_revision') is not None:
            graph[tid] = active_task_contract(feature_dir, doc, tid, state=state)
        else:
            graph[tid] = task
    return graph


def cmd_reopen(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    if args.task_id not in idx:
        die(f'unknown task {args.task_id}')
    feature = str(doc.get('feature', args.feature_dir.name))
    stale: list[str] = []

    assert_repository_verification_drained(args.feature_dir)

    # Reopen and descendant invalidation are one state transition. Validate every lease/status under
    # the state lock before deleting any task-local workspace, otherwise a racing worker can lose a
    # live worktree even though the reopen itself is rejected.
    with locked_state(args.feature_dir, doc) as state:
        stale = sorted(descendants(reopen_dependency_graph(args.feature_dir, doc, state), args.task_id))
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
        exhausted = int(target.get('attempts', 0)) >= max_attempts

        # Preflight every descendant before removing the first workspace. prune_task_workspace
        # repeats the check immediately before each removal to fail closed on intervening edits.
        for tid in stale:
            ensure_task_workspace_clean(feature, tid)

        snapshots = {tid: task_workspace_snapshot(feature, tid) for tid in stale}
        attempted_prunes: list[str] = []
        try:
            for tid in stale:
                # Include the current item so a partially failed worktree/branch removal is restored.
                attempted_prunes.append(tid)
                snapshot = snapshots[tid]
                if snapshot['branch_commit'] is None and snapshot['worktree_commit'] is None:
                    prune_task_workspace(feature, tid)
                else:
                    prune_task_workspace(feature, tid,
                                         expected_branch_commit=snapshot['branch_commit'], guard_branch=True)
        except BaseException:
            restoration_errors = []
            for tid in reversed(attempted_prunes):
                try:
                    restore_task_workspace(feature, tid, snapshots[tid])
                except (OSError, subprocess.CalledProcessError) as exc:
                    restoration_errors.append(f'{tid}: {exc}')
            if restoration_errors:
                die('reopen cleanup failed and workspace rollback was incomplete: ' + '; '.join(restoration_errors))
            raise

        reopened_at = utc_now().isoformat()
        target.update({
            'status': 'escalated' if exhausted else 'failed',
            'last_failure': args.reason,
            'reopened_at': reopened_at,
        })
        target['last_transition'] = {'kind': 'reopened', 'attempts': target.get('attempts', 0),
                                     'status': target['status'], 'at': reopened_at}
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
    if exhausted:
        print(f'ESCALATED {args.task_id}: rework budget exhausted; invalidated descendants: '
              f'{", ".join(stale) if stale else "none"}')
    else:
        print(f'REOPENED {args.task_id}; invalidated descendants: {", ".join(stale) if stale else "none"}')


def cmd_status(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    state = _read_state_unlocked_pure(args.feature_dir, doc)
    bridge_records = []
    for task_id in state.get('tasks', {}):
        task_root = runtime_state_dir(args.feature_dir) / 'packet-identity-bridges' / args.feature_dir.name / task_id
        if task_root.exists() and task_root.is_dir() and not task_root.is_symlink():
            for bridge_path in sorted(task_root.glob('*.json')):
                try:
                    bridge = json.loads(bridge_path.read_text(encoding='utf-8'))
                except (OSError, json.JSONDecodeError):
                    die(f'PACKET_IDENTITY_ATTESTATION_CONFLICT: unreadable bridge {bridge_path.name}')
                if not isinstance(bridge, dict) or bridge.get('classification') != 'HUMAN_ATTESTED':
                    die(f'PACKET_IDENTITY_ATTESTATION_CONFLICT: malformed bridge {bridge_path.name}')
                bridge_records.append(bridge)
    if args.json:
        state['packet_identity_bridges'] = bridge_records
        for tid, entry in state['tasks'].items():
            entry['historical_status'] = entry.get('status')
            entry['effective_status'] = effective_task_status(args.feature_dir, tid, entry)
            entry['status'] = entry['effective_status']
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
        status = effective_task_status(args.feature_dir, tid, entry)
        print(
            f'{tid:9} {status:18} {task["role"]:11} '
            f'attempts={entry.get("attempts", 0)} owner={owner} lease={lease}  {task["title"]}'
        )
    for bridge in bridge_records:
        request = bridge['request']
        print(f"PACKET_IDENTITY_BRIDGE {bridge['bridge_id']} classification=HUMAN_ATTESTED "
              f"task={request['task']} attempt={request['attempt']} "
              f"source={request['source_identity']['scheme']}:{request['source_identity']['value']} "
              f"target={request['target_identity']['scheme']}:{request['target_identity']['value']} "
              f"revision={request['target_revision_identity']['value']} operator={request['operator']} "
              f"semantic={request['semantic_contract_sha256']} "
              f"recovery={request['recovery_record_identity']['scheme']}:{request['recovery_record_identity']['value']} "
              f"reason={request['reason']} created_at={bridge['created_at']}")


def cmd_reset(args: argparse.Namespace) -> None:
    doc = load_json(args.feature_dir / 'tasks.json') if (args.feature_dir / 'tasks.json').exists() else {'feature': args.feature_dir.name, 'tasks': []}
    if args.full or args.prune_worktrees:
        assert_repository_verification_drained(args.feature_dir)
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


def execution_protocol_paths(feature_dir: pathlib.Path) -> list[str]:
    root = repo_root()
    try:
        feature_rel = feature_dir.resolve().relative_to(root)
    except ValueError:
        die('feature_dir must be inside the repository')
    return [
        '.gitignore', 'AGENTS.md', 'CLAUDE.md', '.claude/agents',
        'docs/agentic-sdd', '.github/workflows/agentic-sdd.yml',
        f'{feature_rel}/spec.md', f'{feature_rel}/plan.md', f'{feature_rel}/tasks.json',
        f'{feature_rel}/design.json', f'{feature_rel}/design/gate.json',
        f'{feature_rel}/verification-contract.json', f'{feature_rel}/wayfinder-handoff.json',
    ]


def legacy_execution_snapshot_paths(feature_dir: pathlib.Path) -> list[str]:
    """Keep the pre-task synthetic snapshot scope for design and wayfinder callers."""
    root = repo_root()
    try:
        feature_rel = feature_dir.resolve().relative_to(root)
    except ValueError:
        die('feature_dir must be inside the repository')
    candidates = [
        '.gitignore', 'AGENTS.md', 'CLAUDE.md', '.claude/agents',
        'docs/agentic-sdd', '.github/workflows/agentic-sdd.yml', str(feature_rel),
    ]
    return [candidate for candidate in candidates if (root / candidate).exists()]


def _path_is_selected(path: str, selected: list[str]) -> bool:
    return any(path == item or path.startswith(item.rstrip('/') + '/')
               for item in selected if item)


def current_execution_snapshot(feature_dir: pathlib.Path, doc: dict[str, Any]) -> tuple[str, bool]:
    """Return an immutable snapshot for non-task orchestration callers."""
    root = repo_root()
    head = subprocess.run(
        ['git', 'rev-parse', 'HEAD'], cwd=root, capture_output=True, text=True, check=True
    ).stdout.strip()
    dirty = changed_paths(root)
    try:
        feature_rel = feature_dir.resolve().relative_to(root)
    except ValueError:
        die('feature_dir must be inside the repository')
    unrelated = [path for path in dirty if not bootstrap_path_allowed(path, feature_rel)]
    if unrelated:
        die('primary checkout has uncommitted changes outside the SDD protocol/active feature; '
            f'create a deliberate local checkpoint or stash them before orchestration: {unrelated}')
    selected = legacy_execution_snapshot_paths(feature_dir)
    if not dirty:
        return head, False

    # Preserve the legacy synthetic snapshot for design/wayfinder/verification-contract
    # callers. Task worktrees use task_worktree_snapshot below and never consume this.
    fd, index_name = tempfile.mkstemp(prefix='agent-sdd-index-')
    os.close(fd)
    os.unlink(index_name)
    env = os.environ.copy()
    env['GIT_INDEX_FILE'] = index_name
    try:
        subprocess.run(['git', 'read-tree', head], cwd=root, env=env, check=True, capture_output=True)
        tracked = subprocess.run(['git', 'ls-files'], cwd=root, capture_output=True,
                                 text=True, check=True).stdout.splitlines()
        selected_existing = [path for path in selected
                             if (root / path).exists()
                             or any(_path_is_selected(tracked_path, [path]) for tracked_path in tracked)]
        subprocess.run(['git', 'add', '-A', '--', *selected_existing], cwd=root, env=env, check=True,
                       capture_output=True)
        tree = subprocess.run(['git', 'write-tree'], cwd=root, env=env, capture_output=True,
                              text=True, check=True).stdout.strip()
        commit = subprocess.run(
            ['git', '-c', 'user.name=Agent Harness', '-c',
             'user.email=agent-harness@local.invalid', 'commit-tree', tree, '-p', head,
             '-m', f'agent orchestration base {doc.get("feature", feature_dir.name)}'],
            cwd=root, env=env, capture_output=True, text=True, check=True,
        ).stdout.strip()
    finally:
        if os.path.exists(index_name):
            os.unlink(index_name)
    return commit, True


def task_worktree_snapshot(feature_dir: pathlib.Path) -> str:
    """Return committed HEAD only, rejecting dirty paths that define task freshness."""
    root = repo_root()
    head = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=root, capture_output=True,
                          text=True, check=True).stdout.strip()
    selected = execution_protocol_paths(feature_dir)
    dirty = [path for path in changed_paths(root) if _path_is_selected(path, selected)]
    if dirty:
        die('primary checkout has uncommitted protocol/feature files; commit them before task worktree creation: '
            f'{dirty}')
    return head


def stage_first_task_base(feature_dir: pathlib.Path, state: dict[str, Any],
                          durable_state: dict[str, Any], task: dict[str, Any],
                          target: pathlib.Path) -> None:
    """Stage base authority and persist a recovery breadcrumb before worktree creation."""
    if task.get('depends_on'):
        return
    if state.get('base_commit') is not None:
        return

    base = durable_state.get('base_commit')
    kind = durable_state.get('base_kind')
    if base is None:
        base = durable_state.get('pending_base_commit')
        kind = durable_state.get('pending_base_kind')
        if base is not None:
            check = subprocess.run(['git', 'cat-file', '-e', f'{base}^{{commit}}'], capture_output=True)
            if (check.returncode != 0 or kind != 'head' or
                    historical_feature_fingerprint(feature_dir, base) != feature_fingerprint(feature_dir)):
                die(f'BASE_AUTHORITY_MISSING: pending first-task base cannot prove the accepted feature: {base}')
    if base is None:
        if target.exists():
            die(f'BASE_AUTHORITY_MISSING: existing worktree has no recorded first-task base: {target}')
        base = task_worktree_snapshot(feature_dir)
        kind = 'head'
        durable_state['pending_base_commit'] = base
        durable_state['pending_base_kind'] = kind
        # The pending pair is only a write-ahead recovery breadcrumb. It lets
        # retries recover the original snapshot after an interrupted Git side
        # effect without publishing base_commit/base_kind on a failed start.
        save_state(feature_dir, durable_state)

    check = subprocess.run(['git', 'cat-file', '-e', f'{base}^{{commit}}'], capture_output=True)
    if check.returncode != 0 or kind != 'head':
        die(f'BASE_AUTHORITY_MISSING: pending first-task base is invalid: {base}')
    state['base_commit'] = base
    state['base_kind'] = kind


def execution_base(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any]) -> str:
    existing = state.get('base_commit')
    if isinstance(existing, str) and existing:
        check = subprocess.run(['git', 'cat-file', '-e', f'{existing}^{{commit}}'], capture_output=True)
        if check.returncode != 0:
            die(f'local orchestration base commit no longer exists: {existing}; reset the feature state')
        return existing

    commit, synthetic = current_execution_snapshot(feature_dir, doc)
    state['base_commit'] = commit
    state['base_kind'] = 'synthetic-local' if synthetic else 'head'
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


def prepare_task_worktree(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any],
                          task: dict[str, Any], *,
                          durable_state: dict[str, Any] | None = None) -> pathlib.Path:
    feature = str(doc.get('feature', feature_dir.name))
    target = worktree_path(feature, task['id'])
    if durable_state is None:
        durable_state = state
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

    first_base_missing = not task.get('depends_on') and state.get('base_commit') is None
    stage_first_task_base(feature_dir, state, durable_state, task, target)
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

    if dep_commits:
        base = dep_commits[0]
    elif first_base_missing:
        # Use the exact committed snapshot that was durably recorded as the
        # first historical base; a second HEAD read here could race a commit.
        base = state['base_commit']
    else:
        base = task_worktree_snapshot(feature_dir)
    subprocess.run(['git', 'worktree', 'add', '--quiet', str(target), '-b', branch, base], check=True)
    try:
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
            die(
                'worktree base is missing SDD/spec files: '
                f'{missing}. Reset/re-plan the feature; the harness can synthesize a local base without moving your branch.'
            )

        snapshot = task_worktree_snapshot(feature_dir)
        protocol_paths = execution_protocol_paths(feature_dir)
        needs_refresh = (
            feature_fingerprint(target / rel_feature) != feature_fingerprint(feature_dir)
            or protocol_version(target / rel_feature) != protocol_version(feature_dir)
        )
        if needs_refresh:
            path_diff = subprocess.run(
                ['git', 'diff', '--quiet', 'HEAD', snapshot, '--', *protocol_paths], cwd=target
            )
            if path_diff.returncode not in (0, 1):
                die(f'cannot compare task worktree protocol snapshot: {target}')
            stale_paths = path_diff.returncode == 1
        else:
            stale_paths = False
        if stale_paths:
            root_paths = subprocess.run(['git', 'ls-tree', '-r', '--name-only', snapshot],
                                        cwd=repo_root(), capture_output=True, text=True, check=True).stdout.splitlines()
            target_paths = subprocess.run(['git', 'ls-files'], cwd=target,
                                          capture_output=True, text=True, check=True).stdout.splitlines()
            source_selected = [path for path in root_paths if _path_is_selected(path, protocol_paths)]
            target_selected = [path for path in target_paths if _path_is_selected(path, protocol_paths)]
            removed = sorted(set(target_selected) - set(source_selected))
            if removed:
                subprocess.run(['git', 'rm', '-f', '--', *removed], cwd=target, check=True,
                               capture_output=True)
            if source_selected:
                subprocess.run(
                    ['git', 'restore', '--source', snapshot, '--staged', '--worktree', '--', *source_selected],
                    cwd=target, check=True,
                )
            subprocess.run(
                ['git', '-c', 'user.name=Agent Harness', '-c', 'user.email=agent-harness@local.invalid',
                 'commit', '--no-gpg-sign', '-m', f'agent orchestration refresh {feature}'],
                cwd=target, check=True,
            )

        assert_worktree_protocol_current(feature_dir, target)
    except BaseException as original:
        cleanup_errors = []
        removed = subprocess.run(['git', 'worktree', 'remove', '--force', str(target)],
                                 cwd=repo_root(), capture_output=True, text=True)
        if removed.returncode != 0 and target.exists():
            cleanup_errors.append(removed.stderr.strip() or 'worktree removal failed')
        if not target.exists():
            deleted = subprocess.run(['git', 'branch', '-D', branch], cwd=repo_root(),
                                     capture_output=True, text=True)
            if deleted.returncode != 0:
                cleanup_errors.append(deleted.stderr.strip() or 'task branch removal failed')
        if cleanup_errors:
            raise RuntimeError(f'{original}; cleanup incomplete: {"; ".join(cleanup_errors)}') from original
        raise
    return target


def cmd_worktree_create(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    assert_repository_verification_drained(args.feature_dir)
    with locked_state(args.feature_dir, doc) as state:
        if args.task_id not in ready_ids(doc, state, args.feature_dir):
            die(f'{args.task_id} is not ready')
        task = active_task_contract(args.feature_dir, doc, args.task_id, state=state)
        staged_state = copy.deepcopy(state)
        target = prepare_task_worktree(args.feature_dir, doc, staged_state, task, durable_state=state)
        for key in ('base_commit', 'base_kind'):
            if key in staged_state:
                state[key] = staged_state[key]
        state.pop('pending_base_commit', None)
        state.pop('pending_base_kind', None)
    print(target)


def cmd_start(args: argparse.Namespace) -> None:
    doc = load_validated(args.feature_dir)
    idx = task_index(doc)
    task = idx.get(args.task_id)
    if not task:
        die(f'unknown task {args.task_id}')
    assert_repository_verification_drained(args.feature_dir)
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
        target = prepare_task_worktree(args.feature_dir, doc, staged_state, task, durable_state=state)
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
        for key in ('base_commit', 'base_kind'):
            if key in staged_state:
                state[key] = staged_state[key]
        state.pop('pending_base_commit', None)
        state.pop('pending_base_kind', None)
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
    assert_repository_verification_drained(args.feature_dir)
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

    s = sub.add_parser('materialize-packet-history')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.set_defaults(func=cmd_materialize_packet_history)

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

    s = sub.add_parser('attest-packet-identity')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    for flag in ('expected-repository', 'expected-feature', 'expected-task', 'expected-status',
                 'expected-owner', 'expected-recovery-identity-scheme', 'expected-recovery-identity',
                 'source-scheme', 'source-value', 'target-scheme', 'target-value',
                 'target-revision-scheme', 'target-revision', 'expected-semantic-fingerprint'):
        s.add_argument(f'--{flag}', required=True)
    s.add_argument('--expected-attempts', type=int, required=True)
    s.add_argument('--expected-protocol-version', type=int, required=True)
    s.add_argument('--by', required=True, help='Human operator provenance label; not authenticated by the harness')
    s.add_argument('--reason', required=True)
    s.set_defaults(func=cmd_attest_packet_identity)

    s = sub.add_parser('packet-identity-binding')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--attempt', type=int, required=True)
    s.set_defaults(func=cmd_packet_identity_binding)

    s = sub.add_parser('complete')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--owner', required=True)
    s.add_argument('--evidence', required=True)
    s.set_defaults(func=cmd_complete)

    s = sub.add_parser('complete-repair', help='Publish canonical repaired completion C2 for an authorized repair')
    s.add_argument('feature_dir')
    s.add_argument('task_id')
    s.add_argument('--owner', required=True, help='Must match the active immutable repair claim owner')
    s.add_argument('--evidence', required=True, help='Passing completion evidence bound to the active packet and attempt')
    s.set_defaults(func=cmd_complete_repair)

    s = sub.add_parser('bind-repair-checkpoint',
                       help='Human-attest an immutable checkpoint for a legacy in-flight repair claim')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--attempt', type=int, required=True)
    s.add_argument('--historical-completion-id', required=True)
    s.add_argument('--correction-id', required=True)
    s.add_argument('--authorization-id', required=True)
    s.add_argument('--claim-id', required=True)
    s.add_argument('--owner', required=True)
    s.add_argument('--checkpoint', required=True)
    s.add_argument('--by', required=True, help='Human operator provenance label')
    s.add_argument('--reason', required=True)
    s.set_defaults(func=cmd_bind_repair_checkpoint)

    s = sub.add_parser('correct-completion')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--reason-code', required=True, choices=[COMPLETION_CORRECTION_REASON])
    s.add_argument('--reason', required=True)
    s.add_argument('--evidence', required=True, help='Existing defect evidence file; its content hash is recorded')
    s.add_argument('--by', required=True, help='Operator provenance label')
    s.set_defaults(func=cmd_correct_completion)

    s = sub.add_parser('classify-legacy-completion')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.set_defaults(func=cmd_classify_legacy_completion)

    s = sub.add_parser('bind-legacy-completion-auto')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.set_defaults(func=cmd_bind_legacy_completion_auto)

    s = sub.add_parser('attest-legacy-completion')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--evidence', required=True)
    s.add_argument('--attested-fields', required=True,
                   help='JSON object explicitly attesting checkpoint, repository/task/attempt and packet binding')
    s.add_argument('--sources', required=True,
                   help='JSON array of immutable historical source references with identity and SHA-256')
    s.add_argument('--operator', required=True)
    s.add_argument('--reason', required=True)
    def parse_json_argument(value: str) -> dict[str, Any]:
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError as exc:
            raise argparse.ArgumentTypeError(str(exc)) from exc
        if not isinstance(parsed, dict):
            raise argparse.ArgumentTypeError('expected a JSON object')
        return parsed
    s.set_defaults(func=cmd_attest_legacy_completion)
    # Keep explicit CAS JSON structured at the CLI boundary.
    s.add_argument('--expected-cas', dest='expected', type=parse_json_argument, required=True)

    s = sub.add_parser('authorize-completion-repair')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--correction-id', required=True)
    s.add_argument('--reason', required=True)
    s.add_argument('--by', required=True, help='Operator provenance label')
    s.set_defaults(func=cmd_authorize_completion_repair)

    s = sub.add_parser('claim-completion-repair')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('task_id')
    s.add_argument('--authorization', required=True)
    s.add_argument('--owner', required=True)
    s.set_defaults(func=cmd_claim_completion_repair)

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
    s.add_argument('--expected-feature-generation', type=int,
                   help='Required for failed-task replans; current accepted feature generation')
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

    s = sub.add_parser('reconcile-feature')
    s.add_argument('feature_dir', type=pathlib.Path)
    s.add_argument('--expected-generation', type=int, required=True)
    s.add_argument('--reason', required=True)
    s.add_argument('--by', required=True, help='audit attribution only')
    s.set_defaults(func=cmd_reconcile_feature)

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
