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
import threading
from typing import Any, Iterator

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import trust  # noqa: E402
import verification_contract as vc  # noqa: E402
import state_snapshots  # noqa: E402
import state_recovery  # noqa: E402
import unknown_state_registry  # noqa: E402
from agent_harness import feature as _feature  # noqa: E402
from agent_harness import feature_state as _state  # noqa: E402
from agent_harness import feature_access as _access  # noqa: E402
from agent_harness import lifecycle_checks as _checks  # noqa: E402
from agent_harness import feature_transitions as _transitions  # noqa: E402
from agent_harness import verification_admission as _admission  # noqa: E402
from agent_harness import lifecycle_recovery as _recovery  # noqa: E402
from agent_harness import lifecycle_commands as _commands  # noqa: E402
from agent_harness import lifecycle_cli as _cli  # noqa: E402

STATE_DIR = pathlib.Path('.agent-state')
WORKTREE_ROOT_SUFFIX = '-agent-worktrees'
VALID_ROLES = _feature.VALID_ROLES
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
FEATURE_ID_RE = _feature.FEATURE_ID_RE
TASK_ID_RE = _feature.TASK_ID_RE
PROFILE_RE = _feature.PROFILE_RE
AC_RE = _feature.AC_RE
DEFAULT_LEASE_TTL_SECONDS = _feature.DEFAULT_LEASE_TTL_SECONDS
DEFAULT_HEARTBEAT_INTERVAL_SECONDS = _feature.DEFAULT_HEARTBEAT_INTERVAL_SECONDS
VALID_TEST_POLICIES = _feature.VALID_TEST_POLICIES
VALID_TEST_MODES = _feature.VALID_TEST_MODES
# Bump only when persisted lifecycle state semantics/schema become incompatible.
# Implementation and documentation changes are recorded separately for audit.
STATE_SCHEMA_VERSION = _feature.STATE_SCHEMA_VERSION

VERIFICATION_BLOCKAGE_OUTCOMES = frozenset({
    'busy', 'stale-input', 'environment-blocked', 'needs-human',
    'verification-blocked', 'verification-owned', 'abandoned',
})
COMPLETION_EVIDENCE_SCHEMA_VERSION = 1
COMPLETION_AUTHORITY_SCHEMA_VERSION = _access.COMPLETION_AUTHORITY_SCHEMA_VERSION
LEGACY_COMPLETION_BINDING_VERSION = _access.LEGACY_COMPLETION_BINDING_VERSION
COMPLETION_CORRECTION_REASON = _access.COMPLETION_CORRECTION_REASON
_completion_fault_injector = None
_state_snapshot_fault_injector = None
_authority_transaction = _access._authority_transaction

# Packet identity values occupy distinct namespaces. Recovery v1 persisted the
# embedded checksum, while M1 revisions hash the complete normalized packet
# (including that checksum). Never infer either scheme from an unversioned hash.
PACKET_PAYLOAD_IDENTITY_SCHEME = _checks.PACKET_PAYLOAD_IDENTITY_SCHEME
PACKET_REVISION_IDENTITY_SCHEME = _checks.PACKET_REVISION_IDENTITY_SCHEME
RECOVERY_PACKET_IDENTITY_SCHEMES = _checks.RECOVERY_PACKET_IDENTITY_SCHEMES
RECOVERY_RECORD_IDENTITY_SCHEME = 'canonical-recovery-record-sha256-v1'
BRIDGE_SCHEMA_VERSION = 1
BRIDGE_CLASSIFICATIONS = {'AUTOMATICALLY_PROVEN', 'HUMAN_ATTESTED', 'AMBIGUOUS'}


PacketIdentity = _checks.PacketIdentity



def _policy() -> _feature.FeaturePolicy:
    """AH5-14a: Showcase's policy for the library's feature documents and state, built per call so tests that
    patch this module's names (STATE_DIR, feature_repo_base, protocol_files, reviewers, ...) reach the library."""
    return _feature.FeaturePolicy(
        repo_base=lambda path: feature_repo_base(path),
        is_active=lambda path: is_active_feature_dir(path),
        protocol_files=lambda path: protocol_files(path),
        profile_path=lambda root, profile: root / 'docs' / 'agentic-sdd' / 'agents' / f'{profile}.md',
        reviewers=lambda task: reviewers(task),
        validate_contract=lambda path: vc.validate_contract(path),
        criterion_ids=lambda path: vc.criterion_ids(path),
        state_dir=STATE_DIR,
        legacy_feature_path=lambda root, name: root / 'docs' / 'specs' / name,
    )



def _valid_incident_record(record: Any, feature: str) -> bool:
    """The record shape `state_recovery.load_incident` accepts; non-emptiness alone is not validity."""
    return (type(record) is dict and record.get('schema_version') == 1 and record.get('feature') == feature and
            record.get('status') == 'observed' and record.get('state_present') is False and
            record.get('state_mutations_during_investigation') is False and record.get('cause') == 'unknown')


def checked_state_recovery_incident(feature_dir: pathlib.Path) -> dict[str, Any] | None:
    """The incident lookup the library's recovery check uses, fail-closed (master, 2026-10-24).

    `None` means no incident; a valid incident record goes on to the recovery-epoch check. Any other result (False,
    an empty or partial record, a string, another type) refuses, and the lookup's own refusals stay as they are.
    """
    incident = state_recovery_incident(feature_dir)
    if incident is None:
        return None
    if not _valid_incident_record(incident, feature_dir.name):
        die('STATE_RECOVERY_INCIDENT_INVALID: the incident lookup returned neither None nor a valid incident record')
    return incident


def _state_access() -> _access.StateAccess:
    """AH5-14b-1: Showcase's bindings for the library's state access, built per call from this module's names, so
    tests that patch them (fcntl, save_state, load_validated, _completion_fault_injector, ...) reach the library."""
    import verification.store as verification_store
    return _access.StateAccess(
        policy=_policy(),
        common_dir=lambda path: git_common_dir(path),
        locking=fcntl,
        fingerprint=lambda path: feature_fingerprint(path),
        protocol_fingerprint=lambda path: protocol_fingerprint(path),
        save_state=lambda *args, **kwargs: save_state(*args, **kwargs),
        load_validated=lambda path: load_validated(path),
        read_state_pure=lambda path, doc: _read_state_unlocked_pure(path, doc),
        verification_store=lambda: verification_store,
        completion_fault_injector=lambda: _completion_fault_injector,
        recovery_incident=lambda path: checked_state_recovery_incident(path),
        recovery_epoch_required=lambda path, incident, status: state_recovery_epoch_required(path, incident, status),
    )



def _verification_authority() -> Any:
    """Showcase's `verification.authority`, for the library's transitions (looked up per call)."""
    import verification.authority as module
    return module


def _verification_supervisor() -> Any:
    """Showcase's `verification.supervisor`, for the library's admission (looked up per call)."""
    import verification.supervisor as module
    return module


def _verification_store() -> Any:
    """Showcase's `verification.store`, for the library's transitions (looked up per call)."""
    import verification.store as module
    return module


canonical_packet_payload_sha256 = _checks.canonical_packet_payload_sha256


packet_identity_from_embedded_checksum = _checks.packet_identity_from_embedded_checksum


packet_identity_from_revision = _checks.packet_identity_from_revision


packet_identity_from_recovery = _checks.packet_identity_from_recovery


recovery_proves_packet_binding = _checks.recovery_proves_packet_binding


canonical_json_sha256 = _access.canonical_json_sha256


recovery_record_identity = _checks.recovery_record_identity


try:  # Unix/macOS/Linux - the primary targets for this repository.
    import fcntl  # type: ignore
except ImportError:  # pragma: no cover - lifecycle authority requires cross-process locking.
    fcntl = None


die = _feature.die


load_json = _feature.load_json


feature_files = _feature.feature_files


task_index = _feature.task_index


repo_root = _feature.repo_root


sha256_bytes = _feature.sha256_bytes


feature_fingerprint = _feature.feature_fingerprint


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
    return _feature.protocol_fingerprint(feature_dir, policy=_policy())


protocol_version = _feature.protocol_version


safe_relative_pattern = _feature.safe_relative_pattern


builder_pattern_has_concrete_root = _feature.builder_pattern_has_concrete_root


pattern_static_prefix = _feature.pattern_static_prefix


