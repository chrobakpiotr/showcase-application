"""Authoritative, restart-reconcilable lifecycle for verification executions."""
from __future__ import annotations

import enum
import functools
import hashlib
import json
import os
import pathlib
import re
import shlex
import stat
import time
import uuid
from dataclasses import dataclass, replace

from .serialization import canonical
from .admission import (AdmissionConflict, RepositoryAdmission,
                        validate_launch_capability)
from .store import (ReconciliationOutcome, StoreError, VerificationStore,
                    _fsync_directory, publish_create_once, repository_lock, candidate_failure_fingerprint,
                    _retry_policy_facts, _RETRY_CONTROL_BINDINGS, _RETRY_CONTROL_WRAPPER_SHA256,
                    _RETRY_CONTROL_INPUT_SHA256, _retry_control_inputs_digest)


class SupervisorError(RuntimeError):
    """Execution lifecycle could not be established safely."""


class SupervisorState(enum.Enum):
    STARTED = 'STARTED'
    ACTIVE = 'ACTIVE'
    CANCELLING = 'CANCELLING'
    DRAINING = 'DRAINING'
    DRAINED = 'DRAINED'
    TERMINAL = 'TERMINAL'
    UNCERTAIN = 'UNCERTAIN'
    ABORTED_PREPARED = 'ABORTED_PREPARED'


@dataclass(frozen=True)
class RecoveryResult:
    execution_id: str
    state: SupervisorState
    reason_code: str
    terminal: dict | None = None


def lifecycle_admitted(method):
    """Reserve canonical repository admission before runtime ownership is acquired."""
    @functools.wraps(method)
    def run(self, backend, **kwargs):
        command = kwargs.get('command')
        family_id = kwargs.get('family_id')
        attempt_id = kwargs.get('attempt_id')
        gate_id = kwargs.get('gate_id')
        if not all(isinstance(value, str) and value for value in
                   (command, family_id, attempt_id, gate_id)):
            raise SupervisorError('admission-identity-invalid')
        execution_id = hashlib.sha256(canonical({
            'repository_id': self.store.repository_id, 'family_id': family_id,
            'attempt_id': attempt_id, 'gate_id': gate_id,
            'command_hash': hashlib.sha256(command.encode('utf-8')).hexdigest(),
        })).hexdigest()[:32]
        admission = RepositoryAdmission(self.store.lifecycle_root, self.store.repository_id)
        plan_id = kwargs.get('plan_id')
        validator = None
        if plan_id is not None:
            def validate_accepted_plan():
                from .authority import resolve_accepted
                from .profile import command_identity
                from .store import StoreError
                accepted = resolve_accepted(self.store.lifecycle_root.parent, plan_id)
                obligations = accepted.get('obligations')
                try:
                    # Preserve the same lexical checkout spelling for both
                    # paths (e.g. macOS /tmp -> /private/tmp resolution).
                    worktree = pathlib.Path(os.path.abspath(kwargs['worktree']))
                    cwd = pathlib.Path(os.path.abspath(kwargs['cwd']))
                    relative_cwd = cwd.relative_to(worktree).as_posix()
                    expected_command_hash = command_identity(command, relative_cwd)
                except (KeyError, OSError, ValueError):
                    raise StoreError('ACCEPTED_PLAN_UNAVAILABLE') from None
                matching = [item for item in obligations if isinstance(item, dict) and
                            item.get('gate_id') == gate_id] if isinstance(obligations, list) else []
                if (accepted.get('plan_id') != plan_id or
                        accepted.get('family', {}).get('id') != family_id or
                        accepted.get('profile_hash') != kwargs.get('profile_hash') or
                        accepted.get('policy_checkpoint') != kwargs.get('policy_checkpoint') or
                        accepted.get('candidate_identity') != kwargs.get('candidate_identity') or
                        accepted.get('final_changed_surface_id') != kwargs.get('final_changed_surface_id') or
                        len(matching) != 1 or matching[0].get('command_hash') != expected_command_hash):
                    raise StoreError('ACCEPTED_PLAN_UNAVAILABLE')
            validator = validate_accepted_plan
        try:
            admission.reserve_verification(execution_id, {
                'family_id': family_id, 'attempt_id': attempt_id, 'gate_id': gate_id,
                'command_hash': hashlib.sha256(command.encode('utf-8')).hexdigest(),
                'owner_pid': os.getpid(),
            }, validate=validator)
        except AdmissionConflict:
            active = admission.active()
            owner_pid = active.get('context', {}).get('owner_pid') if isinstance(active, dict) else None
            owner_alive = False
            if type(owner_pid) is int and owner_pid > 0:
                try:
                    os.kill(owner_pid, 0)
                    owner_alive = True
                except OSError:
                    pass
            raise StoreError('busy' if owner_alive else 'verification-owned') from None
        try:
            # The lifecycle CAS is authorized only by this durable repository
            # admission. Callers cannot choose a different owner identifier.
            kwargs['repository_admission_id'] = execution_id
            result = method(self, backend, **kwargs)
        except BaseException:
            # Before a durable launch marker, no external process can own the
            # repository. Once launched, the reservation survives for recovery.
            if not (self.store.executions / execution_id / 'launching.json').exists():
                admission.release(execution_id)
            raise
        admission.release(execution_id)
        return result
    return run


