import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

HARNESS = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HARNESS))

from verification import authority
from verification.candidate import seal_candidate
from verification.model import Family
from verification.planner import build_plan
from verification.profile import load_profile
from verification.store import StoreError
import harness


class AcceptedPlanResolutionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        subprocess.run(['git', 'config', 'user.email', 'test@example.invalid'], cwd=self.root, check=True)
        subprocess.run(['git', 'config', 'user.name', 'test'], cwd=self.root, check=True)
        (self.root / 'tooling/agent-harness').mkdir(parents=True)
        (self.root / 'tooling/agent-harness/seed.py').write_text('seed = 1\n')
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', 'commit', '-qm', 'base'], cwd=self.root, check=True)
        self.base = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.root, text=True).strip()
        (self.root / 'tooling/agent-harness/seed.py').write_text('seed = 2\n')
        self.profile_id = 'showcase'
        self.profile = load_profile(HARNESS / 'verification-profiles/showcase.json')
        self.family = Family('authority-test', self.base, 'integration', self.profile.content_hash,
                             'a' * 64)
        seal = seal_candidate(self.root, self.base, {
            'family_id': self.family.id, 'profile_hash': self.profile.content_hash,
            'policy_checkpoint': self.family.policy_checkpoint,
            'origin_policy': self.family.origin_policy})
        self.family = Family(self.family.id, self.base, self.family.origin_policy,
            self.family.profile_hash, self.family.policy_checkpoint, seal.candidate_identity,
            seal.changed_surface_id)
        self.plan = build_plan(self.root, self.profile, self.family)
        self.record = authority.execution_plan_record('SDD-OBS-001', 'f' * 64, 2,
            self.profile_id, self.profile, self.plan, task_id='T-001', task_attempt=1,
            origin_binding='integration')

    def test_exact_accepted_plan_reconstructs_current_units_without_running_them(self):
        with mock.patch.object(authority, 'resolve_accepted', return_value=self.record):
            record, profile, plan, units = authority.resolve_execution(
                self.root, self.record['plan_id'])
        self.assertEqual(self.record, record)
        self.assertEqual(self.profile.content_hash, profile.content_hash)
        self.assertEqual([item.node.id for item in self.plan.decisions],
                         [item.node.id for item in plan.decisions])
        self.assertEqual(len(self.record['execution_units']), len(units))

    def test_post_seal_candidate_mutation_rejects_before_execution(self):
        (self.root / 'tooling/agent-harness/seed.py').write_text('mutated after seal\n')
        with mock.patch.object(authority, 'resolve_accepted', return_value=self.record):
            with self.assertRaisesRegex(StoreError, 'PLAN_BINDING_MISMATCH'):
                authority.resolve_execution(self.root, self.record['plan_id'])

    def test_unknown_execution_unit_rejects_before_execution(self):
        with mock.patch.object(authority, 'resolve_accepted', return_value=self.record):
            with self.assertRaisesRegex(StoreError, 'PLAN_BINDING_MISMATCH'):
                authority.resolve_execution(self.root, self.record['plan_id'], unit_id='unknown')

    def test_trusted_orchestrator_plan_creation_binds_running_attempt_and_candidate(self):
        feature_dir = self.root / 'docs/specs/SDD-OBS-001'
        feature_dir.mkdir(parents=True)
        state = {'feature_generation': 2,
                 'tasks': {'T-001': {'status': 'running', 'attempts': 1}}}
        with mock.patch.object(harness, 'load_validated', return_value={'feature': 'SDD-OBS-001'}), \
             mock.patch.object(harness, 'load_state', return_value=state), \
             mock.patch.object(harness, 'feature_fingerprint', return_value='f' * 64), \
             mock.patch.object(authority, 'publish_and_accept') as accept:
            record = authority.prepare_task_plan(self.root, feature_dir, 'T-001', 1,
                                                 self.base, ['python3 -V'])
        self.assertEqual('T-001', record['task_id'])
        self.assertEqual(1, record['task_attempt'])
        self.assertEqual(2, record['lifecycle_generation'])
        self.assertEqual(self.family.base_sha, record['family']['base_sha'])
        self.assertEqual(record['candidate_identity'], record['family']['candidate_identity'])
        accept.assert_called_once_with(self.root.resolve(), feature_dir.resolve(), record,
                                       expected_generation=2)

    def test_trusted_orchestrator_cannot_accept_plan_for_noncurrent_attempt(self):
        feature_dir = self.root / 'docs/specs/SDD-OBS-001'
        feature_dir.mkdir(parents=True)
        state = {'feature_generation': 2,
                 'tasks': {'T-001': {'status': 'running', 'attempts': 2}}}
        with mock.patch.object(harness, 'load_validated', return_value={'feature': 'SDD-OBS-001'}), \
             mock.patch.object(harness, 'load_state', return_value=state), \
             mock.patch.object(authority, 'publish_and_accept') as accept:
            with self.assertRaisesRegex(StoreError, 'ACCEPTED_PLAN_UNAVAILABLE'):
                authority.prepare_task_plan(self.root, feature_dir, 'T-001', 1,
                                            self.base, ['python3 -V'])
        accept.assert_not_called()

    def test_subordinate_record_without_exact_lifecycle_acceptance_is_rejected(self):
        from verification.store import VerificationStore
        store = VerificationStore(self.root)
        store.publish_plan_record(self.record)
        feature_dir = self.root / 'docs/specs/SDD-OBS-001'
        binding = {key: self.record.get(key) for key in (
            'plan_id', 'task_id', 'task_attempt', 'feature_fingerprint', 'lifecycle_generation', 'family',
            'profile_hash', 'policy_checkpoint', 'candidate_identity',
            'final_changed_surface_id', 'origin_binding')}
        accepted = {'accepted_plan_id': self.record['plan_id'], 'generation': 2,
                    'binding': binding}
        with mock.patch.object(harness, 'load_validated', return_value={'feature': 'SDD-OBS-001'}), \
             mock.patch.object(harness, 'feature_fingerprint', return_value='f' * 64), \
             mock.patch.object(harness, '_read_state_unlocked_pure',
                               return_value={'feature_generation': 2, 'tasks': {'T-001': {'status': 'running', 'attempts': 1}},
                                             'verification_authority': accepted}):
            self.assertEqual(self.record,
                harness.resolve_accepted_verification_plan(self.root, self.record['plan_id']))
        for stale_state in (
            {'feature_generation': 2, 'tasks': {'T-001': {'status': 'running', 'attempts': 1}}, 'verification_authority': None},
            {'feature_generation': 3, 'tasks': {'T-001': {'status': 'running', 'attempts': 1}}, 'verification_authority': accepted},
            {'feature_generation': 2, 'tasks': {'T-001': {'status': 'running', 'attempts': 1}}, 'verification_authority': {
                **accepted, 'generation': 1}},
            {'feature_generation': 2, 'tasks': {'T-001': {'status': 'running', 'attempts': 1}}, 'verification_authority': {
                **accepted, 'binding': {**binding, 'candidate_identity': 'wrong'}}},
        ):
            with self.subTest(stale_state=stale_state), \
                 mock.patch.object(harness, 'load_validated', return_value={'feature': 'SDD-OBS-001'}), \
                 mock.patch.object(harness, 'feature_fingerprint', return_value='f' * 64), \
                 mock.patch.object(harness, '_read_state_unlocked_pure', return_value=stale_state):
                with self.assertRaisesRegex(StoreError, 'ACCEPTED_PLAN_UNAVAILABLE'):
                    harness.resolve_accepted_verification_plan(self.root, self.record['plan_id'])


if __name__ == '__main__':
    unittest.main()
