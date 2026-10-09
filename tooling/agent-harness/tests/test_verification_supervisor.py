import pathlib
import sys
import tempfile
import unittest
import json
import dataclasses
import hashlib
import subprocess
import threading
import time
from types import SimpleNamespace
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from verification.store import StoreError, VerificationStore, _RETRY_CONTROL_INPUT_SHA256
from verification.supervisor import VerificationSupervisor
from verification.supervisor import SupervisorState
from verification.admission import AdmissionConflict, RepositoryAdmission
from verification.model import Evidence
from verification.serialization import digest, evidence_record
import verification_command
from human_grant_fixture import signed_test_grant, trusted_test_store


class SupervisorTest(unittest.TestCase):
    def test_supervisor_reservation_blocks_lifecycle_mutation_until_terminal_drain(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            entered_launch = threading.Event()
            allow_drain = threading.Event()

            class PausedBackend(LifecycleBackend):
                def launch(self, prepared, **kwargs):
                    entered_launch.set()
                    if not allow_drain.wait(2):
                        raise RuntimeError('test launch gate timed out')
                    return super().launch(prepared, **kwargs)

            backend = PausedBackend(store, root)
            supervisor = VerificationSupervisor(store)
            admission = RepositoryAdmission(store.lifecycle_root, store.repository_id)
            failures = []

            def execute():
                try:
                    supervisor.execute(backend, worktree=root, family_id='family', attempt_id='attempt',
                        gate_id='gate', command='python3 -c pass', cwd=root, run_dir=root / 'run',
                        timeout_seconds=2, sandbox_mode='required')
                except BaseException as exc:
                    failures.append(exc)

            worker = threading.Thread(target=execute)
            worker.start()
            self.assertTrue(entered_launch.wait(2))
            with self.assertRaisesRegex(AdmissionConflict, 'verification-owned'):
                with admission.mutation():
                    pass
            allow_drain.set()
            worker.join(2)
            self.assertFalse(worker.is_alive())
            self.assertFalse(failures)
            with admission.mutation():
                pass

    def test_vc009_08_unknown_tool_retry_behavior_fails_closed_before_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid')
            self.assertNotIn('launch', backend.events)

    def test_vc009_adversary_fake_tool_internal_retries_but_unknown_is_not_proven(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            fake_tool = root / 'fake_tool.py'
            internal_attempts = root / 'internal-attempts'
            fake_tool.write_text("from pathlib import Path\np=Path(" + repr(str(internal_attempts)) +
                ")\np.write_text('attempt-1\\nattempt-2\\n')\n")
            with mock.patch('subprocess.run', wraps=subprocess.run) as harness_spawn:
                launched = harness_spawn([sys.executable, str(fake_tool)], check=False)
            self.assertEqual(0, launched.returncode)
            self.assertEqual(2, len(internal_attempts.read_text().splitlines()))
            self.assertEqual(1, harness_spawn.call_count)
            class InternallyRetryingBackend(LifecycleBackend):
                def launch(self, prepared, **kwargs):
                    self.events.extend(['tool-attempt-1', 'tool-attempt-2'])
                    return super().launch(prepared, **kwargs)
            backend = InternallyRetryingBackend(store, root/'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='g', command='fake-tool --retry-twice', cwd=root, run_dir=root/'run',
                    timeout_seconds=2, sandbox_mode='required', critical=True, retry_policy='forbid')
            self.assertEqual(0, backend.events.count('launch'))
            self.assertEqual(0, backend.events.count('tool-attempt-2'))
            self.assertFalse(any(record.get('retry_policy_proof', {}).get('retry_free') is True
                for record in (json.loads(p.read_text()) for p in store.executions.glob('*/terminal.json'))))

    def test_vc009_backend_failure_after_launch_reconciles_without_relaunch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            class LaunchThenFailBackend(LifecycleBackend):
                def launch(self, prepared, **kwargs):
                    super().launch(prepared, **kwargs)
                    raise RuntimeError('result-channel-failed-after-launch')
            backend = LaunchThenFailBackend(store, root/'unit')
            kwargs = dict(worktree=root, family_id='f', attempt_id='a', gate_id='g',
                command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_policy='forbid',
                retry_controls=('no-internal-retries',))
            with self.assertRaisesRegex(RuntimeError, 'result-channel-failed-after-launch'):
                VerificationSupervisor(store).execute(backend, **kwargs)
            result = VerificationSupervisor(VerificationStore(root, control_root=root/'control')).recover(
                lambda _started: backend)
            self.assertEqual(1, backend.events.count('launch'))
            self.assertEqual('TERMINAL_OBSERVATION_MISSING', result[0].reason_code)

    def test_vc009_01_retry_forbidden_critical_attempt_has_one_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit')
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_policy='forbid',
                retry_controls=('no-internal-retries',))
            self.assertEqual(1, backend.events.count('launch'))
            terminal = json.loads(next(store.executions.glob('*/terminal.json')).read_text())
            self.assertEqual(1, terminal['harness_invocation_upper_bound'])

    def test_vc009_02_nonzero_failure_does_not_relaunch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = CriticalFailureBackend(store, root/'unit')
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_policy='forbid', profile_hash='a' * 64,
                input_fingerprint='b' * 64, retry_controls=('no-internal-retries',))
            self.assertEqual(1, backend.events.count('launch'))

    def test_vc009_03_timeout_does_not_relaunch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            class TimeoutBackend(LifecycleBackend):
                def launch(self, prepared, **kwargs):
                    result = super().launch(prepared, **kwargs); result.timed_out = True
                    return result
            backend = TimeoutBackend(store, root/'unit')
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_policy='forbid',
                retry_controls=('no-internal-retries',))
            self.assertEqual(1, backend.events.count('launch'))

    def test_vc009_04_restart_reconciliation_does_not_relaunch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit'); supervisor = VerificationSupervisor(store)
            supervisor.inject_crash_at = 'after-launch'
            with self.assertRaisesRegex(RuntimeError, 'injected-crash'):
                supervisor.execute(backend, worktree=root, family_id='f', attempt_id='a', gate_id='g',
                    command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('no-internal-retries',))
            fresh = VerificationSupervisor(VerificationStore(root, control_root=root/'control'))
            fresh.recover(lambda _started: backend)
            self.assertEqual(1, backend.events.count('launch'))

    def test_vc009_05_explicit_no_internal_retry_is_valid(self):
        self._vc009_positive('no-internal-retries')

    def test_vc009_06_internal_retries_disabled_control_is_recorded(self):
        self._vc009_positive('critical-postgres-gate-disables-gradle-test-retry-v1')

    def test_vc009_07_bounded_internal_retry_is_not_retry_free(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a', gate_id='g',
                    command='python3 -c pass', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required', critical=True,
                    retry_policy='forbid', retry_controls=('internal-retry-bounded:tool-retry-limit:2',))
            self.assertEqual(0, backend.events.count('launch'))

    def test_vc009_rejects_unregistered_retry_control_before_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a', gate_id='g',
                    command='python3 -c pass', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required', critical=True,
                    retry_policy='forbid',
                    retry_controls=('internal-retry-disabled:invented-control',))
            self.assertEqual(0, backend.events.count('launch'))

    def test_vc009_rejects_registered_control_for_different_command_before_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression', command='fake --retry-twice',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertEqual(0, backend.events.count('launch'))

    def test_vc009_candidate_gradle_retry_mutation_fails_before_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            gradle_file = root / 'apps/ecommerce/backend/ecommerce.gradle'
            gradle_file.parent.mkdir(parents=True, exist_ok=True)
            source_root = pathlib.Path(__file__).resolve().parents[3]
            source = (source_root / 'apps/ecommerce/backend/ecommerce.gradle').read_text()
            mutated = source.replace('maxRetries = strictEvidenceGateEnabled ? 0 : 2', 'maxRetries = 2')
            self.assertNotEqual(source, mutated)
            gradle_file.write_text(mutated)
            wrapper_path = root / 'tooling/scripts/verify-critical-postgres-tests.sh'
            wrapper_path.parent.mkdir(parents=True, exist_ok=True)
            wrapper_path.write_bytes((source_root / 'tooling/scripts/verify-critical-postgres-tests.sh').read_bytes())
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_critical_wrapper_must_enable_strict_gradle_property(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            source_root = pathlib.Path(__file__).resolve().parents[3]
            config_path = root / 'apps/ecommerce/backend/ecommerce.gradle'
            config_path.parent.mkdir(parents=True, exist_ok=True)
            config_path.write_bytes((source_root / 'apps/ecommerce/backend/ecommerce.gradle').read_bytes())
            wrapper_path = root / 'tooling/scripts/verify-critical-postgres-tests.sh'
            wrapper_path.parent.mkdir(parents=True, exist_ok=True)
            wrapper = (source_root / 'tooling/scripts/verify-critical-postgres-tests.sh').read_text()
            wrapper_path.write_text(wrapper.replace('-PcriticalPostgresGate=true', '-PcriticalPostgresGate=false'))
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_wrapper_comment_cannot_substitute_for_effective_gradle_property(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            source_root = pathlib.Path(__file__).resolve().parents[3]
            config_path = root / 'apps/ecommerce/backend/ecommerce.gradle'
            config_path.parent.mkdir(parents=True, exist_ok=True)
            config_path.write_bytes((source_root / 'apps/ecommerce/backend/ecommerce.gradle').read_bytes())
            wrapper_path = root / 'tooling/scripts/verify-critical-postgres-tests.sh'
            wrapper_path.parent.mkdir(parents=True, exist_ok=True)
            wrapper = (source_root / 'tooling/scripts/verify-critical-postgres-tests.sh').read_text()
            wrapper = wrapper.replace('-PcriticalPostgresGate=true', '-PcriticalPostgresGate=false')
            wrapper += '\n# -PcriticalPostgresGate=true\n'
            wrapper_path.write_text(wrapper)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_gradle_init_script_cannot_override_retry_policy(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            source_root = pathlib.Path(__file__).resolve().parents[3]
            config_path = root / 'apps/ecommerce/backend/ecommerce.gradle'
            config_path.parent.mkdir(parents=True, exist_ok=True)
            config_path.write_bytes((source_root / 'apps/ecommerce/backend/ecommerce.gradle').read_bytes())
            wrapper_path = root / 'tooling/scripts/verify-critical-postgres-tests.sh'
            wrapper_path.parent.mkdir(parents=True, exist_ok=True)
            wrapper = (source_root / 'tooling/scripts/verify-critical-postgres-tests.sh').read_text()
            wrapper = wrapper.replace('./gradlew :application:ecommerce:test',
                                      './gradlew :application:ecommerce:test --init-script tooling/retry-override.txt')
            wrapper_path.write_text(wrapper)
            (root / 'tooling/retry-override.txt').write_text(
                'allprojects { tasks.withType(Test).configureEach { retry { maxRetries = 2 } } }\n')
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_echoed_gradle_command_cannot_prove_test_invocation(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            source_root = pathlib.Path(__file__).resolve().parents[3]
            config_path = root / 'apps/ecommerce/backend/ecommerce.gradle'
            config_path.parent.mkdir(parents=True, exist_ok=True)
            config_path.write_bytes((source_root / 'apps/ecommerce/backend/ecommerce.gradle').read_bytes())
            wrapper_path = root / 'tooling/scripts/verify-critical-postgres-tests.sh'
            wrapper_path.parent.mkdir(parents=True, exist_ok=True)
            wrapper = (source_root / 'tooling/scripts/verify-critical-postgres-tests.sh').read_text()
            wrapper = wrapper.replace('./gradlew :application:ecommerce:test',
                                      'echo ./gradlew :application:ecommerce:test')
            wrapper_path.write_text(wrapper)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_wrapper_cannot_mask_gradle_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            source_root = pathlib.Path(__file__).resolve().parents[3]
            config_path = root / 'apps/ecommerce/backend/ecommerce.gradle'
            config_path.parent.mkdir(parents=True, exist_ok=True)
            config_path.write_bytes((source_root / 'apps/ecommerce/backend/ecommerce.gradle').read_bytes())
            wrapper_path = root / 'tooling/scripts/verify-critical-postgres-tests.sh'
            wrapper_path.parent.mkdir(parents=True, exist_ok=True)
            wrapper = (source_root / 'tooling/scripts/verify-critical-postgres-tests.sh').read_text()
            wrapper = wrapper.replace('-PcriticalPostgresGate=true', '-PcriticalPostgresGate=true || true')
            wrapper_path.write_text(wrapper)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_wrapper_cannot_install_gradle_user_home_init_script(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            source_root = pathlib.Path(__file__).resolve().parents[3]
            config_path = root / 'apps/ecommerce/backend/ecommerce.gradle'
            config_path.parent.mkdir(parents=True, exist_ok=True)
            config_path.write_bytes((source_root / 'apps/ecommerce/backend/ecommerce.gradle').read_bytes())
            wrapper_path = root / 'tooling/scripts/verify-critical-postgres-tests.sh'
            wrapper_path.parent.mkdir(parents=True, exist_ok=True)
            wrapper = (source_root / 'tooling/scripts/verify-critical-postgres-tests.sh').read_text()
            injection = '''
export GRADLE_USER_HOME="$PWD/.agent-runs/retry-home"
mkdir -p "$GRADLE_USER_HOME/init.d"
printf '%s\\n' 'gradle.projectsEvaluated { allprojects { tasks.withType(Test).configureEach { retry { maxRetries = 2 } } } }' > "$GRADLE_USER_HOME/init.d/retry.gradle"
'''
            wrapper_path.write_text(wrapper + injection)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_candidate_pre_gradle_verifier_cannot_install_user_home_init_script(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            verifier = root / 'tooling/scripts/verify_critical_postgres_results.py'
            verifier.write_text(verifier.read_text() + '''
from pathlib import Path
import os
home = Path(os.environ['GRADLE_USER_HOME']) / 'init.d'
home.mkdir(parents=True, exist_ok=True)
(home / 'retry.gradle').write_text('gradle.projectsEvaluated { allprojects { tasks.withType(Test).configureEach { retry { maxRetries = 2 } } } }')
''')
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_obfuscated_root_gradle_override_fails_before_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            (root / 'build.gradle').write_text('''
def extensionName = 're' + 'try'
def propertyName = 'max' + 'Retries'
gradle.projectsEvaluated {
    allprojects {
        tasks.withType(Test).configureEach {
            extensions.findByName(extensionName)."${propertyName}" = 2
        }
    }
}
''')
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_registered_gradle_gate_does_not_reuse_poisoned_shared_home(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            shared_run_dir = root / '.agent-runs/runs/family/attempt/sandbox'
            poisoned_init = shared_run_dir / 'verification-home/gradle/init.d/retry.gradle'
            poisoned_init.parent.mkdir(parents=True)
            poisoned_init.write_text(
                'gradle.projectsEvaluated { allprojects { tasks.withType(Test).configureEach { retry { maxRetries = 2 } } } }')

            class IsolatedHomeBackend(LifecycleBackend):
                def prepare(self, **kwargs):
                    self.backend_run_dir = pathlib.Path(kwargs['run_dir'])
                    return super().prepare(**kwargs)

            store = VerificationStore(root, control_root=root / 'control')
            backend = IsolatedHomeBackend(store, root / 'unit')
            VerificationSupervisor(store).execute(
                backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='critical-postgres-regression',
                command='./tooling/scripts/verify-critical-postgres-tests.sh',
                cwd=root, run_dir=shared_run_dir, timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_policy='forbid',
                retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotEqual(shared_run_dir, backend.backend_run_dir)
            self.assertFalse((backend.backend_run_dir / 'verification-home/gradle/init.d/retry.gradle').exists())

    def test_registered_gradle_namespace_is_stable_across_prepare_crash_replay(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')

            class NamespaceRecordingBackend(LifecycleBackend):
                def __init__(self, *args, **kwargs):
                    super().__init__(*args, **kwargs)
                    self.prepare_run_dirs = []

                def prepare(self, **kwargs):
                    self.prepare_run_dirs.append(pathlib.Path(kwargs['run_dir']))
                    return super().prepare(**kwargs)

            store = VerificationStore(root, control_root=root / 'control')
            backend = NamespaceRecordingBackend(store, root / 'unit')
            kwargs = dict(
                worktree=root, family_id='f', attempt_id='a',
                gate_id='critical-postgres-regression',
                command='./tooling/scripts/verify-critical-postgres-tests.sh',
                cwd=root, run_dir=root / '.agent-runs/runs/family/attempt/sandbox',
                timeout_seconds=2, sandbox_mode='required', critical=True,
                retry_policy='forbid',
                retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            first = VerificationSupervisor(store)
            first.inject_crash_at = 'after-prepare'
            with self.assertRaisesRegex(RuntimeError, 'injected-crash:after-prepare'):
                first.execute(backend, **kwargs)
            VerificationSupervisor(store).execute(backend, **kwargs)
            self.assertEqual(2, len(backend.prepare_run_dirs))
            self.assertEqual(backend.prepare_run_dirs[0], backend.prepare_run_dirs[1])
            self.assertEqual(1, backend.events.count('launch'))

    def test_registered_gradle_replay_refuses_init_script_in_its_stable_home(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')

            class NamespaceRecordingBackend(LifecycleBackend):
                def __init__(self, *args, **kwargs):
                    super().__init__(*args, **kwargs)
                    self.prepare_run_dirs = []

                def prepare(self, **kwargs):
                    self.prepare_run_dirs.append(pathlib.Path(kwargs['run_dir']))
                    return super().prepare(**kwargs)

            store = VerificationStore(root, control_root=root / 'control')
            backend = NamespaceRecordingBackend(store, root / 'unit')
            kwargs = dict(
                worktree=root, family_id='f', attempt_id='a',
                gate_id='critical-postgres-regression',
                command='./tooling/scripts/verify-critical-postgres-tests.sh',
                cwd=root, run_dir=root / '.agent-runs/runs/family/attempt/sandbox',
                timeout_seconds=2, sandbox_mode='required', critical=True,
                retry_policy='forbid',
                retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            first = VerificationSupervisor(store)
            first.inject_crash_at = 'after-prepare'
            with self.assertRaisesRegex(RuntimeError, 'injected-crash:after-prepare'):
                first.execute(backend, **kwargs)
            poisoned_home = backend.prepare_run_dirs[0] / 'verification-home/gradle/init.d'
            poisoned_home.mkdir(parents=True)
            (poisoned_home / 'retry.gradle').write_text('retry override')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(backend, **kwargs)
            self.assertEqual(1, len(backend.prepare_run_dirs))
            self.assertNotIn('launch', backend.events)

    def test_vc009_included_symlinked_gradle_project_fails_before_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            temp_root = pathlib.Path(temp)
            root = temp_root / 'candidate'
            root.mkdir()
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            source_root = pathlib.Path(__file__).resolve().parents[3]
            config_path = root / 'apps/ecommerce/backend/ecommerce.gradle'
            config_path.parent.mkdir(parents=True, exist_ok=True)
            config_path.write_bytes((source_root / 'apps/ecommerce/backend/ecommerce.gradle').read_bytes())
            wrapper_path = root / 'tooling/scripts/verify-critical-postgres-tests.sh'
            wrapper_path.parent.mkdir(parents=True, exist_ok=True)
            wrapper_path.write_bytes((source_root / 'tooling/scripts/verify-critical-postgres-tests.sh').read_bytes())
            included_project = root / 'modules/adapters/persistence'
            included_project.parent.mkdir(parents=True, exist_ok=True)
            external_project = temp_root / 'external-persistence'
            external_project.mkdir()
            (external_project / 'persistence.gradle').write_text(
                'tasks.withType(Test).configureEach { maxRetries = 2 }\n')
            (included_project / 'persistence.gradle').unlink()
            included_project.rmdir()
            included_project.symlink_to(external_project, target_is_directory=True)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a',
                    gate_id='critical-postgres-regression',
                    command='./tooling/scripts/verify-critical-postgres-tests.sh',
                    cwd=root, run_dir=root / 'run', timeout_seconds=2,
                    sandbox_mode='required', critical=True, retry_policy='forbid',
                    retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            self.assertNotIn('launch', backend.events)

    def test_vc009_generic_no_retry_marker_cannot_authorize_retrying_command(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='f', attempt_id='a', gate_id='g',
                    command='fake --retry-twice', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required', critical=True,
                    retry_policy='forbid', retry_controls=('no-internal-retries',))
            self.assertEqual(0, backend.events.count('launch'))

    def test_vc020_execution_receipts_never_persist_raw_output_hashes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            class OutputBackend(LifecycleBackend):
                def launch(self, prepared, **kwargs):
                    result = super().launch(prepared, **kwargs)
                    return type('OutputResult', (), {**result.__dict__,
                        'stdout': 'canary-output', 'stderr': 'other-output'})()
            backend = OutputBackend(store, root / 'unit')
            VerificationSupervisor(store).execute(
                backend, worktree=root, family_id='f', attempt_id='a', gate_id='g',
                command='python3 -c pass', cwd=root, run_dir=root / 'run', timeout_seconds=2,
                sandbox_mode='required')
            terminal_path = next(store.executions.glob('*/terminal.json'))
            terminal = json.loads(terminal_path.read_text())
            observation = json.loads(next(store.executions.glob('*/observation.json')).read_text())
            for record in (terminal, observation):
                self.assertNotIn('stdout_hash', record)
                self.assertNotIn('stderr_hash', record)
                self.assertNotIn('canary-output', json.dumps(record))
                self.assertNotIn('other-output', json.dumps(record))
            terminal['stdout_hash'] = hashlib.sha256(b'canary-output').hexdigest()
            terminal.pop('receipt_hash')
            from verification.serialization import canonical
            terminal['receipt_hash'] = hashlib.sha256(canonical(terminal)).hexdigest()
            legacy_write = dict(terminal, schema_version=1)
            legacy_write.pop('output_persistence')
            with self.assertRaisesRegex(StoreError, 'invalid-execution-terminal'):
                store.publish_execution_terminal(legacy_write)
            terminal_path.write_text(json.dumps(terminal))
            with self.assertRaisesRegex(StoreError, 'invalid-execution-terminal'):
                store.reconstruct_execution_terminals()

    def test_vc020_schema_v1_output_hashes_remain_historical_and_reconstructable(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            VerificationSupervisor(store).execute(
                backend, worktree=root, family_id='f', attempt_id='a', gate_id='g',
                command='python3 -c pass', cwd=root, run_dir=root / 'run', timeout_seconds=2,
                sandbox_mode='required')
            terminal_path = next(store.executions.glob('*/terminal.json'))
            terminal = json.loads(terminal_path.read_text())
            terminal['schema_version'] = 1
            terminal['stdout_hash'] = hashlib.sha256(b'old stdout').hexdigest()
            terminal['stderr_hash'] = hashlib.sha256(b'old stderr').hexdigest()
            terminal.pop('output_persistence')
            terminal.pop('receipt_hash')
            from verification.serialization import canonical
            terminal['receipt_hash'] = hashlib.sha256(canonical(terminal)).hexdigest()
            terminal_path.write_text(json.dumps(terminal))
            drained = next(store.executions.glob('*/drained.json'))
            drained_record = json.loads(drained.read_text())
            drained_record['terminal_receipt_hash'] = terminal['receipt_hash']
            drained.write_text(json.dumps(drained_record))
            recovered = VerificationStore(root, control_root=root / 'control').reconstruct_execution_terminals()
            self.assertEqual('PASS', recovered[0]['result'])

    def test_v1_execution_is_readable_but_embedded_evidence_is_not_republished(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            VerificationSupervisor(store).execute(
                backend, worktree=root, family_id='family', attempt_id='attempt', gate_id='gate',
                command='python3 -c pass', cwd=root, run_dir=root / 'run', timeout_seconds=2,
                sandbox_mode='required', input_fingerprint='c' * 64,
                terminal_record_builder=lambda *_: _evidence_record(store))
            terminal_path = next(store.executions.glob('*/terminal.json'))
            terminal = json.loads(terminal_path.read_text())
            evidence = terminal['verification_evidence']
            # Model a historical v1 receipt carrying a syntactically valid v2
            # evidence object. The evidence object itself must not become fresh
            # reusable authority merely because recovery can read the receipt.
            evidence_path = store.runs / evidence['family_id'] / evidence['evidence_id'] / 'terminal.json'
            evidence_path.unlink()
            (evidence_path.parent / 'projection.json').unlink(missing_ok=True)
            terminal['schema_version'] = 1
            terminal['stdout_hash'] = hashlib.sha256(b'').hexdigest()
            terminal['stderr_hash'] = hashlib.sha256(b'').hexdigest()
            terminal.pop('output_persistence', None)
            terminal.pop('receipt_hash', None)
            terminal['receipt_hash'] = digest(terminal)
            terminal_path.write_text(json.dumps(terminal))

            fresh = VerificationStore(root, control_root=root / 'control')
            recovered = fresh.reconstruct_execution_terminals()
            self.assertEqual(1, len(recovered))
            self.assertEqual(1, recovered[0]['schema_version'])
            self.assertEqual([], list(fresh.iter_evidence()))

    def test_vc009_historical_unbound_retry_proofs_remain_readable_only(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            VerificationSupervisor(store).execute(
                backend, worktree=root, family_id='f', attempt_id='a', gate_id='g',
                command='python3 -c pass', cwd=root, run_dir=root / 'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_policy='forbid',
                retry_controls=('no-internal-retries',))
            started_path = next(store.executions.glob('*/started.json'))
            started = json.loads(started_path.read_text())
            legacy_proof = {
                'schema_version': 1, 'policy': 'forbid', 'critical': True,
                'retry_controls': ['internal-retry-disabled:gradle-test-retry-plugin'],
                'retry_free': True,
            }
            started['retry_controls'] = legacy_proof['retry_controls']
            started['retry_policy_proof'] = legacy_proof
            started_path.write_text(json.dumps(started))

            terminal_path = next(store.executions.glob('*/terminal.json'))
            terminal = json.loads(terminal_path.read_text())
            terminal['started_hash'] = hashlib.sha256(started_path.read_bytes()).hexdigest()
            terminal['retry_policy_proof'] = legacy_proof
            terminal.pop('receipt_hash')
            from verification.serialization import canonical
            terminal['receipt_hash'] = hashlib.sha256(canonical(terminal)).hexdigest()
            terminal_path.write_text(json.dumps(terminal))

            reconstructed = VerificationStore(root, control_root=root / 'control').reconstruct_execution_terminals()
            self.assertEqual('PASS', reconstructed[0]['result'])
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                VerificationSupervisor(store).execute(
                    backend, worktree=root, family_id='new-family', attempt_id='new-attempt',
                    gate_id='g', command='fake --retry-twice', cwd=root,
                    run_dir=root / 'new-run', timeout_seconds=2, sandbox_mode='required',
                    critical=True, retry_policy='forbid',
                    retry_controls=('internal-retry-disabled:gradle-test-retry-plugin',))

    def test_vc009_09_missing_required_control_fails_closed(self):
        self.test_vc009_08_unknown_tool_retry_behavior_fails_closed_before_launch()

    def test_vc009_10_retry_free_claim_is_derived_from_controls(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit')
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_controls=('no-internal-retries',))
            terminal = json.loads(next(store.executions.glob('*/terminal.json')).read_text())
            self.assertTrue(terminal['retry_policy_proof']['retry_free'])
            self.assertEqual(['no-internal-retries'], terminal['retry_policy_proof']['retry_controls'])

    def test_vc009_10b_retry_free_boolean_cannot_override_declaration_facts(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit')
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=False, retry_policy='allow')
            terminal_path = next(store.executions.glob('*/terminal.json'))
            terminal = json.loads(terminal_path.read_text())
            terminal['retry_policy_proof']['retry_free'] = True
            terminal.pop('receipt_hash')
            from verification.serialization import canonical
            terminal['receipt_hash'] = hashlib.sha256(canonical(terminal)).hexdigest()
            terminal_path.write_text(json.dumps(terminal))
            with self.assertRaisesRegex(StoreError, 'retry-policy-violation'):
                store.reconstruct_execution_terminals()

    def test_vc009_registered_input_digest_is_validated_during_reconstruction(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self._copy_registered_retry_inputs(root, 'critical-postgres-gate-disables-gradle-test-retry-v1')
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root / 'unit')
            VerificationSupervisor(store).execute(
                backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='critical-postgres-regression',
                command='./tooling/scripts/verify-critical-postgres-tests.sh',
                cwd=root, run_dir=root / 'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_policy='forbid',
                retry_controls=('critical-postgres-gate-disables-gradle-test-retry-v1',))
            terminal_path = next(store.executions.glob('*/terminal.json'))
            terminal = json.loads(terminal_path.read_text())
            terminal['retry_policy_proof']['control_evidence']['registered_inputs_sha256'] = '0' * 64
            terminal.pop('receipt_hash')
            from verification.serialization import canonical
            terminal['receipt_hash'] = hashlib.sha256(canonical(terminal)).hexdigest()
            terminal_path.write_text(json.dumps(terminal))
            with self.assertRaisesRegex(StoreError, 'retry-policy-violation'):
                VerificationStore(root, control_root=root / 'control').reconstruct_execution_terminals()

    def test_vc009_11_noncritical_unknown_behavior_remains_allowed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit')
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=False, retry_policy='allow')
            self.assertEqual(1, backend.events.count('launch'))

    def test_vc009_12_retry_control_proof_survives_serialization(self):
        self._vc009_positive('critical-rabbit-gate-disables-gradle-test-retry-v1')

    def test_vc009_13_stale_retry_declaration_rejected_on_execution_replay(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit'); supervisor = VerificationSupervisor(store)
            kwargs = dict(worktree=root, family_id='f', attempt_id='a', gate_id='g',
                command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_controls=('no-internal-retries',))
            supervisor.execute(backend, **kwargs)
            with self.assertRaisesRegex(RuntimeError, 'retry-policy-violation'):
                supervisor.execute(backend, **{**kwargs,
                    'retry_controls': ('critical-rabbit-gate-disables-gradle-test-retry-v1',)})
            self.assertEqual(1, backend.events.count('launch'))

    def test_vc009_14_command_identity_matches_durable_execution(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit')
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_controls=('no-internal-retries',))
            started = json.loads(next(store.executions.glob('*/started.json')).read_text())
            terminal = json.loads(next(store.executions.glob('*/terminal.json')).read_text())
            self.assertEqual(started['command_identity'], terminal['command_identity'])
            self.assertEqual(started['execution_id'], terminal['execution_id'])

    def test_vc009_15_projection_loss_cannot_create_retry_proof(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit')
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_controls=('no-internal-retries',))
            terminal_path = next(store.executions.glob('*/terminal.json'))
            expected = json.loads(terminal_path.read_text())['retry_policy_proof']
            (terminal_path.parent / 'projection.json').unlink()
            reconstructed = VerificationStore(root, control_root=root/'control').reconstruct_execution_terminals()
            self.assertEqual(expected, reconstructed[0]['retry_policy_proof'])

    def test_vc009_16_fresh_process_reconstruction_preserves_retry_proof(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit')
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id='g', command='python3 -c pass', cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_controls=('no-internal-retries',))
            fresh = VerificationStore(root, control_root=root/'control')
            rebuilt = fresh.reconstruct_execution_terminals()
            self.assertEqual(['no-internal-retries'], rebuilt[0]['retry_policy_proof']['retry_controls'])

    def _vc009_positive(self, declaration):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); store = VerificationStore(root, control_root=root/'control')
            backend = LifecycleBackend(store, root/'unit')
            gate_id, command = {
                'no-internal-retries': ('g', 'python3 -c pass'),
                'critical-postgres-gate-disables-gradle-test-retry-v1': (
                    'critical-postgres-regression', './tooling/scripts/verify-critical-postgres-tests.sh'),
                'critical-rabbit-gate-disables-gradle-test-retry-v1': (
                    'critical-rabbitmq-regression', './tooling/scripts/verify-critical-rabbitmq-tests.sh'),
            }[declaration]
            if declaration != 'no-internal-retries':
                self._copy_registered_retry_inputs(root, declaration)
            VerificationSupervisor(store).execute(backend, worktree=root, family_id='f', attempt_id='a',
                gate_id=gate_id, command=command, cwd=root, run_dir=root/'run', timeout_seconds=2,
                sandbox_mode='required', critical=True, retry_policy='forbid', retry_controls=(declaration,))
            terminal = json.loads(next(store.executions.glob('*/terminal.json')).read_text())
            self.assertEqual([declaration], terminal['retry_policy_proof']['retry_controls'])
            self.assertTrue(terminal['retry_policy_proof']['retry_free'])

    def _copy_registered_retry_inputs(self, root, control_id):
        source_root = pathlib.Path(__file__).resolve().parents[3]
        for relative in _RETRY_CONTROL_INPUT_SHA256[control_id]:
            source = source_root / relative
            destination = root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(source.read_bytes())

    def test_e1_competing_repository_admission_rejects_waiting_execution_before_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            first_entered_launch = threading.Event()
            release_first_launch = threading.Event()

            class BlockingBackend(LifecycleBackend):
                def launch(self, prepared, **kwargs):
                    first_entered_launch.set()
                    if not release_first_launch.wait(5):
                        raise AssertionError('test failed to release first launch')
                    return super().launch(prepared, **kwargs)

            backends = [BlockingBackend(store, root / 'first'), LifecycleBackend(store, root / 'second')]
            outcomes = []
            second_started = threading.Event()

            def invoke(index):
                try:
                    if index == 1:
                        second_started.set()
                    result, _ = VerificationSupervisor(VerificationStore(root, control_root=root / 'control')).execute(
                        backends[index], worktree=root, family_id=f'family-{index}',
                        attempt_id=f'attempt-{index}', gate_id='gate', command='python3 -c pass',
                        cwd=root, run_dir=root / f'run-{index}', timeout_seconds=2, sandbox_mode='required')
                    outcomes.append(('PASS', result.exit_code))
                except Exception as exc:
                    outcomes.append(('REJECTED', str(exc)))

            first = threading.Thread(target=invoke, args=(0,))
            first.start()
            self.assertTrue(first_entered_launch.wait(5))
            second = threading.Thread(target=invoke, args=(1,))
            second.start()
            self.assertTrue(second_started.wait(5))
            time.sleep(0.1)
            release_first_launch.set()
            first.join(5)
            second.join(5)

            self.assertFalse(first.is_alive())
            self.assertFalse(second.is_alive())
            self.assertEqual(1, sum(event == 'launch' for backend in backends for event in backend.events))
            self.assertEqual(1, sum(outcome[0] == 'PASS' for outcome in outcomes))
            self.assertEqual(1, sum(outcome[0] == 'REJECTED' for outcome in outcomes))
            self.assertIn(('REJECTED', 'busy'), outcomes)
            self.assertEqual([], backends[1].events)

    def test_e3_start_and_publication_crashes_recover_without_duplicate_launch(self):
        for boundary, expected_launches, expected_state in (
                ('after-admission', 0, None),
                ('after-started', 0, SupervisorState.ABORTED_PREPARED),
                ('during-launch', 0, SupervisorState.ABORTED_PREPARED),
                ('after-launch', 1, SupervisorState.UNCERTAIN),
                ('after-terminal', 1, SupervisorState.TERMINAL),
                ('after-evidence-projection', 1, SupervisorState.TERMINAL),
                ('after-index-projection', 1, SupervisorState.TERMINAL)):
            with self.subTest(boundary=boundary), tempfile.TemporaryDirectory() as temp:
                root = pathlib.Path(temp)
                store = VerificationStore(root, control_root=root / 'control')
                launch_count = []

                class CountingBackend(LifecycleBackend):
                    def launch(self, prepared, **kwargs):
                        launch_count.append('launch')
                        return super().launch(prepared, **kwargs)

                backend = CountingBackend(store, root / 'unit')
                supervisor = VerificationSupervisor(store)
                supervisor.inject_crash_at = boundary
                with self.assertRaisesRegex(RuntimeError, 'injected-crash'):
                    supervisor.execute(backend, worktree=root, family_id='family', attempt_id='attempt',
                        gate_id='gate', command='python3 -c pass', cwd=root, run_dir=root / 'run',
                        timeout_seconds=2, sandbox_mode='required')

                fresh_store = VerificationStore(root, control_root=root / 'control')
                fresh_backend = CountingBackend(fresh_store, root / 'unit')
                recovery = VerificationSupervisor(fresh_store).recover(
                    lambda _started: fresh_backend)
                if expected_state is None:
                    self.assertEqual((), recovery)
                else:
                    self.assertEqual(expected_state, recovery[0].state)
                self.assertEqual(expected_launches, len(launch_count))
                second_recovery = VerificationSupervisor(
                    VerificationStore(root, control_root=root / 'control')).recover(
                        lambda _started: fresh_backend)
                if expected_state is SupervisorState.UNCERTAIN:
                    self.assertEqual(SupervisorState.UNCERTAIN, second_recovery[0].state)
                    self.assertEqual(recovery[0].reason_code, second_recovery[0].reason_code)
                else:
                    self.assertEqual((), second_recovery)
                self.assertEqual(expected_launches, len(launch_count))
                if expected_state is SupervisorState.UNCERTAIN:
                    with self.assertRaisesRegex(StoreError, 'verification-owned'):
                        fresh_store.admit_repository_verification()

    def test_pd3_unresolved_critical_failure_blocks_supervisor_before_prepare(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            failed = CriticalFailureBackend(store, root)
            first = VerificationSupervisor(store)
            first.execute(failed, worktree=root, family_id='family-1', attempt_id='attempt-1',
                gate_id='critical-gate', command='python3 -c pass', cwd=root, run_dir=root / 'run-1',
                timeout_seconds=2, sandbox_mode='required', profile_hash='profile-hash',
                input_fingerprint='fingerprint', critical=True, retry_controls=('no-internal-retries',))
            context = {'repository_id': store.repository_id, 'profile_hash': 'profile-hash',
                'gate_id': 'critical-gate', 'fingerprint': 'fingerprint'}
            self.assertIsNotNone(store.current_failure(context))
            retry = LifecycleBackend(store, root)
            with self.assertRaisesRegex(RuntimeError, 'CRITICAL_FAILURE_FENCE_ACTIVE'):
                VerificationSupervisor(store).execute(retry, worktree=root, family_id='family-2',
                    attempt_id='attempt-2', gate_id='critical-gate', command='python3 -c pass',
                    cwd=root, run_dir=root / 'run-2', timeout_seconds=2, sandbox_mode='required',
                    profile_hash='profile-hash', input_fingerprint='fingerprint',
                    preflight=lambda: SimpleNamespace(action='REUSE'))
            self.assertEqual([], retry.events)

    def test_pd4_pd5_exact_grant_is_consumed_before_retry_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store, grant_key = trusted_test_store(root)
            VerificationSupervisor(store).execute(CriticalFailureBackend(store, root),
                worktree=root, family_id='family-1', attempt_id='attempt-1', gate_id='critical-gate',
                command='python3 -c pass', cwd=root, run_dir=root / 'run-1', timeout_seconds=2,
                sandbox_mode='required', profile_hash='profile-hash', input_fingerprint='fingerprint',
                critical=True, retry_controls=('no-internal-retries',))
            context = {'repository_id': store.repository_id, 'profile_hash': 'profile-hash',
                'gate_id': 'critical-gate', 'fingerprint': 'fingerprint',
                'policy_identity': 'sha256:' + 'a' * 64, 'backend_identity': 'test-qualified/v1'}
            failure = store.current_failure(context)
            grant = signed_test_grant(store, grant_key, 'human-grant', failure['failure_id'], context,
                reason='exercise-exact-one-shot-retry')
            retry = GrantCheckingBackend(store, root)
            result, _ = VerificationSupervisor(store).execute(retry, worktree=root,
                family_id='family-2', attempt_id='attempt-2', gate_id='critical-gate',
                command='python3 -c pass', cwd=root, run_dir=root / 'run-2', timeout_seconds=2,
                sandbox_mode='required', profile_hash='profile-hash', input_fingerprint='fingerprint',
                failure_grant_id='human-grant', retry_scope=grant['retry_scope'],
                preflight=lambda: SimpleNamespace(action='REUSE'))
            self.assertEqual(0, result.exit_code)
            self.assertIn('consumption-before-launch', retry.events)
            with self.assertRaisesRegex(StoreError, 'FAILURE_GRANT_CONSUMED'):
                store.consume_failure_grant('human-grant', failure_id=failure['failure_id'],
                    context=context, retry_scope=grant['retry_scope'], execution_id='third-retry')
            later = LifecycleBackend(store, root)
            with self.assertRaisesRegex(RuntimeError, 'CRITICAL_FAILURE_FENCE_ACTIVE'):
                VerificationSupervisor(store).execute(later, worktree=root, family_id='family-3',
                    attempt_id='attempt-3', gate_id='critical-gate', command='python3 -c pass',
                    cwd=root, run_dir=root / 'run-3', timeout_seconds=2, sandbox_mode='required',
                    profile_hash='profile-hash', input_fingerprint='fingerprint')
            self.assertEqual([], later.events)

    def test_pd6_crash_after_consumption_burns_grant_without_payload(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store, grant_key = trusted_test_store(root)
            VerificationSupervisor(store).execute(CriticalFailureBackend(store, root),
                worktree=root, family_id='family-1', attempt_id='attempt-1', gate_id='critical-gate',
                command='python3 -c pass', cwd=root, run_dir=root / 'run-1', timeout_seconds=2,
                sandbox_mode='required', profile_hash='profile-hash', input_fingerprint='fingerprint',
                critical=True, retry_controls=('no-internal-retries',))
            context = {'repository_id': store.repository_id, 'profile_hash': 'profile-hash',
                'gate_id': 'critical-gate', 'fingerprint': 'fingerprint',
                'policy_identity': 'sha256:' + 'a' * 64, 'backend_identity': 'test-qualified/v1'}
            failure = store.current_failure(context)
            grant = signed_test_grant(store, grant_key, 'human-grant', failure['failure_id'], context,
                reason='crash-after-consume-fixture')
            crashed_backend = GrantCheckingBackend(store, root)
            crashed = VerificationSupervisor(store)
            crashed.inject_crash_at = 'after-grant-consumed'
            with self.assertRaisesRegex(RuntimeError, 'injected-crash:after-grant-consumed'):
                crashed.execute(crashed_backend, worktree=root, family_id='family-2',
                    attempt_id='attempt-2', gate_id='critical-gate', command='python3 -c pass',
                    cwd=root, run_dir=root / 'run-2', timeout_seconds=2, sandbox_mode='required',
                    profile_hash='profile-hash', input_fingerprint='fingerprint',
                    failure_grant_id='human-grant', retry_scope=grant['retry_scope'])
            self.assertNotIn('launch', crashed_backend.events)
            fresh = VerificationStore(root, control_root=root / 'control', issuer_registry=store.issuer_registry)
            self.assertTrue(fresh.grant_consumed('human-grant'))
            retry = GrantCheckingBackend(fresh, root)
            with self.assertRaisesRegex(RuntimeError, 'FAILURE_GRANT_CONSUMED'):
                VerificationSupervisor(fresh).execute(retry, worktree=root, family_id='family-2',
                    attempt_id='attempt-2', gate_id='critical-gate', command='python3 -c pass',
                    cwd=root, run_dir=root / 'run-2', timeout_seconds=2, sandbox_mode='required',
                    profile_hash='profile-hash', input_fingerprint='fingerprint',
                    failure_grant_id='human-grant', retry_scope=grant['retry_scope'])
            self.assertNotIn('launch', retry.events)

    def test_e38_full_double_critical_failure_requires_new_grant(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store, grant_key = trusted_test_store(root)
            supervisor = VerificationSupervisor(store)
            first_backend = CriticalFailureBackend(store, root / 'first')
            supervisor.execute(first_backend, worktree=root, family_id='family-1', attempt_id='attempt-1',
                gate_id='critical-gate', command='python3 -c pass', cwd=root, run_dir=root / 'run-1',
                timeout_seconds=2, sandbox_mode='required', profile_hash='profile-hash',
                input_fingerprint='fingerprint', critical=True, retry_controls=('no-internal-retries',))
            context = {'repository_id': store.repository_id, 'profile_hash': 'profile-hash',
                'gate_id': 'critical-gate', 'fingerprint': 'fingerprint',
                'policy_identity': 'sha256:' + 'a' * 64, 'backend_identity': 'test-qualified/v1'}
            first_failure = store.current_failure(context)
            grant = signed_test_grant(store, grant_key, 'grant-1', first_failure['failure_id'], context,
                reason='first-explicit-retry')

            class GrantCheckingCriticalBackend(GrantCheckingBackend):
                def launch(self, prepared, **kwargs):
                    if not next(self.store.consumptions.glob('*.json'), None):
                        raise AssertionError('grant-consumption-after-launch')
                    self.events.append('consumption-before-launch')
                    result = LifecycleBackend.launch(self, prepared, **kwargs)
                    result.exit_code = 1
                    return result

            second_backend = GrantCheckingCriticalBackend(store, root / 'second')
            supervisor.execute(second_backend, worktree=root, family_id='family-2', attempt_id='attempt-2',
                gate_id='critical-gate', command='python3 -c pass', cwd=root, run_dir=root / 'run-2',
                timeout_seconds=2, sandbox_mode='required', profile_hash='profile-hash',
                input_fingerprint='fingerprint', critical=True, retry_controls=('no-internal-retries',),
                failure_grant_id='grant-1', retry_scope=grant['retry_scope'])
            self.assertIn('launch', second_backend.events)

            second_failure = store.current_failure(context)
            self.assertNotEqual(first_failure['failure_id'], second_failure['failure_id'])
            self.assertEqual(first_failure['failure_id'], second_failure['predecessor_failure_id'])
            self.assertTrue(store.grant_consumed('grant-1'))
            third_backend = LifecycleBackend(store, root / 'third')
            with self.assertRaisesRegex(RuntimeError, 'FAILURE_GRANT_CONSUMED'):
                VerificationSupervisor(VerificationStore(root, control_root=root / 'control',
                    issuer_registry=store.issuer_registry)).execute(
                    third_backend, worktree=root, family_id='family-3', attempt_id='attempt-3',
                    gate_id='critical-gate', command='python3 -c pass', cwd=root, run_dir=root / 'run-3',
                    timeout_seconds=2, sandbox_mode='required', profile_hash='profile-hash',
                    input_fingerprint='fingerprint', failure_grant_id='grant-1',
                    retry_scope=grant['retry_scope'])
            self.assertNotIn('launch', third_backend.events)

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

    def test_corrupt_launch_marker_keeps_repository_admission_blocked(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            backend = LifecycleBackend(store, root, launch_state='ACTIVE')
            supervisor = VerificationSupervisor(store)
            supervisor.inject_crash_at = 'after-started'
            with self.assertRaisesRegex(RuntimeError, 'injected-crash'):
                supervisor.execute(backend, worktree=root, family_id='family', attempt_id='attempt',
                    gate_id='gate', command='python3 -c pass', cwd=root, run_dir=root / 'run',
                    timeout_seconds=2, sandbox_mode='required')

            journal = next(store.executions.iterdir())
            (journal / 'launching.json').write_text('{ truncated', encoding='utf-8')
            fresh_store = VerificationStore(root, control_root=root / 'control')
            recovered = VerificationSupervisor(fresh_store).recover(
                lambda _record: LifecycleBackend(fresh_store, root, launch_state='DRAINED'))

            self.assertEqual('UNCERTAIN', recovered[0].state.value)
            self.assertEqual('EXECUTION_LAUNCH_MARKER_INVALID', recovered[0].reason_code)
            self.assertFalse((journal / 'terminal.json').exists())
            self.assertFalse((journal / 'drained.json').exists())

            marker = journal / 'launching.json'
            started = json.loads((journal / 'started.json').read_text(encoding='utf-8'))
            valid_target = journal / 'launch-marker-target.json'
            valid_target.write_text(json.dumps({'schema_version': 1,
                'execution_id': started['execution_id'],
                'execution_identity': started['execution_identity'],
                'launch_intent_hash': started['launch_intent_hash']}), encoding='utf-8')
            marker.unlink()
            marker.symlink_to(valid_target)
            recovered = VerificationSupervisor(fresh_store).recover()
            self.assertEqual('UNCERTAIN', recovered[0].state.value)
            self.assertEqual('EXECUTION_LAUNCH_MARKER_INVALID', recovered[0].reason_code)

            real_read_text = pathlib.Path.read_text
            def unreadable_marker(path, *args, **kwargs):
                if path == marker:
                    raise PermissionError('injected unreadable launch marker')
                return real_read_text(path, *args, **kwargs)
            with mock.patch.object(pathlib.Path, 'read_text', unreadable_marker):
                recovered = VerificationSupervisor(fresh_store).recover()
            self.assertEqual('UNCERTAIN', recovered[0].state.value)
            self.assertEqual('EXECUTION_LAUNCH_MARKER_INVALID', recovered[0].reason_code)

            self.assertFalse((journal / 'terminal.json').exists())
            self.assertFalse((journal / 'drained.json').exists())
            with self.assertRaises(StoreError):
                fresh_store.admit_repository_verification()

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
                self.assertNotIn('stdout_hash', receipt)
                self.assertNotIn('stderr_hash', receipt)
                self.assertEqual('UNAVAILABLE', receipt['output_persistence'])
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


class CriticalFailureBackend(LifecycleBackend):
    def launch(self, prepared, **kwargs):
        result = super().launch(prepared, **kwargs)
        result.exit_code = 1
        return result


class GrantCheckingBackend(LifecycleBackend):
    def launch(self, prepared, **kwargs):
        if not next(self.store.consumptions.glob('*.json'), None):
            raise AssertionError('grant-consumption-after-launch')
        self.events.append('consumption-before-launch')
        return super().launch(prepared, **kwargs)


if __name__ == '__main__':
    unittest.main()
