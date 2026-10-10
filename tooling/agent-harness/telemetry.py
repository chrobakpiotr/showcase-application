#!/usr/bin/env python3
"""Structured provenance and usage telemetry for agentic SDD runs.

All data stays in ignored .agent-runs/. No prompt/secret content is copied into provenance.json;
raw provider stdout/stderr remain separate local artifacts.
"""
from __future__ import annotations

import datetime as dt
import base64
import hashlib
import json
import math
import os
import pathlib
import re
import stat
import tempfile
import uuid
from typing import Any


_TOKEN_FIELDS = {
    'input_tokens', 'output_tokens', 'total_tokens', 'cached_input_tokens',
    'cache_creation_input_tokens', 'cache_read_input_tokens',
}
_PROVIDERS = {'codex', 'claude', 'manual'}
_STATUSES = {
    'pass', 'fail', 'provider-error', 'harness-error', 'running', 'abandoned',
    'needs-human', 'busy', 'stale-input', 'environment-blocked',
    'verification-blocked', 'verification-owned', 'invalid-policy',
    'invalid-cache', 'retry-policy-violation', 'verification-failed',
}
_SAFE_PROVIDER_ID = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}\Z')
_MAX_SAFE_INTEGER = 2**63 - 1


def _safe_provider_id(value: Any) -> str | None:
    if not isinstance(value, str) or not _SAFE_PROVIDER_ID.fullmatch(value):
        return None
    from verification.serialization import default_safety
    return value if default_safety().safe(value) else None


def _safe_usage(value: Any) -> dict[str, int] | None:
    """Return only bounded numeric token counters from provider-controlled data."""
    if not isinstance(value, dict):
        return None
    clean: dict[str, int] = {}
    for key in _TOKEN_FIELDS:
        amount = value.get(key)
        if type(amount) is int and 0 <= amount <= 2**63 - 1:
            clean[key] = amount
    return clean or None


def _safe_cost(value: Any) -> float | None:
    if type(value) not in (int, float):
        return None
    try:
        amount = float(value)
    except (OverflowError, ValueError):
        return None
    return amount if math.isfinite(amount) and amount >= 0 else None


def _safe_nonnegative_number(value: Any) -> int | float | None:
    """Keep finite nonnegative durations; bound integers before float conversion."""
    if type(value) is int:
        return value if 0 <= value <= _MAX_SAFE_INTEGER else None
    if type(value) is float and math.isfinite(value) and value >= 0:
        return value
    return None


def utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def iso_now() -> str:
    return utc_now().isoformat()


def sha256_file(path: pathlib.Path) -> str | None:
    if not path.exists() or not path.is_file():
        return None
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_write_json(path: pathlib.Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=path.name + '.', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            json.dump(value, handle, indent=2, sort_keys=True)
            handle.write('\n')
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_name, path)
    finally:
        if os.path.exists(tmp_name):
            os.unlink(tmp_name)


def parse_codex_jsonl(stdout: str) -> dict[str, Any]:
    """Extract stable, non-sensitive execution metadata from `codex exec --json` JSONL."""
    thread_id: str | None = None
    usage: dict[str, Any] | None = None
    terminal_type: str | None = None
    event_count = 0
    for raw in stdout.splitlines():
        raw = raw.strip()
        if not raw:
            continue
        try:
            event = json.loads(raw)
        except json.JSONDecodeError:
            continue
        if not isinstance(event, dict):
            continue
        event_count += 1
        typ = event.get('type')
        if typ == 'thread.started':
            thread_id = _safe_provider_id(event.get('thread_id'))
        if typ in {'turn.completed', 'turn.failed'}:
            terminal_type = str(typ)
            if isinstance(event.get('usage'), dict):
                usage = _safe_usage(event['usage'])
    return {
        'thread_id': thread_id,
        'terminal_event': terminal_type,
        'event_count': event_count,
        'usage': usage,
        # Codex subscription/API pricing is not inferred locally from token counts.
        'cost_usd': None,
    }


def parse_claude_envelope(envelope: dict[str, Any]) -> dict[str, Any]:
    usage = _safe_usage(envelope.get('usage'))
    cost = _safe_cost(envelope.get('total_cost_usd'))
    return {
        'session_id': _safe_provider_id(envelope.get('session_id')),
        'duration_ms': _safe_nonnegative_number(envelope.get('duration_ms')),
        'duration_api_ms': _safe_nonnegative_number(envelope.get('duration_api_ms')),
        'num_turns': envelope.get('num_turns') if type(envelope.get('num_turns')) is int and 0 <= envelope['num_turns'] <= _MAX_SAFE_INTEGER else None,
        'usage': usage,
        'cost_usd': cost,
    }


def reconcile_running(
    root: pathlib.Path, feature: str, orchestration_id: str, task_ids: set[str] | None = None,
    *, reason: str = 'orchestration no longer owns this invocation',
) -> list[str]:
    """Mark orphaned `running` provenance records as abandoned.

    This is used only after the orchestrator has deterministic evidence that ownership is gone
    (an expired lease was recovered) or the whole DAG has reached a terminal state. It never
    guesses liveness from wall-clock age alone.
    """
    base = root / '.agent-runs' / feature
    changed: list[str] = []
    if not base.exists():
        return changed
    for path in sorted(base.glob('*/**/provenance.json')):
        try:
            doc = json.loads(path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError):
            continue
        if not isinstance(doc, dict):
            continue
        if doc.get('orchestration_id') != orchestration_id or doc.get('status') != 'running':
            continue
        if task_ids is not None and str(doc.get('task')) not in task_ids:
            continue
        doc['status'] = 'abandoned'
        doc['completed_at'] = iso_now()
        doc['abandon_reason'] = reason
        atomic_write_json(path, doc)
        changed.append(str(path))
    return changed


