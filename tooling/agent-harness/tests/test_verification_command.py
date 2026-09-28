import pathlib
import sys
import tempfile
import unittest
import subprocess
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import verification_command as command


class VerificationCommandTest(unittest.TestCase):
    def git_root(self, root):
        subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
        subprocess.run(['git', 'config', 'user.email', 'test@example.invalid'], cwd=root, check=True)
        subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
        (root / 'seed').write_text('seed')
        subprocess.run(['git', 'add', 'seed'], cwd=root, check=True)
        subprocess.run(['git', 'commit', '-qm', 'seed'], cwd=root, check=True)

    def test_argv_allowlist_and_no_shell_fallback(self):
        self.assertEqual(['python3', '-c', 'print(1)'], command.verification_argv("python3 -c 'print(1)'"))
        for value in ('python3 -c x | cat', 'python3 $(whoami)', 'sh -c true', ''):
            with self.subTest(value=value), self.assertRaises(command.CommandRejected):
                command.verification_argv(value)

    def test_R15_unqualified_backend_never_executes_payload_even_when_sandbox_is_off(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            sentinel = root / 'sentinel'
            with mock.patch.object(command.CommandExecutionBackend, 'launch') as launch:
                result = command.run_command(
                    f"python3 -c 'open(\\\"{sentinel}\\\", \\\"w\\\").write(\\\"ran\\\")'",
                    cwd=root, run_dir=root / 'run', timeout_seconds=5, sandbox_mode='off',
                    required_capabilities=False)
            self.assertEqual('backend-not-v2-qualified', result.error)
            self.assertFalse(sentinel.exists())
            launch.assert_not_called()

    def test_R15_backend_discovery_or_prepare_failure_never_reaches_payload(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            with mock.patch.object(command.verification_sandbox, 'build_plan',
                                   side_effect=RuntimeError('backend-prepare-failed')), \
                 mock.patch.object(command.CommandExecutionBackend, 'launch') as launch:
                result = command.run_command('python3 -V', cwd=root, run_dir=root / 'run',
                                             timeout_seconds=1, required_capabilities=False)
            self.assertEqual('backend-prepare-failed', result.error)
            launch.assert_not_called()

    def test_unqualified_backend_rejects_long_running_payload_before_spawn(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            result = command.run_command(
                "python3 -c 'import time; print(\"ready\",flush=True); time.sleep(10)'",
                cwd=root, run_dir=root / 'run', timeout_seconds=0.1, sandbox_mode='off')
            self.assertEqual('backend-not-v2-qualified', result.error)
            self.assertFalse(result.timed_out)

    def test_invalid_cwd_and_missing_executable_are_errors(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            invalid_cwd = command.run_command('python3 -V', cwd=root / 'missing', run_dir=root / 'a',
                                              timeout_seconds=1, sandbox_mode='off')
            self.assertEqual('invalid-cwd', invalid_cwd.error)
            # Backend qualification precedes executable launch.
            missing = command.run_command('./gradlew test', cwd=root, run_dir=root / 'b',
                                          timeout_seconds=1, sandbox_mode='off')
            self.assertEqual('backend-not-v2-qualified', missing.error)

    def test_v2_capability_requirement_fails_before_spawn(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            result = command.run_command('python3 -V', cwd=root, run_dir=root / 'run',
                                         timeout_seconds=1, sandbox_mode='off',
                                         required_capabilities=True)
            self.assertEqual('backend-not-v2-qualified', result.error)
            self.assertIsNone(result.exit_code)

    def test_claimed_backend_flags_do_not_authorize_spawn(self):
        with tempfile.TemporaryDirectory() as temp, \
             mock.patch.object(command.verification_sandbox, 'build_plan') as build:
            root = pathlib.Path(temp)
            self.git_root(root)
            plan_type = command.verification_sandbox.SandboxPlan
            build.return_value = plan_type(['python3','-V'], {'PATH':'/usr/bin'}, 'claimed', True,
                                           {'protected_paths':True,'descendant_containment':'strong'})
            result = command.run_command('python3 -V', cwd=root, run_dir=root/'run', timeout_seconds=1,
                                         sandbox_mode='auto', required_capabilities=True)
            self.assertEqual('backend-not-v2-qualified', result.error)


if __name__ == '__main__':
    unittest.main()
