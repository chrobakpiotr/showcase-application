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
import threading
from typing import Any, Callable
import weakref


class AdmissionConflict(RuntimeError):
    pass


_LAUNCH_CAPABILITY_SEAL = object()
_LAUNCH_CAPABILITY_LOCK = threading.Lock()
_CONSUMED_LAUNCH_CAPABILITIES = weakref.WeakSet()


class LaunchCapability:
    """Process-local proof returned only to the winner of a consumption CAS."""
    __slots__ = ('consumption_id', 'reservation_id', '_seal', '__weakref__')

    def __init__(self, consumption_id: str, reservation_id: str, *, _seal=None):
        if _seal is not _LAUNCH_CAPABILITY_SEAL:
            raise ValueError('launch-capability-unavailable')
        self.consumption_id = consumption_id
        self.reservation_id = reservation_id
        self._seal = _seal


def issue_launch_capability(consumption_id: str, reservation_id: str) -> LaunchCapability:
    return LaunchCapability(consumption_id, reservation_id, _seal=_LAUNCH_CAPABILITY_SEAL)


def validate_launch_capability(capability: Any, *, consumption_id: str,
                               reservation_id: str) -> bool:
    """Validate and atomically spend the in-process launch capability once."""
    if (not isinstance(capability, LaunchCapability) or
            capability._seal is not _LAUNCH_CAPABILITY_SEAL or
            capability.consumption_id != consumption_id or
            capability.reservation_id != reservation_id):
        return False
    with _LAUNCH_CAPABILITY_LOCK:
        if capability in _CONSUMED_LAUNCH_CAPABILITIES:
            return False
        _CONSUMED_LAUNCH_CAPABILITIES.add(capability)
        return True


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
    def mutation(self, *, allow_verification_id: str | None = None):
        """Serialize a lifecycle mutation; only the owning execution may advance its claim."""
        with self._locked():
            state = self._read()
            if isinstance(state['active'], dict):
                if (allow_verification_id is not None and state['active'].get('kind') == 'verification' and
                        state['active'].get('id') == allow_verification_id):
                    yield
                    return
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

    def owner_is_proven_dead(self, reservation_id: str) -> bool:
        """Check exact active owner PID+start token without changing admission state."""
        with self._locked():
            active = self._read()['active']
            if not isinstance(active, dict) or active.get('id') != reservation_id:
                return False
            context = active.get('context')
            if not isinstance(context, dict):
                return False
            pid, start = context.get('owner_pid'), context.get('owner_start_time')
            if type(pid) is not int or pid <= 0 or not isinstance(start, str) or not start:
                return False
            return process_instance_is_alive(pid, start) is False

    def active(self) -> dict[str, Any] | None:
        with self._locked():
            return self._read()['active']

# Plan-bound immutable execution authority. These records are subordinate to
# the accepted plan and the lifecycle CAS; this module's repository guard above
# never selects or creates plan authority by itself.
def _record_digest(value: dict[str, Any]) -> str:
    from .serialization import canonical
    return hashlib.sha256(canonical(value)).hexdigest()


def _plan_admission_id(body: dict[str, Any]) -> str:
    return 'verification-admission-v1:sha256:' + _record_digest(body)


def _launch_reservation_id(body: dict[str, Any]) -> str:
    return 'launch-reservation-v1:sha256:' + _record_digest(body)


def build_launch_consumption_record(admission: dict[str, Any], reservation: dict[str, Any], *,
                                    transition_id: str) -> dict[str, Any]:
    import re
    from .store import StoreError
    validate_plan_admission_record_shape(admission)
    validate_plan_launch_reservation(reservation, admission)
    if (not isinstance(transition_id, str) or not re.fullmatch(
            r'[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}',
            transition_id)):
        raise StoreError('invalid-launch-consumption')
    body = {key: reservation[key] for key in (
        'repository_id', 'feature_id', 'task_id', 'task_attempt', 'lifecycle_generation',
        'plan_id', 'plan_acceptance_transition_id', 'family_id', 'admission_id',
        'candidate_identity', 'final_changed_surface_id', 'unit_id', 'obligation_ids',
        'execution_origin', 'independent_execution_class', 'reservation_id')}
    body.update({'schema_version': 1, 'transition_id': transition_id,
                 'status': 'launch_consumed'})
    body['consumption_id'] = 'launch-consumption-v1:sha256:' + _record_digest(body)
    return body