def summarize(root: pathlib.Path, feature: str | None = None, orchestration_id: str | None = None) -> dict[str, Any]:
    base = root / '.agent-runs'
    if feature:
        base = base / feature
    manifests = list(base.rglob('provenance.json')) if base.exists() else []
    total_cost = 0.0
    matched_runs = 0
    known_cost_runs = 0
    unknown_cost_runs = 0
    known_usage_runs = 0
    unknown_usage_runs = 0
    total_duration_ms = 0
    provider_counts: dict[str, int] = {}
    status_counts: dict[str, int] = {}
    token_totals: dict[str, int] = {}
    for path in manifests:
        try:
            doc = json.loads(path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError):
            continue
        if not isinstance(doc, dict):
            continue
        if orchestration_id is not None and doc.get('orchestration_id') != orchestration_id:
            continue
        matched_runs += 1
        provider_value = doc.get('provider')
        provider = provider_value if isinstance(provider_value, str) and provider_value in _PROVIDERS else 'unknown'
        provider_counts[provider] = provider_counts.get(provider, 0) + 1
        status_value = doc.get('status')
        status = status_value if isinstance(status_value, str) and status_value in _STATUSES else 'unknown'
        status_counts[status] = status_counts.get(status, 0) + 1
        duration = doc.get('duration_ms')
        if type(duration) is int and 0 <= duration <= 2**63 - 1:
            total_duration_ms += duration
        metadata = doc.get('provider_metadata')
        if isinstance(metadata, dict):
            cost = _safe_cost(metadata.get('cost_usd'))
            if cost is not None and math.isfinite(total_cost + cost):
                total_cost += cost
                known_cost_runs += 1
            else:
                unknown_cost_runs += 1
            usage = _safe_usage(metadata.get('usage'))
            if usage:
                known_usage_runs += 1
                for key, value in usage.items():
                    token_totals[key] = token_totals.get(key, 0) + value
            else:
                unknown_usage_runs += 1
        else:
            unknown_cost_runs += 1
            unknown_usage_runs += 1
    return {
        'runs': matched_runs,
        'providers': provider_counts,
        'statuses': status_counts,
        'duration_ms': total_duration_ms,
        'known_cost_runs': known_cost_runs,
        'known_cost_usd': round(total_cost, 6) if known_cost_runs else None,
        'unknown_cost_runs': unknown_cost_runs,
        'known_usage_runs': known_usage_runs,
        'unknown_usage_runs': unknown_usage_runs,
        'tokens': token_totals,
    }


_MANUAL_ROLES = {'reviewer', 'evaluator', 'architecture-reviewer', 'verification-author'}
_MANUAL_VERDICTS = {'PASS', 'FAIL', 'NEEDS-HUMAN'}
_MANUAL_ID = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}\Z')
_MANUAL_HASH = re.compile(r'(?:[0-9a-f]{40}|[0-9a-f]{64})\Z')
_MANUAL_ATTESTATION_FIELDS = {
    'schema_version', 'attestation_type', 'attestation_id', 'issuer_registry_id',
    'issuer_registry_checkpoint', 'issuer_registry_sha256', 'issuer_id',
    'reviewer_principal', 'key_fingerprint', 'signature_algorithm', 'role', 'verdict',
    'report_sha256', 'completed_at', 'repository_id', 'feature_id', 'checkpoint_id',
    'candidate_identity', 'final_surface_identity', 'task_id', 'attempt', 'plan_id',
    'family_id', 'plan_acceptance_transition_id', 'lifecycle_generation', 'obligation_ids',
}


def _unique_json_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
        result[key] = value
    return result


def _manual_registry_context(repository: pathlib.Path) -> tuple[dict[str, Any], str, str]:
    """Load only the committed local trusted issuer registry and bind its provenance."""
    registry_path = repository / 'tooling' / 'agent-harness' / 'human-issuer-registry.json'
    if registry_path.is_symlink() or not registry_path.is_file():
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
    raw = registry_path.read_bytes()
    try:
        committed = __import__('subprocess').check_output(
            ['git', '-C', str(repository), 'show', f'HEAD:{registry_path.relative_to(repository).as_posix()}'],
            stderr=__import__('subprocess').DEVNULL)
        checkpoint = __import__('subprocess').check_output(
            ['git', '-C', str(repository), 'log', '-1', '--format=%H', 'HEAD', '--',
             registry_path.relative_to(repository).as_posix()], text=True,
            stderr=__import__('subprocess').DEVNULL).strip()
    except (OSError, __import__('subprocess').CalledProcessError):
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID') from None
    if not checkpoint or raw != committed:
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
    try:
        registry = json.loads(raw.decode('utf-8'), object_pairs_hook=_unique_json_object)
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID') from None
    if (not isinstance(registry, dict) or type(registry.get('schema_version')) is not int or
            registry['schema_version'] != 1 or not isinstance(registry.get('issuers'), list)):
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
    return registry, checkpoint, 'sha256:' + hashlib.sha256(raw).hexdigest()