patterns_may_overlap = _feature.patterns_may_overlap


transitive_dependencies = _feature.transitive_dependencies


def is_active_feature_dir(feature_dir: pathlib.Path) -> bool:
    root = feature_repo_base(feature_dir)
    try:
        rel = feature_dir.resolve().relative_to(root)
    except ValueError:
        return False
    return len(rel.parts) == 3 and rel.parts[:2] == ('docs', 'specs')


def design_gate_errors(feature_dir: pathlib.Path, spec: pathlib.Path, plan: pathlib.Path) -> list[str]:
    return _feature.design_gate_errors(feature_dir, spec, plan, policy=_policy())


master_implementation_closure_errors = _feature.master_implementation_closure_errors


def validate(feature_dir: pathlib.Path) -> list[str]:
    return _feature.validate(feature_dir, policy=_policy())


def state_key(feature_dir: pathlib.Path) -> str:
    return _state.state_key(feature_dir, policy=_policy(), common_dir=lambda path: git_common_dir(path))


def git_common_dir(feature_dir: pathlib.Path) -> pathlib.Path:
    """Return Git's canonical common directory for this checkout/worktree."""
    return _state.git_common_dir(feature_dir, policy=_policy())


def unresolved_state_marker(feature_dir: pathlib.Path) -> bool:
    """Check the protected host registry only for features in this Harness repository."""
    try:
        active_common = state_snapshots.common_dir(REPO)
        feature_common = state_snapshots.common_dir(feature_repo_base(feature_dir))
        if active_common != feature_common:
            return False
        return unknown_state_registry.is_unresolved(REPO, feature_dir.name)
    except unknown_state_registry.RegistryError as exc:
        die(f'STATE_RECOVERY_REGISTRY_INVALID: {exc}')
    except state_snapshots.SnapshotError as exc:
        if feature_dir.name in unknown_state_registry.UNKNOWN_STATE_FEATURES:
            die(f'STATE_RECOVERY_REGISTRY_INVALID: repository identity unavailable ({exc})')
        return False


def refuse_unresolved_state_mutation(feature_dir: pathlib.Path) -> None:
    if unresolved_state_marker(feature_dir):
        die(f'STATE_RECOVERY_REQUIRED: {feature_dir.name} has unresolved lifecycle history; '
            'mutating lifecycle operations are disabled pending a separate recovery decision')


def runtime_state_dir(feature_dir: pathlib.Path) -> pathlib.Path:
    return _state.runtime_state_dir(feature_dir, policy=_policy(), common_dir=lambda path: git_common_dir(path))


def state_path(feature_dir: pathlib.Path) -> pathlib.Path:
    return _state.state_path(feature_dir, policy=_policy(), common_dir=lambda path: git_common_dir(path))


def lock_path(feature_dir: pathlib.Path) -> pathlib.Path:
    return _state.lock_path(feature_dir, policy=_policy(), locking=fcntl)


def recover_prepared_state_locked(feature_dir: pathlib.Path) -> None:
    """Finish an interrupted write-ahead snapshot before reading lifecycle state (the library's).

    The caller must hold the repository-wide lifecycle lock.
    """
    refuse_unresolved_state_mutation(feature_dir)
    _access.recover_prepared_state_locked(feature_dir, access=_state_access())


def lifecycle_state_lock(feature_dir: pathlib.Path, *,
                         repository_admission_id: str | None = None,
                         allowed_reservation: tuple[str, str] | None = None,
                         allow_unvalidated_state: bool = False) -> Any:
    """Hold repository verifier admission through the lifecycle mutation (the library's)."""
    refuse_unresolved_state_mutation(feature_dir)
    return _access.lifecycle_state_lock(feature_dir, repository_admission_id=repository_admission_id,
                                        allowed_reservation=allowed_reservation,
                                        allow_unvalidated_state=allow_unvalidated_state, access=_state_access())


def initial_state(feature_dir: pathlib.Path, doc: dict[str, Any], *, project_completions: bool = True) -> dict[str, Any]:
    return _access.initial_state(feature_dir, doc, project_completions=project_completions, access=_state_access())


def load_state(feature_dir: pathlib.Path, doc: dict[str, Any]) -> dict[str, Any]:
    refuse_unresolved_state_mutation(feature_dir)
    return _access.load_state(feature_dir, doc, access=_state_access())


def _resolve_state_unlocked(feature_dir: pathlib.Path, doc: dict[str, Any]) -> tuple[dict[str, Any], bool]:
    """Resolve canonical or exact historical main-checkout state under the shared lock."""
    return _access._resolve_state_unlocked(feature_dir, doc, access=_state_access())


