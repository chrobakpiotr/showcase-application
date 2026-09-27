import pathlib
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import verification_command as command


class VerificationCommandTest(unittest.TestCase):
    def test_argv_allowlist_and_no_shell_fallback(self):
        self.assertEqual(['python3', '-c', 'print(1)'], command.verification_argv("python3 -c 'print(1)'"))
        for value in ('python3 -c x | cat', 'python3 $(whoami)', 'sh -c true', ''):
            with self.subTest(value=value), self.assertRaises(command.CommandRejected):
                command.verification_argv(value)

    def test_capture_environment_override_and_exit(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            result = command.run_command(
                "python3 -c 'import os,sys; print(os.getenv(\"VERIFY_MARK\")); print(\"err\",file=sys.stderr)'",
                cwd=root, run_dir=root / 'run', timeout_seconds=5, sandbox_mode='off',
                environment={'PATH': __import__('os').environ.get('PATH', ''), 'CI': 'true'},
            )
            self.assertEqual(0, result.exit_code)
            self.assertIn('None', result.stdout)
            self.assertIn('err', result.stderr)
            failed = command.run_command("python3 -c 'raise SystemExit(7)'", cwd=root,
                                         run_dir=root / 'run2', timeout_seconds=5, sandbox_mode='off')
            self.assertEqual(7, failed.exit_code)

    def test_timeout_kills_process_group_and_captures_output(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            result = command.run_command(
                "python3 -c 'import time; print(\"ready\",flush=True); time.sleep(10)'",
                cwd=root, run_dir=root / 'run', timeout_seconds=0.1, sandbox_mode='off')
            self.assertTrue(result.timed_out)
            self.assertIn('ready', result.stdout)

    def test_invalid_cwd_and_missing_executable_are_errors(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            invalid_cwd = command.run_command('python3 -V', cwd=root / 'missing', run_dir=root / 'a',
                                              timeout_seconds=1, sandbox_mode='off')
            self.assertEqual('invalid-cwd', invalid_cwd.error)
            # An allowlisted but absent explicit relative executable reaches Popen.
            missing = command.run_command('./gradlew test', cwd=root, run_dir=root / 'b',
                                          timeout_seconds=1, sandbox_mode='off')
            self.assertEqual('missing-executable', missing.error)

    def test_v2_capability_requirement_fails_before_spawn(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            result = command.run_command('python3 -V', cwd=root, run_dir=root / 'run',
                                         timeout_seconds=1, sandbox_mode='off',
                                         required_capabilities=True)
            self.assertEqual('backend-not-v2-qualified', result.error)
            self.assertIsNone(result.exit_code)

    def test_claimed_backend_flags_do_not_authorize_spawn(self):
        with tempfile.TemporaryDirectory() as temp, \
             mock.patch.object(command.verification_sandbox, 'build_plan') as build:
            root = pathlib.Path(temp)
            plan_type = command.verification_sandbox.SandboxPlan
            build.return_value = plan_type(['python3','-V'], {'PATH':'/usr/bin'}, 'claimed', True,
                                           {'protected_paths':True,'descendant_containment':'strong'})
            result = command.run_command('python3 -V', cwd=root, run_dir=root/'run', timeout_seconds=1,
                                         sandbox_mode='auto', required_capabilities=True)
            self.assertEqual('backend-not-v2-qualified', result.error)


if __name__ == '__main__':
    unittest.main()