def _verify_manual_attestation(payload: bytes, *, repository: pathlib.Path,
                               expected: dict[str, Any]) -> tuple[dict[str, Any], str]:
    """Verify canonical detached Ed25519 proof against trusted registry and scope."""
    if len(payload) > 65536 or payload.startswith(b'\xef\xbb\xbf') or payload.endswith(b'\n'):
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
    try:
        outer = json.loads(payload.decode('utf-8', errors='strict'), object_pairs_hook=_unique_json_object)
        from verification.serialization import canonical_jcs
        if (not isinstance(outer, dict) or set(outer) != {'envelope', 'signature'} or
                canonical_jcs(outer) != payload):
            raise ValueError()
        envelope = outer['envelope']
        signature_text = outer['signature']
        if (not isinstance(envelope, dict) or set(envelope) != _MANUAL_ATTESTATION_FIELDS or
                not isinstance(signature_text, str) or not re.fullmatch(r'[A-Za-z0-9_-]{86}', signature_text)):
            raise ValueError()
        signature = base64.urlsafe_b64decode(signature_text + '==')
        if len(signature) != 64 or base64.urlsafe_b64encode(signature).decode('ascii').rstrip('=') != signature_text:
            raise ValueError()
        registry, registry_checkpoint, registry_sha = _manual_registry_context(repository)
        issuer_id = envelope.get('issuer_id')
        matches = [entry for entry in registry['issuers'] if isinstance(entry, dict) and entry.get('issuer_id') == issuer_id]
        if len(matches) != 1:
            raise ValueError()
        issuer = matches[0]
        actions = issuer.get('actions')
        if (issuer.get('enabled') is not True or issuer.get('revoked') is not False or
                not isinstance(actions, list) or any(not isinstance(action, str) for action in actions) or
                'manual-review' not in actions):
            raise ValueError()
        public_key = base64.b64decode(issuer['public_key_ed25519'], validate=True)
        key_fingerprint = 'sha256:' + hashlib.sha256(public_key).hexdigest()
        principal = issuer.get('reviewer_principal', issuer.get('principal'))
        if (len(public_key) != 32 or issuer.get('key_fingerprint') != key_fingerprint or
                not isinstance(principal, str) or not principal):
            raise ValueError()
        if (type(envelope.get('schema_version')) is not int or envelope.get('schema_version') != 1 or envelope.get('attestation_type') != 'manual-review-attestation-v1' or
                envelope.get('issuer_registry_id') != 'trusted-human-issuer-registry-v1' or
                envelope.get('issuer_registry_checkpoint') != registry_checkpoint or
                envelope.get('issuer_registry_sha256') != registry_sha or envelope.get('issuer_id') != issuer_id or
                envelope.get('reviewer_principal') != principal or envelope.get('key_fingerprint') != key_fingerprint or
                envelope.get('signature_algorithm') != 'Ed25519' or envelope.get('role') not in _MANUAL_ROLES or
                envelope.get('verdict') not in _MANUAL_VERDICTS):
            raise ValueError()
        if envelope != {**expected, 'issuer_registry_checkpoint': registry_checkpoint,
                        'issuer_registry_sha256': registry_sha, 'issuer_id': issuer_id,
                        'reviewer_principal': principal, 'key_fingerprint': key_fingerprint,
                        'signature_algorithm': 'Ed25519', 'attestation_type': 'manual-review-attestation-v1',
                        'issuer_registry_id': 'trusted-human-issuer-registry-v1', 'schema_version': 1,
                        'attestation_id': envelope.get('attestation_id')}:
            raise ValueError()
        parsed_uuid = uuid.UUID(envelope['attestation_id'])
        if str(parsed_uuid) != envelope['attestation_id'] or parsed_uuid.version != 4:
            raise ValueError()
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
        Ed25519PublicKey.from_public_bytes(public_key).verify(signature, canonical_jcs(envelope))
        return envelope, hashlib.sha256(payload).hexdigest()
    except ValueError as exc:
        if str(exc).startswith('MANUAL_EVIDENCE_'):
            raise
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID') from None
    except Exception:
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID') from None


def _read_manual_input(repository: pathlib.Path, relative_name: str, *, limit: int,
                       reason: str) -> tuple[bytes, pathlib.Path]:
    relative = pathlib.PurePosixPath(relative_name)
    if relative.is_absolute() or '..' in relative.parts or not relative.parts:
        raise ValueError(reason)
    candidate = repository.joinpath(*relative.parts)
    if candidate.is_symlink():
        raise ValueError(reason)
    try:
        resolved = candidate.resolve(strict=True)
        store_root = (repository / '.agent-runs').resolve(strict=True)
        resolved.relative_to(store_root)
        fd = os.open(resolved, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
    except (OSError, ValueError):
        raise ValueError(reason) from None
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1 or before.st_size > limit:
            raise ValueError(reason)
        chunks = []
        remaining = limit + 1
        while remaining:
            chunk = os.read(fd, min(65536, remaining))
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        payload = b''.join(chunks)
        after = os.fstat(fd)
        current = resolved.stat(follow_symlinks=False)
        identity = lambda item: (item.st_dev, item.st_ino, item.st_nlink, item.st_mode,
                                 item.st_size, item.st_mtime_ns, item.st_ctime_ns)
        if (len(payload) != before.st_size or identity(before) != identity(after) or
                identity(after) != identity(current)):
            raise ValueError(reason + '_RACE')
        return payload, resolved
    finally:
        os.close(fd)


def _store_manual_snapshot(path: pathlib.Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, 'O_NOFOLLOW', 0), 0o600)
    except FileExistsError:
        if path.is_symlink() or not path.is_file() or path.read_bytes() != payload:
            raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID') from None
        return
    with os.fdopen(fd, 'wb') as handle:
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())
    directory_fd = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory_fd)
    finally:
        os.close(directory_fd)