def validate_launch_consumption_record(record: dict[str, Any], admission: dict[str, Any],
                                       reservation: dict[str, Any]) -> dict[str, Any]:
    from .store import StoreError
    try:
        expected = build_launch_consumption_record(admission, reservation,
                                                   transition_id=record['transition_id'])
        if record != expected:
            raise ValueError()
        return record
    except (KeyError, TypeError, ValueError, StoreError):
        raise StoreError('invalid-launch-consumption') from None


def build_plan_admission_record(plan: dict[str, Any], unit_id: str, *, repository_id: str,
                                plan_acceptance_transition_id: str,
                                admission_transition_id: str) -> dict[str, Any]:
    """Build a closed admission projection from one exact immutable plan unit."""
    import re
    from .store import StoreError
    from .authority import INDEPENDENT_CLASS
    uuid_pattern = r'[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}'
    if (not isinstance(repository_id, str) or not re.fullmatch(r'[0-9a-f]{64}', repository_id) or
            not isinstance(plan_acceptance_transition_id, str) or
            not re.fullmatch(uuid_pattern, plan_acceptance_transition_id) or
            not isinstance(admission_transition_id, str) or
            not re.fullmatch(uuid_pattern, admission_transition_id)):
        raise StoreError('PLAN_BINDING_MISMATCH')
    matches = [item for item in plan.get('execution_units', [])
               if isinstance(item, dict) and item.get('unit_id') == unit_id]
    if len(matches) != 1:
        raise StoreError('PLAN_BINDING_MISMATCH')
    unit = matches[0]
    if (set(unit) != {'unit_id', 'obligation_ids', 'required_origin', 'independent_execution_class'} or
            not isinstance(unit.get('obligation_ids'), list) or not unit['obligation_ids'] or
            any(not isinstance(item, str) for item in unit['obligation_ids']) or
            len(unit['obligation_ids']) != len(set(unit['obligation_ids']))):
        raise StoreError('PLAN_BINDING_MISMATCH')
    obligations = plan.get('obligations')
    if not isinstance(obligations, list):
        raise StoreError('PLAN_BINDING_MISMATCH')
    by_id = {item.get('obligation_id'): item for item in obligations if isinstance(item, dict)}
    members = [by_id.get(item) for item in unit['obligation_ids']]
    if any(item is None for item in members):
        raise StoreError('PLAN_BINDING_MISMATCH')
    origins = {item.get('required_origin') for item in members}
    if origins == {'task'}:
        origin, execution_class = 'task', None
    elif origins <= {'task', 'independent'} and 'independent' in origins and all(
            item.get('independent_execution_class') == INDEPENDENT_CLASS
            for item in members if item.get('required_origin') == 'independent'):
        origin, execution_class = 'independent', INDEPENDENT_CLASS
    else:
        # M5.3 deliberately has no physical/manual execution-origin class.
        raise StoreError('PLAN_BINDING_MISMATCH')
    if (unit['required_origin'], unit['independent_execution_class']) != (origin, execution_class):
        raise StoreError('PLAN_BINDING_MISMATCH')
    body = {
        'schema_version': 1, 'repository_id': repository_id,
        'feature_id': plan['feature_id'], 'task_id': plan['task_id'],
        'task_attempt': plan['task_attempt'], 'lifecycle_generation': plan['lifecycle_generation'],
        'plan_id': plan['plan_id'], 'plan_acceptance_transition_id': plan_acceptance_transition_id,
        'admission_transition_id': admission_transition_id,
        'family_id': plan['family']['id'], 'profile_id': plan['profile_id'],
        'profile_hash': plan['profile_hash'], 'policy_checkpoint': plan['policy_checkpoint'],
        'candidate_identity': plan['candidate_identity'],
        'final_changed_surface_id': plan['final_changed_surface_id'],
        'unit_id': unit_id, 'obligation_ids': list(unit['obligation_ids']),
        'execution_origin': origin, 'independent_execution_class': execution_class,
    }
    return {'admission_id': _plan_admission_id(body), **body}


def validate_plan_admission_record(record: dict[str, Any], plan: dict[str, Any],
                                   unit: dict[str, Any] | None = None) -> dict[str, Any]:
    from .store import StoreError
    try:
        expected = build_plan_admission_record(plan, record['unit_id'],
            repository_id=record['repository_id'],
            plan_acceptance_transition_id=record['plan_acceptance_transition_id'],
            admission_transition_id=record['admission_transition_id'])
        if record != expected or (unit is not None and unit != next(
                item for item in plan['execution_units'] if item['unit_id'] == record['unit_id'])):
            raise ValueError()
        return record
    except (KeyError, TypeError, ValueError, StopIteration, StoreError):
        raise StoreError('invalid-execution-admission') from None


