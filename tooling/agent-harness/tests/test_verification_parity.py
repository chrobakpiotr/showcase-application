"""Cross-surface assertions for the canonical accepted-plan consumer."""
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

HARNESS = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HARNESS))

import runner
import orchestrate


class VerificationParityTest(unittest.TestCase):
    def test_runner_forwards_only_exact_accepted_plan_to_shared_cli(self):
        plan_id = 'verification-plan-v1:sha256:' + 'a' * 64
        response = {'outcome': 'PASS', 'machine_category': None}
        record = {
            'plan_id': plan_id,
            'profile_id': 'showcase',
            'profile_hash': 'b' * 64,
            'control_executable_binding': {'path': sys.executable, 'sha256': 'c' * 64},
        }
        profile = type('Profile', (), {
            'content_hash': record['profile_hash'], 'toolchain_directories': (),
        })()
        safe_environment = {'PATH': '/trusted/bin', 'HOME': '/safe/home'}
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            out = root / 'result.json'
            completed = subprocess.CompletedProcess([], 0, json.dumps(response), '')
            with mock.patch('verification.authority.resolve_accepted', return_value=record) as resolve, \
                 mock.patch('verification.profile.load_profile', return_value=profile) as load_profile, \
                 mock.patch('verification_sandbox.verify_executable_binding') as verify_binding, \
                 mock.patch('verification_sandbox._safe_env', return_value=safe_environment) as safe_env, \
                 mock.patch.object(runner.subprocess, 'run', return_value=completed) as invoke:
                passed, evidence = runner.run_verification({}, root, out, 5,
                    sandbox_mode='required', accepted_plan_id=plan_id)
        self.assertTrue(passed)
        self.assertEqual(plan_id, evidence[0]['plan_id'])
        resolve.assert_called_once_with(root, plan_id)
        load_profile.assert_called_once_with(
            HARNESS / 'verification-profiles' / 'showcase.json', repository=root)
        verify_binding.assert_called_once_with(record['control_executable_binding'])
        safe_env.assert_called_once()
        argv = invoke.call_args.args[0]
        self.assertEqual([sys.executable, '-I', str(HARNESS / 'verify.py'), 'run',
            '--mode', 'integration', '--repo', str(root), '--plan-id', plan_id], argv)
        self.assertEqual(root, invoke.call_args.kwargs['cwd'])
        self.assertEqual(safe_environment, invoke.call_args.kwargs['env'])
        self.assertEqual(5, invoke.call_args.kwargs['timeout'])
        self.assertNotIn('--base', argv)

    def test_runner_missing_plan_is_blocked_without_cli_or_payload(self):
        with tempfile.TemporaryDirectory() as temp, \
             mock.patch.object(runner.subprocess, 'run') as invoke:
            passed, evidence = runner.run_verification({}, pathlib.Path(temp),
                pathlib.Path(temp) / 'result.json', 5, sandbox_mode='required')
        self.assertFalse(passed)
        self.assertEqual('verification-blocked', evidence[0]['machine_category'])
        self.assertEqual(5, evidence[0]['exit_code'])
        invoke.assert_not_called()

    def test_accepted_plan_cli_reaches_durable_executor_and_returns_pass(self):
        import verify
        record = {'plan_id': 'verification-plan-v1:sha256:' + 'b' * 64,
                  'lifecycle_generation': 2}
        profile, plan = object(), object()
        units = [{'unit_id': 'unit-current', 'obligation_ids': ['obligation-current']}]
        result = type('Result', (), {'outcome': 'PASS',
            'to_record': lambda self: {'outcome': 'PASS'}})()
        with tempfile.TemporaryDirectory() as temp, \
             mock.patch('verification.authority.resolve_execution',
                        return_value=(record, profile, plan, units)) as resolve, \
             mock.patch('verification.executor.execute_plan', return_value=result) as execute, \
             mock.patch('verification.store.VerificationStore'), \
             mock.patch('sys.stdout', new_callable=__import__('io').StringIO) as output:
            code = verify.main(['run', '--mode', 'integration', '--repo', temp,
                '--plan-id', record['plan_id'], '--unit-id', 'unit-current'])
        self.assertEqual(0, code)
        resolve.assert_called_once()
        execute.assert_called_once()
        self.assertEqual(record, execute.call_args.kwargs['authority_context'])
        self.assertEqual(['unit-current'], json.loads(output.getvalue())['execution_units'])

    def test_orchestrator_uses_the_same_bound_plan_invocation_as_runner(self):
        plan_id = 'verification-plan-v1:sha256:' + 'd' * 64
        record = {
            'plan_id': plan_id,
            'task_id': 'T-1',
            'task_attempt': 3,
            'profile_id': 'showcase',
            'profile_hash': 'e' * 64,
            'control_executable_binding': {'path': sys.executable, 'sha256': 'f' * 64},
        }
        profile = type('Profile', (), {
            'content_hash': record['profile_hash'], 'toolchain_directories': (),
        })()
        safe_environment = {'PATH': '/trusted/bin', 'HOME': '/safe/home'}
        response = subprocess.CompletedProcess([], 0, '{"outcome":"PASS"}', '')
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            output_dir = root / 'evidence'
            with mock.patch('orchestrate.h.resolve_accepted_verification_plan',
                            return_value=record) as resolve, \
                 mock.patch('orchestrate.load_profile', return_value=profile) as load_profile, \
                 mock.patch('verification_sandbox.verify_executable_binding') as verify_binding, \
                 mock.patch('verification_sandbox._safe_env', return_value=safe_environment), \
                 mock.patch('orchestrate.subprocess.run', return_value=response) as invoke:
                accepted, result = orchestrate.run_accepted_verification(
                    root, output_dir, plan_id, 5, expected_task_id='T-1',
                    expected_task_attempt=3, expected_record=record)
        self.assertEqual(record, accepted)
        self.assertIs(response, result)
        resolve.assert_called_once_with(root, plan_id)
        load_profile.assert_called_once_with(
            HARNESS / 'verification-profiles' / 'showcase.json', repository=root)
        verify_binding.assert_called_once_with(record['control_executable_binding'])
        self.assertEqual([sys.executable, '-I', str(HARNESS / 'verify.py'), 'run',
            '--mode', 'integration', '--repo', str(root), '--plan-id', plan_id],
            invoke.call_args.args[0])
        self.assertEqual(root, invoke.call_args.kwargs['cwd'])
        self.assertEqual(safe_environment, invoke.call_args.kwargs['env'])


if __name__ == '__main__':
    unittest.main()