def state_recovery_incident(feature_dir: pathlib.Path) -> dict[str, Any] | None:
    """Resolve the immutable incident record that fences this feature's lost state."""
    if feature_dir.name != state_recovery.INCIDENT_FEATURE:
        return None
    repository = feature_repo_base(feature_dir)
    incident_path = state_recovery.incident_path(repository)
    relative = incident_path.relative_to(repository).as_posix()
    if not os.path.lexists(incident_path):
        tracked = subprocess.run(['git', '-C', str(repository), 'cat-file', '-e', f'HEAD:{relative}'],
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        if tracked.returncode == 0:
            die('STATE_RECOVERY_INCIDENT_INVALID: committed incident record is missing from the working tree')
        return None
    try:
        incident, _raw = state_recovery.load_incident(repository, feature_dir.name)
        return incident
    except state_recovery.RecoveryError as exc:
        die(f'STATE_RECOVERY_INCIDENT_INVALID: {exc}')


def state_snapshot_status(feature_dir: pathlib.Path) -> str:
    return _access.state_snapshot_status(feature_dir, access=_state_access())


def state_recovery_required(feature_dir: pathlib.Path) -> bool:
    if unresolved_state_marker(feature_dir):
        return True
    return _access.state_recovery_required(feature_dir, access=_state_access())


def state_recovery_epoch_required(feature_dir: pathlib.Path, incident: dict[str, Any], snapshot_status: str) -> bool:
    """AH5-14b-1: Showcase's refuse-only recovery-epoch check, verbatim from its state_recovery_required.

    The library calls it only when an incident exists; True (or anything but False, or an error) refuses.
    """
    canonical = state_path(feature_dir)
    if not canonical.is_file() or canonical.is_symlink():
        return True
    if snapshot_status == 'consistent':
        try:
            snapshot_state = json.loads(canonical.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError):
            return True
        # An exact verified current snapshot may restore old acknowledged history.
        # A new epoch additionally keeps checking its immutable recovery receipt.
        if isinstance(snapshot_state, dict) and not isinstance(snapshot_state.get('recovery_epoch'), dict):
            return False
    try:
        state = json.loads(canonical.read_text(encoding='utf-8'))
        record_ref = state.get('recovery_epoch') if isinstance(state, dict) else None
        if not isinstance(record_ref, dict):
            return True
        repository = feature_repo_base(feature_dir)
        incident_value, incident_bytes = state_recovery.load_incident(repository, feature_dir.name)
        verified = state_recovery.verify_evidence_baseline(repository, incident_value)
        record_path = record_ref.get('record_path')
        if (record_ref.get('incident_sha256') != sha256_bytes(incident_bytes) or
                record_ref.get('evidence_baseline_sha256') != verified['baseline_sha256'] or
                record_ref.get('feature_fingerprint') != state.get('fingerprint') or
                record_ref.get('repository_id') != state_snapshots._repo_identity(repository) or
                record_ref.get('epoch_id') != state.get('lifecycle_epoch_id') or
                state.get('max_rework_attempts') != 2 or
                not isinstance(record_ref.get('record_sha256'), str) or
                re.fullmatch(r'[0-9a-f]{64}', record_ref['record_sha256']) is None or
                not isinstance(record_path, str) or
                not record_path.startswith('recovery-epochs/') or
                any(part in {'', '.', '..'} for part in pathlib.PurePosixPath(record_path).parts)):
            return True
        state_root = runtime_state_dir(feature_dir)
        if state_root.is_symlink():
            return True
        immutable_path = state_root.joinpath(*pathlib.PurePosixPath(record_path).parts)
        immutable_path.resolve().relative_to(state_root.resolve())
        if immutable_path.is_symlink() or not immutable_path.is_file():
            return True
        record_bytes = immutable_path.read_bytes()
        if sha256_bytes(record_bytes) != record_ref['record_sha256']:
            return True
        record = json.loads(record_bytes)
        return not (record.get('epoch_id') == record_ref.get('epoch_id') and
                    record.get('incident_id') == incident_value.get('incident_id') and
                    record.get('incident_sha256') == record_ref.get('incident_sha256') and
                    record.get('evidence_baseline_sha256') == record_ref.get('evidence_baseline_sha256') and
                    record.get('repository_id') == record_ref.get('repository_id') and
                    record.get('feature_fingerprint') == state.get('fingerprint') and
                    record.get('max_rework_attempts') == 2 and
                    record.get('imported_completions') == [])
    except (OSError, json.JSONDecodeError, state_recovery.RecoveryError, AttributeError, TypeError, KeyError,
            ValueError):
        return True


def _load_state_unlocked(feature_dir: pathlib.Path, doc: dict[str, Any]) -> dict[str, Any]:
    refuse_unresolved_state_mutation(feature_dir)
    return _access._load_state_unlocked(feature_dir, doc, access=_state_access())


def cmd_recover_state(args: argparse.Namespace) -> None:
    """Create an explicitly requested, empty new lifecycle epoch after state loss.

    Historical task completions and retry grants are deliberately not projected.
    The owner can re-run and independently evaluate work in the new epoch.
    """
    feature_dir = pathlib.Path(args.feature_dir).resolve()
    refuse_unresolved_state_mutation(feature_dir)
    doc = load_validated(feature_dir)
    if (not isinstance(args.epoch_id, str) or not FEATURE_ID_RE.fullmatch(args.epoch_id) or
            not isinstance(args.operator, str) or not args.operator.strip() or
            len(args.operator) > 256 or any(ord(ch) < 0x20 for ch in args.operator) or
            not isinstance(args.expected_feature_fingerprint, str) or
            re.fullmatch(r'[0-9a-f]{64}', args.expected_feature_fingerprint) is None):
        die('STATE_RECOVERY_INVALID: provide a safe epoch ID, operator label, and current feature fingerprint')
    if type(doc.get('max_rework_attempts', 2)) is not int or doc.get('max_rework_attempts', 2) != 2:
        die('STATE_RECOVERY_POLICY_MISMATCH: new epoch requires max_rework_attempts=2')

    with lock_path(feature_dir).open('a+', encoding='utf-8') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        try:
            recover_prepared_state_locked(feature_dir)
            canonical = state_path(feature_dir)
            if os.path.lexists(canonical):
                if not state_recovery_required(feature_dir):
                    try:
                        existing = json.loads(canonical.read_text(encoding='utf-8'))
                        marker = existing.get('recovery_epoch', {})
                        record_path = marker.get('record_path') if isinstance(marker, dict) else None
                        if (not isinstance(record_path, str) or not record_path.startswith('recovery-epochs/') or
                                any(part in {'', '.', '..'} for part in pathlib.PurePosixPath(record_path).parts)):
                            raise ValueError('unsafe recovery record path')
                        record_file = runtime_state_dir(feature_dir).joinpath(
                            *pathlib.PurePosixPath(record_path).parts)
                        existing_record = json.loads(record_file.read_text(encoding='utf-8'))
                    except (OSError, json.JSONDecodeError, KeyError, TypeError, AttributeError):
                        existing_record = None
                        marker = {}
                    except ValueError:
                        existing_record = None
                        marker = {}
                    if (marker.get('epoch_id') == args.epoch_id and isinstance(existing_record, dict) and
                            existing_record.get('operator') == args.operator.strip() and
                            existing_record.get('feature_fingerprint') == args.expected_feature_fingerprint):
                        print(f'ALREADY_RECOVERED {feature_dir.name} epoch={args.epoch_id}')
                        return
                die('STATE_RECOVERY_ALREADY_PRESENT: refusing to overwrite existing authority')
            if state_snapshot_status(feature_dir) == 'state-missing':
                die('STATE_SNAPSHOT_RESTORE_AVAILABLE: restore the exact current snapshot before starting a new epoch')
            if os.path.lexists(main_legacy_state_path(feature_dir)):
                die('STATE_RECOVERY_LEGACY_PRESENT: resolve alternate state explicitly before recovery')
            repository = feature_repo_base(feature_dir)
            try:
                incident, incident_bytes = state_recovery.load_incident(repository, feature_dir.name)
                baseline = state_recovery.verify_evidence_baseline(repository, incident)
            except state_recovery.RecoveryError as exc:
                die(f'STATE_RECOVERY_EVIDENCE_INVALID: {exc}')
            remote_slug = state_snapshots.remote_repository_slug(repository)
            if (not isinstance(incident.get('repository'), str) or not remote_slug or
                    '/'.join(remote_slug.split('/')[-2:]) != incident['repository'].lower()):
                die('STATE_RECOVERY_REPOSITORY_MISMATCH: incident does not bind this Git origin')
            if not state_recovery.observation_commit_exists(repository, incident):
                die('STATE_RECOVERY_OBSERVATION_UNAVAILABLE: incident observation checkpoint is missing')
            current_fingerprint = feature_fingerprint(feature_dir)
            if current_fingerprint != args.expected_feature_fingerprint:
                die('STATE_RECOVERY_FINGERPRINT_MISMATCH: accepted feature changed since operator inspection')

            record_path = f'recovery-epochs/{args.epoch_id}/recovery-record.json'
            state_root = runtime_state_dir(feature_dir)
            if state_root.is_symlink() or (state_root.exists() and not state_root.is_dir()):
                die('STATE_RECOVERY_PATH_UNSAFE: lifecycle state root is not a real directory')
            recovery_record = {
                'schema_version': 1,
                'record_type': 'lifecycle-state-recovery-epoch',
                'feature': doc.get('feature', feature_dir.name),
                'incident_id': incident['incident_id'],
                'incident_sha256': sha256_bytes(incident_bytes),
                'repository_id': state_snapshots._repo_identity(repository),
                'feature_fingerprint': current_fingerprint,
                'evidence_baseline_sha256': baseline['baseline_sha256'],
                'evidence_entries_verified': baseline['entries_verified'],
                'observation_head': incident['observation_head'],
                'epoch_id': args.epoch_id,
                'operator': args.operator.strip(),
                'historical_attempts': 'unavailable',
                'historical_packet_lineage': 'unavailable',
                'historical_retry_grants_imported': False,
                'imported_completions': [],
                'completion_policy': 'all tasks pending; prior evidence remains archival',
                'max_rework_attempts': 2,
            }
            record_parts = pathlib.PurePosixPath(record_path).parts
            parent = state_root
            for part in record_parts[:-1]:
                parent = parent / part
                if parent.is_symlink():
                    die('STATE_RECOVERY_PATH_UNSAFE: recovery record parent is a symlink')
            existing_record_path = state_root.joinpath(*record_parts)
            try:
                existing_record_path.resolve().relative_to(state_root.resolve())
            except ValueError:
                die('STATE_RECOVERY_PATH_UNSAFE: recovery record escapes lifecycle state root')
            if os.path.lexists(existing_record_path):
                if existing_record_path.is_symlink() or not existing_record_path.is_file():
                    die('STATE_RECOVERY_RECORD_CONFLICT: existing epoch record is unsafe')
                existing_bytes = existing_record_path.read_bytes()
                try:
                    existing_record = json.loads(existing_bytes)
                except (UnicodeDecodeError, json.JSONDecodeError):
                    die('STATE_RECOVERY_RECORD_CONFLICT: existing epoch record is invalid')
                if not isinstance(existing_record, dict):
                    die('STATE_RECOVERY_RECORD_CONFLICT: existing epoch record is invalid')
                existing_binding = {key: value for key, value in existing_record.items() if key != 'created_at'}
                if existing_binding != recovery_record:
                    die('STATE_RECOVERY_RECORD_CONFLICT: existing epoch ID has different bound content')
                recovery_record = existing_record
                record_bytes = existing_bytes
            else:
                recovery_record['created_at'] = utc_now().isoformat()
                record_bytes = json.dumps(recovery_record, sort_keys=True, indent=2,
                                          allow_nan=False).encode('utf-8') + b'\n'
            state = initial_state(feature_dir, doc, project_completions=False)
            state.update({
                'lifecycle_epoch_id': args.epoch_id,
                'feature_generation': 1,
                'historical_attempts': 'unavailable',
                'max_rework_attempts': 2,
                'recovery_epoch': {
                    'epoch_id': args.epoch_id,
                    'record_path': record_path,
                    'record_sha256': sha256_bytes(record_bytes),
                    'incident_sha256': recovery_record['incident_sha256'],
                    'evidence_baseline_sha256': baseline['baseline_sha256'],
                    'repository_id': recovery_record['repository_id'],
                    'feature_fingerprint': current_fingerprint,
                },
            })
            for entry in state['tasks'].values():
                entry['historical_attempts'] = 'unavailable'
            save_state(feature_dir, state, additional_files={record_path: (record_bytes, 0o600)})
            try:
                state_recovery.verify_evidence_baseline(repository, incident)
            except state_recovery.RecoveryError as exc:
                die(f'STATE_RECOVERY_POSTCHECK_FAILED: {exc}')
            if state_recovery_required(feature_dir):
                die('STATE_RECOVERY_POSTCHECK_FAILED: recovery receipt or evidence baseline did not verify')
            print(f'RECOVERED {feature_dir.name} epoch={args.epoch_id} imported_completions=0 retry_budget=2')
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def cmd_restore_state(args: argparse.Namespace) -> None:
    """Restore only the exact, verified current Git snapshot into a missing state root."""
    refuse_unresolved_state_mutation(pathlib.Path(args.feature_dir).resolve())
    return _recovery.cmd_restore_state(args, ns=_NS)


def cmd_discard_prepared_snapshot(args: argparse.Namespace) -> None:
    """Audited owner action for abandoning an unacknowledged prepared write."""
    refuse_unresolved_state_mutation(pathlib.Path(args.feature_dir).resolve())
    return _recovery.cmd_discard_prepared_snapshot(args, ns=_NS)


def _read_state_unlocked_pure(feature_dir: pathlib.Path, doc: dict[str, Any]) -> dict[str, Any]:
    """Validate and read authority without adopting, migrating, cleaning, or writing it."""
    refuse_unresolved_state_mutation(feature_dir)
    return _access._read_state_unlocked_pure(feature_dir, doc, access=_state_access())


def validate_state_identity(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any]) -> None:
    _state.validate_state_identity(feature_dir, doc, state, fingerprint=lambda path: feature_fingerprint(path))


