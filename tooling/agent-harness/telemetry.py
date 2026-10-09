#!/usr/bin/env python3
"""Structured provenance and usage telemetry for agentic SDD runs.

All data stays in ignored .agent-runs/. No prompt/secret content is copied into provenance.json;
raw provider stdout/stderr remain separate local artifacts.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import math
import os
import pathlib
import re
import stat
import tempfile
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
                  provider_run_id: str | None = None) -> pathlib.Path:
    """Record safe manual provenance only; never changes task lifecycle state."""
    if (not _MANUAL_ID.fullmatch(feature or '') or role not in _MANUAL_ROLES or
            not provider or not _MANUAL_HASH.fullmatch(checkpoint or '') or verdict not in _MANUAL_VERDICTS):
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    from verification.store import StoreError, VerificationStore
    root = pathlib.Path(repo).resolve(strict=True)
    store = VerificationStore(root)
    primary = store.root.parents[2]
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
        # Packet-binding history proves that the attempt existed; only its
        # immutable completion record can additionally bind the reviewed
        # checkpoint to that exact attempt.
        feature_dir = primary / 'docs' / 'specs' / feature
        harness_path = pathlib.Path(__file__).resolve().parent
        if str(harness_path) not in __import__('sys').path:
            __import__('sys').path.insert(0, str(harness_path))
        import harness as lifecycle
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
        if role == 'reviewer' and task_doc and task_doc.get('role') == 'builder':
            compatible_roles.add('reviewer')
        if task_doc is None or role not in compatible_roles:
            raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    elif 'Task' in fields:
        raise ValueError('MANUAL_EVIDENCE_REPORT_INVALID')
    if plan_id is not None:
        raise ValueError('MANUAL_EVIDENCE_PLAN_BINDING_UNAVAILABLE')
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
    relative_safe = resolved.relative_to(primary).as_posix()
    # Manual provenance is subordinate to trusted lifecycle authority. This
    # legacy implementation has no way to submit the signed attestation and
    # exact coverage/checkpoint references to the lifecycle CAS. Never create
    # a local record that a later reader could mistake for accepted coverage.
    harness_path = pathlib.Path(__file__).resolve().parent
    if str(harness_path) not in __import__('sys').path:
        __import__('sys').path.insert(0, str(harness_path))
    import harness as lifecycle
    submit = getattr(lifecycle, 'register_manual_observation', None)
    if not callable(submit):
        raise ValueError('MANUAL_EVIDENCE_AUTHORITY_UNAVAILABLE')
    return submit(repository=primary, feature=feature, role=role,
                  checkpoint=checkpoint, verdict=verdict, report_path=relative_safe,
                  report_sha256=hashlib.sha256(payload).hexdigest(), task=task,
                  task_attempt=task_attempt, plan_id=plan_id)


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
                plan_id=args.plan_id, provider_run_id=args.provider_run_id)
        except (OSError, ValueError, RuntimeError) as exc:
            import sys
            print(json.dumps({'status': 'verification-blocked' if 'UNAVAILABLE' in str(exc) or 'RACE' in str(exc) else 'invalid-policy',
                              'reason_code': str(exc)}, sort_keys=True), file=sys.stderr)
            raise SystemExit(5 if 'UNAVAILABLE' in str(exc) or 'RACE' in str(exc) else 2)
        print(json.dumps({'status': 'recorded', 'record': str(record)}, sort_keys=True))
        return
    repo = getattr(args, 'repo', pathlib.Path.cwd())
    print(json.dumps(summarize(repo.resolve(), getattr(args, 'feature', None),
                               getattr(args, 'orchestration_id', None)), indent=2, sort_keys=True))


if __name__ == '__main__':
    main()