def _read_regular_worktree_file(root: pathlib.Path, relative: str) -> bytes:
    """Read a candidate policy input without traversing symlinks."""
    current = root
    info = None
    for component in pathlib.PurePosixPath(relative).parts:
        current = current / component
        try:
            info = current.lstat()
        except OSError:
            raise SupervisorError('retry-policy-violation') from None
        if stat.S_ISLNK(info.st_mode):
            raise SupervisorError('retry-policy-violation')
    if info is None or not stat.S_ISREG(info.st_mode):
        raise SupervisorError('retry-policy-violation')
    try:
        fd = os.open(current, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
        try:
            if not stat.S_ISREG(os.fstat(fd).st_mode):
                raise SupervisorError('retry-policy-violation')
            with os.fdopen(fd, 'rb', closefd=False) as stream:
                return stream.read()
        finally:
            os.close(fd)
    except OSError:
        raise SupervisorError('retry-policy-violation') from None


def _gradle_user_home_has_init_scripts(execution_run_dir: pathlib.Path) -> bool:
    home = execution_run_dir / 'verification-home' / 'gradle'
    try:
        for path in (execution_run_dir / 'verification-home', home):
            if path.is_symlink():
                return True
        if any((home / name).exists() or (home / name).is_symlink()
               for name in ('init.gradle', 'init.gradle.kts')):
            return True
        init_dir = home / 'init.d'
        if init_dir.is_symlink():
            return True
        if init_dir.exists():
            return not init_dir.is_dir() or next(init_dir.iterdir(), None) is not None
        return False
    except OSError:
        return True


def _registered_gradle_retry_evidence(worktree: pathlib.Path, gate_id: str, command: str,
                                      controls: tuple[str, ...]) -> dict | None:
    if len(controls) != 1 or controls[0] not in _RETRY_CONTROL_BINDINGS:
        return None
    expected_gate, expected_command = _RETRY_CONTROL_BINDINGS[controls[0]]
    if gate_id != expected_gate or command != expected_command:
        raise SupervisorError('retry-policy-violation')
    try:
        root = pathlib.Path(worktree).resolve(strict=True)
        if not root.is_dir():
            raise ValueError()
    except (OSError, ValueError, RuntimeError):
        raise SupervisorError('retry-policy-violation') from None
    config_path = 'apps/ecommerce/backend/ecommerce.gradle'
    wrapper_path = command.removeprefix('./')
    try:
        registered_inputs = _RETRY_CONTROL_INPUT_SHA256[controls[0]]
        input_bytes = {relative: _read_regular_worktree_file(root, relative)
                       for relative in registered_inputs}
        if any(hashlib.sha256(input_bytes[path]).hexdigest() != expected
               for path, expected in registered_inputs.items()):
            raise SupervisorError('retry-policy-violation')
        config_bytes = input_bytes[config_path]
        wrapper_bytes = input_bytes[wrapper_path]
        if hashlib.sha256(wrapper_bytes).hexdigest() != _RETRY_CONTROL_WRAPPER_SHA256[controls[0]]:
            raise SupervisorError('retry-policy-violation')
        config = config_bytes.decode('utf-8')
        wrapper = wrapper_bytes.decode('utf-8')
    except (UnicodeDecodeError, SupervisorError):
        raise SupervisorError('retry-policy-violation') from None

    gate_property = 'criticalPostgresGate' if 'postgres' in controls[0] else 'criticalRabbitGate'
    enabled_name = 'criticalPostgresGateEnabled' if 'postgres' in controls[0] else 'criticalRabbitGateEnabled'
    expected_lines = (
        f'def {enabled_name} =',
        f"providers.gradleProperty('{gate_property}').getOrElse('false').toBoolean()",
        'def strictEvidenceGateEnabled = criticalPostgresGateEnabled || criticalRabbitGateEnabled',
        'maxRetries = strictEvidenceGateEnabled ? 0 : 2',
        'failOnPassedAfterRetry = strictEvidenceGateEnabled',
        'outputs.upToDateWhen { false }',
        'outputs.cacheIf { false }',
    )
    if any(config.count(line) != 1 for line in expected_lines):
        raise SupervisorError('retry-policy-violation')
    if (len(re.findall(r'\bretry\s*\{', config)) != 1 or
            len(re.findall(r'\bmaxRetries\b', config)) != 1 or
            len(re.findall(r'\bfailOnPassedAfterRetry\b', config)) != 1):
        raise SupervisorError('retry-policy-violation')

    gradle_files = []
    excluded = {'.git', '.gradle', 'build', 'node_modules', '.agent-runs', '.agent-state'}
    for parent, dirs, files in os.walk(root, followlinks=False):
        retained_dirs = []
        for name in dirs:
            if name in excluded:
                continue
            if (pathlib.Path(parent) / name).is_symlink():
                raise SupervisorError('retry-policy-violation')
            retained_dirs.append(name)
        dirs[:] = retained_dirs
        for name in files:
            if name.endswith('.gradle') or name.endswith('.gradle.kts'):
                path = pathlib.Path(parent) / name
                if path.is_symlink():
                    raise SupervisorError('retry-policy-violation')
                gradle_files.append(path)
                if len(gradle_files) > 512:
                    raise SupervisorError('retry-policy-violation')
    registered_gradle_files = {
        relative for relative in registered_inputs
        if relative.endswith(('.gradle', '.gradle.kts'))
    }
    discovered_gradle_files = {path.relative_to(root).as_posix() for path in gradle_files}
    if discovered_gradle_files != registered_gradle_files:
        raise SupervisorError('retry-policy-violation')
    config_file = root / config_path
    for path in gradle_files:
        if path == config_file:
            continue
        try:
            other = _read_regular_worktree_file(root, path.relative_to(root).as_posix()).decode('utf-8')
        except (OSError, UnicodeDecodeError, SupervisorError):
            raise SupervisorError('retry-policy-violation') from None
        if (re.search(r'\bretry\s*\{|\bmaxRetries\b|\bfailOnPassedAfterRetry\b', other) or
                'strictEvidenceGateEnabled' in other):
            raise SupervisorError('retry-policy-violation')

    gradle_property = f'-P{gate_property}'
    expected_gradle_invocations = 2 if 'postgres' in controls[0] else 1
    logical_wrapper = wrapper.replace('\\\r\n', ' ').replace('\\\n', ' ')
    gradle_commands = []
    forbidden_configuration_options = {
        '--init-script', '-I', '--settings-file', '-c', '--build-file', '-b',
        '--project-dir', '-p', '--include-build',
    }
    try:
        for line in logical_wrapper.splitlines():
            tokens = shlex.split(line, comments=True, posix=True)
            if './gradlew' not in tokens:
                continue
            if (not tokens or tokens[0] != './gradlew' or tokens.count('./gradlew') != 1 or
                    ':application:ecommerce:test' not in tokens):
                raise SupervisorError('retry-policy-violation')
            if any(token in forbidden_configuration_options or
                   token.startswith(('--init-script=', '--settings-file=', '--build-file=',
                                     '--project-dir=', '--include-build=', '-I', '-c', '-b', '-p'))
                   for token in tokens):
                raise SupervisorError('retry-policy-violation')
            properties = [token for token in tokens if token.startswith(gradle_property + '=')]
            if properties != [gradle_property + '=true']:
                raise SupervisorError('retry-policy-violation')
            gradle_commands.append(tokens)
    except ValueError:
        raise SupervisorError('retry-policy-violation') from None
    if len(gradle_commands) != expected_gradle_invocations:
        raise SupervisorError('retry-policy-violation')
    return {
        'schema_version': 1,
        'control_id': controls[0],
        'gate_id': gate_id,
        'command': command,
        'config_path': config_path,
        'config_sha256': hashlib.sha256(config_bytes).hexdigest(),
        'wrapper_sha256': hashlib.sha256(wrapper_bytes).hexdigest(),
        'registered_inputs_sha256': _retry_control_inputs_digest(controls[0]),
        'strict_max_retries': 0,
        'fail_on_passed_after_retry': True,
    }


class VerificationSupervisor:
    """Serialize admission, durable start, qualified launch, drainage and closure.

    A backend facade implements ``prepare``, ``launch``, ``inspect`` and
    ``cancel_and_drain``. Preparation must return a durable Phase-B execution
    identity and all-pass policy/qualification identities before this class
    publishes STARTED. The legacy callable path is retained only to record a
    known pre-launch refusal; it cannot bypass the qualified facade.
    """

    def __init__(self, store: VerificationStore):
        self.store = store
        self.inject_crash_at: str | None = None

    def _crash(self, boundary: str) -> None:
        if self.inject_crash_at == boundary:
            raise RuntimeError('injected-crash:' + boundary)

    @staticmethod
    def _outcome(result) -> str:
        if getattr(result, 'error', None):
            return 'ERROR'
        if getattr(result, 'timed_out', False):
            return 'TIMEOUT'
        return 'PASS' if getattr(result, 'exit_code', None) == 0 else 'FAIL'

    @staticmethod
    def _read_json(path: pathlib.Path) -> dict | None:
        try:
            value = json.loads(path.read_text(encoding='utf-8'))
            return value if isinstance(value, dict) else None
        except (OSError, ValueError):
            return None

    @classmethod
    def _launch_marker_state(cls, journal: pathlib.Path, started: dict) -> str:
        path = journal / 'launching.json'
        try:
            metadata = path.lstat()
        except FileNotFoundError:
            return 'absent'
        except OSError:
            return 'invalid'
        if not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode):
            return 'invalid'
        marker = cls._read_json(path)
        if (marker is None or marker.get('schema_version') not in {1, 2} or
                marker.get('execution_id') != started.get('execution_id') or
                marker.get('execution_identity') != started.get('execution_identity') or
                marker.get('launch_intent_hash') != started.get('launch_intent_hash')):
            return 'invalid'
        if marker['schema_version'] == 2 and (
                not isinstance(marker.get('reservation_id'), str) or
                re.fullmatch(r'launch-reservation-v1:sha256:[0-9a-f]{64}', marker['reservation_id']) is None or
                not isinstance(marker.get('consumption_id'), str) or
                re.fullmatch(r'launch-consumption-v1:sha256:[0-9a-f]{64}', marker['consumption_id']) is None):
            return 'invalid'
        if started.get('plan_id') is not None:
            required_binding = ('plan_id', 'unit_id', 'admission_id', 'admission_sha256',
                'reservation_transition_id', 'consumption_transition_id',
                'plan_acceptance_transition_id', 'lifecycle_generation', 'obligation_ids')
            if (any(key not in marker for key in required_binding) or
                    marker.get('plan_id') != started.get('plan_id') or
                    type(marker.get('lifecycle_generation')) is not int or
                    marker.get('lifecycle_generation') < 1 or
                    not isinstance(marker.get('obligation_ids'), list) or
                    not marker['obligation_ids'] or
                    marker['obligation_ids'] != sorted(set(marker['obligation_ids'])) or
                    any(not isinstance(item, str) or
                        re.fullmatch(r'verification-obligation-v2:sha256:[0-9a-f]{64}', item) is None
                        for item in marker['obligation_ids']) or
                    any(not isinstance(marker.get(key), str) or not marker[key]
                        for key in required_binding if key != 'lifecycle_generation')):
                return 'invalid'
        if marker['schema_version'] == 1 and started.get('plan_id') is not None:
            return 'invalid'
        return 'present'

    def _write_state(self, journal: pathlib.Path, name: str, record: dict) -> None:
        publish_create_once(journal / name, record, fault=self.store._fault)

    def _publish_drained(self, journal: pathlib.Path, started: dict, receipt_hash: str,
                         *, reason: str = 'backend-proven-drained') -> None:
        record = {'schema_version': 2, 'execution_id': started['execution_id'],
                  'repository_id': self.store.repository_id, 'backend': started['backend'],
                  'status': 'drained', 'reason': reason, 'terminal_receipt_hash': receipt_hash,
                  'ended_at': time.time()}
        publish_create_once(journal / 'drained.json', record, fault=self.store._fault)

    @lifecycle_admitted
    def execute(self, backend, *, worktree: pathlib.Path, family_id: str,
                attempt_id: str, gate_id: str, command: str, cwd: pathlib.Path,
                run_dir: pathlib.Path, timeout_seconds: float, sandbox_mode: str,
                environment=None, preflight=None, terminal_publisher=None,
                post_observer=None, recovery_observer=None, terminal_record_builder=None,
                input_fingerprint: str | None = None, profile_hash: str | None = None,
                critical: bool = False, retry_policy: str = 'forbid',
                retry_controls: tuple[str, ...] = (), failure_grant_id: str | None = None,
                candidate_identity: str | None = None, final_changed_surface_id: str | None = None,
                plan_id: str | None = None, retry_scope: dict | None = None,
                policy_checkpoint: str | None = None, launch_authorizer=None,
                repository_admission_id: str | None = None):
        if retry_policy not in {'forbid', 'allow'} or not isinstance(critical, bool):
            raise SupervisorError('invalid-retry-policy')
        if not isinstance(retry_controls, (tuple, list)) or any(not isinstance(x, str) for x in retry_controls):
            raise SupervisorError('invalid-retry-controls')
        retry_controls = tuple(retry_controls)
        if len(set(retry_controls)) != len(retry_controls):
            raise SupervisorError('invalid-retry-controls')
        control_evidence = _registered_gradle_retry_evidence(
            worktree, gate_id, command, retry_controls)
        retry_facts = _retry_policy_facts(retry_policy, critical, list(retry_controls),
                                          gate_id=gate_id, command=command,
                                          control_evidence=control_evidence)
        if retry_facts is None or (critical and retry_policy == 'forbid' and not retry_facts['retry_free']):
            raise SupervisorError('retry-policy-violation')
        retry_policy_proof = retry_facts
        intent_hash = hashlib.sha256(command.encode('utf-8')).hexdigest()
        derived_failure_fingerprint = candidate_failure_fingerprint({
            'repository_id': self.store.repository_id, 'family_id': family_id,
            'policy_checkpoint': policy_checkpoint, 'profile_hash': profile_hash, 'gate_id': gate_id,
            'candidate_identity': candidate_identity,
            'final_changed_surface_id': final_changed_surface_id,
            'launch_intent_hash': intent_hash, 'input_fingerprint': input_fingerprint})
        execution_id = hashlib.sha256(canonical({
            'repository_id': self.store.repository_id, 'family_id': family_id,
            'attempt_id': attempt_id, 'gate_id': gate_id, 'command_hash': intent_hash,
        })).hexdigest()[:32]
        if not hasattr(backend, 'prepare'):
            raise SupervisorError('EXECUTION_BACKEND_REQUIRED')

        # A competing request must observe the in-flight admission and fail
        # before waiting long enough to become a new, sequential execution.
        # Recovery remains an explicit operation and may take the normal lock.
        with repository_lock(self.store.root, timeout=0) as runtime_lock:
            self._recover_locked(lambda _record: backend, recovery_observer, runtime_lock=runtime_lock)
            self.store.admit_repository_verification()
            self._crash('after-admission')
            ready = preflight() if preflight else None
            fence_context = {'repository_id': self.store.repository_id, 'profile_hash': profile_hash,
                             'gate_id': gate_id, 'fingerprint': input_fingerprint or derived_failure_fingerprint}
            has_fence_context = all(isinstance(fence_context.get(key), str) and fence_context[key]
                                    for key in ('profile_hash', 'gate_id', 'fingerprint'))
            active_failure = None
            unresolved_failure = None
            if derived_failure_fingerprint is not None and any(
                    not isinstance(failure.get('context', {}).get('fingerprint'), str) or
                    not failure['context']['fingerprint']
                    for failure in self.store.active_failure_fences()):
                raise SupervisorError('CRITICAL_FAILURE_CONTEXT_REQUIRED')
            if has_fence_context:
                unresolved_failure = self.store.current_failure(fence_context)
                latest_failure = self.store.failure_for_scope(fence_context)
                # A PASS-resolved failure permits exact evidence reuse, but a
                # mandatory fresh RUN still needs a new one-shot grant.
                is_reuse = ready is not None and ready.action == 'REUSE'
                active_failure = unresolved_failure if is_reuse else latest_failure
                if is_reuse and unresolved_failure is None:
                    return None, ready
                if active_failure is not None and not failure_grant_id:
                    raise SupervisorError('CRITICAL_FAILURE_FENCE_ACTIVE')
                if active_failure is None and failure_grant_id:
                    raise SupervisorError('FAILURE_GRANT_STALE')
            elif self.store.active_failure_fences():
                # An entry point without trusted plan identity cannot prove it
                # is independent of an active failure fence.
                raise SupervisorError('CRITICAL_FAILURE_CONTEXT_REQUIRED')
            elif failure_grant_id:
                raise SupervisorError('FAILURE_GRANT_SCOPE_MISMATCH')
            if ready is not None and ready.action == 'REUSE':
                # A scoped retry grant turns an otherwise reusable plan into
                # exactly one fresh execution opportunity.
                try:
                    ready = replace(ready, action='RUN', reason='failure-grant-retry')
                except TypeError:
                    ready.action = 'RUN'
                    ready.reason = 'failure-grant-retry'
            backend_run_dir = pathlib.Path(run_dir)
            if control_evidence is not None:
                # A preceding gate can write Gradle user-home init scripts under
                # the shared plan run directory. Give each execution a stable,
                # isolated namespace so prepare/start crash replay retains its
                # backend policy identity without reusing another gate's home.
                backend_run_dir = backend_run_dir / 'registered-gradle' / execution_id
                if environment and any(key in environment for key in
                                       ('GRADLE_USER_HOME', 'GRADLE_OPTS', 'JAVA_OPTS')):
                    raise SupervisorError('retry-policy-violation')
                if _gradle_user_home_has_init_scripts(backend_run_dir):
                    raise SupervisorError('retry-policy-violation')
            try:
                prepared = backend.prepare(worktree=pathlib.Path(worktree), cwd=pathlib.Path(cwd),
                    run_dir=backend_run_dir, repository_id=self.store.repository_id,
                    command=command, sandbox_mode=sandbox_mode, environment=environment,
                    control_root=self.store.root, execution_id=execution_id)
                self._crash('after-prepare')
            except Exception as exc:
                if isinstance(exc, SupervisorError):
                    raise
                raise SupervisorError(getattr(exc, 'reason_code', None) or str(exc) or
                                       'EXECUTION_PREPARE_FAILED') from None
            identity = getattr(prepared, 'identity', None)
            if hasattr(identity, 'to_dict'):
                identity = identity.to_dict()
            if not isinstance(identity, dict) or not identity:
                raise SupervisorError('EXECUTION_IDENTITY_UNAVAILABLE')
            backend_identity = (getattr(prepared, 'backend_identity', None) or
                                identity.get('backend_identity'))
            policy_identity = (getattr(prepared, 'policy_identity', None) or
                               identity.get('policy_identity'))
            qualification = getattr(prepared, 'qualification_fingerprint', None)
            if not all(isinstance(value, str) and value for value in
                       (backend_identity, policy_identity, qualification or policy_identity)):
                raise SupervisorError('EXECUTION_IDENTITY_UNAVAILABLE')

            retry_consumption = None
            if active_failure is not None:
                exact_context = {**fence_context, 'policy_identity': policy_identity,
                                 'backend_identity': backend_identity}
                if not isinstance(retry_scope, dict):
                    raise SupervisorError('FAILURE_GRANT_SCOPE_MISMATCH')
                try:
                    retry_consumption = self.store.consume_failure_grant(
                        failure_grant_id, failure_id=active_failure['failure_id'],
                        context=exact_context, retry_scope=retry_scope, execution_id=execution_id,
                        lock_held=True)
                except StoreError as exc:
                    raise SupervisorError(str(exc)) from None
                self._crash('after-grant-consumed')

            journal = self.store.executions / execution_id
            existing = self._read_json(journal / 'started.json')
            if existing is not None:
                if (existing.get('launch_intent_hash') != intent_hash or
                        existing.get('family_id') != family_id or existing.get('attempt_id') != attempt_id or
                        existing.get('gate_id') != gate_id or existing.get('execution_identity') != identity or
                        existing.get('policy_identity') != policy_identity or
                        existing.get('backend_identity') != backend_identity or
                        existing.get('candidate_identity') != candidate_identity or
                        existing.get('final_changed_surface_id') != final_changed_surface_id or
                        existing.get('retry_policy_proof') != retry_policy_proof):
                    raise StoreError('immutable-record-collision')
                terminal = self.store._read_execution_terminal(journal, existing)
                if terminal is None:
                    raise StoreError('verification-owned')
                observation = self._read_json(journal / 'observation.json') or {}
                result = self._result_from_observation(observation, existing)
                self._terminalize_lifecycle_authority(terminal, runtime_lock)
                if terminal_publisher:
                    terminal_publisher(result, ready, terminal)
                self.store.reconstruct_execution_terminals()
                return result, ready
            journal.mkdir(parents=True, exist_ok=True)
            started = {
                'schema_version': 2, 'lifecycle_protocol': 1, 'execution_id': execution_id,
                'repository_id': self.store.repository_id, 'worktree': str(pathlib.Path(worktree).resolve()),
                'family_id': family_id, 'attempt_id': attempt_id, 'gate_id': gate_id,
                'backend': backend_identity, 'backend_identity': backend_identity,
                'policy_identity': policy_identity, 'qualification_fingerprint': qualification or policy_identity,
                'execution_identity': identity, 'input_fingerprint': input_fingerprint,
                'candidate_identity': candidate_identity,
                'final_changed_surface_id': final_changed_surface_id,
                'profile_hash': profile_hash, 'policy_checkpoint': policy_checkpoint,
                'critical': bool(critical),
                'plan_id': plan_id,
                'retry_policy': retry_policy, 'retry_controls': list(retry_controls),
                'retry_policy_proof': retry_policy_proof,
                'predecessor_failure_id': active_failure['failure_id'] if retry_consumption else None,
                'consumption_id': retry_consumption['consumption_id'] if retry_consumption else None,
                'retry_proof': ({'failure_id': active_failure['failure_id'],
                    'grant_id': failure_grant_id, 'consumption_id': retry_consumption['consumption_id'],
                    'grant_hash': retry_consumption['grant_hash'], 'retry_scope': retry_scope}
                    if retry_consumption else None),
                'retry_scope': retry_scope if retry_consumption else None,
                'launch_intent_hash': intent_hash, 'command_identity': intent_hash,
                'supervisor_generation': uuid.uuid4().hex, 'protocol_version': 1,
                'started_at': time.time(),
            }
            if derived_failure_fingerprint is not None:
                started['failure_fingerprint'] = derived_failure_fingerprint
            started_hash = publish_create_once(journal / 'started.json', started, fault=self.store._fault)
            _fsync_directory(self.store.executions)
            self._crash('after-started')

            launch_authority = None
            if launch_authorizer is not None:
                if not isinstance(repository_admission_id, str) or not repository_admission_id:
                    raise SupervisorError('VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE')
                # Lifecycle CAS must never nest the runtime ownership lock. Keep
                # the repository-wide lifecycle admission active while yielding
                # only this kernel lock, then revalidate the winner-only token.
                runtime_lock.release()
                try:
                    launch_authority = launch_authorizer(repository_admission_id, execution_id)
                finally:
                    runtime_lock.reacquire(timeout=0)
                if (not isinstance(launch_authority, dict) or
                        not isinstance(launch_authority.get('reservation_id'), str) or
                        re.fullmatch(r'launch-reservation-v1:sha256:[0-9a-f]{64}',
                                     launch_authority['reservation_id']) is None or
                        not isinstance(launch_authority.get('consumption_id'), str) or
                        re.fullmatch(r'launch-consumption-v1:sha256:[0-9a-f]{64}',
                                     launch_authority['consumption_id']) is None or
                        not validate_launch_capability(
                            launch_authority.get('capability'),
                            reservation_id=launch_authority['reservation_id'],
                            consumption_id=launch_authority['consumption_id'])):
                    raise SupervisorError('VERIFICATION_LAUNCH_CAPABILITY_INVALID')
                if plan_id is not None:
                    required_binding = ('plan_id', 'unit_id', 'admission_id', 'admission_sha256',
                        'reservation_transition_id', 'consumption_transition_id',
                        'plan_acceptance_transition_id', 'lifecycle_generation', 'obligation_ids')
                    if (any(key not in launch_authority for key in required_binding) or
                            launch_authority.get('plan_id') != plan_id or
                            type(launch_authority.get('lifecycle_generation')) is not int or
                            launch_authority.get('lifecycle_generation') < 1 or
                            not isinstance(launch_authority.get('obligation_ids'), list) or
                            not launch_authority['obligation_ids'] or
                            launch_authority['obligation_ids'] != sorted(set(launch_authority['obligation_ids'])) or
                            any(not isinstance(item, str) or re.fullmatch(
                                r'verification-obligation-v2:sha256:[0-9a-f]{64}', item) is None
                                for item in launch_authority['obligation_ids']) or
                            any(not isinstance(launch_authority.get(key), str) or
                                not launch_authority[key]
                                for key in required_binding if key not in {
                                    'lifecycle_generation', 'obligation_ids'})):
                        raise SupervisorError('VERIFICATION_LAUNCH_AUTHORITY_INVALID')
            elif plan_id is not None:
                raise SupervisorError('VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE')

            launch_marker = {'schema_version': 2 if launch_authority else 1,
                             'execution_id': execution_id,
                             'execution_identity': identity, 'launch_intent_hash': intent_hash,
                             'launch_requested_at': time.time()}
            if launch_authority:
                launch_marker.update({'reservation_id': launch_authority['reservation_id'],
                                      'consumption_id': launch_authority['consumption_id']})
                launch_marker.update({key: launch_authority[key] for key in (
                    'plan_id', 'unit_id', 'obligation_ids', 'admission_id', 'admission_sha256',
                    'reservation_transition_id', 'consumption_transition_id',
                    'plan_acceptance_transition_id', 'lifecycle_generation') if key in launch_authority})
            publish_create_once(journal / 'launching.json', launch_marker, fault=self.store._fault)
            self._crash('during-launch')
            try:
                result = backend.launch(prepared, command=command, cwd=pathlib.Path(cwd),
                    timeout_seconds=timeout_seconds, environment=environment)
            except Exception as exc:
                raise SupervisorError(getattr(exc, 'reason_code', None) or str(exc) or
                                       'EXECUTION_LAUNCH_UNCERTAIN') from None
            self._crash('after-launch')

            observed = self._inspect(backend, identity)
            cancelled = bool(getattr(result, 'timed_out', False))
            if cancelled or observed.status not in {'DRAINED', 'NOT_LAUNCHED'}:
                self._write_state(journal, 'draining.json', {'schema_version': 1,
                    'execution_id': execution_id, 'state': 'DRAINING', 'at': time.time()})
                self._crash('after-draining')
                if observed.status in {'ACTIVE', 'NOT_DRAINED'} or cancelled:
                    self._write_state(journal, 'cancelling.json', {'schema_version': 1,
                        'execution_id': execution_id, 'state': 'CANCELLING', 'at': time.time()})
                    self._crash('after-cancel-started')
                    observed = backend.cancel_and_drain(identity, timeout_seconds)
                    cancelled = True
            if observed.status != 'DRAINED':
                raise SupervisorError('EXECUTION_RECONCILIATION_UNCERTAIN' if observed.status == 'UNCERTAIN'
                                      else 'EXECUTION_NOT_DRAINED')

            self._crash('after-drain-proof')
            post_observation = post_observer(result, ready) if post_observer else {}
            observation = {
                'schema_version': 2, 'execution_id': execution_id,
                'exit_code': getattr(result, 'exit_code', None),
                'timed_out': bool(getattr(result, 'timed_out', False)), 'cancelled': cancelled,
                'output_observation': 'CAPTURED', 'output_persistence': 'OMITTED',
                'started_at': getattr(result, 'started_at', started['started_at']),
                'ended_at': getattr(result, 'ended_at', time.time()), 'post_observation': post_observation,
                'result': self._outcome(result),
            }
            if post_observation.get('stable') is False and observation['result'] == 'PASS':
                observation['result'] = 'ERROR'
            publish_create_once(journal / 'observation.json', observation, fault=self.store._fault)
            self._crash('after-drained')
            terminal_material = (terminal_record_builder(result, ready, post_observation)
                                 if terminal_record_builder and observation['result'] == 'PASS' else None)
            receipt = {
                'schema_version': 2, 'execution_id': execution_id,
                'repository_id': self.store.repository_id, 'started_hash': started_hash,
                'execution_identity': identity, 'backend_identity': backend_identity,
                'candidate_identity': candidate_identity,
                'final_changed_surface_id': final_changed_surface_id,
                'policy_identity': policy_identity, 'command_identity': intent_hash,
                'retry_policy_proof': retry_policy_proof,
                'harness_invocation_upper_bound': 1,
                'exit_code': observation['exit_code'], 'timed_out': observation['timed_out'],
                'cancelled': cancelled, 'drainage': 'DRAINED',
                'output_observation': 'CAPTURED', 'output_persistence': 'OMITTED',
                'post_observation': post_observation, 'result': observation['result'],
                'ended_at': observation['ended_at'],
            }
            if launch_authority:
                receipt['launch_reservation_id'] = launch_authority['reservation_id']
                receipt['launch_consumption_id'] = launch_authority['consumption_id']
                receipt.update({key: launch_authority[key] for key in (
                    'plan_id', 'unit_id', 'obligation_ids', 'admission_id', 'admission_sha256',
                    'reservation_transition_id', 'consumption_transition_id',
                    'plan_acceptance_transition_id', 'lifecycle_generation') if key in launch_authority})
            if terminal_material is not None:
                receipt['verification_evidence'] = terminal_material
            self.store.publish_execution_terminal(receipt)
            self._reconcile_phase_d(receipt, started)
            self._crash('after-terminal')
            if terminal_publisher:
                terminal_publisher(result, ready, receipt)
            self._crash('after-evidence-projection')
            terminal = next(item for item in self.store.reconstruct_execution_terminals(rebuild=False)
                            if item['execution_id'] == execution_id)
            self._terminalize_lifecycle_authority(terminal, runtime_lock)
            self.store.rebuild_terminal_evidence(terminal)
            self.store.publish_execution_projection(terminal)
            self._crash('after-index-projection')
            self._publish_drained(journal, started, terminal['receipt_hash'])
            return result, ready

    @staticmethod
    def _inspect(backend, identity):
        try:
            return backend.inspect(identity)
        except Exception:
            return type('Reconciliation', (), {'status': 'UNCERTAIN',
                'reason_code': 'EXECUTION_RECONCILIATION_UNCERTAIN', 'evidence': 'backend-inspection-failed'})()

    @staticmethod
    def _result_from_observation(observation: dict, started: dict):
        return type('RecoveredCommandResult', (), {
            'argv': (), 'cwd': started.get('worktree', ''),
            'started_at': observation.get('started_at', started.get('started_at', 0)),
            'ended_at': observation.get('ended_at', started.get('started_at', 0)),
            'duration_seconds': max(0.0, observation.get('ended_at', 0) - observation.get('started_at', 0)),
            'exit_code': observation.get('exit_code'), 'stdout': '', 'stderr': '',
            # CAPTURED records the execution-time observation; raw streams are
            # intentionally omitted, so a recovered result has no diagnostics.
            'output_persistence': observation.get('output_persistence', 'OMITTED'),
            'timed_out': observation.get('timed_out', False),
            'error': ('EXECUTION_ABORTED' if observation.get('result') == 'ABORTED' else
                      'recovered-output-unavailable' if observation.get('output_observation') != 'CAPTURED' else None),
            'sandbox_backend': started.get('backend'), 'strong_isolation': True,
            'protected_paths': True, 'descendant_containment': 'strong',
        })()

    def _reconciler(self, backend):
        def reconcile(record):
            identity = record.get('execution_identity')
            if not identity:
                return ReconciliationOutcome.UNCERTAIN
            result = self._inspect(backend, identity)
            return {'DRAINED': ReconciliationOutcome.PROVEN_DRAINED,
                    'ACTIVE': ReconciliationOutcome.STILL_ACTIVE,
                    'NOT_DRAINED': ReconciliationOutcome.STILL_ACTIVE,
                    'UNCERTAIN': ReconciliationOutcome.UNCERTAIN}.get(
                        result.status, ReconciliationOutcome.UNCERTAIN)
        return reconcile

    def recover(self, backend_factory=None, recovery_observer=None) -> tuple[RecoveryResult, ...]:
        """Reconcile every unresolved STARTED record using fresh durable identities."""
        with repository_lock(self.store.root) as runtime_lock:
            recovered = list(self._recover_locked(backend_factory, recovery_observer,
                                                  runtime_lock=runtime_lock))
        admission = RepositoryAdmission(self.store.lifecycle_root, self.store.repository_id)
        active = admission.active()
        if isinstance(active, dict) and active.get('kind') == 'verification':
            execution_id = active.get('id')
            journal = self.store.executions / execution_id if isinstance(execution_id, str) else None
            started = journal / 'started.json' if journal is not None else None
            if started is not None and not started.exists() and not started.is_symlink():
                try:
                    released = admission.release_unstarted_if_owner_dead(execution_id)
                except AdmissionConflict as exc:
                    raise StoreError(str(exc)) from None
                if released:
                    recovered.append(RecoveryResult(execution_id, SupervisorState.ABORTED_PREPARED,
                                                    'UNSTARTED_RESERVATION_RELEASED'))
                    return tuple(recovered)
            closed = journal is not None and (journal / 'drained.json').is_file()
            outcome = next((item for item in recovered if item.execution_id == execution_id), None)
            if closed and outcome and outcome.state in {SupervisorState.TERMINAL,
                                                         SupervisorState.ABORTED_PREPARED}:
                admission.release(execution_id)
        return tuple(recovered)

    def _recover_locked(self, backend_factory=None, recovery_observer=None, *, runtime_lock=None) -> tuple[RecoveryResult, ...]:
        recovered = []
        for journal, started, closure in self.store._scan_executions():
            if closure is not None:
                continue
            execution_id = started['execution_id']
            if (started.get('predecessor_failure_id') is not None or
                    started.get('consumption_id') is not None or started.get('retry_proof') is not None):
                try:
                    self.store.validate_started_retry_proof(started)
                except StoreError:
                    recovered.append(RecoveryResult(execution_id, SupervisorState.UNCERTAIN,
                        'RETRY_PROOF_MISSING'))
                    continue
            terminal = self._read_json(journal / 'terminal.json')
            if terminal is not None:
                terminal = next(item for item in self.store.reconstruct_execution_terminals(rebuild=True)
                                if item['execution_id'] == execution_id)
                self._reconcile_phase_d(terminal, started)
                if runtime_lock is not None:
                    self._terminalize_lifecycle_authority(terminal, runtime_lock)
                self.store.rebuild_terminal_evidence(terminal)
                self._publish_drained(journal, started, terminal['receipt_hash'],
                                      reason='recovered-terminal-closure')
                recovered.append(RecoveryResult(execution_id, SupervisorState.TERMINAL,
                                                 'TERMINAL_RECONSTRUCTED', terminal))
                continue
            # No launch-request marker proves the trusted supervisor never called
            # the Phase-B launch primitive. Persist an abort, never inferred success.
            launch_marker_state = self._launch_marker_state(journal, started)
            if launch_marker_state == 'invalid':
                recovered.append(RecoveryResult(execution_id, SupervisorState.UNCERTAIN,
                                                 'EXECUTION_LAUNCH_MARKER_INVALID'))
                continue
            if launch_marker_state == 'absent':
                recovered.append(self._abort_prepared(journal, started, 'prepared-never-launched'))
                continue
            if backend_factory is None:
                recovered.append(RecoveryResult(execution_id, SupervisorState.UNCERTAIN,
                                                 'EXECUTION_RECOVERY_REQUIRED'))
                continue
            backend = backend_factory(started)
            identity = started['execution_identity']
            inspection = self._inspect(backend, identity)
            status = inspection.status
            if (status == 'DRAINED' and
                    getattr(inspection, 'reason_code', None) in
                    {'NOT_LAUNCHED', 'LAUNCH_FAILED_BEFORE_PAYLOAD'} and
                    self._read_json(journal / 'observation.json') is None):
                recovered.append(self._abort_prepared(journal, started, 'backend-proved-not-launched'))
                continue
            if (journal / 'cancelling.json').exists() and status in {'ACTIVE', 'NOT_DRAINED'}:
                status = backend.cancel_and_drain(identity, 5).status
            if status in {'ACTIVE', 'NOT_DRAINED'}:
                recovered.append(RecoveryResult(execution_id, SupervisorState.ACTIVE,
                                                 'EXECUTION_ALREADY_ACTIVE'))
                continue
            if status == 'UNCERTAIN':
                recovered.append(RecoveryResult(execution_id, SupervisorState.UNCERTAIN,
                                                 'EXECUTION_RECONCILIATION_UNCERTAIN'))
                continue
            observation = self._read_json(journal / 'observation.json')
            if observation is None:
                if (journal / 'cancelling.json').exists() and status == 'DRAINED':
                    # Cancellation interrupted before observation. Record that
                    # output and post-input evidence are unavailable; do not
                    # substitute an empty-output hash for unknown data.
                    if recovery_observer is None:
                        recovered.append(RecoveryResult(execution_id, SupervisorState.UNCERTAIN,
                            'POST_EXECUTION_OBSERVATION_UNAVAILABLE'))
                        continue
                    try:
                        post_observation = recovery_observer(started)
                    except Exception:
                        post_observation = None
                    if not isinstance(post_observation, dict):
                        recovered.append(RecoveryResult(execution_id, SupervisorState.UNCERTAIN,
                            'POST_EXECUTION_OBSERVATION_UNAVAILABLE'))
                        continue
                    observation = {'schema_version': 2, 'exit_code': None,
                        'timed_out': False, 'cancelled': True,
                        'output_observation': 'UNAVAILABLE', 'output_persistence': 'UNAVAILABLE',
                        'post_observation': post_observation,
                        'result': 'ABORTED', 'ended_at': time.time()}
                else:
                    recovered.append(RecoveryResult(execution_id, SupervisorState.UNCERTAIN,
                                                     'TERMINAL_OBSERVATION_MISSING'))
                    continue
            receipt = self._terminal_from_observation(journal, started, identity, observation)
            self.store.publish_execution_terminal(receipt)
            self._reconcile_phase_d(receipt, started)
            terminal = next(item for item in self.store.reconstruct_execution_terminals(rebuild=True)
                            if item['execution_id'] == execution_id)
            self._publish_drained(journal, started, terminal['receipt_hash'],
                                  reason='recovered-proven-drainage')
            recovered.append(RecoveryResult(execution_id, SupervisorState.TERMINAL,
                                             'TERMINAL_RECONSTRUCTED', terminal))
        return tuple(recovered)

    def _terminalize_lifecycle_authority(self, terminal: dict, runtime_lock) -> None:
        if not terminal.get('plan_id') or not terminal.get('launch_consumption_id'):
            return
        runtime_lock.release()
        try:
            import harness
            harness.terminalize_verification_execution(
                self.store.lifecycle_root.parent, terminal,
                repository_admission_id=terminal.get('execution_id'))
        finally:
            runtime_lock.reacquire(timeout=0)

    def _reconcile_phase_d(self, terminal: dict, started: dict) -> None:
        """Rebuild critical-failure authority from immutable execution truth."""
        if (terminal.get('result') == 'FAIL' and not terminal.get('timed_out') and
                not terminal.get('cancelled') and started.get('critical') is True):
            self.store.publish_critical_failure(terminal['execution_id'],
                predecessor_failure_id=started.get('predecessor_failure_id'),
                consumption_id=started.get('consumption_id'))
        retry = started.get('retry_proof')
        if terminal.get('result') == 'PASS' and isinstance(retry, dict):
            self.store.publish_failure_resolution(retry['failure_id'], terminal['execution_id'], result='PASS')

    def _abort_prepared(self, journal: pathlib.Path, started: dict, reason: str) -> RecoveryResult:
        execution_id = started['execution_id']
        observation = {'schema_version': 2, 'exit_code': None, 'timed_out': False, 'cancelled': False,
            'output_observation': 'UNAVAILABLE', 'output_persistence': 'UNAVAILABLE',
            'post_observation': {},
            'result': 'ABORTED', 'ended_at': time.time()}
        receipt = self._terminal_from_observation(journal, started,
            started.get('execution_identity'), observation)
        self.store.publish_execution_terminal(receipt)
        terminal = next(item for item in self.store.reconstruct_execution_terminals(rebuild=True)
                        if item['execution_id'] == execution_id)
        self._publish_drained(journal, started, terminal['receipt_hash'], reason=reason)
        return RecoveryResult(execution_id, SupervisorState.ABORTED_PREPARED,
                              'PREPARED_NOT_LAUNCHED', terminal)

    def _terminal_from_observation(self, journal, started, identity, observation):
        return {'schema_version': 2, 'execution_id': started['execution_id'],
            'repository_id': self.store.repository_id,
            'started_hash': hashlib.sha256((journal / 'started.json').read_bytes()).hexdigest(),
            'execution_identity': identity or {'state': 'NOT_PREPARED'},
            'candidate_identity': started.get('candidate_identity'),
            'final_changed_surface_id': started.get('final_changed_surface_id'),
            'backend_identity': started.get('backend_identity', started['backend']),
            'policy_identity': started.get('policy_identity', 'unqualified'),
            'command_identity': started['launch_intent_hash'],
            'retry_policy_proof': started.get('retry_policy_proof'),
            'harness_invocation_upper_bound': 1 if (journal / 'launching.json').exists() else 0,
            'exit_code': observation.get('exit_code'), 'timed_out': observation.get('timed_out', False),
            'cancelled': observation.get('cancelled', False), 'drainage': 'DRAINED',
            'output_observation': observation.get('output_observation', 'CAPTURED'),
            'output_persistence': ('UNAVAILABLE' if observation.get('output_observation') == 'UNAVAILABLE'
                                   else 'OMITTED'),
            'post_observation': observation.get('post_observation', {}),
            'result': observation.get('result', 'ABORTED'), 'ended_at': observation.get('ended_at', time.time())}
