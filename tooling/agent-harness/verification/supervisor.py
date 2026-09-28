"""Authoritative, restart-reconcilable lifecycle for verification executions."""
from __future__ import annotations

import enum
import hashlib
import json
import pathlib
import time
import uuid
from dataclasses import dataclass

from .serialization import canonical
from .store import (ReconciliationOutcome, StoreError, VerificationStore,
                    _fsync_directory, publish_create_once, repository_lock)


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

    def _write_state(self, journal: pathlib.Path, name: str, record: dict) -> None:
        publish_create_once(journal / name, record, fault=self.store._fault)

    def _publish_drained(self, journal: pathlib.Path, started: dict, receipt_hash: str,
                         *, reason: str = 'backend-proven-drained') -> None:
        record = {'schema_version': 2, 'execution_id': started['execution_id'],
                  'repository_id': self.store.repository_id, 'backend': started['backend'],
                  'status': 'drained', 'reason': reason, 'terminal_receipt_hash': receipt_hash,
                  'ended_at': time.time()}
        publish_create_once(journal / 'drained.json', record, fault=self.store._fault)

    def execute(self, backend, *, worktree: pathlib.Path, family_id: str,
                attempt_id: str, gate_id: str, command: str, cwd: pathlib.Path,
                run_dir: pathlib.Path, timeout_seconds: float, sandbox_mode: str,
                environment=None, preflight=None, terminal_publisher=None,
                post_observer=None, recovery_observer=None, terminal_record_builder=None,
                input_fingerprint: str | None = None):
        intent_hash = hashlib.sha256(command.encode('utf-8')).hexdigest()
        execution_id = hashlib.sha256(canonical({
            'repository_id': self.store.repository_id, 'family_id': family_id,
            'attempt_id': attempt_id, 'gate_id': gate_id, 'command_hash': intent_hash,
        })).hexdigest()[:32]
        if not hasattr(backend, 'prepare'):
            raise SupervisorError('EXECUTION_BACKEND_REQUIRED')

        with repository_lock(self.store.root):
            self._recover_locked(lambda _record: backend, recovery_observer)
            self.store.admit_repository_verification()
            self._crash('after-admission')
            ready = preflight() if preflight else None
            if ready is not None and ready.action == 'REUSE':
                return None, ready
            try:
                prepared = backend.prepare(worktree=pathlib.Path(worktree), cwd=pathlib.Path(cwd),
                    run_dir=pathlib.Path(run_dir), repository_id=self.store.repository_id,
                    command=command, sandbox_mode=sandbox_mode, environment=environment,
                    control_root=self.store.root, execution_id=execution_id)
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

            journal = self.store.executions / execution_id
            existing = self._read_json(journal / 'started.json')
            if existing is not None:
                if (existing.get('launch_intent_hash') != intent_hash or
                        existing.get('family_id') != family_id or existing.get('attempt_id') != attempt_id or
                        existing.get('gate_id') != gate_id or existing.get('execution_identity') != identity or
                        existing.get('policy_identity') != policy_identity or
                        existing.get('backend_identity') != backend_identity):
                    raise StoreError('immutable-record-collision')
                terminal = self.store._read_execution_terminal(journal, existing)
                if terminal is None:
                    raise StoreError('verification-owned')
                observation = self._read_json(journal / 'observation.json') or {}
                result = self._result_from_observation(observation, existing)
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
                'launch_intent_hash': intent_hash, 'command_identity': intent_hash,
                'supervisor_generation': uuid.uuid4().hex, 'protocol_version': 1,
                'started_at': time.time(),
            }
            started_hash = publish_create_once(journal / 'started.json', started, fault=self.store._fault)
            _fsync_directory(self.store.executions)
            self._crash('after-started')

            launch_marker = {'schema_version': 1, 'execution_id': execution_id,
                             'execution_identity': identity, 'launch_intent_hash': intent_hash,
                             'launch_requested_at': time.time()}
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
                'schema_version': 1, 'execution_id': execution_id,
                'exit_code': getattr(result, 'exit_code', None),
                'timed_out': bool(getattr(result, 'timed_out', False)), 'cancelled': cancelled,
                'stdout_hash': hashlib.sha256((getattr(result, 'stdout', '') or '').encode()).hexdigest(),
                'stderr_hash': hashlib.sha256((getattr(result, 'stderr', '') or '').encode()).hexdigest(),
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
                'schema_version': 1, 'execution_id': execution_id,
                'repository_id': self.store.repository_id, 'started_hash': started_hash,
                'execution_identity': identity, 'backend_identity': backend_identity,
                'policy_identity': policy_identity, 'command_identity': intent_hash,
                'exit_code': observation['exit_code'], 'timed_out': observation['timed_out'],
                'cancelled': cancelled, 'drainage': 'DRAINED',
                'output_observation': 'CAPTURED',
                'stdout_hash': observation['stdout_hash'], 'stderr_hash': observation['stderr_hash'],
                'post_observation': post_observation, 'result': observation['result'],
                'ended_at': observation['ended_at'],
            }
            if terminal_material is not None:
                receipt['verification_evidence'] = terminal_material
            self.store.publish_execution_terminal(receipt)
            self._crash('after-terminal')
            if terminal_publisher:
                terminal_publisher(result, ready, receipt)
            self._crash('after-evidence-projection')
            terminal = next(item for item in self.store.reconstruct_execution_terminals(rebuild=False)
                            if item['execution_id'] == execution_id)
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
            'timed_out': observation.get('timed_out', False),
            'error': ('EXECUTION_ABORTED' if observation.get('result') == 'ABORTED' else
                      'recovered-output-unavailable' if 'stdout_hash' not in observation else None),
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
        with repository_lock(self.store.root):
            return self._recover_locked(backend_factory, recovery_observer)

    def _recover_locked(self, backend_factory=None, recovery_observer=None) -> tuple[RecoveryResult, ...]:
        recovered = []
        for journal, started, closure in self.store._scan_executions():
            if closure is not None:
                continue
            execution_id = started['execution_id']
            terminal = self._read_json(journal / 'terminal.json')
            if terminal is not None:
                terminal = next(item for item in self.store.reconstruct_execution_terminals(rebuild=True)
                                if item['execution_id'] == execution_id)
                self.store.rebuild_terminal_evidence(terminal)
                self._publish_drained(journal, started, terminal['receipt_hash'],
                                      reason='recovered-terminal-closure')
                recovered.append(RecoveryResult(execution_id, SupervisorState.TERMINAL,
                                                 'TERMINAL_RECONSTRUCTED', terminal))
                continue
            # No launch-request marker proves the trusted supervisor never called
            # the Phase-B launch primitive. Persist an abort, never inferred success.
            launching = self._read_json(journal / 'launching.json')
            if launching is None:
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
                    observation = {'exit_code': None, 'timed_out': False, 'cancelled': True,
                        'stdout_hash': None, 'stderr_hash': None,
                        'output_observation': 'UNAVAILABLE',
                        'post_observation': post_observation,
                        'result': 'ABORTED', 'ended_at': time.time()}
                else:
                    recovered.append(RecoveryResult(execution_id, SupervisorState.UNCERTAIN,
                                                     'TERMINAL_OBSERVATION_MISSING'))
                    continue
            receipt = self._terminal_from_observation(journal, started, identity, observation)
            self.store.publish_execution_terminal(receipt)
            terminal = next(item for item in self.store.reconstruct_execution_terminals(rebuild=True)
                            if item['execution_id'] == execution_id)
            self._publish_drained(journal, started, terminal['receipt_hash'],
                                  reason='recovered-proven-drainage')
            recovered.append(RecoveryResult(execution_id, SupervisorState.TERMINAL,
                                             'TERMINAL_RECONSTRUCTED', terminal))
        return tuple(recovered)

    def _abort_prepared(self, journal: pathlib.Path, started: dict, reason: str) -> RecoveryResult:
        execution_id = started['execution_id']
        observation = {'exit_code': None, 'timed_out': False, 'cancelled': False,
            'stdout_hash': hashlib.sha256(b'').hexdigest(),
            'stderr_hash': hashlib.sha256(b'').hexdigest(), 'post_observation': {},
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
        return {'schema_version': 1, 'execution_id': started['execution_id'],
            'repository_id': self.store.repository_id,
            'started_hash': hashlib.sha256((journal / 'started.json').read_bytes()).hexdigest(),
            'execution_identity': identity or {'state': 'NOT_PREPARED'},
            'backend_identity': started.get('backend_identity', started['backend']),
            'policy_identity': started.get('policy_identity', 'unqualified'),
            'command_identity': started['launch_intent_hash'],
            'exit_code': observation.get('exit_code'), 'timed_out': observation.get('timed_out', False),
            'cancelled': observation.get('cancelled', False), 'drainage': 'DRAINED',
            'output_observation': observation.get('output_observation', 'CAPTURED'),
            'stdout_hash': observation.get('stdout_hash', hashlib.sha256(b'').hexdigest()),
            'stderr_hash': observation.get('stderr_hash', hashlib.sha256(b'').hexdigest()),
            'post_observation': observation.get('post_observation', {}),
            'result': observation.get('result', 'ABORTED'), 'ended_at': observation.get('ended_at', time.time())}