def _manual_snapshot_relative(repository: pathlib.Path, category: str, digest: str) -> str:
    """Address immutable proof bytes only beneath the excluded verification control root."""
    from verification.store import VerificationStore
    repository = pathlib.Path(repository).resolve(strict=True)
    control_root = VerificationStore(repository).root
    try:
        prefix = control_root.relative_to(repository).as_posix()
    except ValueError:
        raise ValueError('MANUAL_EVIDENCE_AUTHORITY_UNAVAILABLE') from None
    return f'{prefix}/manual-{category}/{digest}.{"json" if category == "attestations" else "md"}'


def verify_manual_observation_for_coverage(repository: pathlib.Path,
                                          observation: dict[str, Any], *,
                                          plan_binding: dict[str, Any] | None = None) -> dict[str, Any]:
    """Re-verify immutable report and signature bytes before any coverage CAS.

    This is deliberately separate from registration: the lifecycle owner must call
    it immediately before its own coverage transaction and still perform that CAS.
    """
    if plan_binding is not None:
        required = {'plan_id', 'family_id', 'plan_acceptance_transition_id',
                    'lifecycle_generation', 'task_id', 'task_attempt',
                    'candidate_identity', 'final_surface_identity', 'obligation_ids',
                    'reviewer_principal'}
        valid = (isinstance(plan_binding, dict) and set(plan_binding) == required and
                 isinstance(plan_binding.get('plan_id'), str) and
                 re.fullmatch(r'verification-plan-v2:sha256:[0-9a-f]{64}', plan_binding['plan_id']) and
                 isinstance(plan_binding.get('family_id'), str) and bool(plan_binding['family_id']) and
                 isinstance(plan_binding.get('plan_acceptance_transition_id'), str) and
                 bool(plan_binding['plan_acceptance_transition_id']) and
                 type(plan_binding.get('lifecycle_generation')) is int and
                 plan_binding['lifecycle_generation'] >= 1 and
                 isinstance(plan_binding.get('task_id'), str) and
                 bool(plan_binding['task_id']) and
                 type(plan_binding.get('task_attempt')) is int and plan_binding['task_attempt'] >= 1 and
                 all(isinstance(plan_binding.get(key), str) and
                     re.fullmatch(r'[0-9a-f]{64}', plan_binding[key])
                     for key in ('candidate_identity', 'final_surface_identity')) and
                 isinstance(plan_binding.get('reviewer_principal'), str) and
                 bool(plan_binding['reviewer_principal']) and
                 isinstance(plan_binding.get('obligation_ids'), list) and
                 bool(plan_binding['obligation_ids']) and
                 all(isinstance(item, str) and
                     re.fullmatch(r'verification-obligation-v2:sha256:[0-9a-f]{64}', item)
                     for item in plan_binding['obligation_ids']) and
                 plan_binding['obligation_ids'] == sorted(set(plan_binding['obligation_ids'])))
        if not valid:
            raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
        if (not isinstance(observation, dict) or observation.get('plan_binding') != plan_binding or
                not isinstance(observation.get('scope'), dict) or
                observation['scope'].get('task_id') != plan_binding['task_id'] or
                observation['scope'].get('task_attempt') != plan_binding['task_attempt']):
            raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
    repository = pathlib.Path(repository).resolve(strict=True)
    if (not isinstance(observation, dict) or observation.get('record_type') != 'manual-observation' or
            type(observation.get('schema_version')) is not int or observation.get('schema_version') != 1 or
            not re.fullmatch(r'sha256:[0-9a-f]{64}', str(observation.get('attestation_sha256', ''))) or
            not re.fullmatch(r'sha256:[0-9a-f]{64}', str(observation.get('report_sha256', '')))):
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
    attestation_digest = observation['attestation_sha256'].removeprefix('sha256:')
    report_digest = observation['report_sha256'].removeprefix('sha256:')
    attestation_bytes, _ = _read_manual_input(
        repository, _manual_snapshot_relative(repository, 'attestations', attestation_digest), limit=65536,
        reason='MANUAL_EVIDENCE_ATTESTATION_INVALID')
    report_bytes, _ = _read_manual_input(
        repository, _manual_snapshot_relative(repository, 'report-snapshots', report_digest), limit=1024 * 1024,
        reason='MANUAL_EVIDENCE_ATTESTATION_INVALID')
    if (hashlib.sha256(attestation_bytes).hexdigest() != attestation_digest or
            hashlib.sha256(report_bytes).hexdigest() != report_digest):
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
    fields = _manual_report_fields(report_bytes)
    try:
        outer = json.loads(attestation_bytes.decode('utf-8'), object_pairs_hook=_unique_json_object)
        envelope = outer['envelope']
        scope = observation.get('scope')
        if not isinstance(scope, dict):
            raise ValueError()
        expected = {
            'attestation_id': envelope.get('attestation_id'), 'role': observation['role'],
            'verdict': fields['Verdict'], 'report_sha256': observation['report_sha256'],
            'completed_at': fields['Completed at'], 'repository_id': observation['repository_id'],
            'feature_id': observation['feature_id'], 'checkpoint_id': fields['Reviewed checkpoint'],
            'candidate_identity': None, 'final_surface_identity': None,
            'task_id': scope.get('task_id'), 'attempt': scope.get('task_attempt'),
            'plan_id': None, 'family_id': None, 'plan_acceptance_transition_id': None,
            'lifecycle_generation': None, 'obligation_ids': [],
        }
        if plan_binding is not None:
            expected.update({
                'candidate_identity': plan_binding['candidate_identity'],
                'final_surface_identity': plan_binding['final_surface_identity'],
                'plan_id': plan_binding['plan_id'],
                'family_id': plan_binding['family_id'],
                'plan_acceptance_transition_id': plan_binding['plan_acceptance_transition_id'],
                'lifecycle_generation': plan_binding['lifecycle_generation'],
                'obligation_ids': plan_binding['obligation_ids'],
            })
        if (fields['Feature'] != observation['feature_id'] or
                (plan_binding is not None and fields.get('Task') != plan_binding['task_id'])):
            raise ValueError()
        _verify_manual_attestation(attestation_bytes, repository=repository, expected=expected)
    except Exception:
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID') from None
    if plan_binding is not None and envelope.get('reviewer_principal') != plan_binding['reviewer_principal']:
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
    return {'attestation_sha256': observation['attestation_sha256'],
            'report_sha256': observation['report_sha256'],
            'reviewer_principal': envelope['reviewer_principal'],
            'completed_at': envelope['completed_at'], 'checkpoint_id': envelope['checkpoint_id'],
            'plan_binding': plan_binding}
