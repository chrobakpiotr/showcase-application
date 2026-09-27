"""Canonical verification control-store resolution and immutable publication."""
from __future__ import annotations

import contextlib
import hashlib
import json
import os
import pathlib
import re
import subprocess
import tempfile
import time
from dataclasses import asdict

from .model import Evidence
from .serialization import canonical, evidence_record


class StoreError(RuntimeError):
    """Stable control-store failure category."""


def _git(root: pathlib.Path, *args: str) -> bytes:
    try:
        return subprocess.check_output(['git', '-C', str(root), *args], stderr=subprocess.DEVNULL)
    except (OSError, subprocess.CalledProcessError):
        raise StoreError('repository-unresolved') from None


def _worktrees(common: pathlib.Path) -> list[pathlib.Path]:
    raw = _git(common, 'worktree', 'list', '--porcelain', '-z')
    roots = []
    for field in raw.split(b'\0'):
        if field.startswith(b'worktree '):
            roots.append(pathlib.Path(os.fsdecode(field[9:])).resolve())
    return roots


def resolve_control_root(repository: str | pathlib.Path) -> tuple[pathlib.Path, str]:
    """Resolve primary-worktree control root; never fall back to caller checkout."""
    caller = pathlib.Path(repository).resolve(strict=True)
    common = pathlib.Path(os.fsdecode(_git(caller, 'rev-parse', '--path-format=absolute', '--git-common-dir').strip())).resolve()
    if _git(caller, 'rev-parse', '--is-bare-repository').strip() == b'true':
        raise StoreError('bare-repository-unsupported')
    owners = [worktree for worktree in _worktrees(common)
              if (worktree / '.git').is_dir() and (worktree / '.git').resolve() == common]
    if len(owners) != 1:
        raise StoreError('primary-worktree-ambiguous')
    primary = owners[0]
    identity = hashlib.sha256(str(common).encode('utf-8')).hexdigest()
    return primary / '.agent-runs' / 'control' / 'verification-v2', identity


@contextlib.contextmanager
def repository_lock(control_root: pathlib.Path, timeout: float = 30.0):
    """Portable exclusive lock, held for the complete verification mutation."""
    lock = control_root / 'lock'
    lock.parent.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + timeout
    while True:
        try:
            lock.mkdir()
            (lock / 'owner.json').write_text(json.dumps({'pid': os.getpid(), 'started': time.time()}), encoding='utf-8')
            break
        except FileExistsError:
            if time.monotonic() >= deadline:
                raise StoreError('busy')
            time.sleep(0.05)
    try:
        yield
    finally:
        # A stale lock is deliberately not auto-recovered from PID/age alone.
        try:
            (lock / 'owner.json').unlink()
            lock.rmdir()
        except OSError:
            raise StoreError('lock-release-uncertain') from None