def validate_loaded_state(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any]) -> None:
    _state.validate_loaded_state(feature_dir, doc, state, fingerprint=lambda path: feature_fingerprint(path))


def cmd_migrate_state(args: argparse.Namespace) -> None:
    """Validate and explicitly adopt a legacy state under the current semantic version."""
    return _commands.cmd_migrate_state(args, ns=_NS)


def historical_feature_fingerprint(feature_dir: pathlib.Path, commit: str) -> str:
    """Recompute the feature fingerprint from an immutable Git checkpoint."""
    return _transitions.historical_feature_fingerprint(feature_dir, commit, ns=_NS)


def cmd_reconcile_feature(args: argparse.Namespace) -> None:
    """Explicitly supersede a feature fingerprint under .agent-state CAS."""
    return _transitions.cmd_reconcile_feature(args, ns=_NS)


def accept_verification_plan(feature_dir: pathlib.Path, plan_record: dict, *,
                             expected_generation: int) -> dict:
    """Bind a published immutable plan through the sole .agent-state CAS."""
    return _commands.accept_verification_plan(feature_dir, plan_record, expected_generation=expected_generation, ns=_NS)


def resolve_accepted_verification_plan(repository: pathlib.Path, plan_id: str, *,
                                       allowed_task_statuses: set[str] | None = None) -> dict:
    """Resolve subordinate plan bytes and prove current .agent-state acceptance (the library's)."""
    import verification.authority as verification_authority
    return _checks.resolve_accepted_verification_plan(
        repository, plan_id, allowed_task_statuses=allowed_task_statuses, access=_state_access(),
        feature_dir_for=lambda root, name: root / 'docs' / 'specs' / name,
        verification_authority=lambda: verification_authority)


def _append_lifecycle_transition(state: dict[str, Any], feature_dir: pathlib.Path, *,
                                 operation: str, task_id: str, attempt: int, generation: int,
                                 predecessor_ids: list[str], record_refs: list[dict[str, str]],
                                 transition_id: str | None = None) -> str:
    """Append one auditable transition while the canonical state lock is held."""
    return _admission._append_lifecycle_transition(state, feature_dir, operation=operation, task_id=task_id, attempt=attempt, generation=generation, predecessor_ids=predecessor_ids, record_refs=record_refs, transition_id=transition_id, ns=_NS)


def admit_verification_execution(repository: pathlib.Path, plan_id: str, unit_id: str, *,
                                 expected_generation: int, expected_state_revision: int,
                                 repository_admission_id: str | None = None) -> dict:
    """CAS-bind exact immutable admission and launch reservation in one .agent-state write."""
    return _admission.admit_verification_execution(repository, plan_id, unit_id, expected_generation=expected_generation, expected_state_revision=expected_state_revision, repository_admission_id=repository_admission_id, ns=_NS)


def consume_verification_launch(repository: pathlib.Path, plan_id: str, unit_id: str, *,
                                reservation_id: str, expected_generation: int,
                                expected_state_revision: int,
                                repository_admission_id: str) -> dict:
    """Spend one exact launch right by lifecycle CAS and return a process-local capability."""
    return _admission.consume_verification_launch(repository, plan_id, unit_id, reservation_id=reservation_id, expected_generation=expected_generation, expected_state_revision=expected_state_revision, repository_admission_id=repository_admission_id, ns=_NS)


def recover_prelaunch_authority(repository: pathlib.Path, plan_id: str, unit_id: str, *,
                                execution_id: str) -> dict[str, Any]:
    """Recover an exact reserved launch as consumed authority for safe abort.

    The caller must have fenced the prior owner or caught a launch-authorizer
    exception before the supervisor invokes the backend. A reservation without
    consumption is consumed here, then terminalized as a safe prelaunch abort;
    it is never released back into a launchable state.
    """
    return _recovery.recover_prelaunch_authority(repository, plan_id, unit_id, execution_id=execution_id, ns=_NS)


def terminalize_verification_execution(repository: pathlib.Path, terminal: dict, *,
                                        repository_admission_id: str,
                                        safe_abort_proof=None) -> dict:
    """CAS-bind a drained receipt to the exact consumed plan reservation."""
    return _admission.terminalize_verification_execution(repository, terminal, repository_admission_id=repository_admission_id, safe_abort_proof=safe_abort_proof, ns=_NS)



def legacy_state_name(feature_dir: pathlib.Path) -> str:
    return _state.legacy_state_name(feature_dir, policy=_policy(), common_dir=lambda path: git_common_dir(path))


def main_legacy_state_path(feature_dir: pathlib.Path) -> pathlib.Path:
    """Old main-checkout file; the only legacy snapshot safe to adopt automatically."""
    return _state.main_legacy_state_path(feature_dir, policy=_policy(), common_dir=lambda path: git_common_dir(path))


def save_state(feature_dir: pathlib.Path, state: dict[str, Any], *,
               additional_files: dict[str, tuple[bytes, int]] | None = None,
               remove_files: set[str] | None = None) -> None:
    """Write a complete authority snapshot before atomically replacing state (the library's single writer).

    Lifecycle callers hold the shared repository lock for the whole mutation.
    """
    refuse_unresolved_state_mutation(feature_dir)
    _state.save_state(feature_dir, state, additional_files=additional_files, remove_files=remove_files,
                      policy=_policy(), fault_injector=_state_snapshot_fault_injector,
                      common_dir=lambda path: git_common_dir(path))


def completion_authority_dir(feature_dir: pathlib.Path, kind: str, task_id: str) -> pathlib.Path:
    return _access.completion_authority_dir(feature_dir, kind, task_id, access=_state_access())


legacy_completion_binding_id = _access.legacy_completion_binding_id


def legacy_completion_source_id(source: dict[str, Any]) -> str:
    return _commands.legacy_completion_source_id(source, ns=_NS)


def publish_legacy_source(feature_dir: pathlib.Path, task_id: str,
                          source: dict[str, Any]) -> bool:
    return _commands.publish_legacy_source(feature_dir, task_id, source, ns=_NS)