def _manual_report_fields(payload: bytes) -> dict[str, str]:
    try:
        text = payload.decode('utf-8', errors='strict')
    except UnicodeDecodeError:
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID') from None
    if len(payload) > 1024 * 1024:
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    # Secret-bearing reports are rejected before hashing; only the safe digest
    # and path may enter durable telemetry.
    from verification.serialization import default_safety
    safety = default_safety()
    if not safety.safe(text):
        raise ValueError('MANUAL_EVIDENCE_REPORT_SECRET')
    fields = {}
    fenced = False
    html_comment = False
    html_block = False
    for line in text.splitlines():
        stripped = line.lstrip()
        if stripped.startswith(('```', '~~~')):
            fenced = not fenced
            continue
        if fenced or stripped.startswith('>') or (line and line[0].isspace()):
            continue
        if html_block:
            if not stripped:
                html_block = False
            continue
        if '<!--' in line:
            html_comment = True
        if html_comment:
            if '-->' in line:
                html_comment = False
            continue
        if stripped.startswith('<') and not re.match(r'^(Feature|Reviewed checkpoint|Verdict|Completed at|Task):', line):
            html_block = True
            continue
        match = re.fullmatch(r'(Feature|Reviewed checkpoint|Completed at|Task): `([^`\r\n]+)`', line)
        verdict_match = re.fullmatch(r'Verdict: \*\*(PASS|FAIL|NEEDS-HUMAN)\*\*', line)
        if match or verdict_match:
            name, value = (match.groups() if match else ('Verdict', verdict_match.group(1)))
            if name in fields:
                raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
            fields[name] = value
    if not {'Feature', 'Reviewed checkpoint', 'Verdict', 'Completed at'} <= set(fields):
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    if fields['Verdict'] not in _MANUAL_VERDICTS:
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    if not re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z', fields['Completed at']):
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    try:
        completed = dt.datetime.fromisoformat(fields['Completed at'].replace('Z', '+00:00'))
    except ValueError:
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID') from None
    if completed > utc_now().replace(microsecond=0):
        raise ValueError('MANUAL_EVIDENCE_CHRONOLOGY_INVALID')
    return fields


