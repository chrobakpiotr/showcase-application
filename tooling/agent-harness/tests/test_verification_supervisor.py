import pathlib
import sys
import tempfile
import unittest
import json
import dataclasses
import hashlib
import subprocess
from types import SimpleNamespace

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from verification.store import StoreError, VerificationStore
from verification.supervisor import VerificationSupervisor
from verification.supervisor import SupervisorState
from verification.model import Evidence
from verification.serialization import digest, evidence_record
import verification_command


class SupervisorTest(unittest.TestCase):
    def test_pc1_callable_runner_is_rejected_before_payload(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            with self.assertRaisesRegex(RuntimeError, 'EXECUTION_BACKEND_REQUIRED'):
                VerificationSupervisor(store).execute(
                    verification_command.run_command, worktree=root, family_id='family', attempt_id='attempt',
                    gate_id='gate', command='python3 -V', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='off')
            self.assertFalse(store.executions.exists())

    def test_unresolved_or_corrupt_journal_blocks_other_families(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            journal = store.executions / 'x'
            journal.mkdir(parents=True)
            (journal / 'started.json').write_text('{"schema_version":2,"backend":"unknown"}')
            with self.assertRaises(StoreError):
                store.admit_repository_verification()

    def test_pc1_started_binds_prepared_identity_before_payload_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root)
            supervisor = VerificationSupervisor(store)
            result, _ = supervisor.execute(
                backend, worktree=root, family_id='family', attempt_id='attempt', gate_id='gate',
                command='python3 -c pass', cwd=root, run_dir=root / 'run', timeout_seconds=2,
                sandbox_mode='required')
            journal = next(store.executions.iterdir())
            started = json.loads((journal / 'started.json').read_text())
            self.assertIn('execution_identity', started)
            self.assertLess(backend.events.index('started-durable'), backend.events.index('launch'))
            self.assertEqual(0, result.exit_code)

    def test_pc1_started_publication_crash_leaves_no_phantom_execution(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root)
            store._fault_injector = lambda boundary: (_ for _ in ()).throw(RuntimeError('crash')) \
                if boundary == 'during-write' else None
            supervisor = VerificationSupervisor(store)
            with self.assertRaisesRegex(RuntimeError, 'crash'):
                supervisor.execute(backend, worktree=root, family_id='family', attempt_id='attempt',
                    gate_id='gate', command='python3 -c pass', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required')
            self.assertNotIn('launch', backend.events)
            fresh_store = VerificationStore(root, control_root=root / 'control')
            fresh_store.admit_repository_verification()
            fresh_backend = LifecycleBackend(fresh_store, root)
            fresh = VerificationSupervisor(fresh_store)
            result, _ = fresh.execute(fresh_backend, worktree=root, family_id='family',
                attempt_id='attempt', gate_id='gate', command='python3 -c pass', cwd=root,
                run_dir=root / 'run', timeout_seconds=2, sandbox_mode='required')
            self.assertEqual(0, result.exit_code)
            self.assertEqual(1, fresh_backend.events.count('launch'))

    def test_pc2_prepared_not_launched_recovery_aborts_without_inventing_success(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root)
            supervisor = VerificationSupervisor(store)
            supervisor.inject_crash_at = 'after-started'
            with self.assertRaisesRegex(RuntimeError, 'injected-crash'):
                supervisor.execute(backend, worktree=root, family_id='family', attempt_id='attempt',
                    gate_id='gate', command='python3 -c pass', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required')
            fresh = VerificationSupervisor(VerificationStore(root, control_root=root / 'control'))
            recovered = fresh.recover(backend_factory=lambda _record: LifecycleBackend(
                VerificationStore(root, control_root=root / 'control'), root))
            self.assertEqual('ABORTED_PREPARED', recovered[0].state.value)
            self.assertEqual(0, sum(event == 'launch' for event in backend.events))

    def test_pc3_active_restart_reconciles_without_relaunch(self):
        self._crash_and_recover('after-launch', expected='ACTIVE')

    def test_pc4_uncertain_recovery_keeps_admission_blocked(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root, inspect='UNCERTAIN')
            supervisor = VerificationSupervisor(store)
            supervisor.inject_crash_at = 'after-launch'
            with self.assertRaises(RuntimeError):
                supervisor.execute(backend, worktree=root, family_id='family', attempt_id='attempt',
                    gate_id='gate', command='python3 -c pass', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required')
            fresh = VerificationSupervisor(VerificationStore(root, control_root=root / 'control'))
            result = fresh.recover(lambda _record: backend)
            self.assertEqual('UNCERTAIN', result[0].state.value)
            with self.assertRaises(StoreError):
                fresh.store.admit_repository_verification()

    def test_pc5_cancellation_restart_recovery_drains_before_closure(self):
        self._crash_and_recover('after-cancel-started', expected='TERMINAL')

    def test_pc5_cancellation_restart_without_post_observer_stays_uncertain(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root, launch_state='ACTIVE')
            supervisor = VerificationSupervisor(store)
            supervisor.inject_crash_at = 'after-cancel-started'
            with self.assertRaisesRegex(RuntimeError, 'injected-crash'):
                supervisor.execute(backend, worktree=root, family_id='family', attempt_id='attempt',
                    gate_id='gate', command='python3 -c pass', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required')
            fresh_store = VerificationStore(root, control_root=root / 'control')
            fresh = VerificationSupervisor(fresh_store)
            recovered = fresh.recover(lambda _record: LifecycleBackend(fresh_store, root))
            self.assertEqual('UNCERTAIN', recovered[0].state.value)
            journal = next(fresh_store.executions.iterdir())
            self.assertFalse((journal / 'terminal.json').exists())
            self.assertFalse((journal / 'drained.json').exists())
            with self.assertRaises(StoreError):
                fresh_store.admit_repository_verification()

    def test_pc6_terminal_receipt_requires_drainage(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root, inspect='NOT_DRAINED', cancel_status='NOT_DRAINED')
            with self.assertRaisesRegex(RuntimeError, 'EXECUTION_NOT_DRAINED'):
                VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='g', command='python3 -c pass', cwd=root, run_dir=root / 'r', timeout_seconds=2,
                    sandbox_mode='required')
            journal = next(store.executions.iterdir())
            self.assertFalse((journal / 'terminal.json').exists())
            self.assertFalse((journal / 'drained.json').exists())

    def test_pc7_terminal_receipt_is_create_once_and_conflicts_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            VerificationSupervisor(store).execute(LifecycleBackend(store, root), worktree=root,
                family_id='family', attempt_id='attempt', gate_id='gate', command='python3 -c pass',
                cwd=root, run_dir=root / 'run', timeout_seconds=2, sandbox_mode='required')
            path = next(store.executions.glob('*/terminal.json'))
            receipt = json.loads(path.read_text())
            receipt.pop('receipt_hash')
            self.assertEqual(store.publish_execution_terminal(receipt), store.publish_execution_terminal(receipt))
            with self.assertRaisesRegex(StoreError, 'immutable-record-collision'):
                store.publish_execution_terminal({**receipt, 'result': 'FAIL'})

    def test_pc8_terminal_receipt_precedes_projection(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            def publish(_result, _ready, _receipt):
                journal = next(store.executions.iterdir())
                self.assertTrue((journal / 'terminal.json').exists())
                self.assertFalse((journal / 'projection.json').exists())
                terminal = json.loads((journal / 'terminal.json').read_text())
                self.assertIn('verification_evidence', terminal)
                evidence_path = store.runs / 'family' / 'gate:attempt' / 'terminal.json'
                self.assertFalse(evidence_path.exists())
            VerificationSupervisor(store).execute(LifecycleBackend(store, root), worktree=root,
                family_id='family', attempt_id='attempt', gate_id='gate', command='python3 -c pass',
                cwd=root, run_dir=root / 'run', timeout_seconds=2, sandbox_mode='required',
                terminal_publisher=publish, input_fingerprint='c' * 64,
                terminal_record_builder=lambda *_: _evidence_record(store))
            journal = next(store.executions.iterdir())
            self.assertTrue((journal / 'projection.json').exists())
            self.assertTrue((store.root / 'state' / 'execution-index.json').exists())
            self.assertTrue((store.runs / 'family' / 'gate:attempt' / 'projection.json').exists())

    def test_pc9_projection_is_rebuilt_from_execution_receipt(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            VerificationSupervisor(store).execute(LifecycleBackend(store, root), worktree=root,
                family_id='family', attempt_id='attempt', gate_id='gate', command='python3 -c pass',
                cwd=root, run_dir=root / 'run', timeout_seconds=2, sandbox_mode='required')
            journal = next(store.executions.iterdir())
            (journal / 'projection.json').unlink()
            (store.root / 'state' / 'execution-index.json').unlink()
            fresh = VerificationStore(root, control_root=root / 'control')
            self.assertEqual(1, len(fresh.reconstruct_execution_terminals()))
            self.assertTrue((journal / 'projection.json').exists())
            self.assertTrue((fresh.root / 'state' / 'execution-index.json').exists())

    def test_pc10_started_without_drained_blocks_admission(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            supervisor = VerificationSupervisor(store)
            supervisor.inject_crash_at = 'after-launch'
            with self.assertRaises(RuntimeError):
                supervisor.execute(LifecycleBackend(store, root, launch_state='ACTIVE'), worktree=root,
                    family_id='f', attempt_id='a', gate_id='g', command='python3 -c pass', cwd=root,
                    run_dir=root / 'r', timeout_seconds=2, sandbox_mode='required')
            with self.assertRaisesRegex(StoreError, 'verification-owned'):
                VerificationStore(root, control_root=root / 'control').admit_repository_verification()

    def test_pc11_second_execution_is_rejected_before_spawn_while_first_unresolved(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root, inspect='ACTIVE')
            first = VerificationSupervisor(store)
            first.inject_crash_at = 'after-launch'
            with self.assertRaises(RuntimeError):
                first.execute(backend, worktree=root, family_id='f', attempt_id='a', gate_id='g',
                    command='python3 -c pass', cwd=root, run_dir=root / 'r', timeout_seconds=2,
                    sandbox_mode='required')
            fresh = VerificationSupervisor(VerificationStore(root, control_root=root / 'control'))
            second_backend = LifecycleBackend(fresh.store, root, inspect='ACTIVE')
            with self.assertRaises(StoreError):
                fresh.execute(second_backend, worktree=root, family_id='f2', attempt_id='a2', gate_id='g2',
                    command='python3 -c pass', cwd=root, run_dir=root / 'r2', timeout_seconds=2,
                    sandbox_mode='required')
            self.assertNotIn('launch', second_backend.events)

    def test_pc12_c1_empty_admission_recovery(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root)
            supervisor = VerificationSupervisor(store)
            supervisor.inject_crash_at = 'after-admission'
            with self.assertRaisesRegex(RuntimeError, 'injected-crash:after-admission'):
                supervisor.execute(backend, worktree=root, family_id='f', attempt_id='a', gate_id='g',
                    command='python3 -c pass', cwd=root, run_dir=root / 'r', timeout_seconds=2,
                    sandbox_mode='required')
            self.assertFalse(store.executions.exists())
            self.assertNotIn('prepare', backend.events)
            fresh = VerificationSupervisor(VerificationStore(root, control_root=root / 'control'))
            self.assertEqual((), fresh.recover())
            self.assertNotIn('launch', backend.events)

    def test_pc13_c2_prepared_identity_recovery(self):
        self._crash_and_recover('after-started', expected='ABORTED_PREPARED')

    def test_pc13_c2_launch_marker_without_payload_is_backend_proven_not_launched(self):
        self._crash_and_recover('during-launch', expected='ABORTED_PREPARED')

    def test_pc14_c3_active_execution_recovery(self):
        self._crash_and_recover('after-launch', expected='ACTIVE')

    def test_pc15_c4_cancellation_recovery(self):
        self._crash_and_recover('after-cancel-started', expected='TERMINAL')

    def test_pc16_c5_drained_before_terminal_recovery(self):
        self._crash_and_recover('after-drained', expected='TERMINAL')

    def test_pc16_c5_crash_after_drainage_before_observation_fails_closed(self):
        self._crash_and_recover('after-drain-proof', expected='UNCERTAIN')

    def test_pc17_c6_terminal_before_projection_recovery(self):
        self._crash_and_recover('after-terminal', expected='TERMINAL')

    def test_pc18_c7_evidence_before_index_recovery(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            calls = 0
            def fail_index(boundary):
                nonlocal calls
                if boundary == 'projection-before-write':
                    calls += 1
                    if calls == 2:
                        raise RuntimeError('injected-index-projection-crash')
            store._fault_injector = fail_index
            backend = LifecycleBackend(store, root)
            with self.assertRaisesRegex(RuntimeError, 'injected-index-projection-crash'):
                VerificationSupervisor(store).execute(backend, worktree=root, family_id='family',
                    attempt_id='attempt', gate_id='gate', command='python3 -c pass', cwd=root,
                    run_dir=root / 'run', timeout_seconds=2, sandbox_mode='required')
            self.assertTrue(next(store.executions.glob('*/projection.json')).exists())
            self.assertFalse((store.root / 'state' / 'execution-index.json').exists())
            fresh_store = VerificationStore(root, control_root=root / 'control')
            fresh = VerificationSupervisor(fresh_store)
            result = fresh.recover(lambda _record: LifecycleBackend(fresh_store, root))
            self.assertEqual('TERMINAL', result[0].state.value)
            self.assertTrue((fresh_store.root / 'state' / 'execution-index.json').exists())

    def test_pc19_c8_closed_execution_reconciles_admission(self):
        self._crash_and_recover('after-index-projection', expected='TERMINAL')

    def test_pc20_recovery_uses_fresh_store_supervisor_and_backend_facade(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root, launch_state='ACTIVE')
            supervisor = VerificationSupervisor(store)
            supervisor.inject_crash_at = 'after-launch'
            with self.assertRaisesRegex(RuntimeError, 'injected-crash:after-launch'):
                supervisor.execute(backend, worktree=root, family_id='family', attempt_id='attempt',
                    gate_id='gate', command='python3 -c pass', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required')
            code = """import json, pathlib, sys
sys.path.insert(0, sys.argv[1])
from verification.store import VerificationStore
from verification.supervisor import VerificationSupervisor
class Backend:
    def inspect(self, identity):
        return type('Recon', (), {'status': json.loads((pathlib.Path(identity['unit_id']) / 'state.json').read_text())['status']})()
    def cancel_and_drain(self, identity, timeout):
        raise AssertionError('active recovery must not cancel')
root = pathlib.Path(sys.argv[2]); store = VerificationStore(root, control_root=root / 'control')
result = VerificationSupervisor(store).recover(lambda record: Backend())
print(json.dumps([entry.state.value for entry in result]))
"""
            state = root / 'unit-1' / 'state.json'
            state.parent.mkdir(parents=True, exist_ok=True)
            state.write_text(json.dumps({'status': 'ACTIVE'}))
            child = subprocess.run([sys.executable, '-c', code,
                str(pathlib.Path(__file__).resolve().parents[1]), str(root)],
                check=True, text=True, capture_output=True)
            self.assertEqual(['ACTIVE'], json.loads(child.stdout))

    def _crash_and_recover(self, boundary, expected):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root,
                launch_state='ACTIVE' if boundary in {'after-launch', 'after-cancel-started'} else 'DRAINED')
            supervisor = VerificationSupervisor(store)
            supervisor.inject_crash_at = boundary
            kwargs = {}
            if boundary == 'after-terminal':
                kwargs = {'input_fingerprint': 'c' * 64,
                          'terminal_record_builder': lambda *_: _evidence_record(store)}
            with self.assertRaisesRegex(RuntimeError, 'injected-crash'):
                supervisor.execute(backend, worktree=root, family_id='family', attempt_id='attempt',
                    gate_id='gate', command='python3 -c pass', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required', **kwargs)
            fresh_store = VerificationStore(root, control_root=root / 'control')
            fresh_backend = LifecycleBackend(fresh_store, root)
            fresh = VerificationSupervisor(fresh_store)
            recovery_observer = None
            if boundary == 'after-cancel-started':
                recovery_observer = lambda _started: {'status': 'CAPTURED', 'fingerprint': 'c' * 64,
                                                        'stable': True}
            recovered = fresh.recover(lambda _record: fresh_backend, recovery_observer)
            self.assertEqual(expected, recovered[0].state.value)
            if boundary == 'after-cancel-started':
                receipt = json.loads(next(fresh_store.executions.glob('*/terminal.json')).read_text())
                self.assertEqual('ABORTED', receipt['result'])
                self.assertEqual('UNAVAILABLE', receipt['output_observation'])
                self.assertIsNone(receipt['stdout_hash'])
                self.assertIsNone(receipt['stderr_hash'])
                self.assertEqual('CAPTURED', receipt['post_observation']['status'])
            if expected == 'ABORTED_PREPARED':
                self.assertNotIn('launch', backend.events)
            if boundary == 'after-terminal':
                self.assertTrue((fresh_store.runs / 'family' / 'gate:attempt' / 'projection.json').exists())
            if boundary in {'after-terminal', 'after-evidence-projection', 'after-index-projection'}:
                fresh.store.admit_repository_verification()

    def _recovery_without_execution_is_empty(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            fresh = VerificationSupervisor(VerificationStore(root, control_root=root / 'control'))
            self.assertEqual((), fresh.recover())


def _evidence_record(store):
    evidence = Evidence(2, 'gate:attempt', 'family', 'attempt', 'gate', store.repository_id,
        'd' * 64, 'e' * 40, 'f' * 64, 'integration', 'c' * 64, 'c' * 64,
        'required', 'forbid', 1, 0, 1.0, 2.0, 'pass', (), (), '')
    record = evidence_record(evidence, include_receipt=False)
    record['receipt_hash'] = digest(record)
    return evidence_record(dataclasses.replace(evidence, receipt_hash=record['receipt_hash']))


class LifecycleBackend:
    """Deterministic qualified backend fixture used only by supervisor contract tests."""
    def __init__(self, store, root, inspect=None, launch_state='DRAINED', cancel_status='DRAINED'):
        self.store, self.root, self.inspect_state = store, root, inspect
        self.launch_state, self.cancel_status = launch_state, cancel_status
        self.unit_state = root / 'unit-1' / 'state.json'
        self.events = []

    def prepare(self, **_kwargs):
        identity = {'schema_version': 1, 'execution_id': 'unit-1', 'backend_identity': 'test-qualified/v1',
                    'unit_id': str(self.root / 'unit-1'), 'birth_identity': 'generation-1',
                    'repository_id': self.store.repository_id, 'policy_identity': 'sha256:' + 'a' * 64}
        self.events.append('prepare')
        self.unit_state.parent.mkdir(parents=True, exist_ok=True)
        if not self.unit_state.exists():
            self.unit_state.write_text(json.dumps({'status': 'PREPARED'}))
        return SimpleNamespace(identity=identity, backend_identity='test-qualified/v1',
                               policy_identity=identity['policy_identity'],
                               qualification_fingerprint='sha256:' + 'b' * 64)

    def launch(self, _prepared, **_kwargs):
        starts = list(self.store.executions.glob('*/started.json'))
        if not starts or 'execution_identity' not in json.loads(starts[-1].read_text()):
            raise AssertionError('launch-before-durable-start')
        self.events.append('started-durable')
        self.events.append('launch')
        self.unit_state.write_text(json.dumps({'status': self.launch_state}))
        return type('Result', (), {'argv': ('python3',), 'cwd': str(self.root), 'started_at': 1.0,
            'ended_at': 2.0, 'duration_seconds': 1.0, 'exit_code': 0, 'stdout': '', 'stderr': '',
            'timed_out': False, 'error': None})()

    def inspect(self, _identity):
        self.events.append('inspect')
        state = self.inspect_state or json.loads(self.unit_state.read_text())['status']
        status = 'DRAINED' if state == 'PREPARED' else state
        reason = 'NOT_LAUNCHED' if state == 'PREPARED' else state
        return type('Recon', (), {'status': status, 'reason_code': reason,
                                  'evidence': 'fixture'})()

    def cancel_and_drain(self, _identity, _timeout):
        if next(self.store.executions.glob('*/cancelling.json'), None) is None:
            raise AssertionError('cancellation-before-durable-cancelling-state')
        self.events.append('cancel-and-drain')
        if self.cancel_status == 'DRAINED':
            self.unit_state.write_text(json.dumps({'status': 'DRAINED'}))
        return type('Recon', (), {'status': self.cancel_status, 'reason_code': self.cancel_status,
                                  'evidence': 'fixture'})()


if __name__ == '__main__':
    unittest.main()
