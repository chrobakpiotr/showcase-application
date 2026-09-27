"""Canonical verification control-store resolution and immutable publication."""
from __future__ import annotations

import contextlib
import enum
import hashlib
import json
import os
import pathlib
import re
import subprocess
import tempfile
import time
import uuid

from .model import Evidence
from .serialization import canonical, evidence_record


class StoreError(RuntimeError):
    """Stable control-store failure category."""


def _validate_component(value: str | None, error: str) -> None:
    if not isinstance(value, str) or not _SAFE_COMPONENT.fullmatch(value) or value in {'.', '..'}:
        raise StoreError(error)


class ReconciliationOutcome(enum.Enum):
    """Backend assertion about the recorded execution containment scope."""

    PROVEN_DRAINED = 'proven-drained'
    STILL_ACTIVE = 'still-active'
    UNCERTAIN = 'uncertain'


# Authority classification is explicit: these records determine restart truth.
AUTHORITATIVE_IMMUTABLE = frozenset({
    'executions/*/started.json', 'executions/*/admitted.json', 'executions/*/drained.json',
    'runs/*/*/terminal.json', 'grants/*.json', 'consumptions/*.json',
})
DERIVED_REBUILDABLE = frozenset({'state/**', 'runs/*/summary.json', 'runs/*/*/projection.json'})
_SAFE_COMPONENT = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}\Z')


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
    try:
        caller = pathlib.Path(repository).resolve(strict=True)
    except (OSError, RuntimeError):
        raise StoreError('repository-unresolved') from None
    common = pathlib.Path(os.fsdecode(_git(caller, 'rev-parse', '--path-format=absolute', '--git-common-dir').strip())).resolve(strict=True)
    if _git(caller, 'rev-parse', '--is-bare-repository').strip() == b'true':
        raise StoreError('bare-repository-unsupported')
    owners = [worktree for worktree in _worktrees(common)
              if (worktree / '.git').is_dir() and (worktree / '.git').resolve() == common]
    if len(owners) != 1:
        raise StoreError('primary-worktree-ambiguous')
    primary = owners[0]
    identity = hashlib.sha256(os.fsencode(common)).hexdigest()
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


def _atomic_replace_projection(path: pathlib.Path, record: dict, *, fault=None) -> None:
    """Replace rebuildable projection via fsynced same-directory temp file."""
    payload = json.dumps(record, sort_keys=True, indent=2, ensure_ascii=True, allow_nan=False).encode() + b'\n'
    _assert_contained(path, path.parent.parent if path.parent.name == 'state' else path.parent)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=f'.{path.name}.', suffix='.tmp', dir=path.parent)
    temp_path = pathlib.Path(temp_name)
    try:
        with os.fdopen(fd, 'wb') as stream:
            if fault:
                fault('projection-before-write')
            stream.write(payload)
            stream.flush()
            if fault:
                fault('projection-before-fsync')
            os.fsync(stream.fileno())
        if fault:
            fault('projection-before-replace')
        os.replace(temp_path, path)
        _fsync_directory(path.parent)
    except Exception:
        try:
            temp_path.unlink()
        except OSError:
            pass
        raise


def _assert_contained(path: pathlib.Path, root: pathlib.Path, *, allow_equal: bool = False) -> None:
    """Reject symlinks and prove resolved artifact remains beneath root."""
    root = pathlib.Path(root).resolve()
    path = pathlib.Path(os.path.abspath(path))
    probe = path
    while probe != probe.parent:
        if probe.is_symlink():
            raise StoreError('unsafe-authority-path')
        probe = probe.parent
    try:
        relative = path.resolve(strict=False).relative_to(root)
        if not allow_equal and not relative.parts:
            raise ValueError()
    except (OSError, ValueError, RuntimeError):
        raise StoreError('unsafe-authority-path') from None


