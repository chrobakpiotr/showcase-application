"""Durable repository-wide lifecycle admission shared by verifiers and mutations."""
from __future__ import annotations

import contextlib
import fcntl
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile
from typing import Any, Callable


class AdmissionConflict(RuntimeError):
    pass


def process_start_token(pid: int) -> str | None:
    """Return a process-instance token; unknown must never be treated as dead."""
    if type(pid) is not int or pid < 1:
        return None
    proc_stat = pathlib.Path(f'/proc/{pid}/stat')
    try:
        raw = proc_stat.read_text(encoding='ascii')
        close = raw.rfind(')')
        fields = raw[close + 2:].split()
        if fields[0] in {'Z', 'X'}:
            return None
        start_ticks = fields[19]  # proc stat field 22 (starttime)
        boot_id = pathlib.Path('/proc/sys/kernel/random/boot_id').read_text(encoding='ascii').strip()
        if start_ticks.isdigit() and boot_id:
            return f'linux:{boot_id}:{start_ticks}'
    except (OSError, IndexError, UnicodeError):
        pass
    try:
        result = subprocess.run(['ps', '-o', 'lstart=', '-p', str(pid)], capture_output=True,
                                text=True, timeout=2, check=False)
        value = result.stdout.strip()
        if result.returncode == 0 and value:
            return f'ps:{value}'
    except (OSError, subprocess.SubprocessError):
        pass
    return None


def process_instance_is_alive(pid: int, start_token: str) -> bool | None:
    """Return False only when PID absence or a different start token proves death."""
    try:
        raw = pathlib.Path(f'/proc/{pid}/stat').read_text(encoding='ascii')
        close = raw.rfind(')')
        if raw[close + 2:].split()[0] in {'Z', 'X'}:
            return False
    except (OSError, IndexError, UnicodeError):
        pass
    current = process_start_token(pid)
    if current is not None:
        return current == start_token
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return None
    except OSError:
        return None
    return None


