"""Adversarial qualification contract for verification execution backends."""
import importlib.util
import pathlib
import os
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'verification_sandbox.py'
sys.path.insert(0, str(MODULE_PATH.parent))
import verification_command as command
spec = importlib.util.spec_from_file_location('sdd_verification_sandbox_qualification', MODULE_PATH)
vs = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = vs
spec.loader.exec_module(vs)


class BackendQualificationTest(unittest.TestCase):
    def _live_process_group_members(self, pgid):
        proc = pathlib.Path('/proc')
        if proc.is_dir():
            live = []
            for stat_path in proc.glob('[0-9]*/stat'):
                try:
                    raw = stat_path.read_text(encoding='ascii')
                    fields = raw[raw.rfind(')') + 1:].split()
                    if len(fields) >= 3 and int(fields[2]) == pgid and fields[0] != 'Z':
                        live.append(stat_path.parent.name)
                except (OSError, ValueError):
                    continue
            return live
        rows = subprocess.check_output(
            ['/bin/ps', '-axo', 'pgid=,stat='], text=True, stderr=subprocess.DEVNULL
        )
        return [row for row in rows.splitlines()
                if len(row.split()) >= 2 and row.split()[0].isdigit()
                and int(row.split()[0]) == pgid and not row.split()[1].startswith('Z')]

    def test_group_status_treats_zombie_only_group_as_drained(self):
        with mock.patch.object(vs.subprocess, 'check_output', return_value='123 Z\n'):
            self.assertEqual('DRAINED', vs.PosixProcessGroupBackend._group_status(123))
        with mock.patch.object(vs.subprocess, 'check_output', return_value='123 S\n'):
            self.assertEqual('NOT_DRAINED', vs.PosixProcessGroupBackend._group_status(123))

    def test_group_status_rejects_live_execution_unit_member(self):
        process = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'],
                                   start_new_session=True)
        try:
            self.assertNotEqual('DRAINED', vs.PosixProcessGroupBackend._group_status(process.pid))
        finally:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=2)

    def test_process_birth_identity_is_precise_on_current_host(self):
        import os
        identity = vs.PosixProcessGroupBackend._birth_identity(os.getpid())
        self.assertTrue(identity.endswith(f':{os.getpid()}'))
        self.assertGreaterEqual(len(identity.split(':')), 2)









    def test_execution_identity_round_trips_and_rejects_unknown_schema(self):
        identity = vs.ExecutionIdentity('exec', 'backend/v1', 'unit-1', 'repo', 'policy', 'birth-1')
        encoded = identity.to_dict()
        self.assertEqual(identity, vs.ExecutionIdentity.from_dict(encoded))
        with self.assertRaises(ValueError):
            vs.ExecutionIdentity.from_dict({**encoded, 'surprise': True})

    def test_R12_pid_reuse_with_different_birth_identity_is_never_active(self):
        expected = vs.ExecutionIdentity('exec', 'backend/v1', 'pid:123', 'repo', 'policy', 'birth-a')
        observed = vs.ExecutionIdentity('exec', 'backend/v1', 'pid:123', 'repo', 'policy', 'birth-b')
        self.assertEqual('UNCERTAIN', vs.compare_execution_identity(expected, observed).status)

    def test_R12_stale_identity_cannot_cancel_or_drain_reused_unit(self):
        with tempfile.TemporaryDirectory() as tmp:
            backend = vs.PosixProcessGroupBackend()
            prepared = backend.prepare(pathlib.Path(tmp), repository_id='repo', policy_identity='policy')
            identity = prepared.identity
            stale = vs.ExecutionIdentity(identity.execution_id, identity.backend_identity,
                                         identity.unit_id, identity.repository_id,
                                         identity.policy_identity, 'different-generation')
            self.assertEqual('UNCERTAIN', backend.cancel(stale))
            self.assertEqual('UNCERTAIN', backend.drain(stale, .01))
            self.assertEqual('PREPARED', backend._read_state(prepared.state_path)['state'])

    def test_adversarial_check_matrix_is_complete_and_failure_blocks_payload(self):
        self.assertEqual(16, len(vs.REQUIRED_ADVERSARIAL_CHECKS))
        self.assertIn('PROTECTED_WRITE_GRANDCHILD', vs.REQUIRED_ADVERSARIAL_CHECKS)
        self.assertIn('CONTAINMENT_NEW_SESSION', vs.REQUIRED_ADVERSARIAL_CHECKS)
        self.assertFalse(vs.admit_payload(vs.BackendQualification(
            'REJECTED', 'backend', 'policy', 'fingerprint', (), (), ('qualification-failed',))))

    def test_production_candidate_prepares_durable_identity_before_payload(self):
        with tempfile.TemporaryDirectory() as tmp:
            backend = vs.PosixProcessGroupBackend()
            prepared = backend.prepare(pathlib.Path(tmp), repository_id='repo-id', policy_identity='policy')
            persisted = vs.ExecutionIdentity.from_dict(backend._read_state(prepared.state_path)['identity'])
            self.assertEqual(prepared.identity, persisted)
            self.assertEqual('PREPARED', backend._read_state(prepared.state_path)['state'])
            self.assertEqual('DRAINED', backend.reconcile(persisted).status)

    def test_R10_restart_active_uses_only_serialized_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            prepared = vs.PosixProcessGroupBackend().prepare(
                root, repository_id='repo-id', policy_identity='policy')
            with mock.patch.object(vs.PosixProcessGroupBackend, '_birth_identity',
                                   side_effect=lambda pid: f'test-generation:{pid}'):
                backend = vs.PosixProcessGroupBackend()
                running = backend.launch(prepared, [sys.executable, '-c', 'import time; time.sleep(30)'],
                                         cwd=root, env=os.environ.copy())
                encoded = running.identity.to_dict()
                pid = running.process.pid
                reaper = threading.Thread(target=running.process.wait, daemon=True)
                reaper.start()
                running.process.stdout.close()
                running.process.stderr.close()
                del running, backend, prepared
                identity = vs.ExecutionIdentity.from_dict(encoded)
                restarted = vs.PosixProcessGroupBackend()
                try:
                    with mock.patch.object(vs.subprocess, 'check_output', return_value=f'{pid} S\n'):
                        self.assertEqual('ACTIVE', restarted.reconcile(identity).status)
                finally:
                    restarted.cancel(identity)
                    with mock.patch.object(vs.subprocess, 'check_output', return_value=''):
                        restarted.drain(identity, 2)
                    reaper.join(timeout=2)
                    self.assertFalse(reaper.is_alive(), 'test payload did not exit after cancellation')

    def test_R11_restart_drained_after_idempotent_cancel(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            prepared = vs.PosixProcessGroupBackend().prepare(
                root, repository_id='repo-id', policy_identity='policy')
            with mock.patch.object(vs.PosixProcessGroupBackend, '_birth_identity',
                                   side_effect=lambda pid: f'test-generation:{pid}'):
                first = vs.PosixProcessGroupBackend()
                running = first.launch(prepared, [sys.executable, '-c', 'import time; time.sleep(30)'],
                                       cwd=root, env=os.environ.copy())
                identity = vs.ExecutionIdentity.from_dict(running.identity.to_dict())
                reaper = threading.Thread(target=running.process.wait, daemon=True)
                reaper.start()
                running.process.stdout.close()
                running.process.stderr.close()
                del running, first, prepared
                restarted = vs.PosixProcessGroupBackend()
                restarted.cancel(identity)
                with mock.patch.object(vs.subprocess, 'check_output', return_value=''):
                    drainage = restarted.drain(identity, 2)
                del restarted
                with mock.patch.object(vs.subprocess, 'check_output', return_value=''):
                    final = vs.PosixProcessGroupBackend().reconcile(identity)
                self.assertEqual('DRAINED', drainage)
                self.assertEqual('DRAINED', final.status)
                reaper.join(timeout=2)
                self.assertFalse(reaper.is_alive(), 'test payload did not exit after cancellation')


    def test_R13_unproved_candidate_is_rejected_and_cannot_authorize_launch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            candidate = vs.BackendCandidate('fixture', '1', '/bin/false', 'test-host')
            result = vs.qualify_backend(candidate, worktree=root, writable_paths=(root / 'work',),
                                        protected_paths=(root / 'authority',), repository_id='repo')
            self.assertEqual('REJECTED', result.status)
            self.assertFalse(result.authorizes_execution)
        self.assertIn('CANDIDATE_PROCESS_PROBE_UNSUPPORTED', result.reason_codes)

    def test_policy_and_repository_identity_bind_qualification(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            first = vs.sandbox_policy_identity((root / 'work',), (root / 'authority',))
            second = vs.sandbox_policy_identity((root / 'other-work',), (root / 'authority',))
            self.assertNotEqual(first, second)
            self.assertRegex(first, r'^sha256:[0-9a-f]{64}$')

    def test_macos_policy_denies_canonical_authority_roots(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            policy = vs.macos_sandbox_profile((root / 'work',), (root / 'canonical' / '.agent-runs',))
            self.assertIn('(deny file-write* (subpath', policy)
            self.assertIn(str(root / 'canonical' / '.agent-runs'), policy)
            self.assertEqual(policy, vs.macos_sandbox_profile((root / 'work',),
                             (root / 'canonical' / '.agent-runs',)))

    def test_uncertain_restart_identity_never_means_drained(self):
        result = vs.reconcile_execution(vs.ExecutionIdentity('unknown', 'backend', 0, 'repo', 'policy'))
        self.assertEqual('UNCERTAIN', result.status)
        self.assertEqual('DRAINAGE_UNCERTAIN', result.reason_code)

    def test_R16_missing_birth_identity_fails_before_payload(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            marker = root / 'payload-ran'
            backend = vs.PosixProcessGroupBackend()
            prepared = backend.prepare(root, repository_id='repo', policy_identity='policy')
            def unavailable_after_observation(_pid):
                deadline = time.monotonic() + .2
                while time.monotonic() < deadline and not marker.exists():
                    time.sleep(.005)
                raise OSError('unavailable')
            with mock.patch.object(backend, '_birth_identity', side_effect=unavailable_after_observation):
                with self.assertRaisesRegex(RuntimeError, 'process-birth-identity-unavailable'):
                    backend.launch(prepared,
                                   [sys.executable, '-c', 'import pathlib,sys; pathlib.Path(sys.argv[1]).write_text("ran")', str(marker)],
                                   cwd=root, env=os.environ.copy())
            self.assertFalse(marker.exists())
            self.assertEqual('DRAINED', backend.reconcile(prepared.identity).status)



    def test_R8_positive_drainage_and_R9_uncertain_are_distinct(self):
        with mock.patch.object(vs.subprocess, 'check_output', return_value='42 S\n'):
            self.assertEqual('NOT_DRAINED', vs.PosixProcessGroupBackend._group_status(42))
        with mock.patch.object(vs.subprocess, 'check_output', return_value='42 Z\n'):
            self.assertEqual('DRAINED', vs.PosixProcessGroupBackend._group_status(42))
        with mock.patch.object(vs.subprocess, 'check_output', side_effect=OSError('denied')):
            self.assertEqual('UNCERTAIN', vs.PosixProcessGroupBackend._group_status(42))

    def test_R6_R7_cancel_during_descendant_spawning_reinspects_until_drained(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            writable = root / 'writable'
            writable.mkdir()
            candidate = vs.BackendCandidate('fixture', '1', '/bin/false', 'test')
            with mock.patch.object(vs.PosixProcessGroupBackend, '_birth_identity',
                                   side_effect=lambda pid: f'test-generation:{pid}'), \
                 mock.patch.object(vs.PosixProcessGroupBackend, '_group_status',
                                   side_effect=['NOT_DRAINED', 'NOT_DRAINED', 'DRAINED']):
                result = vs._run_active_qualification_check(
                    candidate, 'Q13', 'CANCEL_EXECUTION_UNIT', worktree=root,
                    writable=(str(writable),), protected=(), repository_id='repo', policy_identity='policy')
            self.assertEqual('PASS', result[0])
            self.assertEqual('WHOLE_UNIT_CANCELLED_AND_DRAINED', result[1])
            self.assertEqual([], list(writable.iterdir()))

    def test_R14_changed_policy_invalidates_otherwise_complete_qualification(self):
        checks = tuple(vs.QualificationCheck(check_id, name, 'PASS', 'PASS', 'probe-evidence')
                       for check_id, name in vs.QUALIFICATION_CHECKS)
        proof = vs.BackendQualification('QUALIFIED', 'candidate:version', 'policy-A', 'fingerprint', (),
                                        checks, ())
        plan = vs.SandboxPlan(['true'], {'PATH': os.environ.get('PATH', '')}, 'candidate', True,
                              {'backend_identity': 'candidate:version', 'policy_fingerprint': 'policy-B'}, proof)
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            with mock.patch.object(command.verification_sandbox, 'build_plan', return_value=plan), \
                 mock.patch.object(command.CommandExecutionBackend, 'launch',
                                   side_effect=AssertionError('payload-launched')) as launch:
                result = command.run_command('python3 -V', cwd=root, run_dir=root / 'run',
                                             timeout_seconds=1, sandbox_mode='auto',
                                             required_capabilities=False)
        self.assertEqual('backend-not-v2-qualified', result.error)
        launch.assert_not_called()


if __name__ == '__main__':
    unittest.main()