def validate_legacy_binding_shape(binding: Any, feature_dir: pathlib.Path,
                                  task_id: str) -> bool:
    return _access.validate_legacy_binding_shape(binding, feature_dir, task_id, access=_state_access())


def legacy_completion_bindings(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return _access.legacy_completion_bindings(feature_dir, task_id, access=_state_access())


def legacy_binding_as_completion(feature_dir: pathlib.Path, task_id: str,
                                 binding: dict[str, Any]) -> dict[str, Any]:
    return _access.legacy_binding_as_completion(feature_dir, task_id, binding, access=_state_access())


completion_record_id = _access.completion_record_id


REPAIR_CHECKPOINT_BINDING_SCHEME = 'completion-repair-checkpoint-binding-v1'


def repair_checkpoint_binding_id(record: dict[str, Any]) -> str:
    return _commands.repair_checkpoint_binding_id(record, ns=_NS)


def repair_checkpoint_bindings(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return _commands.repair_checkpoint_bindings(feature_dir, task_id, ns=_NS)


def effective_repair_checkpoint(feature_dir: pathlib.Path, task_id: str, claim: dict[str, Any],
                                active: dict[str, Any], historical: dict[str, Any],
                                correction: dict[str, Any], authorization: dict[str, Any]) -> str:
    # Newer immutable repair claims may carry the checkpoint directly.
    return _commands.effective_repair_checkpoint(feature_dir, task_id, claim, active, historical, correction, authorization, ns=_NS)


def _completion_record_semantics(record: dict[str, Any]) -> dict[str, Any]:
    return _transitions._completion_record_semantics(record, ns=_NS)


_valid_completion_hash = _access._valid_completion_hash


_valid_completion_commit = _access._valid_completion_commit


_valid_completion_revision = _access._valid_completion_revision


validate_completion_authority_shape = _access.validate_completion_authority_shape


def publish_completion_authority(path: pathlib.Path, record: dict[str, Any]) -> bool:
    """Durably publish an immutable record. Return False for exact idempotent replay."""
    return _transitions.publish_completion_authority(path, record, ns=_NS)


def read_completion_authority(feature_dir: pathlib.Path, kind: str, task_id: str,
                              *, record_type: str) -> list[dict[str, Any]]:
    return _access.read_completion_authority(feature_dir, kind, task_id, record_type=record_type, access=_state_access())


def _completion_failpoint(boundary: str) -> None:
    return _transitions._completion_failpoint(boundary, ns=_NS)


def completion_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return _access.completion_records(feature_dir, task_id, access=_state_access())


def correction_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return _transitions.correction_records(feature_dir, task_id, ns=_NS)


def repair_authorization_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return _transitions.repair_authorization_records(feature_dir, task_id, ns=_NS)


def repair_claim_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return _transitions.repair_claim_records(feature_dir, task_id, ns=_NS)


def repaired_completion_records(feature_dir: pathlib.Path, task_id: str) -> list[dict[str, Any]]:
    return _access.repaired_completion_records(feature_dir, task_id, access=_state_access())


def correction_is_repaired(feature_dir: pathlib.Path, task_id: str,
                           correction: dict[str, Any]) -> bool:
    return _transitions.correction_is_repaired(feature_dir, task_id, correction, ns=_NS)


def correction_targets_current_completion(feature_dir: pathlib.Path, task_id: str,
                                          entry: dict[str, Any],
                                          correction: dict[str, Any]) -> bool:
    """Return whether lifecycle state still names the completion this correction repairs."""
    return _transitions.correction_targets_current_completion(feature_dir, task_id, entry, correction, ns=_NS)


def retire_finalized_completion_repair_pointers(feature_dir: pathlib.Path, task_id: str,
                                                entry: dict[str, Any], *, retired_by: str) -> None:
    """Move a finalized repair's active pointers into immutable attempt-tagged state history."""
    return _transitions.retire_finalized_completion_repair_pointers(feature_dir, task_id, entry, retired_by=retired_by, ns=_NS)


def task_has_effective_completion(feature_dir: pathlib.Path, task_id: str,
                                  entry: dict[str, Any]) -> bool:
    return _transitions.task_has_effective_completion(feature_dir, task_id, entry, ns=_NS)


def effective_task_status(feature_dir: pathlib.Path, task_id: str,
                          entry: dict[str, Any]) -> str:
    return _commands.effective_task_status(feature_dir, task_id, entry, ns=_NS)


def remove_state_locked(feature_dir: pathlib.Path) -> None:
    refuse_unresolved_state_mutation(feature_dir)
    _access.remove_state_locked(feature_dir, access=_state_access())


fail_on_verification_store_error = _access.fail_on_verification_store_error


def locked_state(feature_dir: pathlib.Path, doc: dict[str, Any], *,
                 admission_guard: bool = True) -> Any:
    refuse_unresolved_state_mutation(feature_dir)
    return _access.locked_state(feature_dir, doc, admission_guard=admission_guard, access=_state_access())



def utc_now() -> dt.datetime:
    return _transitions.utc_now(ns=_NS)


def parse_timestamp(value: Any) -> dt.datetime | None:
    return _transitions.parse_timestamp(value, ns=_NS)


parse_aware_timestamp = _checks.parse_aware_timestamp


def lease_ttl_seconds(doc: dict[str, Any]) -> int:
    return _transitions.lease_ttl_seconds(doc, ns=_NS)


def heartbeat_interval_seconds(doc: dict[str, Any]) -> int:
    return _transitions.heartbeat_interval_seconds(doc, ns=_NS)


def refresh_lease(entry: dict[str, Any], doc: dict[str, Any], *, now: dt.datetime | None = None) -> None:
    return _transitions.refresh_lease(entry, doc, now=now, ns=_NS)


def lease_expired(entry: dict[str, Any], *, now: dt.datetime | None = None) -> bool:
    return _transitions.lease_expired(entry, now=now, ns=_NS)


def heartbeat(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, owner: str) -> str:
    # Lease renewal does not claim/change a worktree and must remain available
    # while the verifier's durable repository reservation is active.
    return _transitions.heartbeat(feature_dir, doc, task_id, owner, ns=_NS)


def recover_stale_leases(feature_dir: pathlib.Path, doc: dict[str, Any], *, reason: str = 'lease expired') -> list[str]:
    """Recover expired task leases so a crashed orchestrator can be resumed safely.

    Dirty task worktrees are checkpointed only on that task-local branch as failed-attempt evidence.
    The checkpoint is never exposed to dependents because the task remains failed until re-executed
    and completed successfully.
    """
    return _recovery.recover_stale_leases(feature_dir, doc, reason=reason, ns=_NS)


def assert_repository_verification_drained(feature_dir: pathlib.Path) -> None:
    """Refuse worktree lifecycle mutations while verifier ownership is unresolved."""
    return _admission.assert_repository_verification_drained(feature_dir, ns=_NS)


is_unrecovered_partial_claim = _checks.is_unrecovered_partial_claim


def ready_ids(doc: dict[str, Any], state: dict[str, Any], feature_dir: pathlib.Path | None = None) -> list[str]:
    return _transitions.ready_ids(doc, state, feature_dir, ns=_NS)


def cmd_resume_verification(args: argparse.Namespace) -> None:
    """Durably schedule exact-plan verification again without a new builder attempt."""
    return _recovery.cmd_resume_verification(args, ns=_NS)


def cmd_invalidate_verification_resume(args: argparse.Namespace) -> None:
    return _transitions.cmd_invalidate_verification_resume(args, ns=_NS)


def cmd_recover_verification(args: argparse.Namespace) -> None:
    """Recover verifier journals and clear only proven-dead unstarted admissions."""
    return _recovery.cmd_recover_verification(args, ns=_NS)


validate_verification_resume = _checks.validate_verification_resume


validate_active_verification_resume = _checks.validate_active_verification_resume


validate_pending_verification_resume = _checks.validate_pending_verification_resume


def prior_builder_result_digest(feature_dir: pathlib.Path, entry: dict[str, Any], task_id: str,
                                evidence_path: str | None = None) -> str:
    """Resolve and hash the exact passing builder result for a verification resume."""
    return _transitions.prior_builder_result_digest(feature_dir, entry, task_id, evidence_path, ns=_NS)


def trusted_task_history(repository: pathlib.Path, feature_id: str, *,
                         _lifecycle_state: dict | None = None) -> dict:
    """Project trusted task/attempt metrics from the validated DAG and lifecycle authorities."""
    return _commands.trusted_task_history(repository, feature_id, _lifecycle_state=_lifecycle_state, ns=_NS)


def resolve_manual_review_scope(repository: pathlib.Path, feature_id: str, *, role: str,
                                task_id: str | None = None, task_attempt: int | None = None,
                                checkpoint: str | None = None,
                                _lifecycle_state: dict | None = None) -> dict:
    """Resolve manual evidence scope from trusted lifecycle history; never trust caller metrics."""
    return _commands.resolve_manual_review_scope(repository, feature_id, role=role, task_id=task_id, task_attempt=task_attempt, checkpoint=checkpoint, _lifecycle_state=_lifecycle_state, ns=_NS)


def register_manual_observation(repository: pathlib.Path, feature_id: str, *, observation_id: str,
                                role: str, task_id: str | None = None,
                                task_attempt: int | None = None, checkpoint: str | None = None,
                                attestation_sha256: str, report_sha256: str,
                                expected_feature_generation: int) -> dict[str, Any]:
    """Record immutable manual provenance under the lifecycle lock.

    T-005's telemetry `record-manual` boundary must validate the signed
    attestation against the accepted issuer registry before calling this API.
    This lifecycle API stores only its digest and re-resolves trusted scope; it
    does not authenticate the signer, accept metrics, or grant obligation coverage.
    """
    return _commands.register_manual_observation(repository, feature_id, observation_id=observation_id, role=role, task_id=task_id, task_attempt=task_attempt, checkpoint=checkpoint, attestation_sha256=attestation_sha256, report_sha256=report_sha256, expected_feature_generation=expected_feature_generation, ns=_NS)


def reviewers(task: dict[str, Any]) -> list[str]:
    selected: list[str] = []
    for risk in task.get('risk_tags', []):
        for reviewer in RISK_TO_REVIEWERS.get(risk, ()):
            if reviewer not in selected:
                selected.append(reviewer)
    return selected


def packet_payload(doc: dict[str, Any], task: dict[str, Any], feature_dir: pathlib.Path) -> dict[str, Any]:
    return _transitions.packet_payload(doc, task, feature_dir, ns=_NS)


def write_packet(doc: dict[str, Any], task: dict[str, Any], feature_dir: pathlib.Path,
                 *, state: dict[str, Any] | None = None) -> pathlib.Path:
    return _transitions.write_packet(doc, task, feature_dir, state=state, ns=_NS)


def legacy_packet_path(feature_dir: pathlib.Path, task_id: str) -> pathlib.Path:
    return _transitions.legacy_packet_path(feature_dir, task_id, ns=_NS)


safe_task_id = _access.safe_task_id


packet_revision_id = _checks.packet_revision_id


def legacy_revision_id(feature_dir: pathlib.Path, task_id: str) -> str:
    return _transitions.legacy_revision_id(feature_dir, task_id, ns=_NS)


def validate_packet_integrity(packet: dict[str, Any]) -> None:
    return _transitions.validate_packet_integrity(packet, ns=_NS)


def revision_path(feature_dir: pathlib.Path, task_id: str, revision_id: str) -> pathlib.Path:
    return _transitions.revision_path(feature_dir, task_id, revision_id, ns=_NS)


def resolve_active_packet(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str,
                          *, state: dict[str, Any] | None = None) -> dict[str, Any]:
    """Resolve one validated active packet; legacy tasks keep their historical path."""
    return _transitions.resolve_active_packet(feature_dir, doc, task_id, state=state, ns=_NS)


def validate_attempt_binding_ledger(entry: dict[str, Any]) -> None:
    return _transitions.validate_attempt_binding_ledger(entry, ns=_NS)


def append_attempt_binding(entry: dict[str, Any], feature_dir: pathlib.Path, doc: dict[str, Any],
                           task_id: str, packet_path: pathlib.Path) -> None:
    return _transitions.append_attempt_binding(entry, feature_dir, doc, task_id, packet_path, ns=_NS)


def active_task_contract(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str,
                         *, state: dict[str, Any] | None = None) -> dict[str, Any]:
    return _transitions.active_task_contract(feature_dir, doc, task_id, state=state, ns=_NS)


def packet_matches_semantic_contract(packet: dict[str, Any], doc: dict[str, Any],
                                     task: dict[str, Any], feature_dir: pathlib.Path) -> bool:
    """Validate packet integrity and execution identity, ignoring generation provenance."""
    return _transitions.packet_matches_semantic_contract(packet, doc, task, feature_dir, ns=_NS)


def validate_evidence(path: pathlib.Path, *, require_pass: bool = False) -> list[str]:
    return _transitions.validate_evidence(path, require_pass=require_pass, ns=_NS)


def required_completion_criteria(task: dict[str, Any]) -> set[str]:
    return _transitions.required_completion_criteria(task, ns=_NS)


def completion_verification_artifact(feature_dir: pathlib.Path, value: Any) -> str:
    return _transitions.completion_verification_artifact(feature_dir, value, ns=_NS)


def validate_completion_proofs(evidence: dict[str, Any], task: dict[str, Any],
                               required: set[str], feature_dir: pathlib.Path) -> list[str]:
    return _transitions.validate_completion_proofs(evidence, task, required, feature_dir, ns=_NS)


def load_validated(feature_dir: pathlib.Path) -> dict[str, Any]:
    return _feature.load_validated(feature_dir, policy=_policy())


def cmd_validate(args: argparse.Namespace) -> None:
    return _commands.cmd_validate(args, ns=_NS)


def cmd_validate_all(args: argparse.Namespace) -> None:
    return _commands.cmd_validate_all(args, ns=_NS)


def cmd_ready(args: argparse.Namespace) -> None:
    return _transitions.cmd_ready(args, ns=_NS)


def cmd_packet(args: argparse.Namespace) -> None:
    return _commands.cmd_packet(args, ns=_NS)


def cmd_reviewers(args: argparse.Namespace) -> None:
    return _commands.cmd_reviewers(args, ns=_NS)


def cmd_claim(args: argparse.Namespace) -> None:
    return _transitions.cmd_claim(args, ns=_NS)


def cmd_recover_claim(args: argparse.Namespace) -> None:
    """Human-attest that an already committed exceptional claim never began execution."""
    return _recovery.cmd_recover_claim(args, ns=_NS)


def owner_guard(entry: dict[str, Any], owner: str) -> None:
    return _transitions.owner_guard(entry, owner, ns=_NS)


def consume_attempt_authorization(entry: dict[str, Any], task_id: str, doc: dict[str, Any], feature_dir: pathlib.Path | None = None, state: dict[str, Any] | None = None) -> str | None:
    """Consume normal retry budget or one explicit human resume grant."""
    return _transitions.consume_attempt_authorization(entry, task_id, doc, feature_dir, state, ns=_NS)


def retry_binding(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, attempts: int,
                  *, state: dict[str, Any] | None = None) -> dict[str, Any]:
    return _transitions.retry_binding(feature_dir, doc, task_id, attempts, state=state, ns=_NS)


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
    return _transitions.packet_bound_semantic_contract_sha256(feature_dir, packet, ns=_NS)


def semantic_task_contract(feature_dir: pathlib.Path, doc: dict[str, Any], task: dict[str, Any], *,
                           feature_sha256: str | None = None,
                           test_policy: str | None = None) -> dict[str, Any]:
    """Canonical execution contract shared by retry authorization and packet identity."""
    return _transitions.semantic_task_contract(feature_dir, doc, task, feature_sha256=feature_sha256, test_policy=test_policy, ns=_NS)


def semantic_task_contract_sha256(feature_dir: pathlib.Path, doc: dict[str, Any], task: dict[str, Any], *,
                                 feature_sha256: str | None = None,
                                 test_policy: str | None = None) -> str:
    return _transitions.semantic_task_contract_sha256(feature_dir, doc, task, feature_sha256=feature_sha256, test_policy=test_policy, ns=_NS)


def classify_retry_authorizations(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any], task_id: str, attempts: int) -> tuple[str, dict[str, Any] | None]:
    return _transitions.classify_retry_authorizations(feature_dir, doc, state, task_id, attempts, ns=_NS)


def matching_retry_authorization(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any], task_id: str, attempts: int) -> dict[str, Any] | None:
    return _commands.matching_retry_authorization(feature_dir, doc, state, task_id, attempts, ns=_NS)


