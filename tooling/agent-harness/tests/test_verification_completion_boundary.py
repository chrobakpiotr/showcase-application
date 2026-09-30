"""Origin-aware authority cannot complete through legacy command proof tuples."""
import argparse
import contextlib
import io
import json
import subprocess
import unittest

import harness as control_harness
import test_harness as fixtures

harness = fixtures.harness


class OriginCompletionBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.addCleanup(setattr, harness, 'STATE_DIR', harness.STATE_DIR)
        self.fixture = fixtures.HarnessTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.tearDown)
        # The shared fixture imports harness by file location; orchestration
        # imports its canonical module name. Other suites configure that module's
        # state directory, so bind both real instances to this fixture and restore
        # their settings afterwards. No lifecycle operation is mocked.
        self.addCleanup(setattr, control_harness, 'STATE_DIR', control_harness.STATE_DIR)
        control_harness.STATE_DIR = self.fixture.root / '.agent-state'
        self.feature = self.fixture.feature()
        self.doc = harness.load_validated(self.feature)
        harness.write_packet(self.doc, harness.task_index(self.doc)['T-001'], self.feature)
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='worker'))
        self.checkpoint = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        state = harness.load_state(self.feature, self.doc)
        state['tasks']['T-001']['checkpoint_commit'] = self.checkpoint
        harness.save_state(self.feature, state)
        self.evidence = self.fixture.root / 'evidence.json'
        self.evidence.write_text(json.dumps(self.fixture.passing_completion_evidence(
            self.feature, self.doc, 'T-001', 1, self.checkpoint)))
        self.args = argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                                       owner='worker', evidence=self.evidence)

    def bind_v2(self, *, historical=False):
        # Negative fixture: a syntactically scoped authority projection is enough
        # to require refusal. It is deliberately not a qualifying admission.
        authority = {'accepted_plan_id': 'verification-plan-v2:sha256:' + 'a' * 64,
                     'binding': {'schema_version': 2, 'task_id': 'T-001', 'task_attempt': 1}}
        state = harness.load_state(self.feature, self.doc)
        if historical:
            state['verification_plan_history'] = [{'prior_authority': authority}]
        else:
            state['verification_authority'] = authority
        harness.save_state(self.feature, state)

    def assert_blocked(self, command):
        diagnostic = io.StringIO()
        with contextlib.redirect_stderr(diagnostic), self.assertRaises(SystemExit):
            command(self.args)
        self.assertIn('VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE', diagnostic.getvalue())
        self.assertEqual('running', harness.load_state(self.feature, self.doc)['tasks']['T-001']['status'])
        self.assertEqual([], harness.completion_records(self.feature, 'T-001'))

    def test_v2_normal_completion_rejects_fabricated_legacy_success(self):
        self.bind_v2()
        self.assert_blocked(harness.cmd_complete)

    def test_v2_repair_rejects_before_legacy_replay_or_publication(self):
        self.bind_v2()
        self.assert_blocked(harness.cmd_complete_repair)

    def test_superseded_v2_authority_still_blocks_same_attempt_completion(self):
        self.bind_v2(historical=True)
        self.assert_blocked(harness.cmd_complete)

    def test_feature_reconciliation_cannot_erase_v2_completion_requirement(self):
        subprocess.run(['git', 'add', 'docs'], check=True)
        subprocess.run(['git', 'commit', '-qm', 'feature baseline'], check=True)
        base = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        state = harness.load_state(self.feature, self.doc)
        state['base_commit'] = base
        harness.save_state(self.feature, state)
        self.bind_v2()
        (self.feature / 'plan.md').write_text('# revised plan\n')
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_reconcile_feature(argparse.Namespace(feature_dir=self.feature,
                expected_generation=1, reason='contract revision', by='test-operator'))
        current = harness.load_state(self.feature, self.doc)
        self.assertEqual(1, current['tasks']['T-001']['attempts'])
        diagnostic = io.StringIO()
        with contextlib.redirect_stderr(diagnostic), self.assertRaises(SystemExit):
            harness.require_available_completion_origin(current, 'T-001', current['tasks']['T-001'])
        self.assertIn('VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE', diagnostic.getvalue())

    def test_unplanned_legacy_completion_retains_existing_behavior(self):
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_complete(self.args)
        self.assertEqual('completed', harness.load_state(self.feature, self.doc)['tasks']['T-001']['status'])

    def test_real_plan_acceptance_cannot_be_completed_with_legacy_proofs(self):
        from verification import authority
        from verification.store import StoreError, VerificationStore
        record = authority.prepare_task_plan(self.fixture.root, self.feature,
            'T-001', 1, self.checkpoint, self.doc['tasks'][0]['verification'])
        self.assertEqual(record, VerificationStore(self.fixture.root).load_plan_record(record['plan_id']))
        self.assertEqual(record, authority.resolve_accepted(self.fixture.root, record['plan_id']))
        origins = {item['required_origin'] for item in record['obligations']}
        self.assertEqual({'task', 'independent'}, origins)
        with self.assertRaisesRegex(StoreError, 'VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE'):
            authority.resolve_execution(self.fixture.root, record['plan_id'])
        self.assert_blocked(harness.cmd_complete)


if __name__ == '__main__':
    unittest.main()