def publish_create_once(path: pathlib.Path, record: dict, *, fault=None) -> str:
    """Atomically and durably create immutable JSON; identical replay is safe."""
    payload = json.dumps(record, sort_keys=True, indent=2, ensure_ascii=True, allow_nan=False).encode() + b'\n'
    path = pathlib.Path(path)
    if path.parent.exists() and path.parent.is_symlink():
        raise StoreError('unsafe-authority-path')
    path.parent.mkdir(parents=True, exist_ok=True)
    _assert_contained(path, path.parent, allow_equal=False)
    temp_path = path.parent / f'.{path.name}.{uuid.uuid4().hex}.tmp'
    fd = None
    try:
        if fault:
            fault('before-publication')
        fd = os.open(temp_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as stream:
            fd = None
            if fault:
                fault('during-write')
            stream.write(payload)
            stream.flush()
            if fault:
                fault('before-file-fsync')
            os.fsync(stream.fileno())
        if fault:
            fault('before-atomic-publication')
        try:
            # Hard-link publication is atomic and cannot replace an authority.
            os.link(temp_path, path)
        except FileExistsError:
            try:
                existing = path.read_bytes()
            except OSError:
                raise StoreError('immutable-record-unreadable') from None
            if existing != payload:
                raise StoreError('immutable-record-collision')
        if fault:
            fault('before-directory-fsync')
        _fsync_directory(path.parent)
    except StoreError:
        raise
    except OSError:
        if path.exists():
            # The atomic name may be visible, but directory durability is
            # uncertain. Preserve the bytes and make readers fail closed.
            marker = path.with_name(path.name + '.publication-uncertain')
            try:
                marker.write_text('uncertain\n', encoding='ascii')
                _fsync_directory(path.parent)
            except OSError:
                pass
        raise StoreError('immutable-record-publication-failed') from None
    finally:
        if fd is not None:
            os.close(fd)
        try:
            temp_path.unlink()
        except FileNotFoundError:
            pass
        except OSError:
            # A leftover private temp is not authority and is never scanned.
            pass
    return hashlib.sha256(payload).hexdigest()


class VerificationStore:
    def __init__(self, repository: str | pathlib.Path, *, control_root: pathlib.Path | None = None):
        # An explicit control root is only a test seam; identity always follows
        # the canonical Git common-dir model used by the repository harness.
        if control_root is None:
            control_root, repository_id = resolve_control_root(repository)
        else:
            # Explicit roots are used by isolated state-machine tests only.
            # Production entry points omit this argument and require Git identity.
            repository_id = hashlib.sha256(str(pathlib.Path(repository).resolve()).encode()).hexdigest()
        self.root = pathlib.Path(control_root).resolve()
        self.repository_id = repository_id
        self.executions = self.root / 'executions'
        self.runs = self.root / 'runs'
        self.grants = self.root / 'grants'
        self.consumptions = self.root / 'consumptions'
        self._fault_injector = None

    def _fault(self, boundary: str) -> None:
        if self._fault_injector:
            self._fault_injector(boundary)

    def _validated_started(self, directory: pathlib.Path) -> dict:
        if not directory.is_dir() or directory.is_symlink() or not _SAFE_COMPONENT.fullmatch(directory.name):
            raise StoreError('invalid-execution-history')
        started = directory / 'started.json'
        try:
            if not started.is_file() or started.is_symlink():
                raise StoreError('invalid-execution-history')
            record = json.loads(started.read_text(encoding='utf-8'))
        except (OSError, ValueError, TypeError, json.JSONDecodeError):
            raise StoreError('invalid-execution-history') from None
        required = {'schema_version', 'execution_id', 'repository_id', 'backend', 'worktree',
                    'launch_intent_hash', 'started_at', 'family_id', 'attempt_id', 'gate_id'}
        if (not isinstance(record, dict) or not required <= set(record) or record.get('schema_version') != 2 or
                record.get('execution_id') != directory.name or record.get('repository_id') != self.repository_id or
                not all(isinstance(record.get(k), str) and record[k] for k in
                        ('backend', 'worktree', 'family_id', 'attempt_id', 'gate_id')) or
                not isinstance(record.get('launch_intent_hash'), str) or
                not re.fullmatch(r'[0-9a-f]{64}', record['launch_intent_hash']) or
                type(record.get('started_at')) not in (float, int)):
            raise StoreError('invalid-execution-history')
        return record

    def _validate_admission(self, directory: pathlib.Path, started: dict) -> None:
        admitted_path = directory / 'admitted.json'
        if not admitted_path.exists():
            return  # Historical journals from the earlier protocol are readable.
        _assert_contained(admitted_path, self.root)
        try:
            admitted = json.loads(admitted_path.read_text(encoding='utf-8'))
        except (OSError, ValueError):
            raise StoreError('invalid-execution-history') from None
        required = {'schema_version', 'execution_id', 'repository_id', 'status', 'admitted_at'}
        if (not isinstance(admitted, dict) or not required <= set(admitted) or
                admitted.get('schema_version') != 2 or admitted.get('execution_id') != started['execution_id'] or
                admitted.get('repository_id') != self.repository_id or admitted.get('status') != 'admitted' or
                type(admitted.get('admitted_at')) not in (int, float)):
            raise StoreError('invalid-execution-history')

    def _scan_executions(self) -> list[tuple[pathlib.Path, dict, dict | None]]:
        executions = []
        if not self.executions.exists():
            return executions
        _assert_contained(self.executions, self.root)
        if self.executions.is_symlink() or not self.executions.is_dir():
            raise StoreError('invalid-execution-history')
        try:
            directories = sorted(self.executions.iterdir())
        except OSError:
            raise StoreError('invalid-execution-history') from None
        for directory in directories:
            if not _SAFE_COMPONENT.fullmatch(directory.name):
                raise StoreError('invalid-execution-history')
            if any((directory / name).exists() for name in
                   ('started.json.publication-uncertain', 'drained.json.publication-uncertain',
                    'admitted.json.publication-uncertain')):
                raise StoreError('invalid-execution-history')
            started = self._validated_started(directory)
            _assert_contained(directory, self.root)
            self._validate_admission(directory, started)
            drained_path = directory / 'drained.json'
            closure = None
            if drained_path.exists() or drained_path.is_symlink():
                _assert_contained(drained_path, self.root)
                try:
                    if not drained_path.is_file() or drained_path.is_symlink():
                        raise ValueError()
                    closure = json.loads(drained_path.read_text(encoding='utf-8'))
                except (OSError, ValueError, TypeError, json.JSONDecodeError):
                    raise StoreError('invalid-execution-history') from None
                required = {'schema_version', 'execution_id', 'repository_id', 'backend', 'status', 'ended_at'}
                if (not isinstance(closure, dict) or not required <= set(closure) or closure.get('schema_version') != 2 or
                        closure.get('execution_id') != directory.name or closure.get('repository_id') != self.repository_id or
                        closure.get('backend') != started['backend'] or closure.get('status') != 'drained' or
                        type(closure.get('ended_at')) not in (int, float)):
                    raise StoreError('invalid-execution-history')
            executions.append((directory, started, closure))
        return executions

    def admit_repository_verification(self, reconciler=None) -> None:
        executions = self._scan_executions()
        for directory, started, closure in executions:
            if closure is not None:
                continue
            try:
                outcome = reconciler(started) if reconciler is not None else ReconciliationOutcome.UNCERTAIN
            except Exception:
                outcome = ReconciliationOutcome.UNCERTAIN
            if outcome == ReconciliationOutcome.PROVEN_DRAINED:
                record = {'schema_version': 2, 'execution_id': started['execution_id'],
                          'repository_id': self.repository_id, 'backend': started['backend'],
                          'status': 'drained', 'reason': 'reconciled-proven-drained', 'ended_at': time.time()}
                publish_create_once(directory / 'drained.json', record, fault=self._fault)
            elif outcome in (ReconciliationOutcome.STILL_ACTIVE, ReconciliationOutcome.UNCERTAIN):
                raise StoreError('verification-owned')
            else:
                raise StoreError('invalid-reconciliation-outcome')
        if any(closure is None for _, _, closure in self._scan_executions()):
            raise StoreError('verification-owned')

    def admit_and_reserve(self, execution_id: str, record: dict, reconciler=None) -> pathlib.Path:
        """Serialize validation, reconciliation, rescan, and immutable admission."""
        _validate_component(execution_id, 'invalid-execution-id')
        if record.get('execution_id') != execution_id or record.get('repository_id') != self.repository_id:
            raise StoreError('invalid-start-record')
        if record.get('schema_version') != 2:
            raise StoreError('invalid-start-record')
        journal = self.executions / execution_id
        _assert_contained(journal, self.root)
        with repository_lock(self.root):
            self.admit_repository_verification(reconciler)
            if journal.exists():
                existing = self._validated_started(journal)
                if existing == record:
                    return journal
                raise StoreError('immutable-record-collision')
            self._fault('before-admission-publication')
            journal.mkdir(parents=True, exist_ok=True)
            # The start journal is the atomic reservation and admission fact.
            # A second record would create a crash gap between reservation
            # and start, so no separately authoritative admitted marker exists.
            publish_create_once(journal / 'started.json', record, fault=self._fault)
            _fsync_directory(self.executions)
        return journal

    def _scan_terminals(self) -> list[dict]:
        records = []
        if not self.runs.exists():
            return records
        _assert_contained(self.runs, self.root)
        for family_dir in sorted(self.runs.iterdir()):
            _validate_component(family_dir.name, 'invalid-terminal-identity')
            _assert_contained(family_dir, self.root)
            if not family_dir.is_dir() or family_dir.is_symlink():
                raise StoreError('invalid-terminal-identity')
            for evidence_dir in sorted(family_dir.iterdir()):
                _validate_component(evidence_dir.name, 'invalid-terminal-identity')
                _assert_contained(evidence_dir, self.root)
                if not evidence_dir.is_dir() or evidence_dir.is_symlink():
                    raise StoreError('invalid-terminal-identity')
                terminal = evidence_dir / 'terminal.json'
                projection = evidence_dir / 'projection.json'
                if projection.exists() or projection.is_symlink():
                    _assert_contained(projection, self.root)
                if not terminal.exists() and not terminal.is_symlink():
                    if projection.exists():
                        raise StoreError('projection-without-terminal')
                    continue
                _assert_contained(terminal, self.root)
                try:
                    record = json.loads(terminal.read_text(encoding='utf-8'))
                    from .serialization import validate_evidence_record
                    record = validate_evidence_record(record)
                except Exception:
                    raise StoreError('invalid-terminal-evidence') from None
                if record['family_id'] != family_dir.name or record['evidence_id'] != evidence_dir.name or record['repository_id'] != self.repository_id:
                    raise StoreError('invalid-terminal-evidence')
                if (evidence_dir / 'terminal.json.publication-uncertain').exists():
                    raise StoreError('terminal-publication-uncertain')
                if projection.exists():
                    try:
                        derived = json.loads(projection.read_text(encoding='utf-8'))
                    except (OSError, ValueError):
                        derived = None
                    if derived != {'schema_version': 2, 'evidence_id': record['evidence_id'],
                                   'family_id': record['family_id'], 'receipt_hash': record['receipt_hash'],
                                   'status': record['status']}:
                        # Terminal authority wins; replace derived data from it.
                        self.rebuild_projection(record)
                records.append(record)
                if not projection.exists():
                    self.rebuild_projection(record)
        return records

    def reconstruct_terminals(self) -> list[dict]:
        """Rebuild only projections from validated immutable terminal receipts."""
        return self._scan_terminals()

    def rebuild_projection(self, terminal: dict) -> None:
        _validate_component(terminal.get('family_id'), 'invalid-terminal-identity')
        _validate_component(terminal.get('evidence_id'), 'invalid-terminal-identity')
        path = self.runs / terminal['family_id'] / terminal['evidence_id'] / 'projection.json'
        projection = {'schema_version': 2, 'evidence_id': terminal['evidence_id'],
                      'family_id': terminal['family_id'], 'receipt_hash': terminal['receipt_hash'],
                      'status': terminal['status']}
        _atomic_replace_projection(path, projection, fault=self._fault)

    def publish_evidence(self, evidence: Evidence) -> str:
        record = evidence_record(evidence)
        _validate_component(evidence.family_id, 'invalid-terminal-identity')
        _validate_component(evidence.evidence_id, 'invalid-terminal-identity')
        path = self.runs / evidence.family_id / evidence.evidence_id / 'terminal.json'
        receipt = publish_create_once(path, record, fault=self._fault)
        return receipt

    def iter_evidence(self):
        yield from self._scan_terminals()

    def critical_failures(self, *, repository_id: str, profile_hash: str, gate_id: str, fingerprint: str):
        matches = []
        if not self.runs.exists():
            return matches
        for path in sorted(self.runs.glob('*/*/terminal.json')):
            try:
                record = json.loads(path.read_text(encoding='utf-8'))
            except (OSError, ValueError):
                continue
            if (isinstance(record, dict) and record.get('repository_id') == repository_id and
                    record.get('profile_hash') == profile_hash and record.get('gate_id') == gate_id and
                    record.get('pre_fingerprint') == fingerprint and
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