def retry_authorization_status(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any],
                              task_id: str, attempts: int, authorization_id: str) -> str:
    return _commands.retry_authorization_status(feature_dir, doc, state, task_id, attempts, authorization_id, ns=_NS)




def cmd_authorize_retry(args: argparse.Namespace) -> None:
    return _commands.cmd_authorize_retry(args, ns=_NS)


def replan_failpoint(_stage: str) -> None:
    """Test seam for durable transaction failure injection."""
    return _transitions.replan_failpoint(_stage, ns=_NS)


def validate_replan_task(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str,
                         proposed: Any) -> dict[str, Any]:
    return _transitions.validate_replan_task(feature_dir, doc, task_id, proposed, ns=_NS)


def publish_revision(feature_dir: pathlib.Path, task_id: str,
                     packet: dict[str, Any],
                     staged_files: dict[str, tuple[bytes, int]]) -> tuple[str, pathlib.Path]:
    return _transitions.publish_revision(feature_dir, task_id, packet, staged_files, ns=_NS)


def validate_latest_human_resolution(feature_dir: pathlib.Path, doc: dict[str, Any],
                                     task_id: str, entry: dict[str, Any]) -> dict[str, Any]:
    """Require the current failed state to be the exact audited human-resolution transition."""
    return _transitions.validate_latest_human_resolution(feature_dir, doc, task_id, entry, ns=_NS)