def record_manual(*, repo: pathlib.Path, feature: str, role: str, provider: str,
                  checkpoint: str, verdict: str, report: str, task: str | None = None,
                  task_attempt: str | None = None, plan_id: str | None = None,
                  provider_run_id: str | None = None, attestation: str | None = None) -> pathlib.Path:
    """Record safe manual provenance only; never changes task lifecycle state."""
    if (not _MANUAL_ID.fullmatch(feature or '') or role not in _MANUAL_ROLES or
            not provider or not _MANUAL_HASH.fullmatch(checkpoint or '') or verdict not in _MANUAL_VERDICTS):
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    from verification.store import StoreError, VerificationStore
    root = pathlib.Path(repo).resolve(strict=True)
    store = VerificationStore(root)
    primary = store.root.parents[2]
    # Check registry commit binding before other checkpoint checks can mask a
    # locally modified trust source as an ordinary dirty-worktree rejection.
    if attestation:
        _manual_registry_context(primary)
    task_index_path = primary / 'docs' / 'specs' / feature / 'tasks.json'
    if not task_index_path.is_file():
        raise ValueError('MANUAL_EVIDENCE_FEATURE_UNRESOLVED')
    manual_root = store.root / 'manual-reports'
    manual_root.mkdir(parents=True, exist_ok=True)
    relative = pathlib.PurePosixPath(report)
    if relative.is_absolute() or '..' in relative.parts or not relative.parts:
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    path = (primary / pathlib.Path(*relative.parts))
    if path.is_symlink():
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    try:
        resolved = path.resolve(strict=True)
        resolved.relative_to(manual_root.resolve(strict=True))
    except (OSError, ValueError):
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID') from None
    if resolved.is_symlink():
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    fd = os.open(resolved, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1 or before.st_size > 1024 * 1024:
            raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
        chunks = []
        remaining = 1024 * 1024 + 1
        while remaining:
            chunk = os.read(fd, min(65536, remaining))
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        payload = b''.join(chunks)
        after = os.fstat(fd)
        path_after = path.stat(follow_symlinks=False)
        identity = lambda item: (item.st_dev, item.st_ino, item.st_nlink, item.st_mode,
                                 item.st_size, item.st_mtime_ns, item.st_ctime_ns)
        if len(payload) != before.st_size or identity(before) != identity(after) or identity(after) != identity(path_after):
            raise ValueError('MANUAL_EVIDENCE_REPORT_SNAPSHOT_RACE')
    finally:
        os.close(fd)
    fields = _manual_report_fields(payload)
    if fields['Feature'] != feature or fields['Reviewed checkpoint'] != checkpoint or fields['Verdict'] != verdict:
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    if task_attempt is not None:
        if not task_attempt.isdigit() or int(task_attempt) < 1:
            raise ValueError('MANUAL_EVIDENCE_REPORT_ATTEMPT_UNRESOLVED')
        # The durable attempt binding is created by the lifecycle claim CAS.
        state_candidates = list((primary / '.agent-state').glob(f'{feature}-*.json'))
        resolved_attempts = []
        for state_path in state_candidates:
            try:
                state = json.loads(state_path.read_text(encoding='utf-8'))
            except (OSError, json.JSONDecodeError):
                continue
            for task_id, entry in state.get('tasks', {}).items():
                if task is not None and task_id != task:
                    continue
                if any(item.get('attempt') == int(task_attempt) for item in entry.get('attempt_bindings', [])):
                    resolved_attempts.append(task_id)
        if len(resolved_attempts) != 1:
            raise ValueError('MANUAL_EVIDENCE_REPORT_ATTEMPT_UNRESOLVED')
        if task is not None and task != resolved_attempts[0]:
            raise ValueError('MANUAL_EVIDENCE_REPORT_ATTEMPT_UNRESOLVED')
        task = resolved_attempts[0]
        feature_dir = primary / 'docs' / 'specs' / feature
        harness_path = pathlib.Path(__file__).resolve().parent
        if str(harness_path) not in __import__('sys').path:
            __import__('sys').path.insert(0, str(harness_path))
        import harness as lifecycle
        if plan_id is not None:
            state_candidates = list((primary / '.agent-state').glob(f'{feature}-*.json'))
            active_matches = []
            for state_path in state_candidates:
                try:
                    state = json.loads(state_path.read_text(encoding='utf-8'))
                except (OSError, json.JSONDecodeError):
                    continue
                entry = state.get('tasks', {}).get(task)
                bindings = entry.get('attempt_bindings', []) if isinstance(entry, dict) else []
                if (isinstance(entry, dict) and entry.get('status') == 'running' and
                        entry.get('attempts') == int(task_attempt) and
                        any(isinstance(item, dict) and item.get('attempt') == int(task_attempt) and
                            item.get('binding_status') == 'proven' for item in bindings)):
                    active_matches.append(entry)
            if len(active_matches) != 1:
                raise ValueError('MANUAL_EVIDENCE_REPORT_ATTEMPT_UNRESOLVED')
        else:
            checkpoint_records = [item for item in lifecycle.completion_records(feature_dir, task)
                                  if item.get('attempt') == int(task_attempt) and item.get('checkpoint') == checkpoint]
            if len(checkpoint_records) != 1:
                raise ValueError('MANUAL_EVIDENCE_REPORT_ATTEMPT_UNRESOLVED')
    if task is not None:
        if not _MANUAL_ID.fullmatch(task) or fields.get('Task') != task:
            raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
        tasks = json.loads(task_index_path.read_text(encoding='utf-8'))
        task_doc = next((item for item in tasks.get('tasks', []) if item.get('id') == task), None)
        compatible_roles = {task_doc.get('role'), task_doc.get('agent_profile'), *task_doc.get('required_reviewers', [])} if task_doc else set()
        if task_doc is None or role not in compatible_roles:
            raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    elif 'Task' in fields:
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    head = __import__('subprocess').check_output(['git', '-C', str(primary), 'rev-parse', 'HEAD'],
                                                text=True, stderr=__import__('subprocess').DEVNULL).strip()
    if head != checkpoint:
        raise ValueError('MANUAL_EVIDENCE_CHECKPOINT_UNAVAILABLE')
    dirty = __import__('subprocess').check_output(['git', '-C', str(primary), 'status', '--porcelain', '--untracked-files=all'],
                                                  text=True, stderr=__import__('subprocess').DEVNULL)
    if dirty:
        raise ValueError('MANUAL_EVIDENCE_CHECKPOINT_UNAVAILABLE')
    object_type = __import__('subprocess').check_output(['git', '-C', str(primary), 'cat-file', '-t', checkpoint],
                                                       text=True, stderr=__import__('subprocess').DEVNULL).strip()
    if object_type != 'commit':
        raise ValueError('MANUAL_EVIDENCE_CHECKPOINT_UNAVAILABLE')
    if not attestation:
        raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
    harness_path = pathlib.Path(__file__).resolve().parent
    if str(harness_path) not in __import__('sys').path:
        __import__('sys').path.insert(0, str(harness_path))
    import harness as lifecycle
    from verification.serialization import canonical_jcs
    try:
        attempt_number = int(task_attempt) if task_attempt is not None else None
    except (TypeError, ValueError):
        raise ValueError('MANUAL_EVIDENCE_REPORT_ATTEMPT_UNRESOLVED') from None
    attestation_payload, _ = _read_manual_input(primary, attestation, limit=65536,
                                                reason='MANUAL_EVIDENCE_ATTESTATION_INVALID')
    plan_binding = None
    try:
        from verification.authority import resolve_manual_review_scope
        with lifecycle.lifecycle_state_lock(primary / 'docs' / 'specs' / feature):
            feature_doc = lifecycle.load_validated(primary / 'docs' / 'specs' / feature)
            lifecycle_state = lifecycle._load_state_unlocked(primary / 'docs' / 'specs' / feature, feature_doc)
            expected_generation = lifecycle_state.get('feature_generation', 1)
            trusted_scope = resolve_manual_review_scope(
                primary, feature, role=role, task_id=task, task_attempt=attempt_number,
                checkpoint=checkpoint, allow_active_attempt=plan_id is not None,
                _lifecycle_state=lifecycle_state)
            if plan_id is not None:
                from verification.authority import _load_trusted_profile, validate_plan_record
                from verification.store import VerificationStore
                plan = VerificationStore(primary).load_plan_record(plan_id)
                validate_plan_record(plan, repository=primary, reconstruct=True)
                authority = lifecycle_state.get('verification_authority')
                if (plan.get('plan_id') != plan_id or plan.get('feature_id') != feature or
                        plan.get('task_id') != trusted_scope.get('task_id') or
                        plan.get('task_attempt') != trusted_scope.get('task_attempt') or
                        plan.get('lifecycle_generation') != expected_generation or
                        not isinstance(authority, dict) or authority.get('accepted_plan_id') != plan_id or
                        authority.get('generation') != expected_generation or
                        not isinstance(authority.get('plan_acceptance_transition_id'), str) or
                        not authority.get('plan_acceptance_transition_id')):
                    raise ValueError('MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED')
                profile_root = pathlib.Path(__file__).resolve().parent / 'verification-profiles'
                profile_path = (profile_root / (plan['profile_id'] + '.json')).resolve(strict=True)
                if profile_path.parent != profile_root.resolve(strict=True):
                    raise ValueError('MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED')
                profile = _load_trusted_profile(primary, profile_path)
                try:
                    submitted = json.loads(attestation_payload.decode('utf-8'))['envelope']
                except (UnicodeDecodeError, json.JSONDecodeError, KeyError, TypeError):
                    raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID') from None
                principal = submitted.get('reviewer_principal') if isinstance(submitted, dict) else None
                submitted_ids = submitted.get('obligation_ids') if isinstance(submitted, dict) else None
                gates = {gate.id: gate for gate in profile.gates}
                eligible = {item['obligation_id'] for item in plan.get('obligations', [])
                    if isinstance(item, dict) and item.get('requirement_source') == 'profile' and
                    item.get('required_origin') == 'manual' and item.get('required_manual_reviewer_principal') is None and
                    item.get('profile_gate_id') in gates and
                    gates[item['profile_gate_id']].required_manual_reviewer_principal == principal}
                # Obligation identity intentionally omits the secret-free profile
                # principal projection; derive the accepted relation from the
                # trusted profile, never from the signed caller's assertion.
                if not isinstance(submitted_ids, list) or len(submitted_ids) != 1 or \
                        submitted_ids != sorted(set(submitted_ids)) or not set(submitted_ids) <= eligible:
                    raise ValueError('MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED')
                plan_binding = {
                    'plan_id': plan_id, 'family_id': plan['family']['id'],
                    'plan_acceptance_transition_id': authority['plan_acceptance_transition_id'],
                    'lifecycle_generation': expected_generation,
                    'task_id': plan['task_id'], 'task_attempt': plan['task_attempt'],
                    'candidate_identity': plan['candidate_identity'],
                    'final_surface_identity': plan['final_changed_surface_id'],
                    'obligation_ids': submitted_ids, 'reviewer_principal': principal,
                }
    except Exception as exc:
        code = str(exc)
        raise ValueError(code if code.startswith('MANUAL_EVIDENCE_') else
                         'MANUAL_EVIDENCE_SCOPE_UNAVAILABLE') from None
    repository_id = hashlib.sha256(os.fsencode(lifecycle.git_common_dir(primary / 'docs' / 'specs' / feature))).hexdigest()
    expected_envelope = {
        'attestation_id': None,
        'role': role,
        'verdict': verdict,
        'report_sha256': 'sha256:' + hashlib.sha256(payload).hexdigest(),
        'completed_at': fields['Completed at'],
        'repository_id': repository_id,
        'feature_id': feature,
        'checkpoint_id': checkpoint,
        'candidate_identity': plan_binding['candidate_identity'] if plan_binding else None,
        'final_surface_identity': plan_binding['final_surface_identity'] if plan_binding else None,
        'task_id': trusted_scope.get('task_id'),
        'attempt': trusted_scope.get('task_attempt'),
        'plan_id': plan_binding['plan_id'] if plan_binding else None,
        'family_id': plan_binding['family_id'] if plan_binding else None,
        'plan_acceptance_transition_id': plan_binding['plan_acceptance_transition_id'] if plan_binding else None,
        'lifecycle_generation': plan_binding['lifecycle_generation'] if plan_binding else None,
        'obligation_ids': plan_binding['obligation_ids'] if plan_binding else [],
    }
    signed, attestation_sha = _verify_manual_attestation(
        attestation_payload, repository=primary, expected=expected_envelope)
    # Keep the exact bytes addressable for any later coverage decision. A later
    # coverage consumer must re-run signature and scope validation from these bytes.
    from verification.store import VerificationStore
    runs_root = VerificationStore(primary).root
    attestation_store = runs_root / 'manual-attestations' / (attestation_sha + '.json')
    report_store = runs_root / 'manual-report-snapshots' / (hashlib.sha256(payload).hexdigest() + '.md')
    _store_manual_snapshot(attestation_store, attestation_payload)
    _store_manual_snapshot(report_store, payload)
    semantic_projection = {
        'repository_id': repository_id, 'feature_id': feature, 'role': role,
        'reviewer_principal': signed['reviewer_principal'], 'verdict': verdict,
        'report_sha256': expected_envelope['report_sha256'], 'completed_at': fields['Completed at'],
        'task_id': trusted_scope.get('task_id'), 'attempt': trusted_scope.get('task_attempt'),
        'plan_id': expected_envelope['plan_id'], 'family_id': expected_envelope['family_id'],
        'lifecycle_generation': expected_envelope['lifecycle_generation'],
        'plan_acceptance_transition_id': expected_envelope['plan_acceptance_transition_id'],
        'checkpoint_id': checkpoint,
        'candidate_identity': expected_envelope['candidate_identity'],
        'final_surface_identity': expected_envelope['final_surface_identity'],
        'obligation_ids': expected_envelope['obligation_ids'],
    }
    observation_id = hashlib.sha256(canonical_jcs(semantic_projection)).hexdigest()
    submit = getattr(lifecycle, 'register_manual_observation', None)
    if not callable(submit):
        raise ValueError('MANUAL_EVIDENCE_AUTHORITY_UNAVAILABLE')
    result = submit(repository=primary, feature_id=feature, observation_id=observation_id,
                    role=role, task_id=trusted_scope.get('task_id'),
                    task_attempt=trusted_scope.get('task_attempt'), checkpoint=checkpoint,
                    attestation_sha256='sha256:' + attestation_sha,
                    report_sha256=expected_envelope['report_sha256'],
                    expected_feature_generation=expected_generation,
                    plan_binding=plan_binding)
    return pathlib.Path(result['record_path'])


def main() -> None:
    import argparse
    p = argparse.ArgumentParser(description='Record or summarize local agentic SDD provenance/usage telemetry')
    p.add_argument('--repo', type=pathlib.Path, default=pathlib.Path.cwd())
    p.add_argument('--feature')
    p.add_argument('--orchestration-id')
    sub = p.add_subparsers(dest='command')
    summary = sub.add_parser('summary')
    summary.add_argument('--repo', type=pathlib.Path, default=pathlib.Path.cwd())
    summary.add_argument('--feature')
    summary.add_argument('--orchestration-id')
    manual = sub.add_parser('record-manual')
    manual.add_argument('--repo', type=pathlib.Path, default=pathlib.Path.cwd())
    manual.add_argument('--feature', required=True)
    manual.add_argument('--role', required=True, choices=sorted(_MANUAL_ROLES))
    manual.add_argument('--provider', required=True)
    manual.add_argument('--checkpoint', required=True)
    manual.add_argument('--verdict', required=True, choices=sorted(_MANUAL_VERDICTS))
    manual.add_argument('--report', required=True)
    manual.add_argument('--attestation', required=True)
    manual.add_argument('--task')
    manual.add_argument('--task-attempt')
    manual.add_argument('--plan-id')
    manual.add_argument('--provider-run-id')
    args = p.parse_args()
    if args.command == 'record-manual':
        try:
            record = record_manual(repo=args.repo, feature=args.feature, role=args.role,
                provider=args.provider, checkpoint=args.checkpoint, verdict=args.verdict,
                report=args.report, task=args.task, task_attempt=args.task_attempt,
                plan_id=args.plan_id, provider_run_id=args.provider_run_id,
                attestation=args.attestation)
        except (OSError, ValueError, RuntimeError) as exc:
            import sys
            blocked = ('UNAVAILABLE' in str(exc) or 'RACE' in str(exc) or
                       str(exc) == 'MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED')
            print(json.dumps({'status': 'verification-blocked' if blocked else 'invalid-policy',
                              'reason_code': str(exc)}, sort_keys=True), file=sys.stderr)
            raise SystemExit(5 if blocked else 2)
        print(json.dumps({'status': 'recorded', 'record': str(record)}, sort_keys=True))
        return
    repo = getattr(args, 'repo', pathlib.Path.cwd())
    print(json.dumps(summarize(repo.resolve(), getattr(args, 'feature', None),
                               getattr(args, 'orchestration_id', None)), indent=2, sort_keys=True))


if __name__ == '__main__':
    main()