class RepositoryAdmission:
    """CAS-like, fsynced admission record under the repository lifecycle authority.

    Reservations deliberately survive process death. Recovery must prove the owning
    operation ended and release the exact ID; task leases and process locks do not
    clear an admission.
    """

    def __init__(self, lifecycle_root: pathlib.Path, repository_id: str):
        self.root = pathlib.Path(lifecycle_root)
        self.repository_id = repository_id
        self.path = self.root / 'repository-admission.json'
        self.lock_path = self.root / 'repository-admission.lock'

    @contextlib.contextmanager
    def _locked(self):
        if self.root.is_symlink():
            raise AdmissionConflict('admission-authority-invalid')
        self.root.mkdir(parents=True, exist_ok=True)
        if not self.root.is_dir() or self.root.is_symlink():
            raise AdmissionConflict('admission-authority-invalid')
        flags = os.O_RDWR | os.O_CREAT | getattr(os, 'O_NOFOLLOW', 0)
        fd = os.open(self.lock_path, flags, 0o600)
        with open(fd, 'r+') as lock:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)

    def _read(self) -> dict[str, Any]:
        if self.path.is_symlink():
            raise AdmissionConflict('admission-authority-invalid')
        try:
            value = json.loads(self.path.read_text(encoding='utf-8'))
        except FileNotFoundError:
            return {'schema_version': 1, 'repository_id': self.repository_id,
                    'generation': 0, 'active': None}
        except (OSError, ValueError):
            raise AdmissionConflict('admission-authority-invalid') from None
        if (not isinstance(value, dict) or value.get('schema_version') != 1 or
                value.get('repository_id') != self.repository_id or
                type(value.get('generation')) is not int or value['generation'] < 0 or
                (value.get('active') is not None and not isinstance(value.get('active'), dict))):
            raise AdmissionConflict('admission-authority-invalid')
        return value

    def _write(self, value: dict[str, Any]) -> None:
        fd, tmp = tempfile.mkstemp(prefix=self.path.name + '.', dir=self.root)
        try:
            with os.fdopen(fd, 'w', encoding='utf-8') as out:
                json.dump(value, out, indent=2, sort_keys=True)
                out.write('\n')
                out.flush()
                os.fsync(out.fileno())
            os.replace(tmp, self.path)
            directory_fd = os.open(self.root, os.O_RDONLY)
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)
        finally:
            if os.path.exists(tmp):
                os.unlink(tmp)

    def _reserve(self, kind: str, reservation_id: str, context: dict[str, Any],
                 validate: Callable[[], None] | None = None) -> dict[str, Any]:
        if not isinstance(reservation_id, str) or not reservation_id or not isinstance(context, dict):
            raise AdmissionConflict('admission-request-invalid')
        if kind == 'verification':
            owner_pid = os.getpid()
            owner_start = process_start_token(owner_pid)
            if owner_start is None:
                raise AdmissionConflict('process-identity-unavailable')
            context = {**context, 'owner_pid': owner_pid, 'owner_start_time': owner_start}
        with self._locked():
            state = self._read()
            candidate = {'kind': kind, 'id': reservation_id, 'context': context}
            active = state['active']
            if validate is not None:
                validate()
            if active is not None:
                if active == candidate:
                    return state
                label = 'verification-owned' if active.get('kind') == 'verification' else 'mutation-owned'
                raise AdmissionConflict(label)
            state = {**state, 'generation': state['generation'] + 1, 'active': candidate}
            self._write(state)
            return state

    @contextlib.contextmanager
    def mutation(self):
        """Serialize one short canonical lifecycle mutation against admissions."""
        with self._locked():
            state = self._read()
            if isinstance(state['active'], dict):
                label = ('verification-owned' if state['active'].get('kind') == 'verification'
                         else 'mutation-owned')
                raise AdmissionConflict(label)
            yield

    @staticmethod
    def for_repository(repository: pathlib.Path) -> 'RepositoryAdmission':
        """Resolve the same primary-checkout .agent-state for every linked worktree."""
        try:
            common = pathlib.Path(subprocess.check_output(
                ['git', '-C', str(repository), 'rev-parse', '--path-format=absolute', '--git-common-dir'],
                stderr=subprocess.DEVNULL, text=True).strip()).resolve(strict=True)
        except (OSError, subprocess.CalledProcessError, RuntimeError):
            raise AdmissionConflict('repository-unresolved') from None
        identity = hashlib.sha256(os.fsencode(common)).hexdigest()
        return RepositoryAdmission(common.parent / '.agent-state', identity)

    def reserve_verification(self, execution_id: str, context: dict[str, Any], *,
                             validate: Callable[[], None] | None = None) -> dict[str, Any]:
        return self._reserve('verification', execution_id, context, validate)

    def reserve_mutation(self, mutation_id: str, context: dict[str, Any]) -> dict[str, Any]:
        return self._reserve('mutation', mutation_id, context)

    def release(self, reservation_id: str) -> dict[str, Any]:
        with self._locked():
            state = self._read()
            active = state['active']
            if not isinstance(active, dict) or active.get('id') != reservation_id:
                raise AdmissionConflict('reservation-mismatch')
            state = {**state, 'generation': state['generation'] + 1, 'active': None}
            self._write(state)
            return state

    def release_unstarted_if_owner_dead(self, reservation_id: str) -> bool:
        """Release only a matching verification reservation with a proven-dead owner."""
        with self._locked():
            state = self._read()
            active = state['active']
            if (not isinstance(active, dict) or active.get('kind') != 'verification' or
                    active.get('id') != reservation_id):
                return False
            context = active.get('context')
            if not isinstance(context, dict):
                raise AdmissionConflict('admission-authority-invalid')
            pid, start = context.get('owner_pid'), context.get('owner_start_time')
            if type(pid) is not int or not isinstance(start, str) or not start:
                raise AdmissionConflict('owner-identity-unavailable')
            alive = process_instance_is_alive(pid, start)
            if alive is not False:
                return False
            state = {**state, 'generation': state['generation'] + 1, 'active': None}
            self._write(state)
            return True

    def active(self) -> dict[str, Any] | None:
        with self._locked():
            return self._read()['active']
