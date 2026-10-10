import pathlib
import json
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
        (self.root / 'tooling/agent-harness/human-issuer-registry.json').write_text(
            json.dumps({'schema_version': 1, 'issuers': []}) + '\n')
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

    def test_origin_authority_is_v2_and_resolution_is_not_a_launch(self):
        self.assertEqual(2, self.record['schema_version'])
        for obligation in self.record['obligations']:
            self.assertEqual('independent', obligation['required_origin'])
        with mock.patch.object(authority, 'resolve_accepted', return_value=self.record):
            record, profile, plan, units = authority.resolve_execution(self.root, self.record['plan_id'])
        self.assertEqual(self.record, record)
        self.assertEqual(self.profile.content_hash, profile.content_hash)
        self.assertEqual(self.plan, plan)
        self.assertEqual(self.record['execution_units'], list(units.values()))

    def test_execute_plan_wires_trusted_launch_authorizer_to_supervisor(self):
        from verification import executor
        from verification.supervisor import VerificationSupervisor
        captured = {}

        def stop_after_capture(_supervisor, _backend, **kwargs):
            captured.update(kwargs)
            raise RuntimeError('test-stop-before-launch')

        with mock.patch.object(authority, 'resolve_execution',
                               return_value=(self.record, self.profile, self.plan,
                                   {item['unit_id']: item for item in self.record['execution_units']})), \
             mock.patch.object(VerificationSupervisor, 'execute', stop_after_capture):
            result = executor.execute_plan(self.root, self.profile, self.plan,
                                           authority_context=self.record, sandbox_mode='off')
        self.assertEqual('ERROR', result.gates[0].outcome)
        self.assertTrue(callable(captured.get('launch_authorizer')))

    def test_rehashed_origin_or_membership_cannot_publish(self):
        import copy
        import hashlib
        from verification.serialization import canonical
        from verification.store import VerificationStore
        for mutation in ('missing', 'unknown', 'source', 'class', 'membership'):
            record = copy.deepcopy(self.record)
            if mutation == 'missing':
                record['obligations'][0].pop('required_origin', None)
            elif mutation == 'unknown':
                record['obligations'][0]['required_origin'] = 'manual'
            elif mutation == 'source':
                record['obligations'][0]['requirement_source'] = 'task-command'
            elif mutation == 'class':
                record['obligations'][0]['independent_execution_class'] = 'manual'
            else:
                record['execution_units'][0]['obligation_ids'] *= 2
            keys = {'family_id', 'gate_id', 'profile_gate_id', 'occurrence', 'ordinal',
                    'requirement_source', 'required_origin', 'independent_execution_class'}
            for item, unit in zip(record['obligations'], record['execution_units']):
                previous = item['obligation_id']
                item['obligation_id'] = 'verification-obligation-v2:sha256:' + hashlib.sha256(
                    canonical({k: v for k, v in item.items() if k in keys})).hexdigest()
                unit['obligation_ids'] = [item['obligation_id'] if oid == previous else oid
                                           for oid in unit['obligation_ids']]
                unit['required_origin'] = item.get('required_origin')
                unit['independent_execution_class'] = item['independent_execution_class']
                unit['unit_id'] = 'verification-unit-v2:sha256:' + hashlib.sha256(
                    canonical({k: v for k, v in unit.items() if k != 'unit_id'})).hexdigest()
            body = {k: v for k, v in record.items() if k != 'plan_id'}
            record['plan_id'] = 'verification-plan-v2:sha256:' + hashlib.sha256(canonical(body)).hexdigest()
            with self.subTest(mutation=mutation), self.assertRaises(StoreError):
                VerificationStore(self.root).publish_plan_record(record)

    def test_rehashed_malformed_publication_shape_rejects(self):
        import copy
        import hashlib
        from verification.serialization import canonical
        from verification.store import VerificationStore
        mutations = (
            ('origin', 'manual'), ('base_sha', None), ('base_sha', 'not-a-sha'),
            ('candidate_identity', 'candidate-v99:sha256:' + 'a' * 64),
            ('final_changed_surface_id', 123),
            ('dependencies', ['unknown']),
            ('dependencies', ['agentic-sdd-doctor', 'agentic-sdd-doctor']),
            ('dependencies', ['agentic-sdd-doctor']),
            ('retry_controls', ['x', 'x']),
        )
        for field, value in mutations:
            record = copy.deepcopy(self.record)
            if field == 'origin':
                record['origin_binding'] = value
                record['family']['origin_policy'] = value
            elif field == 'base_sha':
                record['family'][field] = value
            elif field in ('candidate_identity', 'final_changed_surface_id'):
                record[field] = value
                record['family'][field] = value
            else:
                record['obligations'][0][field] = value
            record['plan_id'] = 'verification-plan-v2:sha256:' + hashlib.sha256(
                canonical({k: v for k, v in record.items() if k != 'plan_id'})).hexdigest()
            with self.subTest(field=field, value=value), self.assertRaises(StoreError):
                VerificationStore(self.root).publish_plan_record(record)

    def test_full_sha1_policy_checkpoint_preserves_existing_shape(self):
        import dataclasses
        family = dataclasses.replace(self.plan.family, policy_checkpoint='a' * 40)
        record = authority.execution_plan_record('SDD-OBS-001', 'f' * 64, 2,
            self.profile_id, self.profile, dataclasses.replace(self.plan, family=family),
            task_id='T-001', task_attempt=1, origin_binding='integration')
        authority.validate_plan_record(record)

    def test_invalid_family_identifier_and_unmapped_critical_retry_reject(self):
        import dataclasses
        import hashlib
        from verification.serialization import canonical
        plan = build_plan(self.root, self.profile, self.family, task_commands=['python3 -V'])
        record = authority.execution_plan_record('SDD-OBS-001', 'f' * 64, 2,
            self.profile_id, self.profile, plan, task_id='T-001', task_attempt=1,
            task_commands=['python3 -V'], origin_binding='integration')
        item = next(o for o in record['obligations'] if o['profile_gate_id'] is None)
        item['critical'] = True
        item['retry_policy'] = 'allow'
        record['plan_id'] = 'verification-plan-v2:sha256:' + hashlib.sha256(
            canonical({k: v for k, v in record.items() if k != 'plan_id'})).hexdigest()
        with self.assertRaises(StoreError):
            authority.validate_plan_record(record)
        bad_plan = dataclasses.replace(self.plan,
            family=dataclasses.replace(self.plan.family, id='../untrusted'))
        record = authority.execution_plan_record('SDD-OBS-001', 'f' * 64, 2,
            self.profile_id, self.profile, bad_plan, task_id='T-001', task_attempt=1,
            origin_binding='integration')
        with self.assertRaises(StoreError):
            authority.validate_plan_record(record)

    def test_profile_absent_permission_cannot_authorize_independent(self):
        import dataclasses
        profile = dataclasses.replace(self.profile, gates=tuple(
            dataclasses.replace(g, independent_execution_classes=()) for g in self.profile.gates))
        with mock.patch('verification.profile.load_profile', return_value=profile):
            with self.assertRaisesRegex(StoreError, 'invalid-verification-plan'):
                authority.validate_plan_record(self.record)

    def test_legacy_plan_id_is_unavailable_without_modifying_artifacts(self):
        from verification.store import VerificationStore
        store = VerificationStore(self.root)
        with self.assertRaisesRegex(StoreError, 'VERIFICATION_EXECUTION_PLAN_REQUIRED'):
            store.load_plan_record('verification-plan-v1:sha256:' + 'a' * 64)
        self.assertFalse(store.plans.exists())

    def test_authority_context_cannot_launch_or_create_receipts(self):
        from verification.executor import execute_plan
        with mock.patch('verification.executor.VerificationStore') as store, \
             mock.patch('verification.authority.resolve_execution',
                        side_effect=StoreError('VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE')) as resolve, \
             mock.patch('verification.candidate.seal_candidate') as seal:
            for context in (self.record, {'schema_version': 1}):
                with self.subTest(context=context), self.assertRaisesRegex(
                        StoreError, 'VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE'):
                    execute_plan(self.root, self.profile, self.plan, authority_context=context)
            store.assert_not_called()
            seal.assert_not_called()
            self.assertEqual(2, resolve.call_count)

    def test_unmapped_integration_command_is_task_origin(self):
        plan = build_plan(self.root, self.profile, self.family, task_commands=['python3 -V'])
        record = authority.execution_plan_record('SDD-OBS-001', 'f' * 64, 2,
            self.profile_id, self.profile, plan, task_id='T-001', task_attempt=1,
            task_commands=['python3 -V'], origin_binding='integration')
        authority.validate_plan_record(record)
        legacy = [o for o in record['obligations'] if o['profile_gate_id'] is None]
        self.assertEqual(1, len(legacy))
        self.assertEqual('task', legacy[0]['required_origin'])
        self.assertEqual('task-command', legacy[0]['requirement_source'])

    def test_profile_origin_and_principal_bind_obligation_family_and_plan(self):
        registry = {'schema_version': 1, 'issuers': [
            {'issuer_id': 'issuer-a', 'reviewer_principal': 'human:alice',
             'enabled': True, 'revoked': False, 'actions': ['manual-review']},
            {'issuer_id': 'issuer-b', 'reviewer_principal': 'human:bob',
             'enabled': True, 'revoked': False, 'actions': ['manual-review']}]}

        def make_record(origin, principal=None, task_commands=()):
            gate = {'id': 'manual-gate', 'command': 'python3 -m unittest',
                    'inputs': ['tooling/agent-harness/**'], 'mandatory': True,
                    'applicability': ['**'], 'required_origin': origin,
                    'independent_execution_classes':
                        ['harness-managed-independent-execution-v1'] if origin == 'independent' else [],
                    'independent_registration_classes': [], 'cacheable': False}
            if principal is not None:
                gate['required_manual_reviewer_principal'] = principal
            profile = load_profile({'schema_version': 1, 'gates': [gate]},
                                   manual_issuer_registry=registry)
            family_id = authority.task_family_id('SDD-OBS-001', 'T-001', 1, profile)
            family = Family(family_id, self.base, 'task-completion', profile.content_hash, 'd' * 64)
            plan = build_plan(self.root, profile, family, task_commands=task_commands)
            return authority.execution_plan_record(
                'SDD-OBS-001', 'f' * 64, 2, self.profile_id, profile, plan,
                task_id='T-001', task_attempt=1, task_commands=task_commands,
                origin_binding='task-completion')

        alice = make_record('manual', 'human:alice')
        bob = make_record('manual', 'human:bob')
        independent = make_record('independent')
        self.assertNotEqual(alice['family']['id'], bob['family']['id'])
        self.assertNotEqual(alice['family']['id'], independent['family']['id'])
        self.assertNotEqual(alice['obligations'][0]['obligation_id'], bob['obligations'][0]['obligation_id'])
        self.assertNotEqual(alice['plan_id'], bob['plan_id'])
        self.assertNotEqual(alice['plan_id'], independent['plan_id'])
        self.assertEqual('manual', alice['obligations'][0]['required_origin'])
        self.assertEqual('independent', independent['obligations'][0]['required_origin'])

    def test_untrusted_task_origin_does_not_upgrade_profile_gate(self):
        profile = self.profile
        family = Family('untrusted-origin-attempt', self.base, 'task-completion',
                        profile.content_hash, 'd' * 64)
        commands = [{'command': profile.gates[0].command, 'cwd': profile.gates[0].cwd}]
        plan = build_plan(self.root, profile, family, task_commands=commands)
        record = authority.execution_plan_record('SDD-OBS-001', 'f' * 64, 2,
            self.profile_id, profile, plan, task_id='T-001', task_attempt=1,
            task_commands=commands, origin_binding='task-completion')
        matching = [item for item in record['obligations']
                    if item['command_hash'] == profile.gates[0].command_hash]
        self.assertEqual({'task', 'independent'}, {item['required_origin'] for item in matching})
        self.assertEqual(2, len(matching))

    def test_exact_accepted_plan_reconstructs_current_units_without_running_them(self):
        with mock.patch.object(authority, 'resolve_accepted', return_value=self.record):
            record, _, _, units = authority.resolve_execution(self.root, self.record['plan_id'])
        self.assertEqual(self.record, record)
        self.assertEqual(self.record['execution_units'], list(units.values()))

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
            'schema_version', 'profile_id', 'plan_id', 'task_id', 'task_attempt', 'feature_fingerprint', 'lifecycle_generation', 'family',
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
