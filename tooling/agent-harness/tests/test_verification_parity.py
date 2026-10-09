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


class VerificationParityTest(unittest.TestCase):
    def test_runner_forwards_only_exact_accepted_plan_to_shared_cli(self):
        plan_id = 'verification-plan-v1:sha256:' + 'a' * 64
        response = {'outcome': 'PASS', 'machine_category': None, 'plan_id': plan_id}
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            (root / 'docs' / 'specs' / 'SDD-OBS-001').mkdir(parents=True)
            out = root / 'result.json'
            task_commands = [{'command': 'python3 -m unittest tests.test_example', 'cwd': '.'}]
            packet = {'feature': 'SDD-OBS-001', 'task': 'T-006',
                      'verification': [task_commands[0]['command']]}
            lifecycle = mock.Mock()
            lifecycle.load_validated.return_value = {}
            lifecycle.resolve_active_packet.return_value = {
                'packet': packet, 'revision_id': 'revision-1', 'contract_sha256': 'c' * 64}
            lifecycle.packet_revision_id.return_value = 'revision-1'
            lifecycle.packet_bound_semantic_contract_sha256.return_value = 'c' * 64
            accepted_record = {
                'feature_id': 'SDD-OBS-001', 'task_id': 'T-006',
                'task_commands': task_commands, 'origin_binding': 'task-completion',
                'family': {'origin_policy': 'task-completion'},
            }
            completed = subprocess.CompletedProcess([], 0, json.dumps(response), '')
            with mock.patch.dict(sys.modules, {'harness': lifecycle}), \
                 mock.patch('verification.authority.resolve_execution',
                            return_value=(accepted_record, None, None, None)), \
                 mock.patch.object(runner, 'git_snapshot', return_value=('clean',)), \
                 mock.patch.object(runner.subprocess, 'run', return_value=completed) as invoke:
                passed, evidence = runner.run_verification(packet, root, out, 5,
                    sandbox_mode='required', accepted_plan_id=plan_id)
            self.assertTrue(passed, evidence)
        self.assertEqual(plan_id, evidence[0]['plan_id'])
        argv = invoke.call_args.args[0]
        self.assertEqual([sys.executable, str(HARNESS / 'verify.py'), 'run',
            '--mode', 'integration', '--repo', str(root), '--plan-id', plan_id], argv)
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


if __name__ == '__main__':
    unittest.main()
