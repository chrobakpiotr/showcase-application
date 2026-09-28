"""RED-first coverage for immutable task packet replanning."""
import argparse
import importlib.util
import json
import pathlib
import tempfile
import threading
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'harness.py'
spec = importlib.util.spec_from_file_location('replan_harness', MODULE_PATH)
harness = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(harness)


class ReplanInfrastructureTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        self.feature = self.root / 'docs/specs/REPLAN-TEST'
        self.feature.mkdir(parents=True)
        import subprocess
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '--allow-empty', '-q', '-m', 'base'], cwd=self.root, check=True)
        harness.STATE_DIR = self.root / '.agent-state'
        roles = self.root / 'docs/agentic-sdd/agents'
        roles.mkdir(parents=True)
        (roles / 'builder.md').write_text('# builder\n')
        (roles / 'evaluator.md').write_text('# evaluator\n')
        (self.feature / 'spec.md').write_text('# REPLAN-TEST\n\n- AC-001: safe\n')
        (self.feature / 'plan.md').write_text('# plan\n')
        self.doc = {'feature': 'REPLAN-TEST', 'max_parallel': 2, 'max_rework_attempts': 2,
                    'tasks': [
                        {'id': 'T-001', 'title': 'Build', 'objective': 'Old objective', 'role': 'builder',
                         'depends_on': [], 'allowed_paths': ['src/**'], 'acceptance_criteria': ['AC-001'],
                         'verification': ['python -m unittest'], 'risk_tags': [],
                         'test_mode': 'red-green-refactor', 'test_seam': 'direct check'},
                        {'id': 'T-002', 'title': 'Downstream', 'objective': 'Check', 'role': 'evaluator',
                         'depends_on': ['T-001'], 'allowed_paths': ['evidence/**'],
                         'acceptance_criteria': ['AC-001'], 'verification': ['python -m unittest'],
                         'risk_tags': []}]}
        (self.feature / 'tasks.json').write_text(json.dumps(self.doc))

    def tearDown(self):
        self.tmp.cleanup()

    def running_replan_fixture(self, objective='New objective', *, feature=None, doc=None):
        feature = feature or self.feature
        doc = doc or self.doc
        task = harness.task_index(doc)['T-001']
        old_path = harness.write_packet(doc, task, feature)
        state = harness.initial_state(feature, doc)
        old_binding = harness.retry_binding(feature, doc, 'T-001', 3, state=state)
        old_active = harness.resolve_active_packet(feature, doc, 'T-001', state=state)
        grant = {'version': 2, 'id': f'spent-{feature.name}', 'binding': old_binding,
                 'reason': 'fixture grant', 'provenance': 'test operator',
                 'issued_at': '2026-01-01T00:00:00Z', 'consumed_at': '2026-01-01T00:01:00Z',
                 'consumed_attempt': 4}
        state['tasks']['T-001'].update({'status': 'running', 'attempts': 4, 'owner': 'worker',
            'active_retry_authorization': grant['id'], 'retry_authorizations': [grant],
            'lease_expires_at': '2026-01-01T01:00:00Z',
            'attempt_bindings': [{'attempt': 4, 'packet_revision': old_active['revision_id'],
                'contract_sha256': old_active['contract_sha256'], 'binding_status': 'proven',
                'bound_at': '2026-01-01T00:01:00Z'}]})
        harness.save_state(feature, state)
        proposal_dir = feature / 'planning'
        proposal_dir.mkdir(exist_ok=True)
        proposal = dict(task, objective=objective)
        proposal_path = proposal_dir / 'proposed-T-001.json'
        proposal_path.write_text(json.dumps(proposal))
        active = harness.resolve_active_packet(feature, doc, 'T-001')
        args = argparse.Namespace(feature_dir=feature, task_id='T-001', expected_status='running',
            expected_attempts=4, expected_active_revision=active['revision_id'],
            expected_contract_sha256=active['contract_sha256'], proposed_task_file=str(proposal_path),
            reason='accepted new plan', by='test operator', checkpoint='synthetic-checkpoint')
        return args, old_path.read_bytes(), grant['id']

    def create_additional_feature(self, feature_id):
        feature = self.root / 'docs' / 'specs' / feature_id
        feature.mkdir(parents=True)
        (feature / 'spec.md').write_text(f'#{feature_id}\n\n- AC-001: safe\n')
        (feature / 'plan.md').write_text('# plan\n')
        doc = json.loads(json.dumps(self.doc))
        doc['feature'] = feature_id
        (feature / 'tasks.json').write_text(json.dumps(doc))
        return feature, doc

    def test_explicit_replan_cli_and_active_packet_resolver_exist(self):
        commands = list(harness.parser()._subparsers._group_actions[0].choices)
        self.assertIn('replan-task', commands)
        self.assertTrue(callable(getattr(harness, 'resolve_active_packet', None)))

    def test_replan_publishes_immutable_revision_without_rewriting_legacy_packet(self):
        task = harness.task_index(self.doc)['T-001']
        old_path = harness.write_packet(self.doc, task, self.feature)
        old_bytes = old_path.read_bytes()
        state = harness.initial_state(self.feature, self.doc)
        old_binding = harness.retry_binding(self.feature, self.doc, 'T-001', 3, state=state)
        old_active = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=state)
        grant = {'version': 2, 'id': 'spent', 'binding': old_binding, 'reason': 'fixture grant',
                 'provenance': 'test operator', 'issued_at': '2026-01-01T00:00:00Z',
                 'consumed_at': '2026-01-01T00:01:00Z', 'consumed_attempt': 4}
        state['tasks']['T-001'].update({'status': 'running', 'attempts': 4, 'owner': 'worker',
            'active_retry_authorization': 'spent', 'retry_authorizations': [grant],
            'attempt_bindings': [{'attempt': 4, 'packet_revision': old_active['revision_id'],
                'contract_sha256': old_active['contract_sha256'], 'binding_status': 'proven',
                'bound_at': '2026-01-01T00:01:00Z'}]})
        harness.save_state(self.feature, state)
        updated = dict(task, objective='New objective')
        proposal = self.feature / 'planning' / 'replan-T-001.json'
        proposal.parent.mkdir(parents=True)
        proposal.write_text(json.dumps(updated))
        active = harness.resolve_active_packet(self.feature, self.doc, 'T-001')
        args = argparse.Namespace(feature_dir=self.feature, task_id='T-001', current_status='running',
                                  expected_status='running', expected_attempts=4,
                                  expected_active_revision=active['revision_id'],
                                  expected_contract_sha256=active['contract_sha256'],
                                  proposed_task_file=str(proposal),
                                  reason='new verified plan', by='test-operator',
                                  checkpoint='synthetic-checkpoint')
        harness.cmd_replan_task(args)
        self.assertEqual(old_bytes, old_path.read_bytes())
        revision = harness.resolve_active_packet(self.feature, self.doc, 'T-001')
        self.assertNotEqual(revision['revision_id'], 'legacy')
        self.assertEqual('New objective', revision['packet']['objective'])
        state = harness.load_state(self.feature, self.doc)
        self.assertEqual(('failed', 4), (state['tasks']['T-001']['status'], state['tasks']['T-001']['attempts']))
        self.assertEqual('REPLAN_SUPERSEDED', state['tasks']['T-001']['attempt_termination']['classification'])
        self.assertEqual('proven', state['tasks']['T-001']['attempt_termination']['historical_binding_status'])
        self.assertEqual(args.expected_active_revision,
                         state['tasks']['T-001']['attempt_termination']['packet_revision'])
        self.assertEqual('synthetic-checkpoint', state['tasks']['T-001']['replan_requests'][0]['checkpoint'])
        self.assertEqual([1, 2, 3], state['tasks']['T-001']['unbound_historical_attempts'])
        self.assertEqual('New objective', harness.active_task_contract(
            self.feature, self.doc, 'T-001', state=state)['objective'])
        self.assertEqual('consumed', harness.retry_authorization_status(
            self.feature, self.doc, state, 'T-001', 4, 'spent'))
        self.assertNotIn('T-001', harness.ready_ids(self.doc, state, self.feature))
        self.assertNotIn('T-002', harness.ready_ids(self.doc, state, self.feature))

    def test_new_retry_grant_binds_new_revision_and_only_it_claims_attempt_five(self):
        args, _old_bytes, old_grant_id = self.running_replan_fixture()
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                reason='reviewed changed task contract', by='new-operator'))
        state = harness.load_state(self.feature, self.doc)
        entry = state['tasks']['T-001']
        old_grant = next(g for g in entry['retry_authorizations'] if g['id'] == old_grant_id)
        new_grant = next(g for g in entry['retry_authorizations'] if g['id'] != old_grant_id)
        active = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=state)
        self.assertEqual('consumed', harness.retry_authorization_status(
            self.feature, self.doc, state, 'T-001', 4, old_grant_id))
        self.assertEqual(active['revision_id'], new_grant['binding']['packet_revision'])
        self.assertEqual(active['contract_sha256'], new_grant['binding']['contract_sha256'])
        self.assertNotEqual(old_grant['binding']['contract_sha256'], new_grant['binding']['contract_sha256'])
        self.assertEqual(['T-001'], harness.ready_ids(self.doc, state, self.feature))
        with redirect_stdout(StringIO()):
            harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='attempt-five'))
        claimed = harness.load_state(self.feature, self.doc)['tasks']['T-001']
        self.assertEqual(('running', 5, 'attempt-five'),
                         (claimed['status'], claimed['attempts'], claimed['owner']))
        self.assertEqual(new_grant['id'], claimed['active_retry_authorization'])
        self.assertEqual(active['revision_id'], claimed['attempt_bindings'][-1]['packet_revision'])
        self.assertNotIn('T-002', harness.ready_ids(self.doc, harness.load_state(self.feature, self.doc), self.feature))

    def test_superseded_packet_validation_failure_precedes_claim_mutation(self):
        args, _old_packet, _old_grant_id = self.running_replan_fixture()
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                reason='new contract retry', by='operator'))
        before = harness.load_state(self.feature, self.doc)
        active = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=before)
        path = pathlib.Path(active['path'])
        packet = json.loads(path.read_text())
        packet['objective'] = 'tampered superseded packet content'
        packet['packet_sha256'] = harness.sha256_bytes(json.dumps(
            {k: v for k, v in packet.items() if k != 'packet_sha256'}, sort_keys=True,
            separators=(',', ':')).encode())
        path.write_text(json.dumps(packet, indent=2, sort_keys=True) + '\n')
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='attempt-five'))
        after = harness.load_state(self.feature, self.doc)
        old = before['tasks']['T-001']
        current = after['tasks']['T-001']
        for key in ('status', 'attempts', 'owner', 'lease_expires_at', 'active_retry_authorization',
                    'retry_authorizations', 'packet_lineage', 'active_packet_revision'):
            self.assertEqual(old.get(key), current.get(key), key)
        self.assertEqual('failed', current['status'])
        self.assertEqual(4, current['attempts'])
        with self.assertRaises(SystemExit):
            harness.ready_ids(self.doc, after, self.feature)
        current_grant = current['retry_authorizations'][-1]
        self.assertIsNone(current_grant['consumed_at'])

    def test_historical_provenance_incident_and_explicit_replan_end_to_end(self):
        state = harness.initial_state(self.feature, self.doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(self.feature, state)
        task = harness.task_index(self.doc)['T-001']
        packet_path = harness.write_packet(self.doc, task, self.feature)
        packet_before = packet_path.read_bytes()
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                reason='first explicit retry', by='operator'))
        before = harness.load_state(self.feature, self.doc)
        old_grant = before['tasks']['T-001']['retry_authorizations'][0]
        original_protocol_fingerprint = harness.protocol_fingerprint
        try:
            harness.protocol_fingerprint = lambda _feature: 'f' * 64
            self.assertEqual(['T-001'], harness.ready_ids(self.doc, before, self.feature))
            with redirect_stdout(StringIO()):
                harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='attempt-four'))
        finally:
            harness.protocol_fingerprint = original_protocol_fingerprint
        self.assertEqual(packet_before, packet_path.read_bytes())
        attempt_four = harness.load_state(self.feature, self.doc)
        self.assertEqual('running', attempt_four['tasks']['T-001']['status'])
        self.assertEqual(4, attempt_four['tasks']['T-001']['attempts'])
        self.assertEqual(3, old_grant['binding']['expected_attempts'])
        spent_grant = attempt_four['tasks']['T-001']['retry_authorizations'][0]
        self.assertEqual(4, spent_grant['consumed_attempt'])
        self.assertEqual('consumed', harness.retry_authorization_status(
            self.feature, self.doc, attempt_four, 'T-001', 3, old_grant['id']))

        proposal_path = self.feature / 'planning' / 'accepted-T-001.json'
        proposal_path.parent.mkdir(parents=True)
        proposal = dict(task, objective='New objective for the explicit replan')
        proposal_path.write_text(json.dumps(proposal))
        active_old = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=attempt_four)
        args = argparse.Namespace(feature_dir=self.feature, task_id='T-001', expected_status='running',
            expected_attempts=4, expected_active_revision=active_old['revision_id'],
            expected_contract_sha256=active_old['contract_sha256'], proposed_task_file=str(proposal_path),
            reason='accepted revised contract', by='operator', checkpoint='synthetic-baseline')
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)
        replanned = harness.load_state(self.feature, self.doc)
        self.assertEqual(('failed', 4), (replanned['tasks']['T-001']['status'], replanned['tasks']['T-001']['attempts']))
        self.assertEqual('consumed', harness.retry_authorization_status(
            self.feature, self.doc, replanned, 'T-001', 4, old_grant['id']))
        self.assertEqual([], harness.ready_ids(self.doc, replanned, self.feature))
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                reason='new contract retry', by='second operator'))
        authorized = harness.load_state(self.feature, self.doc)
        new_grant = authorized['tasks']['T-001']['retry_authorizations'][-1]
        active_new = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=authorized)
        self.assertEqual(active_new['revision_id'], new_grant['binding']['packet_revision'])
        self.assertEqual(['T-001'], harness.ready_ids(self.doc, authorized, self.feature))
        with redirect_stdout(StringIO()):
            harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='attempt-five'))
        final = harness.load_state(self.feature, self.doc)
        self.assertEqual(('running', 5), (final['tasks']['T-001']['status'], final['tasks']['T-001']['attempts']))
        self.assertEqual(new_grant['id'], final['tasks']['T-001']['active_retry_authorization'])
        self.assertEqual(active_new['revision_id'], final['tasks']['T-001']['attempt_bindings'][-1]['packet_revision'])

    def test_new_retry_grant_binds_new_revision_and_only_it_claims_attempt_five(self):
        args, _old_bytes, old_grant_id = self.running_replan_fixture()
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                reason='reviewed changed task contract', by='new-operator'))
        state = harness.load_state(self.feature, self.doc)
        entry = state['tasks']['T-001']
        old_grant = next(g for g in entry['retry_authorizations'] if g['id'] == old_grant_id)
        new_grant = next(g for g in entry['retry_authorizations'] if g['id'] != old_grant_id)
        active = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=state)
        self.assertEqual('consumed', harness.retry_authorization_status(
            self.feature, self.doc, state, 'T-001', 4, old_grant_id))
        self.assertEqual(active['revision_id'], new_grant['binding']['packet_revision'])
        self.assertEqual(active['contract_sha256'], new_grant['binding']['contract_sha256'])
        self.assertNotEqual(old_grant['binding']['contract_sha256'], new_grant['binding']['contract_sha256'])
        self.assertEqual(['T-001'], harness.ready_ids(self.doc, state, self.feature))
        with redirect_stdout(StringIO()):
            harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='attempt-five'))
        claimed = harness.load_state(self.feature, self.doc)['tasks']['T-001']
        self.assertEqual(('running', 5, 'attempt-five'),
                         (claimed['status'], claimed['attempts'], claimed['owner']))
        self.assertEqual(new_grant['id'], claimed['active_retry_authorization'])
        self.assertEqual(active['revision_id'], claimed['attempt_bindings'][-1]['packet_revision'])
        self.assertNotIn('T-002', harness.ready_ids(self.doc, harness.load_state(self.feature, self.doc), self.feature))

    def test_replan_failure_after_revision_publication_reloads_unactivated_old_contract(self):
        args, _packet, _grant_id = self.running_replan_fixture()
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)
        state = harness.load_state(self.feature, self.doc)
        state['tasks']['T-001']['packet_lineage'][-1]['previous_revision'] = 'sha256:' + ('0' * 64)
        harness.save_state(self.feature, state)
        with self.assertRaises(SystemExit):
            harness.resolve_active_packet(self.feature, self.doc, 'T-001')
        with self.assertRaises(SystemExit):
            harness.revision_path(self.feature, '../escape', 'sha256:' + ('a' * 64))

    def test_legacy_attempt_without_matching_packet_evidence_stays_ambiguous(self):
        args, _packet, _grant_id = self.running_replan_fixture()
        state = harness.load_state(self.feature, self.doc)
        entry = state['tasks']['T-001']
        entry.pop('attempt_bindings')
        entry['claim_recovery'] = {'attempt': 4, 'packet_identity': '9' * 64,
                                   'semantic_contract_sha256': args.expected_contract_sha256}
        harness.save_state(self.feature, state)
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)
        replanned = harness.load_state(self.feature, self.doc)['tasks']['T-001']
        history = next(item for item in replanned['attempt_bindings'] if item['attempt'] == 4)
        self.assertEqual('ambiguous', history['binding_status'])
        self.assertNotIn('packet_revision', history)
        self.assertEqual('ambiguous', replanned['attempt_termination']['historical_binding_status'])
        self.assertNotIn('packet_revision', replanned['attempt_termination'])

    def test_legacy_history_replan_preserves_only_provable_attempt_evidence_after_restart(self):
        task = harness.task_index(self.doc)['T-001']
        packet_path = harness.write_packet(self.doc, task, self.feature)
        packet_bytes = packet_path.read_bytes()
        packet = json.loads(packet_bytes)
        packet_revision = harness.packet_revision_id(packet)
        contract_sha = packet['semantic_contract_sha256']
        v1 = {'version': 1, 'id': 'legacy-v1', 'binding': {
                  'repository': str(harness.git_common_dir(self.feature)), 'feature': 'REPLAN-TEST',
                  'task': 'T-001', 'expected_status': 'failed', 'expected_attempts': 3,
                  'feature_fingerprint': harness.feature_fingerprint(self.feature),
                  'packet_sha256': packet['packet_sha256'], 'protocol_version': 1},
              'reason': 'legacy authorization', 'provenance': 'fixture operator',
              'issued_at': '2026-01-01T00:00:00Z', 'consumed_at': None}
        v2 = {'version': 2, 'id': 'consumed-v2',
              'binding': {'binding_version': 2, 'repository': str(harness.git_common_dir(self.feature)),
                          'feature': 'REPLAN-TEST', 'task': 'T-001', 'expected_status': 'failed',
                          'expected_attempts': 3, 'protocol_version': 1,
                          'contract_sha256': contract_sha},
              'reason': 'superseding authorization', 'provenance': 'fixture operator',
              'issued_at': '2026-01-01T00:01:00Z', 'supersedes': 'legacy-v1',
              'consumed_at': '2026-01-01T00:02:00Z', 'consumed_attempt': 4}
        state = harness.initial_state(self.feature, self.doc)
        entry = state['tasks']['T-001']
        entry.update({'status': 'running', 'attempts': 4, 'owner': 'legacy-worker',
            'active_retry_authorization': 'consumed-v2', 'retry_authorizations': [v1, v2],
            'retry_authorization_supersessions': [{'authorization_id': 'consumed-v2',
                'supersedes': 'legacy-v1', 'issued_at': '2026-01-01T00:01:00Z',
                'provenance': 'fixture operator', 'reason': 'superseding authorization'}],
            'last_failure': 'attempt 3 failed: synthetic historical failure',
            'last_failure_attempt': None,
            'claim_recovery': {'attempt': 4, 'attestation': 'no_execution_started',
                'authorization_id': 'consumed-v2', 'operator_provenance': 'fixture operator',
                'owner': 'legacy-worker', 'packet_identity': packet['packet_sha256'],
                'semantic_contract_sha256': contract_sha, 'recovery_version': 1,
                'task': 'T-001'}})
        # This is the legacy evidence shape: attempts=4 and no per-attempt ledger.
        self.assertNotIn('attempt_bindings', entry)
        harness.save_state(self.feature, state)
        before_ready = harness.ready_ids(self.doc, state, self.feature)
        self.assertNotIn('T-001', before_ready)
        with self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='not-ready'))

        proposed = dict(task, objective='Replanned after legacy history')
        proposal_path = self.feature / 'planning' / 'proposed-T-001.json'
        proposal_path.parent.mkdir(parents=True)
        proposal_path.write_text(json.dumps(proposed))
        active = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=state)
        args = argparse.Namespace(feature_dir=self.feature, task_id='T-001', expected_status='running',
            expected_attempts=4, expected_active_revision=active['revision_id'],
            expected_contract_sha256=active['contract_sha256'], proposed_task_file=str(proposal_path),
            reason='synthetic accepted replan', by='fixture operator', checkpoint='synthetic-checkpoint')
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)

        # Reconstruct from durable files, with no process-local state reused.
        reloaded = harness.load_state(self.feature, self.doc)
        replanned = reloaded['tasks']['T-001']
        self.assertEqual(2, reloaded['state_version'])
        self.assertEqual(1, reloaded['protocol_version'])
        self.assertEqual(4, replanned['attempts'])
        self.assertEqual('attempt 3 failed: synthetic historical failure', replanned['last_failure'])
        self.assertIsNone(replanned['last_failure_attempt'])
        self.assertEqual([1, 2, 3], replanned['unbound_historical_attempts'])
        self.assertEqual([4], [record['attempt'] for record in replanned['attempt_bindings']])
        attempt4 = replanned['attempt_bindings'][0]
        self.assertEqual('proven', attempt4['binding_status'])
        self.assertEqual(packet_revision, attempt4['packet_revision'])
        self.assertEqual(contract_sha, attempt4['contract_sha256'])
        self.assertEqual('REPLAN_SUPERSEDED', replanned['attempt_termination']['classification'])
        self.assertEqual('proven', replanned['attempt_termination']['historical_binding_status'])
        self.assertEqual('consumed', harness.retry_authorization_status(
            self.feature, self.doc, reloaded, 'T-001', 4, 'consumed-v2'))
        self.assertNotIn('T-001', harness.ready_ids(self.doc, reloaded, self.feature))
        new_active = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=reloaded)
        self.assertNotEqual(packet_revision, new_active['revision_id'])
        self.assertEqual('Replanned after legacy history', new_active['packet']['objective'])
        self.assertEqual(packet_bytes, packet_path.read_bytes())
        self.assertEqual(packet_revision, replanned['packet_lineage'][0]['revision_id'])
        self.assertTrue(replanned['packet_lineage'][0]['legacy'])
        self.assertEqual(packet_revision, replanned['replan_requests'][0]['old_revision'])
        with self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='unauthorized'))
        after_rejected_claim = harness.load_state(self.feature, self.doc)['tasks']['T-001']
        self.assertEqual(4, after_rejected_claim['attempts'])
        self.assertEqual('failed', after_rejected_claim['status'])

    def test_legacy_packet_resolution_is_pure_read_and_first_replan_bootstraps_lineage(self):
        task = harness.task_index(self.doc)['T-001']
        packet_path = harness.write_packet(self.doc, task, self.feature)
        packet_bytes = packet_path.read_bytes()
        state = harness.initial_state(self.feature, self.doc)
        entry = state['tasks']['T-001']
        entry.update({'status': 'running', 'attempts': 1, 'owner': 'fixture-worker'})
        harness.save_state(self.feature, state)
        authority = harness.runtime_state_dir(self.feature) / 'packet-revisions'
        before_listing = sorted(str(path.relative_to(harness.runtime_state_dir(self.feature)))
                                for path in authority.rglob('*')) if authority.exists() else []
        before_hashes = {str(path.relative_to(authority)): harness.sha256_bytes(path.read_bytes())
                         for path in authority.rglob('*') if path.is_file()} if authority.exists() else {}

        resolved = harness.resolve_active_packet(self.feature, self.doc, 'T-001')

        after_listing = sorted(str(path.relative_to(harness.runtime_state_dir(self.feature)))
                               for path in authority.rglob('*')) if authority.exists() else []
        after_hashes = {str(path.relative_to(authority)): harness.sha256_bytes(path.read_bytes())
                        for path in authority.rglob('*') if path.is_file()} if authority.exists() else {}
        self.assertEqual(before_listing, after_listing)
        self.assertEqual(before_hashes, after_hashes)
        self.assertEqual(harness.packet_revision_id(json.loads(packet_bytes)), resolved['revision_id'])
        self.assertTrue(resolved['legacy'])

        proposal_path = self.feature / 'planning' / 'proposed-T-001.json'
        proposal_path.parent.mkdir(parents=True)
        proposal_path.write_text(json.dumps(dict(task, objective='First revisioned plan')))
        args = argparse.Namespace(feature_dir=self.feature, task_id='T-001', expected_status='running',
            expected_attempts=1, expected_active_revision=resolved['revision_id'],
            expected_contract_sha256=resolved['contract_sha256'], proposed_task_file=str(proposal_path),
            reason='bootstrap legacy lineage', by='fixture operator', checkpoint='synthetic-checkpoint')
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)
        after = harness.load_state(self.feature, self.doc)
        active = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=after)
        lineage = after['tasks']['T-001']['packet_lineage']
        self.assertEqual(packet_bytes, packet_path.read_bytes())
        self.assertEqual(resolved['revision_id'], lineage[0]['revision_id'])
        self.assertTrue(lineage[0]['legacy'])
        self.assertEqual(active['revision_id'], lineage[1]['revision_id'])
        self.assertEqual(resolved['revision_id'], lineage[1]['previous_revision'])
        self.assertEqual(1, len(list((authority / self.feature.name / 'T-001').glob('*.json'))))

    def test_replan_preserves_current_state_and_protocol_schema_versions(self):
        args, _packet, _grant_id = self.running_replan_fixture()
        state = harness.load_state(self.feature, self.doc)
        self.assertEqual(2, state['state_version'])
        self.assertEqual(1, state['protocol_version'])
        before = json.loads(json.dumps(state))
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)
        reloaded = harness.load_state(self.feature, self.doc)
        self.assertEqual(before['state_version'], reloaded['state_version'])
        self.assertEqual(before['protocol_version'], reloaded['protocol_version'])
        self.assertEqual(1, harness.STATE_SCHEMA_VERSION)

    def test_crash_boundaries_reload_old_or_committed_active_contract(self):
        args, old_packet, _grant_id = self.running_replan_fixture()
        expected_old_revision = args.expected_active_revision
        original_failpoint = harness.replan_failpoint
        for stage in ('R1', 'R2', 'R3', 'R4', 'R5', 'R6', 'R7', 'R8'):
            with self.subTest(stage=stage):
                proposal_path = pathlib.Path(args.proposed_task_file)
                proposal = json.loads(proposal_path.read_text())
                proposal['objective'] = f'Contract at {stage}'
                proposal_path.write_text(json.dumps(proposal))
                failure = RuntimeError(f'injected {stage}')
                try:
                    if stage == 'R7':
                        original_replace = harness.os.replace
                        try:
                            harness.os.replace = lambda *_a, **_k: (_ for _ in ()).throw(OSError('state persistence failure'))
                            with self.assertRaises(OSError):
                                harness.cmd_replan_task(args)
                        finally:
                            harness.os.replace = original_replace
                    else:
                        harness.replan_failpoint = lambda point, target=stage: (_ for _ in ()).throw(failure) if point == target else None
                        with self.assertRaisesRegex(RuntimeError, stage):
                            harness.cmd_replan_task(args)
                finally:
                    harness.replan_failpoint = original_failpoint
                # A new state object is loaded after every simulated process death.
                state = harness.load_state(self.feature, self.doc)
                active = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=state)
                if stage != 'R8':
                    self.assertEqual(expected_old_revision, active['revision_id'])
                    self.assertEqual(('running', 4), (state['tasks']['T-001']['status'], state['tasks']['T-001']['attempts']))
                    self.assertEqual(old_packet, harness.legacy_packet_path(self.feature, 'T-001').read_bytes())
                else:
                    self.assertNotEqual(expected_old_revision, active['revision_id'])
                    self.assertEqual(('failed', 4), (state['tasks']['T-001']['status'], state['tasks']['T-001']['attempts']))
                self.assertEqual('pending', state['tasks']['T-002']['status'])
                self.assertNotIn('T-002', harness.ready_ids(self.doc, state, self.feature))

    def test_exact_replay_is_idempotent_conflict_and_completed_task_replan_fail_closed(self):
        args, original_packet, _grant_id = self.running_replan_fixture()
        with redirect_stdout(StringIO()):
            harness.cmd_replan_task(args)
        committed = harness.load_state(self.feature, self.doc)
        state_bytes = harness.state_path(self.feature).read_bytes()
        with redirect_stdout(StringIO()) as output:
            harness.cmd_replan_task(args)
        self.assertIn('ALREADY_REPLANNED', output.getvalue())
        self.assertEqual(state_bytes, harness.state_path(self.feature).read_bytes())
        self.assertEqual(original_packet, harness.legacy_packet_path(self.feature, 'T-001').read_bytes())
        active = harness.resolve_active_packet(self.feature, self.doc, 'T-001')
        revisions = list((harness.runtime_state_dir(self.feature) / 'packet-revisions' / self.feature.name / 'T-001').glob('*.json'))
        self.assertEqual(1, len(revisions))
        proposal = json.loads(pathlib.Path(args.proposed_task_file).read_text())
        proposal['objective'] = 'Conflicting replay'
        pathlib.Path(args.proposed_task_file).write_text(json.dumps(proposal))
        with redirect_stderr(StringIO()) as error, self.assertRaises(SystemExit):
            harness.cmd_replan_task(args)
        self.assertIn('STALE_ACTIVE_PACKET', error.getvalue())
        self.assertEqual(active['revision_id'], harness.resolve_active_packet(self.feature, self.doc, 'T-001')['revision_id'])
        self.assertEqual(1, len(harness.load_state(self.feature, self.doc)['tasks']['T-001']['replan_requests']))

        # Completed work is rejected without changing either durable state or packet bytes.
        entry = committed['tasks']['T-001']
        entry['status'] = 'completed'
        harness.save_state(self.feature, committed)
        before = harness.state_path(self.feature).read_bytes()
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_replan_task(args)
        self.assertEqual(before, harness.state_path(self.feature).read_bytes())

    def test_two_identical_replans_commit_one_revision(self):
        args, _packet, _grant_id = self.running_replan_fixture()
        barrier = threading.Barrier(2)
        results = []
        def invoke():
            barrier.wait()
            try:
                with redirect_stdout(StringIO()):
                    harness.cmd_replan_task(args)
                results.append('ok')
            except BaseException as exc:
                results.append(type(exc).__name__)
        threads = [threading.Thread(target=invoke) for _ in range(2)]
        for thread in threads: thread.start()
        for thread in threads: thread.join(timeout=20)
        self.assertTrue(all(not thread.is_alive() for thread in threads))
        self.assertEqual(['ok', 'ok'], sorted(results))
        self.assertEqual(1, len(harness.load_state(self.feature, self.doc)['tasks']['T-001']['replan_requests']))
        revisions = list((harness.runtime_state_dir(self.feature) / 'packet-revisions' / self.feature.name / 'T-001').glob('*.json'))
        self.assertEqual(1, len(revisions))

    def test_conflicting_replans_serialize_and_one_loses_compare_and_swap(self):
        args, _packet, _grant_id = self.running_replan_fixture()
        proposal = json.loads(pathlib.Path(args.proposed_task_file).read_text())
        conflicting = argparse.Namespace(**vars(args))
        proposal['objective'] = 'Other plan'
        other_path = pathlib.Path(args.proposed_task_file).with_name('conflicting.json')
        other_path.write_text(json.dumps(proposal))
        conflicting.proposed_task_file = str(other_path)
        barrier = threading.Barrier(2)
        outcomes = []
        def invoke(request):
            barrier.wait()
            try:
                with redirect_stdout(StringIO()): harness.cmd_replan_task(request)
                outcomes.append('committed')
            except SystemExit:
                outcomes.append('rejected')
        threads = [threading.Thread(target=invoke, args=(request,)) for request in (args, conflicting)]
        for thread in threads: thread.start()
        for thread in threads: thread.join(timeout=20)
        self.assertTrue(all(not thread.is_alive() for thread in threads))
        self.assertCountEqual(['committed', 'rejected'], outcomes)
        state = harness.load_state(self.feature, self.doc)
        self.assertEqual(1, len(state['tasks']['T-001']['replan_requests']))
        self.assertEqual('failed', state['tasks']['T-001']['status'])

    def test_linked_worktree_uses_shared_lock_state_and_packet_revision_store(self):
        import subprocess
        args, _packet, _grant_id = self.running_replan_fixture()
        subprocess.run(['git', 'add', 'docs/specs/REPLAN-TEST', 'docs/agentic-sdd/agents'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'fixture planning input'], cwd=self.root, check=True)
        linked_root = self.root.parent / f'{self.root.name}-linked'
        subprocess.run(['git', 'worktree', 'add', '-q', '-b', 'replan-linked-fixture',
                        str(linked_root), 'HEAD'], cwd=self.root, check=True)
        linked_feature = linked_root / 'docs/specs/REPLAN-TEST'
        linked_args = argparse.Namespace(**vars(args))
        linked_args.feature_dir = linked_feature
        linked_args.proposed_task_file = str(linked_feature / 'planning/proposed-T-001.json')
        barrier = threading.Barrier(2)
        outcomes = []
        def invoke(request):
            barrier.wait()
            try:
                with redirect_stdout(StringIO()): harness.cmd_replan_task(request)
                outcomes.append('committed-or-replay')
            except BaseException as exc:
                outcomes.append(type(exc).__name__)
        threads = [threading.Thread(target=invoke, args=(request,)) for request in (args, linked_args)]
        for thread in threads: thread.start()
        for thread in threads: thread.join(timeout=20)
        self.assertTrue(all(not thread.is_alive() for thread in threads))
        self.assertEqual(['committed-or-replay', 'committed-or-replay'], outcomes)
        main_state = harness.load_state(self.feature, self.doc)
        linked_state = harness.load_state(linked_feature, self.doc)
        self.assertEqual(main_state, linked_state)
        active_main = harness.resolve_active_packet(self.feature, self.doc, 'T-001', state=main_state)
        active_linked = harness.resolve_active_packet(linked_feature, self.doc, 'T-001', state=linked_state)
        self.assertEqual(active_main['revision_id'], active_linked['revision_id'])
        self.assertEqual(active_main['path'], active_linked['path'])

    def test_replan_races_authorization_start_completion_and_release(self):
        import subprocess
        for suffix, operation in (
            ('AUTH', 'authorization'), ('START', 'start'),
            ('COMPLETE', 'complete'), ('RELEASE', 'release')):
            with self.subTest(operation=operation):
                feature, doc = self.create_additional_feature(f'REPLAN-{suffix}')
                args, _packet, _grant_id = self.running_replan_fixture(feature=feature, doc=doc)
                if operation == 'complete':
                    evidence = feature / 'pass.json'
                    evidence.write_text(json.dumps({'status': 'pass', 'summary': 'verified fixture',
                        'changed_paths': [], 'commands': [], 'assumptions': [], 'residual_risks': []}))
                barrier = threading.Barrier(2)
                outcomes = []
                def replan():
                    barrier.wait()
                    try:
                        with redirect_stdout(StringIO()): harness.cmd_replan_task(args)
                        outcomes.append('replan')
                    except BaseException:
                        outcomes.append('replan-rejected')
                def lifecycle():
                    barrier.wait()
                    try:
                        with redirect_stdout(StringIO()):
                            if operation == 'authorization':
                                harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001',
                                    reason='race grant', by='race operator'))
                            elif operation == 'start':
                                harness.cmd_start(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='racer'))
                            elif operation == 'complete':
                                harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-001',
                                    owner='worker', evidence=str(evidence)))
                            else:
                                harness.cmd_release(argparse.Namespace(feature_dir=feature, task_id='T-001',
                                    owner='worker', reason='race release'))
                        outcomes.append(operation)
                    except BaseException:
                        outcomes.append(operation + '-rejected')
                threads = [threading.Thread(target=replan), threading.Thread(target=lifecycle)]
                for thread in threads: thread.start()
                for thread in threads: thread.join(timeout=20)
                self.assertTrue(all(not thread.is_alive() for thread in threads))
                self.assertEqual(2, len(outcomes))
                state = harness.load_state(feature, doc)
                task_state = state['tasks']['T-001']
                self.assertEqual(4, task_state['attempts'])
                self.assertEqual('pending', state['tasks']['T-002']['status'])
                if operation in ('authorization', 'start'):
                    self.assertIn('replan', outcomes)
                    self.assertEqual('failed', task_state['status'])
                else:
                    transitioned_by_replan = 'replan' in outcomes
                    self.assertIn(task_state['status'], {'failed', 'completed'})
                    if task_state['status'] == 'completed':
                        self.assertEqual(['complete', 'replan-rejected'], sorted(outcomes))
                        self.assertNotIn('active_packet_revision', task_state)
                        self.assertIn('T-002', harness.ready_ids(doc, state, feature))
                    elif transitioned_by_replan:
                        self.assertIn(operation + '-rejected', outcomes)
                        self.assertIn('active_packet_revision', task_state)
                        self.assertNotIn('T-002', harness.ready_ids(doc, state, feature))
                    else:
                        self.assertEqual([operation, 'replan-rejected'], sorted(outcomes))
                        self.assertNotIn('active_packet_revision', task_state)
                        self.assertNotIn('T-002', harness.ready_ids(doc, state, feature))

    def test_replan_and_claim_race_has_one_authoritative_transition(self):
        args, _packet, _grant_id = self.running_replan_fixture()
        barrier = threading.Barrier(2)
        outcomes = []
        def do_replan():
            barrier.wait()
            try:
                with redirect_stdout(StringIO()): harness.cmd_replan_task(args)
                outcomes.append('replan')
            except BaseException:
                outcomes.append('replan-rejected')
        def do_claim():
            barrier.wait()
            try:
                with redirect_stdout(StringIO()):
                    harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='other'))
                outcomes.append('claim')
            except BaseException:
                outcomes.append('claim-rejected')
        threads = [threading.Thread(target=do_replan), threading.Thread(target=do_claim)]
        for thread in threads: thread.start()
        for thread in threads: thread.join(timeout=20)
        self.assertTrue(all(not thread.is_alive() for thread in threads))
        state = harness.load_state(self.feature, self.doc)
        self.assertEqual(4, state['tasks']['T-001']['attempts'])
        self.assertEqual('failed', state['tasks']['T-001']['status'])
        self.assertCountEqual(['replan', 'claim-rejected'], outcomes)
        self.assertEqual('pending', state['tasks']['T-002']['status'])