def _fsync_directory(path: pathlib.Path) -> None:
    fd = os.open(path, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def publish_create_once(path: pathlib.Path, record: dict) -> str:
    """Durably create immutable JSON. Identical replay is idempotent."""
    payload = json.dumps(record, sort_keys=True, indent=2, ensure_ascii=True, allow_nan=False).encode() + b'\n'
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        try:
            existing = path.read_bytes()
        except OSError:
            raise StoreError('immutable-record-unreadable') from None
        if existing != payload:
            raise StoreError('immutable-record-collision')
        return hashlib.sha256(payload).hexdigest()
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        _fsync_directory(path.parent)
    except OSError:
        raise StoreError('immutable-record-publication-failed') from None
    return hashlib.sha256(payload).hexdigest()


class VerificationStore:
    def __init__(self, repository: str | pathlib.Path, *, control_root: pathlib.Path | None = None):
        if control_root is None:
            control_root, repository_id = resolve_control_root(repository)
        else:
            repository_id = hashlib.sha256(str(pathlib.Path(repository).resolve()).encode()).hexdigest()
        self.root = control_root
        self.repository_id = repository_id
        self.executions = self.root / 'executions'
        self.runs = self.root / 'runs'
        self.grants = self.root / 'grants'
        self.consumptions = self.root / 'consumptions'

    def admit_repository_verification(self) -> None:
        """Fail closed on malformed/unresolved ownership journals.

        Reconciliation needs a qualified backend; this version refuses any
        unresolved journal rather than using PID, age, or lock state as proof.
        """
        if not self.executions.exists():
            return
        try:
            for execution in sorted(self.executions.iterdir()):
                if not execution.is_dir():
                    raise StoreError('invalid-execution-history')
                started = execution / 'started.json'
                if not started.exists():
                    raise StoreError('invalid-execution-history')
                record = json.loads(started.read_text(encoding='utf-8'))
                if (not isinstance(record, dict) or record.get('schema_version') != 2 or
                        record.get('execution_id') != execution.name or
                        not isinstance(record.get('repository_id'), str) or
                        not isinstance(record.get('repository_id'), str)):
                    raise StoreError('invalid-execution-history')
                drained = execution / 'drained.json'
                if not drained.exists():
                    raise StoreError('verification-owned')
                closure = json.loads(drained.read_text(encoding='utf-8'))
                if (not isinstance(closure, dict) or closure.get('schema_version') != 2 or
                        closure.get('execution_id') != execution.name or
                        closure.get('status') != 'drained' or closure.get('backend') != record.get('backend')):
                    raise StoreError('invalid-execution-history')
        except (OSError, ValueError, TypeError, json.JSONDecodeError):
            raise StoreError('invalid-execution-history') from None

    def publish_evidence(self, evidence: Evidence) -> str:
        record = evidence_record(evidence)
        path = self.runs / evidence.family_id / evidence.evidence_id / 'terminal.json'
        return publish_create_once(path, record)

    def iter_evidence(self):
        if not self.runs.exists():
            return
        for path in sorted(self.runs.glob('*/*/terminal.json')):
            try:
                yield json.loads(path.read_text(encoding='utf-8'))
            except (OSError, ValueError):
                # Invalid evidence cannot authorize reuse; preserve it for audit.
                continue

    def critical_failures(self, *, repository_id: str, profile_hash: str, gate_id: str, fingerprint: str):
        matches = []
        for record in self.iter_evidence():
            if (record.get('repository_id') == repository_id and record.get('profile_hash') == profile_hash and
                    record.get('gate_id') == gate_id and record.get('pre_fingerprint') == fingerprint and
                    record.get('status') == 'verification-failed' and record.get('critical') is True):
                matches.append(record)
        return sorted(matches, key=lambda item: item.get('evidence_id', ''))

    def consume_grant(self, grant_id: str, *, failure_id: str, key: dict) -> dict:
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}', grant_id):
            raise StoreError('invalid-grant-id')
        grant_path = self.grants / f'{grant_id}.json'
        try:
            grant = json.loads(grant_path.read_text(encoding='utf-8'))
        except (OSError, ValueError):
            raise StoreError('grant-not-found') from None
        if grant.get('failure_id') != failure_id or grant.get('key') != key:
            raise StoreError('grant-scope-mismatch')
        consumption = {'schema_version': 2, 'grant_id': grant_id, 'failure_id': failure_id, 'key': key}
        consumption_path = self.consumptions / f'{grant_id}.json'
        if consumption_path.exists():
            raise StoreError('grant-already-consumed')
        try:
            publish_create_once(consumption_path, consumption)
        except StoreError as exc:
            if str(exc) == 'immutable-record-collision':
                raise StoreError('grant-already-consumed') from None
            raise
        return consumption

    def publish_terminal_failure(self, record: dict) -> str:
        """Persist a terminal failure as immutable authority before projections."""
        required = {'schema_version', 'evidence_id', 'family_id', 'repository_id', 'profile_hash',
                    'gate_id', 'pre_fingerprint', 'status', 'critical'}
        if (not isinstance(record, dict) or not required <= set(record) or record.get('schema_version') != 2 or
                record.get('status') != 'verification-failed' or type(record.get('critical')) is not bool or
                record.get('repository_id') != self.repository_id):
            raise StoreError('invalid-terminal-evidence')
        path = self.runs / record['family_id'] / record['evidence_id'] / 'terminal.json'
        return publish_create_once(path, record)
