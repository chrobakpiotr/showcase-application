"""Synthetic, disk-backed coverage for human-attested packet identity bridges."""
import argparse
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'harness.py'
spec = importlib.util.spec_from_file_location('bridge_harness', MODULE_PATH)
harness = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(harness)


class PacketIdentityBridgeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '--allow-empty', '-q', '-m', 'base'], cwd=self.root, check=True)
        self.feature = self.root / 'docs/specs/BRIDGE-TEST'
        self.feature.mkdir(parents=True)
        self.roles = self.root / 'docs/agentic-sdd/agents'
        self.roles.mkdir(parents=True)
        (self.roles / 'builder.md').write_text('# builder\n')
        (self.roles / 'evaluator.md').write_text('# evaluator\n')
        (self.feature / 'spec.md').write_text('# BRIDGE-TEST\n\n- AC-001: safe\n')
        (self.feature / 'plan.md').write_text('# plan\n')
        self.doc = {'feature': 'BRIDGE-TEST', 'max_parallel': 1, 'max_rework_attempts': 1,
            'tasks': [
                {'id': 'T-001', 'title': 'Build', 'objective': 'Build it', 'role': 'builder',
                 'depends_on': [], 'allowed_paths': ['src/**'], 'acceptance_criteria': ['AC-001'],
                 'verification': ['python3 -m unittest'], 'risk_tags': [],
                 'test_mode': 'red-green-refactor', 'test_seam': 'fixture seam'},
                {'id': 'T-002', 'title': 'Downstream', 'objective': 'Check it', 'role': 'evaluator',
                 'depends_on': ['T-001'], 'allowed_paths': ['evidence/**'],
                 'acceptance_criteria': ['AC-001'], 'verification': ['python3 -m unittest'], 'risk_tags': []}]}
        (self.feature / 'tasks.json').write_text(json.dumps(self.doc))
        harness.STATE_DIR = self.root / '.agent-state'
        packet_path = harness.write_packet(self.doc, self.doc['tasks'][0], self.feature)
        self.packet = json.loads(packet_path.read_text())
        self.active = harness.resolve_active_packet(self.feature, self.doc, 'T-001')
        self.source = 'a' * 64
        self.recovery = {'recovery_version': 1, 'attempt': 4, 'packet_identity': self.source,
                         'semantic_contract_sha256': self.active['contract_sha256'],
                         'operator': 'Piotr', 'no_execution_started': True}
        state = harness.initial_state(self.feature, self.doc)
        entry = state['tasks']['T-001']
        entry.update({'status': 'running', 'attempts': 4, 'owner': 'codex-t001',
                      'claim_recovery': self.recovery, 'retry_authorizations': [
                          {'version': 2, 'id': 'spent-v2', 'consumed_at': '2026-01-01T00:01:00Z',
                           'consumed_attempt': 4}], 'active_retry_authorization': 'spent-v2'})
        harness.save_state(self.feature, state)
        self.args = argparse.Namespace(feature_dir=self.feature, task_id='T-001',
            expected_repository=str(harness.git_common_dir(self.feature)), expected_feature='BRIDGE-TEST',
            expected_task='T-001', expected_status='running', expected_attempts=4,
            expected_owner='codex-t001', expected_recovery_identity_scheme=harness.RECOVERY_RECORD_IDENTITY_SCHEME,
            expected_recovery_identity=harness.recovery_record_identity(self.recovery),
            source_scheme=harness.PACKET_PAYLOAD_IDENTITY_SCHEME, source_value=self.source,
            target_scheme=harness.PACKET_PAYLOAD_IDENTITY_SCHEME,
            target_value=self.packet['packet_sha256'], target_revision_scheme=harness.PACKET_REVISION_IDENTITY_SCHEME,
            target_revision=self.active['revision_id'],
            expected_semantic_fingerprint=self.active['contract_sha256'], expected_protocol_version=1,
            by='Piotr', reason='Attest a narrowly scoped historical packet binding for fixture purposes.')

    def tearDown(self):
        self.tmp.cleanup()

    def attest(self):
        return harness.attest_packet_identity(self.args)

    def read_classification(self):
        doc = harness.load_validated(self.feature)
        state = harness._load_state_unlocked(self.feature, doc)
        active = harness.resolve_active_packet(self.feature, doc, 'T-001', state=state)
        return harness.classify_historical_packet_binding(self.feature, doc, 'T-001', state, active, 4)

    def reject(self):
        with self.assertRaises(SystemExit):
            self.attest()

    def test_H01_valid_exact_bridge_is_human_attested(self):
        result, bridge = self.attest()
        self.assertEqual('ATTESTED', result)
        self.assertEqual('HUMAN_ATTESTED', bridge['classification'])
        self.assertEqual({'scheme': self.args.source_scheme, 'value': self.source}, bridge['request']['source_identity'])

    def test_H02_exact_replay_is_idempotent(self):
        self.attest()
        result, _ = self.attest()
        self.assertEqual('ALREADY_ATTESTED', result)
        self.assertEqual(1, len(list((harness.runtime_state_dir(self.feature) / 'packet-identity-bridges').rglob('*.json'))))

    def test_H03_source_identity_mismatch_rejected(self):
        self.args.source_value = 'b' * 64
        self.reject()

    def test_H04_target_packet_mismatch_rejected(self):
        self.args.target_value = 'b' * 64
        self.reject()

    def test_H05_target_revision_mismatch_rejected(self):
        self.args.target_revision = 'sha256:' + 'b' * 64
        self.reject()

    def test_H06_semantic_fingerprint_mismatch_rejected(self):
        self.args.expected_semantic_fingerprint = 'b' * 64
        self.reject()

    def test_H07_recovery_record_mismatch_rejected(self):
        self.args.expected_recovery_identity = 'sha256:' + 'b' * 64
        self.reject()

    def test_H08_wrong_attempt_rejected(self):
        self.args.expected_attempts = 3
        self.reject()

    def test_H09_wrong_repository_rejected(self):
        self.args.expected_repository = str(self.root / 'other')
        self.reject()

    def test_H10_wrong_feature_or_task_rejected(self):
        self.args.expected_feature = 'OTHER'
        self.reject()
        self.args.expected_feature = 'BRIDGE-TEST'
        self.args.expected_task = 'T-002'
        self.reject()

    def test_H11_malformed_identity_rejected(self):
        self.args.source_value = 'not-a-hash'
        self.reject()
        self.args.source_value = self.source
        self.attest()
        path = next((harness.runtime_state_dir(self.feature) / 'packet-identity-bridges').rglob('*.json'))
        path.write_text('{bad json')
        with self.assertRaises(SystemExit):
            self.read_classification()

    def test_H12_conflicting_second_bridge_rejected(self):
        self.attest()
        self.args.reason += ' Changed request.'
        self.reject()

    def test_H13_crash_before_durable_write_leaves_no_bridge(self):
        for stage in ('B1', 'B2', 'B3', 'B4'):
            with self.subTest(stage=stage), patch.object(harness, 'bridge_failpoint',
                    side_effect=lambda actual, expected=stage: (_ for _ in ()).throw(RuntimeError(actual))
                    if actual == expected else None):
                with self.assertRaises(RuntimeError):
                    self.attest()
                self.assertEqual([], list((harness.runtime_state_dir(self.feature) /
                    'packet-identity-bridges').rglob('*.json')))

    def test_H14_crash_after_durable_write_leaves_exactly_one_bridge(self):
        with patch.object(harness, 'bridge_failpoint', side_effect=lambda stage: (_ for _ in ()).throw(RuntimeError(stage)) if stage == 'B5' else None):
            with self.assertRaises(RuntimeError):
                self.attest()
        records = list((harness.runtime_state_dir(self.feature) / 'packet-identity-bridges').rglob('*.json'))
        self.assertEqual(1, len(records))
        self.assertEqual('ALREADY_ATTESTED', self.attest()[0])

    def test_H15_restart_reconstructs_bridge_from_disk(self):
        self.attest()
        self.assertEqual('HUMAN_ATTESTED', self.read_classification()['classification'])

    def test_H16_bridge_does_not_change_retry_authorization(self):
        lifecycle_path = harness.state_path(self.feature)
        lifecycle_before = lifecycle_path.read_bytes()
        before = harness.load_json(harness.state_path(self.feature))['tasks']['T-001']['retry_authorizations']
        self.attest()
        self.assertEqual(lifecycle_before, lifecycle_path.read_bytes())
        after = harness.load_json(harness.state_path(self.feature))['tasks']['T-001']['retry_authorizations']
        self.assertEqual(before, after)
        self.assertEqual('consumed', 'consumed' if after[0]['consumed_at'] else 'active')

    def test_H17_bridge_does_not_make_task_claimable(self):
        self.attest()
        self.assertEqual('running', harness.load_json(harness.state_path(self.feature))['tasks']['T-001']['status'])
        self.assertEqual(4, harness.load_json(harness.state_path(self.feature))['tasks']['T-001']['attempts'])

    def test_H18_bridge_does_not_unblock_dependency(self):
        self.attest()
        ready = harness.ready_ids(self.doc, harness.load_json(harness.state_path(self.feature)), self.feature)
        self.assertNotIn('T-002', ready)

    def test_H19_replan_classifier_uses_bridge_as_human_attested(self):
        self.attest()
        proposal = self.feature / 'proposal.json'
        proposal.write_text(json.dumps(dict(self.doc['tasks'][0], objective='Replanned objective')))
        args = argparse.Namespace(feature_dir=self.feature, task_id='T-001', expected_status='running',
            expected_attempts=4, expected_active_revision=self.active['revision_id'],
            expected_contract_sha256=self.active['contract_sha256'], proposed_task_file=str(proposal),
            reason='Controlled replan after a scoped human packet identity attestation.',
            by='Piotr', checkpoint='fixture-checkpoint')
        with patch.object(harness, 'publish_revision', wraps=harness.publish_revision):
            harness.cmd_replan_task(args)
        updated = harness.load_json(harness.state_path(self.feature))['tasks']['T-001']
        self.assertEqual('HUMAN_ATTESTED', updated['attempt_termination']['historical_binding_classification'])
        self.assertEqual(self.read_bridge_id(), updated['attempt_termination']['identity_bridge_id'])
        self.assertEqual('HUMAN_ATTESTED', updated['replan_requests'][-1]['historical_binding_classification'])
        self.assertEqual(self.read_bridge_id(), updated['replan_requests'][-1]['identity_bridge_id'])
        attempt4 = next(item for item in updated['attempt_bindings'] if item['attempt'] == 4)
        self.assertEqual('human_attested', attempt4['binding_status'])

    def read_bridge_id(self):
        record = json.loads(next((harness.runtime_state_dir(self.feature) /
            'packet-identity-bridges').rglob('*.json')).read_text())
        return record['bridge_id']

    def test_H20_replan_classifier_without_bridge_is_ambiguous(self):
        self.assertEqual('AMBIGUOUS', self.read_classification()['classification'])

    def test_H21_automatic_proof_needs_no_bridge(self):
        state = harness.load_json(harness.state_path(self.feature))
        state['tasks']['T-001']['claim_recovery']['packet_identity'] = self.packet['packet_sha256']
        harness.save_state(self.feature, state)
        self.assertEqual('AUTOMATICALLY_PROVEN', self.read_classification()['classification'])
        self.assertEqual([], list((harness.runtime_state_dir(self.feature) / 'packet-identity-bridges').rglob('*.json')))

    def test_H22_human_attestation_is_never_automatic_proof(self):
        self.attest()
        result = self.read_classification()
        self.assertEqual('HUMAN_ATTESTED', result['classification'])
        self.assertNotEqual('AUTOMATICALLY_PROVEN', result['classification'])


if __name__ == '__main__':
    unittest.main()
