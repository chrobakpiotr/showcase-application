import pathlib
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from machine_outcomes import classify_reason, exit_code
import verify


class MachineOutcomeTests(unittest.TestCase):
    def test_stable_control_categories_share_cli_codes(self):
        self.assertEqual({'needs-human': 4, 'verification-blocked': 5,
                          'verification-owned': 6}, {
            category: exit_code(category) for category in
            ('needs-human', 'verification-blocked', 'verification-owned')})

    def test_control_reasons_are_not_generic_verifier_failures(self):
        self.assertEqual('verification-owned', classify_reason('verification-owned'))
        self.assertEqual('needs-human', classify_reason('CRITICAL_FAILURE_FENCE_ACTIVE'))
        self.assertEqual('verification-blocked', classify_reason('VERIFICATION_EXECUTION_PLAN_REQUIRED'))
        self.assertIsNone(classify_reason('verifier-assertion-failed'))

    def test_direct_integration_run_without_lifecycle_accepted_plan_is_blocked(self):
        for selector in ([], ['--plan-id', 'verification-plan-v1:sha256:' + 'a' * 64]):
            with self.subTest(selector=selector), mock.patch('builtins.print') as output:
                self.assertEqual(5, verify.main(['run', '--mode', 'integration', *selector]))
                result = __import__('json').loads(output.call_args.args[0])
                self.assertEqual('verification-blocked', result['status'])
                self.assertEqual(5, result['exit_code'])


if __name__ == '__main__':
    unittest.main()