def build_plan_launch_reservation(admission: dict[str, Any], *, transition_id: str) -> dict[str, Any]:
    import re
    from .store import StoreError
    validate_plan_admission_record_shape(admission)
    if not re.fullmatch(r'[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}',
                        transition_id or ''):
        raise StoreError('invalid-launch-reservation')
    body = {key: admission[key] for key in (
        'schema_version', 'repository_id', 'feature_id', 'task_id', 'task_attempt',
        'lifecycle_generation', 'plan_id', 'plan_acceptance_transition_id', 'family_id',
        'admission_transition_id',
        'candidate_identity', 'final_changed_surface_id', 'unit_id', 'obligation_ids',
        'execution_origin', 'independent_execution_class')}
    body.update({'admission_id': admission['admission_id'], 'transition_id': transition_id,
                 'status': 'launch_reserved'})
    return {'reservation_id': _launch_reservation_id(body), **body}


def validate_plan_admission_record_shape(record: dict[str, Any]) -> dict[str, Any]:
    import re
    from .store import StoreError
    keys = {'admission_id', 'schema_version', 'repository_id', 'feature_id', 'task_id',
            'task_attempt', 'lifecycle_generation', 'plan_id', 'plan_acceptance_transition_id',
            'admission_transition_id', 'family_id', 'profile_id', 'profile_hash', 'policy_checkpoint', 'candidate_identity',
            'final_changed_surface_id', 'unit_id', 'obligation_ids', 'execution_origin',
            'independent_execution_class'}
    try:
        if (not isinstance(record, dict) or set(record) != keys or record['schema_version'] != 1 or
                type(record['task_attempt']) is not int or record['task_attempt'] < 1 or
                type(record['lifecycle_generation']) is not int or record['lifecycle_generation'] < 1 or
                not re.fullmatch(r'[0-9a-f]{64}', record['repository_id']) or
                not re.fullmatch(r'[0-9a-f]{64}', record['profile_hash']) or
                not re.fullmatch(r'[0-9a-f]{64}', record['candidate_identity']) or
                not re.fullmatch(r'[0-9a-f]{64}', record['final_changed_surface_id']) or
                not isinstance(record['obligation_ids'], list) or not record['obligation_ids'] or
                any(not isinstance(item, str) for item in record['obligation_ids']) or
                len(record['obligation_ids']) != len(set(record['obligation_ids'])) or
                record['execution_origin'] not in {'task', 'independent'} or
                (record['execution_origin'] == 'task' and record['independent_execution_class'] is not None) or
                (record['execution_origin'] == 'independent' and
                 record['independent_execution_class'] != 'harness-managed-independent-execution-v1')):
            raise ValueError()
        body = {key: record[key] for key in keys if key != 'admission_id'}
        if record['admission_id'] != _plan_admission_id(body):
            raise ValueError()
        return record
    except (KeyError, TypeError, ValueError):
        raise StoreError('invalid-execution-admission') from None


def validate_plan_launch_reservation(record: dict[str, Any], admission: dict[str, Any] | None = None) -> dict[str, Any]:
    import re
    from .store import StoreError
    keys = {'reservation_id', 'schema_version', 'repository_id', 'feature_id', 'task_id',
            'task_attempt', 'lifecycle_generation', 'plan_id', 'plan_acceptance_transition_id',
            'admission_transition_id', 'family_id', 'candidate_identity',
            'final_changed_surface_id', 'unit_id', 'obligation_ids',
            'execution_origin', 'independent_execution_class', 'admission_id', 'transition_id', 'status'}
    try:
        if (not isinstance(record, dict) or set(record) != keys or record.get('schema_version') != 1 or
                record.get('status') != 'launch_reserved' or
                not re.fullmatch(r'[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}', record['transition_id'])):
            raise ValueError()
        body = {key: record[key] for key in keys if key != 'reservation_id'}
        if record['reservation_id'] != _launch_reservation_id(body):
            raise ValueError()
        if admission is not None:
            validate_plan_admission_record_shape(admission)
            if (record['admission_id'] != admission['admission_id'] or any(
                    record[key] != admission[key] for key in keys & set(admission) if key != 'reservation_id')):
                raise ValueError()
        return record
    except (KeyError, TypeError, ValueError, StoreError):
        raise StoreError('invalid-launch-reservation') from None