def cmd_materialize_packet_history(args: argparse.Namespace) -> None:
    """Publish a validated canonical legacy root into immutable repository authority."""
    return _commands.cmd_materialize_packet_history(args, ns=_NS)


def packet_identity_bridge_path(feature_dir: pathlib.Path, task_id: str, attempt: int) -> pathlib.Path:
    return _transitions.packet_identity_bridge_path(feature_dir, task_id, attempt, ns=_NS)


def bridge_failpoint(_stage: str) -> None:
    """Test seam for immutable bridge publication failure injection."""
    return _commands.bridge_failpoint(_stage, ns=_NS)


def publish_immutable_bridge(feature_dir: pathlib.Path, state: dict[str, Any],
                            path: pathlib.Path, record: dict[str, Any]) -> None:
    """Publish the bridge and unchanged lifecycle state in one repository snapshot."""
    return _commands.publish_immutable_bridge(feature_dir, state, path, record, ns=_NS)


def _bridge_request_identity(*, repository: str, feature: str, task: str, attempt: int,
                             recovery_id: str, source_scheme: str, source_value: str,
                             target_scheme: str, target_value: str, target_revision: str,
                             semantic_fingerprint: str, protocol_version_value: int,
                             operator: str, reason: str) -> dict[str, Any]:
    return _transitions._bridge_request_identity(repository=repository, feature=feature, task=task, attempt=attempt, recovery_id=recovery_id, source_scheme=source_scheme, source_value=source_value, target_scheme=target_scheme, target_value=target_value, target_revision=target_revision, semantic_fingerprint=semantic_fingerprint, protocol_version_value=protocol_version_value, operator=operator, reason=reason, ns=_NS)


def _read_packet_identity_bridge(feature_dir: pathlib.Path, task_id: str, attempt: int,
                                 expected_request: dict[str, Any] | None = None) -> dict[str, Any] | None:
    return _transitions._read_packet_identity_bridge(feature_dir, task_id, attempt, expected_request, ns=_NS)


def classify_historical_packet_binding(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str,
                                        state: dict[str, Any], active: dict[str, Any],
                                        attempt: int) -> dict[str, Any]:
    return _transitions.classify_historical_packet_binding(feature_dir, doc, task_id, state, active, attempt, ns=_NS)


def attest_packet_identity(args: argparse.Namespace) -> tuple[str, dict[str, Any]]:
    return _commands.attest_packet_identity(args, ns=_NS)


def cmd_attest_packet_identity(args: argparse.Namespace) -> None:
    return _commands.cmd_attest_packet_identity(args, ns=_NS)


def cmd_packet_identity_binding(args: argparse.Namespace) -> None:
    return _commands.cmd_packet_identity_binding(args, ns=_NS)


def cmd_replan_task(args: argparse.Namespace) -> None:
    return _transitions.cmd_replan_task(args, ns=_NS)


def restore_attempt_authorization(entry: dict[str, Any]) -> None:
    """Return a consumed human grant when a start is rolled back before provider execution."""
    return _recovery.restore_attempt_authorization(entry, ns=_NS)


def changed_paths(worktree: pathlib.Path) -> list[str]:
    return _transitions.changed_paths(worktree, ns=_NS)


def path_matches(path: str, patterns: list[str]) -> bool:
    return _transitions.path_matches(path, patterns, ns=_NS)


def checkpoint_worktree(doc: dict[str, Any], task: dict[str, Any], target: pathlib.Path, label: str = 'checkpoint') -> str:
    return _transitions.checkpoint_worktree(doc, task, target, label, ns=_NS)


def completion_record_for_entry(feature_dir: pathlib.Path, task_id: str, entry: dict[str, Any],
                                active: dict[str, Any]) -> dict[str, Any]:
    return _commands.completion_record_for_entry(feature_dir, task_id, entry, active, ns=_NS)


def legacy_attempt_identity(feature_dir: pathlib.Path, task_id: str,
                            entry: dict[str, Any]) -> str:
    return _commands.legacy_attempt_identity(feature_dir, task_id, entry, ns=_NS)


def classify_legacy_completion(feature_dir: pathlib.Path, doc: dict[str, Any],
                               task_id: str) -> tuple[str, dict[str, Any] | None, list[str]]:
    """Read-only classifier; AUTO requires two independent immutable source records."""
    return _commands.classify_legacy_completion(feature_dir, doc, task_id, ns=_NS)


def publish_legacy_binding(feature_dir: pathlib.Path, binding: dict[str, Any]) -> bool:
    return _commands.publish_legacy_binding(feature_dir, binding, ns=_NS)


def cmd_classify_legacy_completion(args: argparse.Namespace) -> None:
    return _commands.cmd_classify_legacy_completion(args, ns=_NS)


def cmd_bind_legacy_completion_auto(args: argparse.Namespace) -> None:
    return _commands.cmd_bind_legacy_completion_auto(args, ns=_NS)


def cmd_attest_legacy_completion(args: argparse.Namespace) -> None:
    return _commands.cmd_attest_legacy_completion(args, ns=_NS)


def add_completion_record(feature_dir: pathlib.Path, record: dict[str, Any]) -> None:
    return _transitions.add_completion_record(feature_dir, record, ns=_NS)


def validate_completion_binding(evidence: dict[str, Any], feature_dir: pathlib.Path, task_id: str,
                                entry: dict[str, Any], active: dict[str, Any]) -> list[str]:
    return _transitions.validate_completion_binding(evidence, feature_dir, task_id, entry, active, ns=_NS)


def require_available_completion_origin(feature_dir: pathlib.Path, state: dict[str, Any],
                                        task_id: str, entry: dict[str, Any]) -> None:
    """Require every accepted v2 origin obligation to have a bound PASS terminal."""
    return _transitions.require_available_completion_origin(feature_dir, state, task_id, entry, ns=_NS)


def cmd_complete(args: argparse.Namespace) -> None:
    return _transitions.cmd_complete(args, ns=_NS)


def cmd_complete_repair(args: argparse.Namespace) -> None:
    """Publish canonical C2 for an already authorized repair on the same attempt."""
    return _commands.cmd_complete_repair(args, ns=_NS)


def cmd_bind_repair_checkpoint(args: argparse.Namespace) -> None:
    """Human-attest an immutable checkpoint for an existing legacy repair claim."""
    return _commands.cmd_bind_repair_checkpoint(args, ns=_NS)


def cmd_correct_completion(args: argparse.Namespace) -> None:
    return _commands.cmd_correct_completion(args, ns=_NS)


def cmd_authorize_completion_repair(args: argparse.Namespace) -> None:
    return _commands.cmd_authorize_completion_repair(args, ns=_NS)


def cmd_claim_completion_repair(args: argparse.Namespace) -> None:
    return _commands.cmd_claim_completion_repair(args, ns=_NS)

