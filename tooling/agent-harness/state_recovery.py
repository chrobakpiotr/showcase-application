"""Strict read-only validation helpers for the explicit lifecycle recovery path."""
from __future__ import annotations

import hashlib
import json
import pathlib
import re
import subprocess
from typing import Any


class RecoveryError(RuntimeError):
    """Recovery authority did not satisfy its accepted evidence contract."""


def _require_safe_file(repository: pathlib.Path, path: pathlib.Path, error: str) -> None:
    try:
        relative = path.relative_to(repository)
    except ValueError:
        raise RecoveryError(error) from None
    cursor = repository
    for part in relative.parts:
        cursor = cursor / part
        if cursor.is_symlink():
            raise RecoveryError(error)
    if not path.is_file():
        raise RecoveryError(error)


def sha256_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def incident_path(repository: pathlib.Path) -> pathlib.Path:
    return repository / 'docs' / 'specs' / 'AGENT-STATE-RECOVERY-001' / 'evidence' / 'incident-record.json'


def load_incident(repository: pathlib.Path, feature: str) -> tuple[dict[str, Any], bytes]:
    path = incident_path(repository)
    _require_safe_file(repository, path, 'incident-record-unavailable')
    try:
        raw = path.read_bytes()
    except OSError:
        raise RecoveryError('incident-record-unavailable') from None
    relative = path.relative_to(repository).as_posix()
    committed = subprocess.run(['git', '-C', str(repository), 'show', f'HEAD:{relative}'],
                               stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
    status = subprocess.run(['git', '-C', str(repository), 'status', '--porcelain', '--', relative],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
    if committed.returncode != 0 or committed.stdout != raw or status.returncode != 0 or status.stdout:
        raise RecoveryError('incident-record-not-committed-or-modified')
    try:
        incident = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise RecoveryError('incident-record-invalid') from None
    if (not isinstance(incident, dict) or incident.get('schema_version') != 1 or
            incident.get('feature') != feature or incident.get('status') != 'observed' or
            incident.get('state_present') is not False or
            incident.get('state_mutations_during_investigation') is not False or
            incident.get('cause') != 'unknown'):
        raise RecoveryError('incident-record-invalid')
    return incident, raw


def verify_evidence_baseline(repository: pathlib.Path, incident: dict[str, Any]) -> dict[str, Any]:
    relative = incident.get('evidence_baseline_path')
    expected_digest = incident.get('evidence_baseline_sha256')
    if (relative != 'docs/specs/AGENT-STATE-RECOVERY-001/evidence/evidence-baseline.json' or
            not isinstance(expected_digest, str) or re.fullmatch(r'[0-9a-f]{64}', expected_digest) is None):
        raise RecoveryError('evidence-baseline-binding-invalid')
    baseline_path = repository / relative
    _require_safe_file(repository, baseline_path, 'evidence-baseline-unavailable')
    try:
        raw = baseline_path.read_bytes()
    except OSError:
        raise RecoveryError('evidence-baseline-unavailable') from None
    if sha256_bytes(raw) != expected_digest:
        raise RecoveryError('evidence-baseline-digest-mismatch')
    baseline_rel = baseline_path.relative_to(repository).as_posix()
    committed = subprocess.run(['git', '-C', str(repository), 'show', f'HEAD:{baseline_rel}'],
                               stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
    status = subprocess.run(['git', '-C', str(repository), 'status', '--porcelain', '--', baseline_rel],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
    if committed.returncode != 0 or committed.stdout != raw or status.returncode != 0 or status.stdout:
        raise RecoveryError('evidence-baseline-not-committed-or-modified')
    try:
        baseline = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise RecoveryError('evidence-baseline-invalid') from None
    entries = baseline.get('entries') if isinstance(baseline, dict) else None
    if not isinstance(entries, list):
        raise RecoveryError('evidence-baseline-invalid')
    seen: set[str] = set()
    prefix = f"docs/specs/{incident['feature']}/evidence/"
    for entry in entries:
        if not isinstance(entry, dict):
            raise RecoveryError('evidence-baseline-invalid')
        path_value = entry.get('path')
        size = entry.get('bytes')
        digest = entry.get('sha256')
        if (not isinstance(path_value, str) or not path_value.startswith(prefix) or
                pathlib.PurePosixPath(path_value).is_absolute() or
                any(part in {'', '.', '..'} for part in path_value.split('/')) or
                path_value in seen or entry.get('kind') != 'file' or type(size) is not int or size < 0 or
                not isinstance(digest, str) or re.fullmatch(r'[0-9a-f]{64}', digest) is None):
            raise RecoveryError('evidence-baseline-invalid')
        seen.add(path_value)
        target = repository.joinpath(*pathlib.PurePosixPath(path_value).parts)
        cursor = repository
        for part in pathlib.PurePosixPath(path_value).parts:
            cursor = cursor / part
            if cursor.is_symlink():
                raise RecoveryError('evidence-baseline-path-unsafe')
        if not target.is_file():
            raise RecoveryError('evidence-baseline-file-missing')
        try:
            value = target.read_bytes()
        except OSError:
            raise RecoveryError('evidence-baseline-file-unavailable') from None
        if len(value) != size or sha256_bytes(value) != digest:
            raise RecoveryError('evidence-baseline-file-changed')
    return {'baseline': baseline, 'baseline_sha256': expected_digest,
            'entries_verified': len(seen)}


def observation_commit_exists(repository: pathlib.Path, incident: dict[str, Any]) -> bool:
    commit = incident.get('observation_head')
    if not isinstance(commit, str) or re.fullmatch(r'(?:[0-9a-f]{40}|[0-9a-f]{64})', commit) is None:
        return False
    exists = subprocess.run(['git', '-C', str(repository), 'cat-file', '-e', f'{commit}^{{commit}}'],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    ancestor = subprocess.run(['git', '-C', str(repository), 'merge-base', '--is-ancestor', commit, 'HEAD'],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    return exists.returncode == 0 and ancestor.returncode == 0