def cmd_fail(args: argparse.Namespace) -> None:
    return _transitions.cmd_fail(args, ns=_NS)

def cmd_release(args: argparse.Namespace) -> None:
    return _transitions.cmd_release(args, ns=_NS)

def descendants(idx: dict[str, dict[str, Any]], target: str) -> set[str]:
    return _transitions.descendants(idx, target, ns=_NS)


def prune_task_workspace(feature: str, task_id: str, *, expected_branch_commit: str | None = None,
                         guard_branch: bool = False) -> None:
    return _transitions.prune_task_workspace(feature, task_id, expected_branch_commit=expected_branch_commit, guard_branch=guard_branch, ns=_NS)


def ensure_task_workspace_clean(feature: str, task_id: str) -> None:
    return _transitions.ensure_task_workspace_clean(feature, task_id, ns=_NS)


def task_workspace_snapshot(feature: str, task_id: str) -> dict[str, str | bool | None]:
    return _recovery.task_workspace_snapshot(feature, task_id, ns=_NS)


def restore_task_workspace(feature: str, task_id: str, snapshot: dict[str, str | bool | None]) -> None:
    return _recovery.restore_task_workspace(feature, task_id, snapshot, ns=_NS)


def human_resolution_identity(explicit: str | None) -> str:
    return _commands.human_resolution_identity(explicit, ns=_NS)


def audit_reference(value: Any, feature_dir: pathlib.Path) -> str | None:
    return _commands.audit_reference(value, feature_dir, ns=_NS)


def write_human_resolution(
    feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, *, decision: str, decided_by: str,
    prior_entry: dict[str, Any], source: pathlib.Path | None = None,
) -> pathlib.Path:
    return _commands.write_human_resolution(feature_dir, doc, task_id, decision=decision, decided_by=decided_by, prior_entry=prior_entry, source=source, ns=_NS)


def cmd_human_resolve(args: argparse.Namespace) -> None:
    return _commands.cmd_human_resolve(args, ns=_NS)


def reopen_dependency_graph(feature_dir: pathlib.Path, doc: dict[str, Any],
                            state: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Build reopen dependencies without resolving unrelated legacy packets."""
    return _transitions.reopen_dependency_graph(feature_dir, doc, state, ns=_NS)


def cmd_reopen(args: argparse.Namespace) -> None:
    return _transitions.cmd_reopen(args, ns=_NS)


def cmd_status(args: argparse.Namespace) -> None:
    return _commands.cmd_status(args, ns=_NS)


def _cmd_status_locked(args: argparse.Namespace, doc: dict[str, Any]) -> None:
    return _commands._cmd_status_locked(args, doc, ns=_NS)


def cmd_reset(args: argparse.Namespace) -> None:
    return _commands.cmd_reset(args, ns=_NS)


def worktree_path(feature: str, task_id: str) -> pathlib.Path:
    return _transitions.worktree_path(feature, task_id, ns=_NS)


def bootstrap_path_allowed(path: str, feature_rel: pathlib.Path) -> bool:
    return _commands.bootstrap_path_allowed(path, feature_rel, ns=_NS)


def execution_protocol_paths(feature_dir: pathlib.Path) -> list[str]:
    return _transitions.execution_protocol_paths(feature_dir, ns=_NS)


def legacy_execution_snapshot_paths(feature_dir: pathlib.Path) -> list[str]:
    """Keep the pre-task synthetic snapshot scope for design and wayfinder callers."""
    return _commands.legacy_execution_snapshot_paths(feature_dir, ns=_NS)


def _path_is_selected(path: str, selected: list[str]) -> bool:
    return _transitions._path_is_selected(path, selected, ns=_NS)


def current_execution_snapshot(feature_dir: pathlib.Path, doc: dict[str, Any]) -> tuple[str, bool]:
    """Return an immutable snapshot for non-task orchestration callers."""
    return _commands.current_execution_snapshot(feature_dir, doc, ns=_NS)


def task_worktree_snapshot(feature_dir: pathlib.Path) -> str:
    """Return committed HEAD only, rejecting dirty paths that define task freshness."""
    return _transitions.task_worktree_snapshot(feature_dir, ns=_NS)


def stage_first_task_base(feature_dir: pathlib.Path, state: dict[str, Any],
                          durable_state: dict[str, Any], task: dict[str, Any],
                          target: pathlib.Path) -> None:
    """Stage base authority and persist a recovery breadcrumb before worktree creation."""
    return _transitions.stage_first_task_base(feature_dir, state, durable_state, task, target, ns=_NS)


def execution_base(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any]) -> str:
    return _commands.execution_base(feature_dir, doc, state, ns=_NS)


def assert_worktree_protocol_current(feature_dir: pathlib.Path, target: pathlib.Path) -> None:
    return _transitions.assert_worktree_protocol_current(feature_dir, target, ns=_NS)


def task_base_commits(task: dict[str, Any], state: dict[str, Any]) -> list[tuple[str, str]]:
    """Return the immutable commits a task branch must contain."""
    return _transitions.task_base_commits(task, state, ns=_NS)


def require_task_commit_ancestry(feature_dir: pathlib.Path, task: dict[str, Any],
                                 state: dict[str, Any], candidate: str, *,
                                 operation: str) -> None:
    """Fail closed unless a task checkpoint contains its accepted base commits."""
    return _transitions.require_task_commit_ancestry(feature_dir, task, state, candidate, operation=operation, ns=_NS)


def prepare_task_worktree(feature_dir: pathlib.Path, doc: dict[str, Any], state: dict[str, Any],
                          task: dict[str, Any], *,
                          durable_state: dict[str, Any] | None = None) -> pathlib.Path:
    return _transitions.prepare_task_worktree(feature_dir, doc, state, task, durable_state=durable_state, ns=_NS)


def cmd_worktree_create(args: argparse.Namespace) -> None:
    return _commands.cmd_worktree_create(args, ns=_NS)


def cmd_start(args: argparse.Namespace) -> dict[str, Any]:
    return _transitions.cmd_start(args, ns=_NS)

def rollback_unexecuted_start(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, owner: str, reason: str,
                              *, attempt: int, claimed_at: str) -> None:
    """Undo a start that failed before a provider process was launched (`attempt`, `claimed_at`: the start the caller
    made, as `cmd_start` returned it).

    The attempt keeps its number and history, marked rolled back; it does not consume rework budget and only succeeds
    for a clean task worktree with no heartbeat since its start.
    """
    return _transitions.rollback_unexecuted_start(feature_dir, doc, task_id, owner, reason, attempt=attempt,
                                                  claimed_at=claimed_at, ns=_NS)


def _git_ref(repository: pathlib.Path, ref: str) -> str | None:
    return _commands._git_ref(repository, ref, ns=_NS)


def cmd_worktree_remove(args: argparse.Namespace) -> None:
    """Remove a task worktree and its branch, keeping the branch's commits under an archive ref (F1 recovery).

    Two transactions, so the record is durable before anything is removed and a rerun finishes a partial removal:
    1. the ref `refs/attempts/<feature>/<task>/<attempt>/<sha>` is created only if absent (never overwritten), must
       point at the exact tip, and is recorded in the attempt's `archived_attempt_refs`;
    2. the worktree is removed (refused while it has uncommitted changes: the ref keeps commits, not files) and the
       branch deleted with the archived tip as the expected old value.
    """
    return _commands.cmd_worktree_remove(args, ns=_NS)



def cmd_heartbeat(args: argparse.Namespace) -> None:
    return _transitions.cmd_heartbeat(args, ns=_NS)


def cmd_recover_stale(args: argparse.Namespace) -> None:
    return _recovery.cmd_recover_stale(args, ns=_NS)



def cmd_doctor(_: argparse.Namespace) -> None:
    return _cli.cmd_doctor(_, ns=_NS)


def parser() -> argparse.ArgumentParser:
    return _cli.parser(ns=_NS)


# AH5-14b-2: the library's transitions read this module's names through `_NS`; checked once, here, before any
# operation (a missing name or a non-callable recovery binding refuses the import).
_NS = _transitions.bind(_transitions.ModuleNamespace(globals()))
_admission.bind(_NS)
_recovery.bind(_NS)
_commands.bind(_NS)
_cli.bind(_NS)


if __name__ == '__main__':
    _cli.main(ns=_NS)
