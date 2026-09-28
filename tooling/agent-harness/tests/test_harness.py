import argparse
import contextlib
import importlib.util
import io
import json
import os
import pathlib
import subprocess
import tempfile
import threading
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'harness.py'
spec = importlib.util.spec_from_file_location('sdd_harness', MODULE_PATH)
harness = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(harness)


class HarnessTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        self.old_cwd = pathlib.Path.cwd()
        os.chdir(self.root)
        harness.STATE_DIR = self.root / '.agent-state'
        import subprocess
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '--allow-empty', '-q', '-m', 'base'], cwd=self.root, check=True)

    def tearDown(self):
        os.chdir(self.old_cwd)
        self.tmp.cleanup()

    def feature(self, tasks=None, spec_text=None, *, root=None):
        repo = root or self.root
        feature = repo / 'docs' / 'specs' / 'TST-001'
        feature.mkdir(parents=True)
        roles = repo / 'docs' / 'agentic-sdd' / 'agents'
        roles.mkdir(parents=True, exist_ok=True)
        for profile in {
            'builder', 'evaluator', 'integration', 'architect', 'specialist',
            'architecture-reviewer', 'messaging-reviewer', 'persistence-reviewer',
            'concurrency-reviewer', 'security-reviewer', 'platform-reviewer',
            'ai-reviewer', 'frontend-reviewer', 'performance-reviewer',
            'grill-reviewer', 'prototype-agent', 'prototype-evaluator'
        }:
            (roles / f'{profile}.md').write_text(f'# {profile}\n', encoding='utf-8')
        (feature / 'spec.md').write_text(
            spec_text or '# TST-001\n\n- AC-001: works\n- AC-002: is independently verified\n',
            encoding='utf-8',
        )
        (feature / 'plan.md').write_text('# plan\n', encoding='utf-8')
        doc = {
            'feature': 'TST-001',
            'max_parallel': 4,
            'max_rework_attempts': 2,
            'tasks': tasks or [
                {
                    'id': 'T-001', 'title': 'Build', 'objective': 'Implement it', 'role': 'builder',
                    'depends_on': [], 'allowed_paths': ['modules/domain/**'], 'risk_tags': ['domain'],
                    'acceptance_criteria': ['AC-001'], 'verification': ['./gradlew :domain:test'],
                },
                {
                    'id': 'T-900', 'title': 'Evaluate', 'objective': 'Falsify it', 'role': 'evaluator',
                    'depends_on': ['T-001'], 'allowed_paths': ['docs/specs/TST-001/evidence/**'],
                    'risk_tags': ['evaluation'], 'acceptance_criteria': ['AC-001', 'AC-002'],
                    'verification': ['./gradlew test'],
                },
            ],
        }
        (feature / 'tasks.json').write_text(json.dumps(doc), encoding='utf-8')
        return feature

    def passing_completion_evidence(self, feature, doc, task_id, attempt, checkpoint, *, changed_paths=None):
        state = harness.load_state(feature, doc)
        active = harness.resolve_active_packet(feature, doc, task_id, state=state)
        task = harness.active_task_contract(feature, doc, task_id, state=state)
        criteria = task['acceptance_criteria']
        commands = task['verification']
        run_dir = self.root / '.agent-runs' / f'{task_id}-{attempt}'
        run_dir.mkdir(parents=True, exist_ok=True)
        verification = []
        proofs = []
        proof_ids = []
        for index, command in enumerate(commands):
            stdout = run_dir / f'verify-{index}.stdout'
            stderr = run_dir / f'verify-{index}.stderr'
            stdout.write_text(f'PASS {command}\n', encoding='utf-8')
            stderr.write_text('', encoding='utf-8')
            stdout_sha = harness.sha256_bytes(stdout.read_bytes())
            stderr_sha = harness.sha256_bytes(stderr.read_bytes())
            sandbox_backend = 'deterministic-test-backend'
            verification.append({'command': command, 'exit_code': 0, 'stdout': str(stdout), 'stderr': str(stderr),
                                 'sandbox_backend': sandbox_backend, 'strong_isolation': True})
            proof_id = f'proof-{index + 1}'
            proof_ids.append(proof_id)
            receipt = {'command': command, 'exit_code': 0, 'stdout_sha256': stdout_sha,
                       'stderr_sha256': stderr_sha, 'sandbox_backend': sandbox_backend,
                       'strong_isolation': True}
            proofs.append({'proof_id': proof_id, 'command_index': index,
                           'command_sha256': harness.sha256_bytes(command.encode()),
                           'status': 'PASS', 'exit_code': 0,
                           'result_sha256': harness.canonical_json_sha256(receipt), 'criteria': list(criteria)})
        return {
            'schema_version': 1, 'status': 'pass', 'summary': 'fixture verification passed',
            'repository': str(harness.git_common_dir(feature)), 'feature': feature.name, 'task': task_id,
            'attempt': attempt, 'checkpoint': checkpoint,
            'packet_revision': active['revision_id'], 'contract_fingerprint': active['contract_sha256'],
            'commands': commands, 'harness_verification': verification, 'proofs': proofs,
            'criterion_results': {criterion: {'status': 'PASS', 'proof_ids': proof_ids}
                                  for criterion in criteria},
            'changed_paths': changed_paths or [], 'assumptions': [], 'residual_risks': [],
        }

    def add_legacy_retry_grant(self, feature, doc, task_id='T-001', *, grant_id='legacy-grant'):
        task = harness.task_index(doc)[task_id]
        packet = harness.packet_payload(doc, task, feature)
        binding = {
            'repository': str(harness.git_common_dir(feature)),
            'feature': str(doc.get('feature', feature.name)),
            'task': task_id,
            'expected_status': 'failed',
            'expected_attempts': 3,
            'feature_fingerprint': harness.feature_fingerprint(feature),
            'packet_sha256': packet['packet_sha256'],
            'protocol_version': harness.protocol_version(feature),
        }
        return {'version': 1, 'id': grant_id, 'binding': binding, 'reason': 'legacy fixture',
                'provenance': 'fixture-operator', 'issued_at': '2026-01-01T00:00:00Z', 'consumed_at': None}

    def exhausted_authorized_task(self, *, root=None):
        feature = self.feature([
            {'id': 'T-001', 'title': 'Build', 'objective': 'Implement it', 'role': 'builder',
             'depends_on': [], 'allowed_paths': ['modules/domain/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001'], 'verification': ['./gradlew test'],
             'test_mode': 'red-green-refactor', 'test_seam': 'fixture seam'},
            {'id': 'T-900', 'title': 'Evaluate', 'objective': 'Falsify it', 'role': 'evaluator',
             'depends_on': ['T-001'], 'allowed_paths': ['evidence/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['./gradlew test']},
        ], root=root)
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(feature, state)
        from contextlib import redirect_stdout
        from io import StringIO
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(
                feature_dir=feature, task_id='T-001', reason='fixture exceptional retry', by='fixture-operator'))
        return feature, doc

    def assert_precommit_claim_state(self, feature, doc, grant_id):
        state = harness.load_state(feature, doc)
        entry = state['tasks']['T-001']
        self.assertEqual('failed', entry['status'])
        self.assertEqual(3, entry['attempts'])
        self.assertNotIn('owner', entry)
        self.assertNotIn('lease_expires_at', entry)
        self.assertNotIn('heartbeat_at', entry)
        grant = next(item for item in entry['retry_authorizations'] if item['id'] == grant_id)
        self.assertIsNone(grant['consumed_at'])
        self.assertNotIn('active_retry_authorization', entry)

    def historical_partial_claim_fixture(self):
        """Build the exact failed-attempt-3 -> committed running-attempt-4 incident."""
        root = pathlib.Path(tempfile.mkdtemp(dir=self.root))
        import subprocess
        subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '--allow-empty', '-q', '-m', 'base'], cwd=root, check=True)
        feature, doc = self.exhausted_authorized_task(root=root)
        task = harness.task_index(doc)['T-001']
        harness.write_packet(doc, task, feature)
        state = harness.load_state(feature, doc)
        entry = state['tasks']['T-001']
        grant = entry['retry_authorizations'][0]
        grant['issued_at'] = '2026-09-27T09:59:00+00:00'
        grant['consumed_at'] = '2026-09-27T10:00:00+00:00'
        grant['consumed_attempt'] = 4
        entry.update({
            'status': 'running', 'attempts': 4, 'owner': 'codex-t002',
            'claimed_at': '2026-09-27T10:00:01+00:00',
            'active_retry_authorization': grant['id'],
            'heartbeat_at': '2026-09-27T10:00:01+00:00',
            'lease_expires_at': '2000-01-01T00:00:00+00:00',
        })
        entry.update({
            'released_at': '2026-09-27T09:59:59+00:00',
            'last_attempt_commit': 'a' * 40,
            'last_failure': 'attempt 3 failed before authorized retry',
            'last_failure_attempt': 3,
        })
        harness.save_state(feature, state)
        return feature, doc, grant['id']

    def test_recovery_cli_and_partial_claim_are_explicitly_recoverable(self):
        feature, doc, grant_id = self.historical_partial_claim_fixture()
        state = harness.load_state(feature, doc)
        self.assertEqual(('running', 4, 'codex-t002'), (
            state['tasks']['T-001']['status'], state['tasks']['T-001']['attempts'],
            state['tasks']['T-001']['owner']))
        self.assertEqual(grant_id, state['tasks']['T-001']['active_retry_authorization'])
        self.assertNotIn('claim_recovery', state['tasks']['T-001'])
        self.assertTrue(harness.lease_expired(state['tasks']['T-001']))
        self.assertNotIn('claim_recovery', state['tasks']['T-001'])
        self.assertTrue(hasattr(harness, 'cmd_recover_claim'))

    def test_red_expired_partial_claim_is_auto_failed_by_stale_lease_recovery(self):
        feature, doc, _ = self.historical_partial_claim_fixture()
        from contextlib import redirect_stderr
        from io import StringIO
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.recover_stale_leases(feature, doc)
        state = harness.load_state(feature, doc)
        self.assertEqual('running', state['tasks']['T-001']['status'])

    def recovered_claim_fixture(self):
        feature, doc, grant_id = self.historical_partial_claim_fixture()
        args = argparse.Namespace(
            feature_dir=feature, task_id='T-001', attempt=4, authorization=grant_id,
            owner='codex-t002', reason='claim returned error before execution', by='Piotr',
            attest_no_execution_started=True,
        )
        return feature, doc, grant_id, args

    def test_recover_claim_requires_explicit_attestation(self):
        feature, doc, grant_id, args = self.recovered_claim_fixture()
        args.attest_no_execution_started = False
        from contextlib import redirect_stderr
        from io import StringIO
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_recover_claim(args)
        state = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertNotIn('claim_recovery', state)
        self.assertEqual(grant_id, state['active_retry_authorization'])

    def test_recover_claim_preserves_attempt_grant_packet_and_renews_expired_lease(self):
        from contextlib import redirect_stdout
        from io import StringIO
        feature, doc, grant_id, args = self.recovered_claim_fixture()
        packet_path = feature / 'packets' / 'T-001.json'
        packet_bytes = packet_path.read_bytes()
        before = harness.load_state(feature, doc)['tasks']['T-001']
        original_grants = json.loads(json.dumps(before['retry_authorizations']))
        with redirect_stdout(StringIO()):
            harness.cmd_recover_claim(args)
        state = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(('running', 4, 'codex-t002'), (state['status'], state['attempts'], state['owner']))
        self.assertEqual(grant_id, state['active_retry_authorization'])
        self.assertEqual(original_grants, state['retry_authorizations'])
        self.assertEqual(packet_bytes, packet_path.read_bytes())
        self.assertEqual('no_execution_started', state['claim_recovery']['attestation'])
        self.assertEqual(4, state['claim_recovery']['attempt'])
        self.assertEqual(4, state['retry_authorizations'][0]['consumed_attempt'])
        self.assertEqual(grant_id, state['active_retry_authorization'])
        self.assertEqual(4, state['attempts'])
        self.assertEqual('running', state['status'])
        self.assertGreater(harness.parse_timestamp(state['lease_expires_at']), harness.utc_now())
        self.assertEqual([], harness.ready_ids(doc, harness.load_state(feature, doc), feature))

    def test_recover_claim_accepts_exact_consumed_v2_supersession_shape(self):
        """RED regression for a valid V2 retry that replaced an unusable V1 grant."""
        from contextlib import redirect_stdout
        from io import StringIO
        feature, doc, _, args = self.recovered_claim_fixture()
        state = harness.load_state(feature, doc)
        entry = state['tasks']['T-001']
        replacement = entry['retry_authorizations'][0]
        replacement.update({
            'version': 2,
            'binding': harness.retry_binding(feature, doc, 'T-001', 3),
            'consumed_at': '2026-09-27T10:00:00+00:00',
            'consumed_attempt': 4,
            'supersedes': 'legacy-v1-grant',
        })
        replacement['id'] = 'replacement-v2-grant'
        entry['retry_authorizations'].insert(0, {
            'version': 1, 'id': 'legacy-v1-grant',
            'binding': self.add_legacy_retry_grant(feature, doc, grant_id='legacy-v1-grant')['binding'],
            'reason': 'historical authorization', 'provenance': 'fixture-operator',
            'issued_at': '2026-09-27T09:00:00+00:00', 'consumed_at': None,
        })
        entry['active_retry_authorization'] = replacement['id']
        entry['retry_authorization_supersessions'] = [{
            'supersedes': 'legacy-v1-grant', 'authorization_id': replacement['id'],
            'reason': 'replace unverifiable V1 grant', 'provenance': 'fixture-operator',
            'issued_at': '2026-09-27T09:59:00+00:00',
        }]
        args.authorization = replacement['id']
        harness.save_state(feature, state)
        with redirect_stdout(StringIO()):
            harness.cmd_recover_claim(args)
        recovered = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(4, recovered['attempts'])
        self.assertEqual(replacement['id'], recovered['active_retry_authorization'])
        self.assertEqual('no_execution_started', recovered['claim_recovery']['attestation'])

    def test_recover_claim_is_idempotent_and_conflicting_assertion_fails(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature, doc, _, args = self.recovered_claim_fixture()
        with redirect_stdout(StringIO()):
            harness.cmd_recover_claim(args)
        first = harness.load_state(feature, doc)['tasks']['T-001']['claim_recovery']
        with redirect_stdout(StringIO()) as output:
            harness.cmd_recover_claim(args)
        self.assertIn('ALREADY_RECOVERED', output.getvalue())
        self.assertEqual(first, harness.load_state(feature, doc)['tasks']['T-001']['claim_recovery'])
        args.reason = 'a different assertion'
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_recover_claim(args)

    def test_recover_claim_rejects_wrong_bindings_and_authorization_history(self):
        mutations = {
            'attempt': lambda a: setattr(a, 'attempt', 5),
            'owner': lambda a: setattr(a, 'owner', 'other'),
            'authorization': lambda a: setattr(a, 'authorization', 'wrong-grant'),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name):
                feature, doc, _, args = self.recovered_claim_fixture()
                mutate(args)
                from contextlib import redirect_stderr
                from io import StringIO
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_recover_claim(args)

    def test_recover_claim_v2_grant_predicates_fail_independently(self):
        from contextlib import redirect_stderr
        from io import StringIO
        mutations = {
            'version': lambda e, g: g.update(version=1),
            'binding version': lambda e, g: g['binding'].update(binding_version=1),
            'expected status': lambda e, g: g['binding'].update(expected_status='running'),
            'expected attempts': lambda e, g: g['binding'].update(expected_attempts=4),
            'consumed attempt': lambda e, g: g.update(consumed_attempt=3),
            'consumed at absent': lambda e, g: g.update(consumed_at=None),
            'wrong task': lambda e, g: g['binding'].update(task='T-900'),
            'wrong feature': lambda e, g: g['binding'].update(feature='OTHER'),
            'wrong repository': lambda e, g: g['binding'].update(repository='/wrong/.git'),
            'wrong protocol': lambda e, g: g['binding'].update(protocol_version=99),
            'wrong active grant': lambda e, g: e.update(active_retry_authorization='other'),
            'malformed binding': lambda e, g: g['binding'].pop('contract_sha256'),
            'unconsumed': lambda e, g: (g.update(consumed_at=None), g.pop('consumed_attempt', None)),
            'consumed into another attempt': lambda e, g: g.update(consumed_attempt=5),
            'grant itself superseded': lambda e, g: e.update(retry_authorization_supersessions=[{
                'supersedes': g['id'], 'authorization_id': 'later-grant', 'reason': 'replacement',
                'provenance': 'fixture-operator', 'issued_at': '2026-09-27T10:01:00+00:00'}]),
            'ambiguous supersession': lambda e, g: e.update(retry_authorization_supersessions=[{
                'supersedes': 'other', 'authorization_id': g['id'], 'reason': 'replacement',
                'provenance': 'fixture-operator', 'issued_at': '2026-09-27T10:01:00+00:00'}]),
        }
        for name, mutate in mutations.items():
            with self.subTest(predicate=name):
                feature, doc, _, args = self.recovered_claim_fixture()
                state = harness.load_state(feature, doc)
                entry = state['tasks']['T-001']
                mutate(entry, entry['retry_authorizations'][0])
                harness.save_state(feature, state)
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_recover_claim(args)

        for name, mutate in {
            'unconsumed': lambda entry, grant: (grant.update(consumed_at=None), grant.pop('consumed_attempt', None)),
            'wrong boundary': lambda entry, grant: grant['binding'].update(expected_attempts=2),
            'wrong task': lambda entry, grant: grant['binding'].update(task='T-900'),
            'ambiguous': lambda entry, grant: entry['retry_authorizations'].append(json.loads(json.dumps(grant))),
        }.items():
            with self.subTest(history=name):
                feature, doc, _, args = self.recovered_claim_fixture()
                state = harness.load_state(feature, doc)
                entry = state['tasks']['T-001']
                mutate(entry, entry['retry_authorizations'][0])
                harness.save_state(feature, state)
                from contextlib import redirect_stderr
                from io import StringIO
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_recover_claim(args)

    def test_recover_claim_rejects_semantic_packet_mismatch_but_accepts_provenance(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature, doc, _, args = self.recovered_claim_fixture()
        packet = json.loads((feature / 'packets' / 'T-001.json').read_text())
        packet['objective'] = 'semantic mismatch'
        body = {key: value for key, value in packet.items() if key != 'packet_sha256'}
        packet['packet_sha256'] = harness.sha256_bytes(json.dumps(body, sort_keys=True, separators=(',', ':')).encode())
        packet_path = feature / 'packets' / 'T-001.json'
        packet_path.write_text(json.dumps(packet))
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_recover_claim(args)

        feature, doc, _, args = self.recovered_claim_fixture()
        packet_path = feature / 'packets' / 'T-001.json'
        packet = json.loads(packet_path.read_text())
        packet['protocol_fingerprint'] = '0' * 64
        body = {key: value for key, value in packet.items() if key != 'packet_sha256'}
        packet['packet_sha256'] = harness.sha256_bytes(json.dumps(body, sort_keys=True, separators=(',', ':')).encode())
        packet_path.write_text(json.dumps(packet))
        original = packet_path.read_bytes()
        with redirect_stdout(StringIO()):
            harness.cmd_recover_claim(args)
        self.assertEqual(original, packet_path.read_bytes())

    def test_recover_claim_rejects_execution_checkpoint_completion_and_legacy_unidentified_states(self):
        cases = {
            'checkpoint': lambda e: e.update(checkpoint_commit='a' * 40),
            'execution': lambda e: e.update(execution_started_at='2026-09-27T10:01:00Z'),
            'completion': lambda e: e.update(released_at='2026-09-27T10:01:00Z'),
            'malformed active authorization': lambda e: e.update(active_retry_authorization='other'),
        }
        for name, mutate in cases.items():
            with self.subTest(evidence=name):
                feature, doc, _, args = self.recovered_claim_fixture()
                state = harness.load_state(feature, doc)
                mutate(state['tasks']['T-001'])
                harness.save_state(feature, state)
                from contextlib import redirect_stderr
                from io import StringIO
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_recover_claim(args)

    def test_attempt_scoped_recovery_accepts_proven_historical_fields_and_rejects_current_or_ambiguous(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        historical = self.recovered_claim_fixture()
        with redirect_stdout(StringIO()):
            harness.cmd_recover_claim(historical[3])

        cases = {
            'current last_attempt_commit': lambda e: e.update(last_attempt_commit_attempt=4),
            'current release': lambda e: e.update(released_at='2026-09-27T10:01:00+00:00', released_attempt=4),
            'ambiguous failure': lambda e: e.pop('released_at'),
            'current completion': lambda e: e.update(completion_marker={'attempt': 4}),
            'current checkpoint': lambda e: e.update(checkpoint_commit='b' * 40, checkpoint_attempt=4),
            'current execution': lambda e: e.update(execution_started_at='2026-09-27T10:01:00+00:00', execution_started_attempt=4),
            'execution journal': lambda e: e.update(execution_journal={'attempt': 4}),
            'verification journal': lambda e: e.update(verification_journal={'attempt': 4}),
            'ambiguous checkpoint': lambda e: e.update(checkpoint_commit='b' * 40),
        }
        for name, mutate in cases.items():
            with self.subTest(evidence=name):
                feature, doc, _, args = self.recovered_claim_fixture()
                state = harness.load_state(feature, doc)
                mutate(state['tasks']['T-001'])
                harness.save_state(feature, state)
                err = StringIO()
                with redirect_stderr(err), self.assertRaises(SystemExit):
                    harness.cmd_recover_claim(args)
                self.assertTrue('CLAIM_RECOVERY_' in err.getvalue())

    def test_attempt_scoped_runtime_evidence_uses_explicit_attempt_binding(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        for attempt, should_block in ((4, True), (3, False), (None, True)):
            with self.subTest(attempt=attempt):
                feature, doc, _, args = self.recovered_claim_fixture()
                run = feature.parents[2] / '.agent-runs' / doc['feature'] / 'prior' / 'evidence.json'
                run.parent.mkdir(parents=True)
                obj = {'task': 'T-001'}
                if attempt is not None:
                    obj['attempt'] = attempt
                run.write_text(json.dumps(obj))
                if should_block:
                    with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                        harness.cmd_recover_claim(args)
                else:
                    with redirect_stdout(StringIO()):
                        harness.cmd_recover_claim(args)

    def test_recover_claim_rejects_runner_journal_and_wrong_feature_repository(self):
        from contextlib import redirect_stderr
        from io import StringIO
        feature, doc, _, args = self.recovered_claim_fixture()
        run = feature.parents[2] / '.agent-runs' / doc['feature'] / 'task-attempt' / 'orchestration' / 'provenance.json'
        run.parent.mkdir(parents=True)
        evidence_file = run
        evidence_file.write_text(json.dumps({'task': 'T-001', 'status': 'running'}))
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_recover_claim(args)

        repository = self.root / 'another-repo'
        repository.mkdir()
        import subprocess
        subprocess.run(['git', 'init', '-q'], cwd=repository, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '--allow-empty', '-q', '-m', 'base'], cwd=repository, check=True)
        feature, doc = self.exhausted_authorized_task_at(repository)
        task = harness.task_index(doc)['T-001']
        harness.write_packet(doc, task, feature)
        state = harness.load_state(feature, doc)
        grant = state['tasks']['T-001']['retry_authorizations'][0]
        grant['issued_at'] = '2026-09-27T09:59:00+00:00'
        grant['consumed_at'] = '2026-09-27T10:00:00+00:00'
        grant['consumed_attempt'] = 4
        state['tasks']['T-001'].update({
            'status': 'running', 'attempts': 4, 'owner': 'codex-t002',
            'active_retry_authorization': grant['id'], 'worktree': None,
        })
        harness.save_state(feature, state)
        args = argparse.Namespace(feature_dir=feature, task_id='T-001', attempt=4,
                                  authorization=grant['id'], owner='codex-t002', reason='r', by='op',
                                  attest_no_execution_started=True)
        other = self.root / 'other'
        other.mkdir()
        original_git_common = harness.git_common_dir
        try:
            harness.git_common_dir = lambda _feature: other
            with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                harness.cmd_recover_claim(args)
        finally:
            harness.git_common_dir = original_git_common

    def test_normal_atomic_claim_does_not_require_recovery(self):
        from contextlib import redirect_stdout
        from io import StringIO
        feature, doc = self.exhausted_authorized_task()
        with redirect_stdout(StringIO()):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
        entry = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(('running', 4), (entry['status'], entry['attempts']))
        self.assertNotIn('claim_recovery', entry)

    def test_recovered_attempt_stays_running_and_blocks_dependent_ready_task(self):
        from contextlib import redirect_stdout
        from io import StringIO
        feature, doc, _, args = self.recovered_claim_fixture()
        with redirect_stdout(StringIO()):
            harness.cmd_recover_claim(args)
        state = harness.load_state(feature, doc)
        self.assertEqual('running', state['tasks']['T-001']['status'])
        self.assertEqual(4, state['tasks']['T-001']['attempts'])
        self.assertNotIn('T-900', harness.ready_ids(doc, state, feature))

    def test_claim_accepts_provenance_only_packet_change_without_rewriting_packet(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature, doc = self.exhausted_authorized_task()
        task = harness.task_index(doc)['T-001']
        packet_path = harness.write_packet(doc, task, feature)
        legacy_packet = json.loads(packet_path.read_text())
        packet_contract_hash = legacy_packet['semantic_contract_sha256']
        legacy_packet.pop('semantic_contract_sha256')
        legacy_body = {key: value for key, value in legacy_packet.items() if key != 'packet_sha256'}
        legacy_packet['packet_sha256'] = harness.sha256_bytes(
            json.dumps(legacy_body, sort_keys=True, separators=(',', ':')).encode())
        packet_path.write_text(json.dumps(legacy_packet, indent=2, sort_keys=True) + '\n')
        original_bytes = packet_path.read_bytes()
        packet = json.loads(original_bytes)
        before = harness.load_state(feature, doc)
        grant = before['tasks']['T-001']['retry_authorizations'][0]
        grant_id = grant['id']
        self.assertEqual(grant['binding']['contract_sha256'], packet_contract_hash)
        original_protocol_fingerprint = harness.protocol_fingerprint
        try:
            harness.protocol_fingerprint = lambda _feature: 'f' * 64
            with redirect_stdout(StringIO()):
                harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
        finally:
            harness.protocol_fingerprint = original_protocol_fingerprint
        self.assertEqual(original_bytes, packet_path.read_bytes())
        state = harness.load_state(feature, doc)
        entry = state['tasks']['T-001']
        self.assertEqual(('running', 4, 'worker'), (entry['status'], entry['attempts'], entry['owner']))
        grant = next(item for item in entry['retry_authorizations'] if item['id'] == grant_id)
        self.assertEqual(4, grant['consumed_attempt'])
        self.assertIsNotNone(grant['consumed_at'])
        self.assertEqual(packet['protocol_fingerprint'], json.loads(packet_path.read_text())['protocol_fingerprint'])
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='duplicate'))
        reloaded = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(('running', 4, 'worker'), (reloaded['status'], reloaded['attempts'], reloaded['owner']))

    def test_claim_semantic_packet_mismatch_fails_before_authorization_commit(self):
        from contextlib import redirect_stderr
        from io import StringIO
        feature, doc = self.exhausted_authorized_task()
        task = harness.task_index(doc)['T-001']
        packet_path = harness.write_packet(doc, task, feature)
        existing = json.loads(packet_path.read_text())
        existing['objective'] = 'A different immutable objective'
        without_hash = {key: value for key, value in existing.items() if key != 'packet_sha256'}
        existing['packet_sha256'] = harness.sha256_bytes(json.dumps(without_hash, sort_keys=True, separators=(',', ':')).encode())
        packet_path.write_text(json.dumps(existing, indent=2, sort_keys=True) + '\n')
        grant_id = harness.load_state(feature, doc)['tasks']['T-001']['retry_authorizations'][0]['id']
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
        self.assert_precommit_claim_state(feature, doc, grant_id)

    def test_claim_rejects_each_changed_execution_contract_field_before_commit(self):
        from contextlib import redirect_stderr
        from io import StringIO
        changes = {
            'objective': 'different objective',
            'acceptance_criteria': ['AC-002'],
            'verification': ['./gradlew other-test'],
            'allowed_paths': ['different/**'],
            'depends_on': ['T-900'],
            'test_seam': 'different seam',
        }
        for field, value in changes.items():
            with self.subTest(field=field):
                repo = self.root / field
                repo.mkdir()
                import subprocess
                subprocess.run(['git', 'init', '-q'], cwd=repo, check=True)
                subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                                'commit', '--allow-empty', '-q', '-m', 'base'], cwd=repo, check=True)
                feature, doc = self.exhausted_authorized_task_at(repo)
                task = harness.task_index(doc)['T-001']
                path = harness.write_packet(doc, task, feature)
                packet = json.loads(path.read_text())
                packet[field] = value
                body = {key: item for key, item in packet.items() if key != 'packet_sha256'}
                packet['packet_sha256'] = harness.sha256_bytes(json.dumps(body, sort_keys=True, separators=(',', ':')).encode())
                path.write_text(json.dumps(packet, indent=2, sort_keys=True) + '\n')
                grant_id = harness.load_state(feature, doc)['tasks']['T-001']['retry_authorizations'][0]['id']
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
                self.assert_precommit_claim_state(feature, doc, grant_id)

    def test_claim_packet_precommit_failure_injection_preserves_retry_state(self):
        from contextlib import redirect_stderr
        from io import StringIO
        feature, doc = self.exhausted_authorized_task()
        grant_id = harness.load_state(feature, doc)['tasks']['T-001']['retry_authorizations'][0]['id']
        original = harness.packet_payload
        try:
            harness.packet_payload = lambda *_args, **_kwargs: (_ for _ in ()).throw(ValueError('serialize failure'))
            with self.assertRaises(SystemExit):
                harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
            self.assert_precommit_claim_state(feature, doc, grant_id)
            harness.packet_payload = original
            class FailingPacketWriter:
                def __init__(self, fd): self.fd = fd
                def __enter__(self): return self
                def __exit__(self, *_args): os.close(self.fd)
                def write(self, _value): raise OSError('packet write failure')
                def flush(self): pass
                def fileno(self): return self.fd
            original_fdopen = harness.os.fdopen
            fdopen_calls = []
            def fail_first_fdopen(fd, *args, **kwargs):
                fdopen_calls.append(fd)
                if len(fdopen_calls) == 1:
                    return FailingPacketWriter(fd)
                return original_fdopen(fd, *args, **kwargs)
            with mock.patch.object(harness.os, 'fdopen', side_effect=fail_first_fdopen):
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
            self.assert_precommit_claim_state(feature, doc, grant_id)
            original_mkstemp = harness.tempfile.mkstemp
            mkstemp_calls = []
            def fail_first_packet_create(*args, **kwargs):
                mkstemp_calls.append(True)
                if len(mkstemp_calls) == 1:
                    raise OSError('packet file creation failure')
                return original_mkstemp(*args, **kwargs)
            with mock.patch.object(harness.tempfile, 'mkstemp', side_effect=fail_first_packet_create):
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
            self.assert_precommit_claim_state(feature, doc, grant_id)
            original_fsync = harness.os.fsync
            fsync_calls = []
            def fail_packet_fsync_once(fd):
                fsync_calls.append(fd)
                if len(fsync_calls) == 1:
                    raise OSError('file fsync failure')
                return original_fsync(fd)
            with mock.patch.object(harness.os, 'fsync', side_effect=fail_packet_fsync_once):
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
            self.assert_precommit_claim_state(feature, doc, grant_id)
            packet_dir_fsync_calls = []
            def fail_directory_fsync_once(fd):
                packet_dir_fsync_calls.append(fd)
                if len(packet_dir_fsync_calls) == 2:
                    raise OSError('directory fsync failure')
                return original_fsync(fd)
            with mock.patch.object(harness.os, 'fsync', side_effect=fail_directory_fsync_once):
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
            self.assert_precommit_claim_state(feature, doc, grant_id)
            packet_path = feature / 'packets' / 'T-001.json'
            packet_path.unlink()
            original_link = harness.os.link
            def fail_packet_link(source, destination):
                if pathlib.Path(destination) == packet_path:
                    raise OSError('atomic publication failure')
                return original_link(source, destination)
            with mock.patch.object(harness.os, 'link', side_effect=fail_packet_link):
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
            self.assert_precommit_claim_state(feature, doc, grant_id)
        finally:
            harness.packet_payload = original

    def exhausted_authorized_task_at(self, repo):
        feature = self.feature([
            {'id': 'T-001', 'title': 'Build', 'objective': 'Implement it', 'role': 'builder',
             'depends_on': [], 'allowed_paths': ['modules/domain/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001'], 'verification': ['./gradlew test'],
             'test_mode': 'red-green-refactor', 'test_seam': 'fixture seam'},
            {'id': 'T-900', 'title': 'Evaluate', 'objective': 'Falsify it', 'role': 'evaluator',
             'depends_on': ['T-001'], 'allowed_paths': ['evidence/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['./gradlew test']},
        ], root=repo)
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(feature, state)
        from contextlib import redirect_stdout
        from io import StringIO
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(
                feature_dir=feature, task_id='T-001', reason='fixture exceptional retry', by='fixture-operator'))
        return feature, doc

    def test_claim_exception_immediately_before_publication_preserves_state(self):
        feature, doc = self.exhausted_authorized_task()
        grant_id = harness.load_state(feature, doc)['tasks']['T-001']['retry_authorizations'][0]['id']
        with mock.patch.object(harness, 'refresh_lease', side_effect=RuntimeError('precommit injection')):
            with self.assertRaises(RuntimeError):
                harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
        self.assert_precommit_claim_state(feature, doc, grant_id)

    def test_claim_state_commit_failure_is_read_back_as_unclaimed_after_restart(self):
        feature, doc = self.exhausted_authorized_task()
        grant_id = harness.load_state(feature, doc)['tasks']['T-001']['retry_authorizations'][0]['id']
        with mock.patch.object(harness, 'save_state', side_effect=OSError('state commit failure')):
            with self.assertRaises(OSError):
                harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
        # A fresh load resolves persisted state rather than the detached in-memory claim copy.
        self.assert_precommit_claim_state(feature, doc, grant_id)
        self.assertTrue((feature / 'packets' / 'T-001.json').exists())

    def test_claim_output_failure_after_commit_keeps_success_exit_contract(self):
        feature, doc = self.exhausted_authorized_task()
        with mock.patch('builtins.print', side_effect=OSError('closed output')):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
        entry = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(('running', 4, 'worker'), (entry['status'], entry['attempts'], entry['owner']))

    def test_exhausted_retry_authorization_is_one_shot_atomic_and_preserves_dag_history(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature = self.feature([
            {'id': 'T-001', 'title': 'A', 'objective': 'A', 'role': 'builder', 'depends_on': [], 'allowed_paths': ['a'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-002', 'title': 'B', 'objective': 'B', 'role': 'builder', 'depends_on': ['T-001'], 'allowed_paths': ['b'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-003', 'title': 'C', 'objective': 'C', 'role': 'builder', 'depends_on': ['T-001', 'T-002'], 'allowed_paths': ['c'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-900', 'title': 'Eval', 'objective': 'Evaluate', 'role': 'evaluator', 'depends_on': ['T-001', 'T-002', 'T-003'], 'allowed_paths': ['evidence/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['true']},
        ])
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'completed', 'attempts': 1, 'checkpoint_commit': 'a' * 40, 'completion_marker': 'keep'})
        state['tasks']['T-001']['human_resume_grants'] = 0
        state['tasks']['T-002'].update({'status': 'failed', 'attempts': 3, 'last_failure': 'third failed', 'failure_marker': 'keep'})
        harness.save_state(feature, state)
        original_packet = harness.packet_payload(doc, harness.task_index(doc)['T-002'], feature)
        self.assertEqual(3, 1 + doc['max_rework_attempts'])
        self.assertEqual([], harness.ready_ids(doc, state, feature))
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-002', owner='no-grant'))
        auth_args = argparse.Namespace(feature_dir=feature, task_id='T-002', reason='Reviewed exceptional retry', by='operator-1')
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(auth_args)
        authorized = harness.load_state(feature, doc)
        self.assertEqual('completed', authorized['tasks']['T-001']['status'])
        self.assertEqual(1, authorized['tasks']['T-001']['attempts'])
        self.assertEqual('failed', authorized['tasks']['T-002']['status'])
        self.assertEqual(3, authorized['tasks']['T-002']['attempts'])
        self.assertEqual(['T-002'], harness.ready_ids(doc, authorized, feature))
        self.assertNotIn('T-003', harness.ready_ids(doc, authorized, feature))

        errors = []
        barrier = threading.Barrier(2)
        def claimant(owner):
            barrier.wait()
            try:
                harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-002', owner=owner))
            except SystemExit as exc:
                errors.append(exc.code)
        threads = [threading.Thread(target=claimant, args=(f'worker-{i}',)) for i in range(2)]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        running = harness.load_state(feature, doc)
        self.assertEqual('running', running['tasks']['T-002']['status'])
        self.assertEqual(4, running['tasks']['T-002']['attempts'])
        self.assertEqual('keep', running['tasks']['T-001']['completion_marker'])
        self.assertEqual('keep', running['tasks']['T-002']['failure_marker'])
        self.assertEqual('completed', running['tasks']['T-001']['status'])
        self.assertEqual(1, running['tasks']['T-001']['attempts'])
        self.assertEqual(1, len(errors))
        consumed = running['tasks']['T-002']['retry_authorizations'][0]
        self.assertIsNotNone(consumed['consumed_at'])
        self.assertEqual(original_packet, harness.packet_payload(doc, harness.task_index(doc)['T-002'], feature))
        self.assertNotIn('T-003', harness.ready_ids(doc, running, feature))

        with redirect_stdout(StringIO()):
            harness.cmd_release(argparse.Namespace(feature_dir=feature, task_id='T-002', owner=running['tasks']['T-002']['owner'], reason='attempt four failed'))
        failed = harness.load_state(feature, doc)
        self.assertEqual(('failed', 4), (failed['tasks']['T-002']['status'], failed['tasks']['T-002']['attempts']))
        self.assertNotIn('T-003', harness.ready_ids(doc, failed, feature))
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-002', owner='worker-5'))
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-002', reason='Second explicit decision', by='operator-2'))
        with redirect_stdout(StringIO()):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-002', owner='worker-5'))
        fifth = harness.load_state(feature, doc)
        self.assertEqual(5, fifth['tasks']['T-002']['attempts'])
        self.assertEqual(2, sum(g['consumed_at'] is not None for g in fifth['tasks']['T-002']['retry_authorizations']))
        self.assertNotIn('T-003', harness.ready_ids(doc, fifth, feature))
        success_state = json.loads(json.dumps(fifth))
        success_state['tasks']['T-002'].update({'status': 'completed', 'checkpoint_commit': 'completed-normally'})
        self.assertIn('T-003', harness.ready_ids(doc, success_state, feature))
        self.assertEqual(1, success_state['tasks']['T-001']['attempts'])

        rollback_state = json.loads(json.dumps(authorized))
        grant = rollback_state['tasks']['T-002']['retry_authorizations'][0]
        grant['consumed_at'] = '2026-01-01T00:00:00+00:00'
        grant['consumed_attempt'] = 4
        rollback_state['tasks']['T-002']['active_retry_authorization'] = grant['id']
        harness.restore_attempt_authorization(rollback_state['tasks']['T-002'])
        self.assertIsNone(rollback_state['tasks']['T-002']['retry_authorizations'][0]['consumed_at'])

    def test_retry_authorization_stale_task_feature_packet_and_corruption_fail_closed(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(feature, state)
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='decision', by='op'))
        state = harness.load_state(feature, doc)
        grant = state['tasks']['T-001']['retry_authorizations'][0]
        grant['binding']['contract_sha256'] = 'changed'
        harness.save_state(feature, state)
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))

    def test_legacy_provenance_change_is_not_ready_and_claim_explains_supersession(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(feature, state)
        state['tasks']['T-001']['retry_authorizations'] = [self.add_legacy_retry_grant(feature, doc)]
        harness.save_state(feature, state)
        # RED observation on the old harness: ready_ids returned T-001 here,
        # while cmd_claim rejected the packet hash after this provenance edit.
        impl = self.root / 'tooling' / 'agent-harness' / 'harness.py'
        impl.parent.mkdir(parents=True, exist_ok=True)
        impl.write_text('compatible implementation provenance change\n')
        state = harness.load_state(feature, doc)
        self.assertEqual([], harness.ready_ids(doc, state, feature))
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))

    def test_unusable_legacy_requires_explicit_supersession_and_preserves_history(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(feature, state)
        state = harness.load_state(feature, doc)
        old = self.add_legacy_retry_grant(feature, doc)
        state['tasks']['T-001']['retry_authorizations'] = [old]
        old['binding']['packet_sha256'] = 'f' * 64
        # RED observation on the old harness: a normal authorization could be
        # appended beside this unusable V1 grant, with no supersession relation.
        original = json.dumps(old, sort_keys=True)
        harness.save_state(feature, state)
        self.assertEqual('legacy-unverifiable', harness.retry_authorization_status(feature, doc, state, 'T-001', 3, old['id']))
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='second', by='op'))
        old_id = old['id']
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='replace current contract', by='op', supersedes=old_id))
        updated = harness.load_state(feature, doc)
        grants = updated['tasks']['T-001']['retry_authorizations']
        self.assertEqual(2, len(grants))
        self.assertEqual(original, json.dumps(grants[0], sort_keys=True))
        self.assertIsNone(grants[0]['consumed_at'])
        self.assertEqual(old_id, grants[1]['supersedes'])
        self.assertEqual('valid', harness.retry_authorization_status(feature, doc, updated, 'T-001', 3, grants[1]['id']))
        self.assertEqual('valid', harness.classify_retry_authorizations(feature, doc, updated, 'T-001', 3)[0])
        self.assertEqual('superseded', harness.retry_authorization_status(feature, doc, updated, 'T-001', 3, old_id))
        self.assertEqual(1, len(updated['tasks']['T-001']['retry_authorization_supersessions']))
        self.assertEqual(grants[1]['id'], updated['tasks']['T-001']['retry_authorization_supersessions'][0]['authorization_id'])

    def test_supersession_requires_current_expected_attempt_count_and_cli_flag(self):
        from contextlib import redirect_stderr
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        old = self.add_legacy_retry_grant(feature, doc)
        old['binding']['expected_attempts'] = 2
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3, 'retry_authorizations': [old]})
        harness.save_state(feature, state)
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='stale', by='op', supersedes=old['id']))
        parsed = harness.parser().parse_args(['authorize-retry', str(feature), 'T-001', '--reason', 'explicit', '--supersedes', old['id']])
        self.assertEqual(old['id'], parsed.supersedes)

    def test_v2_survives_implementation_provenance_change_and_claims_once(self):
        from contextlib import redirect_stdout
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(feature, state)
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='v2 decision', by='op'))
        state = harness.load_state(feature, doc)
        grant = state['tasks']['T-001']['retry_authorizations'][0]
        self.assertEqual(2, grant['version'])
        before_contract = grant['binding']['contract_sha256']
        old_packet_hash = harness.packet_payload(doc, harness.task_index(doc)['T-001'], feature)['packet_sha256']
        impl = self.root / 'tooling' / 'agent-harness' / 'harness.py'
        impl.parent.mkdir(parents=True, exist_ok=True)
        impl.write_text('compatible implementation-only change\n')
        self.assertEqual(before_contract, harness.retry_binding(feature, doc, 'T-001', 3)['contract_sha256'])
        self.assertNotEqual(old_packet_hash, harness.packet_payload(doc, harness.task_index(doc)['T-001'], feature)['packet_sha256'])
        state = harness.load_state(feature, doc)
        self.assertEqual(['T-001'], harness.ready_ids(doc, state, feature))
        with redirect_stdout(StringIO()):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
        claimed = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(4, claimed['attempts'])
        self.assertEqual(grant['id'], claimed['active_retry_authorization'])
        self.assertEqual('consumed', harness.retry_authorization_status(feature, doc, {'tasks': {'T-001': claimed}}, 'T-001', 3, grant['id']))

    def test_v2_semantic_contract_changes_make_ready_and_claim_reject(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        mutations = [
            ('acceptance_criteria', ['AC-002']), ('verification', ['false']),
            ('allowed_paths', ['changed/**']), ('depends_on', ['T-900']),
            ('objective', 'Changed objective'), ('role', 'specialist'),
            ('test_mode', 'existing-suite'), ('test_seam', 'changed seam'),
        ]
        for index, (field, value) in enumerate(mutations):
            with self.subTest(field=field):
                feature = self.feature(root=self.root / f'semantic-{index}')
                doc = harness.load_json(feature / 'tasks.json')
                state = harness.initial_state(feature, doc)
                state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
                harness.save_state(feature, state)
                with redirect_stdout(StringIO()):
                    harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='decision', by='op'))
                state = harness.load_state(feature, doc)
                grant = state['tasks']['T-001']['retry_authorizations'][0]
                doc['tasks'][0][field] = value
                (feature / 'tasks.json').write_text(json.dumps(doc), encoding='utf-8')
                self.assertNotEqual(grant['binding']['contract_sha256'], harness.semantic_task_contract_sha256(feature, doc, harness.task_index(doc)['T-001']))
                self.assertEqual([], harness.ready_ids(doc, state, feature))
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.consume_attempt_authorization(state['tasks']['T-001'], 'T-001', doc, feature, state)

    def test_v2_survives_linked_worktree_provenance_advance(self):
        from contextlib import redirect_stdout
        from io import StringIO
        feature_a = self.feature()
        doc_a = harness.load_json(feature_a / 'tasks.json')
        (self.root / 'docs' / 'agentic-sdd' / 'README.md').write_text('initial protocol audit input\n', encoding='utf-8')
        import subprocess
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '-q', '-m', 'fixture feature'], cwd=self.root, check=True)
        state = harness.initial_state(feature_a, doc_a)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(feature_a, state)
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature_a, task_id='T-001', reason='linked worktree decision', by='op'))
        worktree = self.root / 'linked-checkout'
        subprocess.run(['git', 'worktree', 'add', '-b', 'retry-contract-linked', str(worktree), 'HEAD'], cwd=self.root, check=True, capture_output=True)
        (worktree / 'docs' / 'agentic-sdd' / 'README.md').write_text('compatible implementation provenance advanced\n', encoding='utf-8')
        feature_b = worktree / 'docs' / 'specs' / 'TST-001'
        doc_b = harness.load_json(feature_b / 'tasks.json')
        self.assertNotEqual(state['protocol_fingerprint'], harness.protocol_fingerprint(feature_b))
        self.assertEqual(['T-001'], harness.ready_ids(doc_b, harness.load_state(feature_b, doc_b), feature_b))
        with redirect_stdout(StringIO()):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature_b, task_id='T-001', owner='linked-worker'))
        self.assertEqual(4, harness.load_state(feature_b, doc_b)['tasks']['T-001']['attempts'])

    def test_v2_stale_consumed_malformed_and_unknown_versions_fail_closed(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        for mode in ('stale', 'attempts', 'repository', 'feature', 'task', 'protocol', 'consumed', 'malformed', 'unknown'):
            with self.subTest(mode=mode):
                feature = self.feature(root=self.root / f'classification-{mode}')
                doc = harness.load_json(feature / 'tasks.json')
                state = harness.initial_state(feature, doc)
                entry = state['tasks']['T-001']
                entry.update({'status': 'failed', 'attempts': 3})
                harness.save_state(feature, state)
                with redirect_stdout(StringIO()):
                    harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='decision', by='op'))
                state = harness.load_state(feature, doc)
                grant = state['tasks']['T-001']['retry_authorizations'][0]
                if mode == 'stale': grant['binding']['expected_attempts'] = 2
                if mode == 'attempts': grant['binding']['expected_attempts'] = 2
                if mode == 'repository': grant['binding']['repository'] = '/wrong/.git'
                if mode == 'feature': grant['binding']['feature'] = 'OTHER'
                if mode == 'task': grant['binding']['task'] = 'T-900'
                if mode == 'protocol': grant['binding']['protocol_version'] = 99
                if mode == 'consumed':
                    grant['consumed_at'] = '2026-01-02T00:00:00Z'
                    grant['consumed_attempt'] = 4
                if mode == 'malformed': grant['binding'].pop('contract_sha256')
                if mode == 'unknown': grant['binding']['binding_version'] = 99
                classification = None
                if mode in {'malformed', 'unknown'}:
                    with self.assertRaises(SystemExit):
                        harness.ready_ids(doc, state, feature)
                else:
                    classification = harness.classify_retry_authorizations(feature, doc, state, 'T-001', 3)[0]
                    self.assertEqual('consumed' if mode == 'consumed' else 'stale', classification)
                    self.assertEqual([], harness.ready_ids(doc, state, feature))
                with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                    harness.consume_attempt_authorization(entry, 'T-001', doc, feature, state)

    def test_supersession_wrong_ids_cross_task_and_duplicate_are_rejected(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3, 'retry_authorizations': [self.add_legacy_retry_grant(feature, doc)]})
        state['tasks']['T-900'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(feature, state)
        for target_task, ident in [('T-001', 'wrong-id'), ('T-900', 'legacy-grant')]:
            with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id=target_task, reason='reject', by='op', supersedes=ident))
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='replace', by='op', supersedes='legacy-grant'))
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='again', by='op', supersedes='legacy-grant'))

    def test_supersession_rejects_cross_feature_and_cross_repository_grants(self):
        from contextlib import redirect_stderr
        from io import StringIO
        import shutil
        feature_a = self.feature()
        doc_a = harness.load_json(feature_a / 'tasks.json')
        state_a = harness.initial_state(feature_a, doc_a)
        state_a['tasks']['T-001'].update({'status': 'failed', 'attempts': 3, 'retry_authorizations': [self.add_legacy_retry_grant(feature_a, doc_a)]})
        harness.save_state(feature_a, state_a)

        feature_b = self.root / 'docs' / 'specs' / 'TST-002'
        shutil.copytree(feature_a, feature_b)
        doc_b = harness.load_json(feature_b / 'tasks.json')
        doc_b['feature'] = 'TST-002'
        (feature_b / 'tasks.json').write_text(json.dumps(doc_b), encoding='utf-8')
        state_b = harness.initial_state(feature_b, doc_b)
        state_b['tasks']['T-001'].update({'status': 'failed', 'attempts': 3, 'retry_authorizations': [self.add_legacy_retry_grant(feature_a, doc_a)]})
        harness.save_state(feature_b, state_b)
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature_b, task_id='T-001', reason='cross feature', by='op', supersedes='legacy-grant'))

        other_root = self.root / 'other-repository'
        other_root.mkdir()
        import subprocess
        subprocess.run(['git', 'init', '-q'], cwd=other_root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', 'commit', '--allow-empty', '-q', '-m', 'base'], cwd=other_root, check=True)
        feature_c = self.feature(root=other_root)
        doc_c = harness.load_json(feature_c / 'tasks.json')
        state_c = harness.initial_state(feature_c, doc_c)
        state_c['tasks']['T-001'].update({'status': 'failed', 'attempts': 3, 'retry_authorizations': [self.add_legacy_retry_grant(feature_a, doc_a)]})
        harness.save_state(feature_c, state_c)
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature_c, task_id='T-001', reason='cross repository', by='op', supersedes='legacy-grant'))

    def test_consumed_authorization_cannot_be_superseded(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3})
        harness.save_state(feature, state)
        with redirect_stdout(StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='v2', by='op'))
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
            harness.cmd_release(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker', reason='attempt four failed'))
        state = harness.load_state(feature, doc)
        grant = state['tasks']['T-001']['retry_authorizations'][0]
        self.assertEqual('consumed', harness.retry_authorization_status(feature, doc, state, 'T-001', 4, grant['id']))
        with redirect_stderr(StringIO()), self.assertRaises(SystemExit):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='invalid replacement', by='op', supersedes=grant['id']))

    def test_supersession_racing_legacy_claim_keeps_attempt_exhausted(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3, 'retry_authorizations': [self.add_legacy_retry_grant(feature, doc)]})
        harness.save_state(feature, state)
        barrier = threading.Barrier(2)
        errors = []
        def claim():
            barrier.wait()
            try:
                with redirect_stderr(StringIO()):
                    harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker'))
            except SystemExit as exc:
                errors.append(exc.code)
        def supersede():
            barrier.wait()
            try:
                with redirect_stdout(StringIO()):
                    harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='explicit replacement', by='op', supersedes='legacy-grant'))
            except SystemExit as exc:
                errors.append(exc.code)
        threads = [threading.Thread(target=claim), threading.Thread(target=supersede)]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        current = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(1, len(current.get('retry_authorization_supersessions', [])))
        if current['status'] == 'running':
            self.assertEqual(0, len(errors))
            self.assertEqual(4, current['attempts'])
            replacement = current['retry_authorizations'][1]
            self.assertEqual(2, replacement['version'])
            self.assertIsNotNone(replacement['consumed_at'])
        else:
            self.assertEqual(1, len(errors))
            self.assertEqual('failed', current['status'])
            self.assertEqual(3, current['attempts'])

    def test_concurrent_supersession_and_ordinary_authorization_have_one_transition(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3, 'retry_authorizations': [self.add_legacy_retry_grant(feature, doc)]})
        harness.save_state(feature, state)
        errors = []
        barrier = threading.Barrier(2)
        def authorize(supersedes):
            barrier.wait()
            try:
                with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
                    harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='serialized decision', by='op', supersedes=supersedes))
            except SystemExit as exc:
                errors.append(exc.code)
        threads = [threading.Thread(target=authorize, args=(None,)), threading.Thread(target=authorize, args=('legacy-grant',))]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        current = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(1, len(errors))
        self.assertEqual(2, len(current['retry_authorizations']))
        self.assertEqual(1, len(current.get('retry_authorization_supersessions', [])))

    def test_concurrent_supersession_has_one_winner(self):
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'failed', 'attempts': 3, 'retry_authorizations': [self.add_legacy_retry_grant(feature, doc)]})
        harness.save_state(feature, state)
        errors = []
        barrier = threading.Barrier(2)
        def supersede():
            barrier.wait()
            try:
                with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
                    harness.cmd_authorize_retry(argparse.Namespace(feature_dir=feature, task_id='T-001', reason='one decision', by='op', supersedes='legacy-grant'))
            except SystemExit as exc:
                errors.append(exc.code)
        threads = [threading.Thread(target=supersede) for _ in range(2)]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        current = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(1, len(errors))
        self.assertEqual(2, len(current['retry_authorizations']))
        self.assertEqual(1, len(current['retry_authorization_supersessions']))

    def test_protocol_fingerprint_is_audit_only_and_state_schema_controls_compatibility(self):
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        before = state['protocol_fingerprint']
        guide = self.root / 'docs' / 'agentic-sdd' / 'README.md'
        guide.parent.mkdir(parents=True, exist_ok=True)
        guide.write_text('documentation-only change\n')
        self.assertNotEqual(before, harness.protocol_fingerprint(feature))
        state['protocol_fingerprint'] = before
        harness.validate_loaded_state(feature, doc, state)
        (self.root / 'tooling' / 'agent-harness').mkdir(parents=True, exist_ok=True)
        impl = self.root / 'tooling' / 'agent-harness' / 'harness.py'
        impl.write_text('implementation refactor with same state contract\n')
        harness.validate_loaded_state(feature, doc, state)
        state['protocol_version'] = 999
        with self.assertRaises(SystemExit):
            harness.validate_loaded_state(feature, doc, state)

    def test_explicit_migrate_state_preserves_legacy_history_and_is_idempotent(self):
        feature = self.feature([
            {'id': 'T-A', 'title': 'A', 'objective': 'A', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['a.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-B', 'title': 'B', 'objective': 'B', 'role': 'builder', 'depends_on': ['T-A'],
             'allowed_paths': ['b.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-900', 'title': 'E', 'objective': 'E', 'role': 'evaluator', 'depends_on': ['T-A', 'T-B'],
             'allowed_paths': ['evidence/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['true']},
        ])
        doc = harness.load_json(feature / 'tasks.json')
        old = harness.initial_state(feature, doc)
        old.pop('protocol_version')
        old['protocol_fingerprint'] = 'older-protocol-audit-fingerprint'
        old['tasks']['T-A'].update({
            'status': 'completed', 'attempts': 1, 'checkpoint_commit': 'a' * 40,
            'attempt_history': [{'attempt': 1, 'result': 'pass'}],
        })
        old['tasks']['T-B']['attempts'] = 2
        harness.save_state(feature, old)
        with self.assertRaises(SystemExit):
            harness.validate_loaded_state(feature, doc, old)
        harness.cmd_migrate_state(argparse.Namespace(feature_dir=feature))
        migrated = harness.load_state(feature, doc)
        self.assertEqual('completed', migrated['tasks']['T-A']['status'])
        self.assertEqual('a' * 40, migrated['tasks']['T-A']['checkpoint_commit'])
        self.assertEqual([{'attempt': 1, 'result': 'pass'}], migrated['tasks']['T-A']['attempt_history'])
        self.assertEqual('pending', migrated['tasks']['T-B']['status'])
        self.assertEqual(2, migrated['tasks']['T-B']['attempts'])
        self.assertEqual(['T-B'], harness.ready_ids(doc, migrated, feature))
        harness.cmd_migrate_state(argparse.Namespace(feature_dir=feature))

    def test_migrate_state_resolves_old_path_key_used_by_status_and_ready(self):
        import subprocess
        import shutil
        from contextlib import redirect_stderr, redirect_stdout
        from io import StringIO

        main_root = self.root / 'showcase'
        main_root.mkdir()
        subprocess.run(['git', 'init', '-q'], cwd=main_root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '--allow-empty', '-q', '-m', 'base'], cwd=main_root, check=True)
        feature = self.feature([
            {'id': 'T-A', 'title': 'A', 'objective': 'A', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['a.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-B', 'title': 'B', 'objective': 'B', 'role': 'builder', 'depends_on': ['T-A'],
             'allowed_paths': ['b.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-E', 'title': 'E', 'objective': 'E', 'role': 'evaluator', 'depends_on': ['T-A', 'T-B'],
             'allowed_paths': ['evidence/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['true']},
        ], root=main_root)
        doc = harness.load_json(feature / 'tasks.json')
        # Use production layout: a checkout nested under a temp worktree root, with
        # linked task worktrees as siblings of that main checkout.
        subprocess.run(['git', 'add', '.'], cwd=main_root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'fixture'], cwd=main_root, check=True)
        old_key = harness.sha256_bytes(str(feature.resolve()).encode())[:20]
        old_path = main_root / '.agent-state' / f'{feature.name}-{old_key}.json'
        old_path.parent.mkdir(parents=True, exist_ok=True)
        state = harness.initial_state(feature, doc)
        state.pop('protocol_version')
        state['protocol_fingerprint'] = 'older-compatible-protocol-hash'
        state['tasks']['T-A'].update({
            'status': 'completed', 'attempts': 1, 'checkpoint_commit': 'a' * 40,
            'attempt_history': [{'attempt': 1, 'result': 'pass'}],
        })
        state['tasks']['T-B']['attempts'] = 2
        old_path.write_text(json.dumps(state))
        self.assertFalse(harness.state_path(feature).exists())
        self.assertNotEqual(old_path, harness.state_path(feature))

        worktree = self.root / f'{main_root.name}{harness.WORKTREE_ROOT_SUFFIX}' / 'TST-001' / 'T-B'
        worktree.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['git', 'worktree', 'add', '--quiet', str(worktree), '-b', 'agent/TST-001/T-B', 'HEAD'],
                       cwd=main_root, check=True)
        feature_wt = worktree / 'docs/specs/TST-001'
        try:
            self.assertEqual(harness.state_path(feature), harness.state_path(feature_wt))
            out, err = StringIO(), StringIO()
            with redirect_stdout(out), redirect_stderr(err), self.assertRaises(SystemExit):
                harness.cmd_status(argparse.Namespace(feature_dir=feature, json=True))
            self.assertIn('migrate-state', err.getvalue())
            self.assertFalse(harness.state_path(feature).exists())
            with redirect_stdout(out), redirect_stderr(err), self.assertRaises(SystemExit):
                harness.cmd_ready(argparse.Namespace(feature_dir=feature, json=True))
            self.assertIn('migrate-state', err.getvalue())
            self.assertFalse(harness.state_path(feature).exists())
            with redirect_stdout(out), redirect_stderr(err), self.assertRaises(SystemExit):
                harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a'))
            self.assertIn('migrate-state', err.getvalue())
            self.assertFalse(harness.state_path(feature).exists())

            migrate_out = StringIO()
            with redirect_stdout(migrate_out):
                harness.cmd_migrate_state(argparse.Namespace(feature_dir=feature_wt))
            self.assertIn('MIGRATED', migrate_out.getvalue())
            self.assertTrue(harness.state_path(feature).exists())
            self.assertFalse(old_path.exists())
            migrated = harness.load_state(feature, doc)
            self.assertEqual('completed', migrated['tasks']['T-A']['status'])
            self.assertEqual('a' * 40, migrated['tasks']['T-A']['checkpoint_commit'])
            self.assertEqual([{'attempt': 1, 'result': 'pass'}], migrated['tasks']['T-A']['attempt_history'])
            self.assertEqual(2, migrated['tasks']['T-B']['attempts'])
            self.assertEqual('pending', migrated['tasks']['T-B']['status'])

            ready_out = StringIO()
            with redirect_stdout(ready_out):
                harness.cmd_ready(argparse.Namespace(feature_dir=feature_wt, json=True))
            self.assertEqual(['T-B'], json.loads(ready_out.getvalue()))
            harness.cmd_claim(argparse.Namespace(feature_dir=feature_wt, task_id='T-B', owner='worker-b'))
            current = harness.load_state(feature, doc)
            self.assertEqual('running', current['tasks']['T-B']['status'])
            self.assertEqual('worker-b', current['tasks']['T-B']['owner'])
            self.assertEqual(harness.state_path(feature), harness.state_path(feature_wt))
            status_out = StringIO()
            with redirect_stdout(status_out):
                harness.cmd_status(argparse.Namespace(feature_dir=feature, json=True))
            main_observed = json.loads(status_out.getvalue())
            self.assertEqual('running', main_observed['tasks']['T-B']['status'])
            self.assertEqual('worker-b', main_observed['tasks']['T-B']['owner'])
        finally:
            subprocess.run(['git', 'worktree', 'remove', '--force', str(worktree)], cwd=main_root, check=False)
            shutil.rmtree(worktree.parent.parent.parent, ignore_errors=True)

    def test_conflicting_valid_legacy_state_candidates_fail_closed(self):
        import subprocess
        from contextlib import redirect_stderr
        from io import StringIO
        feature = self.feature()
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'fixture'], cwd=self.root, check=True)
        doc = harness.load_json(feature / 'tasks.json')
        canonical = harness.initial_state(feature, doc)
        harness.save_state(feature, canonical)
        legacy = harness.main_legacy_state_path(feature)
        legacy.parent.mkdir(parents=True, exist_ok=True)
        old = json.loads(json.dumps(canonical))
        old['tasks']['T-001']['status'] = 'completed'
        legacy.write_text(json.dumps(old))
        errors = StringIO()
        with redirect_stderr(errors), self.assertRaises(SystemExit):
            harness.cmd_migrate_state(argparse.Namespace(feature_dir=feature))
        self.assertIn('conflicting valid lifecycle states', errors.getvalue())
        self.assertEqual(canonical, json.loads(harness.state_path(feature).read_text()))

    def test_equivalent_canonical_and_legacy_states_resolve_to_canonical(self):
        import subprocess
        feature = self.feature()
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'fixture'], cwd=self.root, check=True)
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        harness.save_state(feature, state)
        legacy = harness.main_legacy_state_path(feature)
        legacy.parent.mkdir(parents=True, exist_ok=True)
        legacy.write_text(json.dumps(state))
        resolved = harness.load_state(feature, doc)
        self.assertEqual(state, resolved)
        self.assertFalse(legacy.exists())

    def test_malformed_exact_legacy_state_fails_closed(self):
        import subprocess
        feature = self.feature()
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'fixture'], cwd=self.root, check=True)
        legacy = harness.main_legacy_state_path(feature)
        legacy.parent.mkdir(parents=True, exist_ok=True)
        legacy.write_text('{malformed')
        with self.assertRaises(SystemExit):
            harness.load_state(feature, harness.load_json(feature / 'tasks.json'))
        self.assertFalse(harness.state_path(feature).exists())

    def test_migration_fails_closed_for_dag_change_corruption_and_unknown_version(self):
        import subprocess
        feature = self.feature()
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'fixture'], cwd=self.root, check=True)
        doc = harness.load_json(feature / 'tasks.json')
        old = harness.initial_state(feature, doc)
        old.pop('protocol_version')
        harness.save_state(feature, old)
        tasks_file = feature / 'tasks.json'
        original = tasks_file.read_text()
        changed = json.loads(original)
        changed['tasks'][0]['title'] = 'Changed DAG input'
        tasks_file.write_text(json.dumps(changed))
        with self.assertRaises(SystemExit):
            harness.cmd_migrate_state(argparse.Namespace(feature_dir=feature))
        tasks_file.write_text(original)
        state_path = harness.state_path(feature)
        state_path.write_text('{broken')
        with self.assertRaises(SystemExit):
            harness.cmd_migrate_state(argparse.Namespace(feature_dir=feature))
        state_path.write_text(json.dumps(old))
        old['protocol_version'] = 777
        state_path.write_text(json.dumps(old))
        with self.assertRaises(SystemExit):
            harness.cmd_migrate_state(argparse.Namespace(feature_dir=feature))

    def test_migration_never_infers_completion_from_git_ancestry(self):
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        old = harness.initial_state(feature, doc)
        old.pop('protocol_version')
        old['tasks']['T-001']['status'] = 'pending'
        old['tasks']['T-001']['checkpoint_commit'] = 'f' * 40
        harness.save_state(feature, old)
        harness.cmd_migrate_state(argparse.Namespace(feature_dir=feature))
        migrated = harness.load_state(feature, doc)
        self.assertEqual('pending', migrated['tasks']['T-001']['status'])

    def test_valid_feature(self):
        self.assertEqual([], harness.validate(self.feature()))

    def test_lifecycle_cli_cross_worktree_manual_exercise(self):
        import subprocess
        import shutil

        feature = self.feature([
            {'id': 'T-A', 'title': 'A', 'objective': 'A', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['a.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-B', 'title': 'B', 'objective': 'B', 'role': 'builder', 'depends_on': ['T-A'],
             'allowed_paths': ['b.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-900', 'title': 'E', 'objective': 'E', 'role': 'evaluator', 'depends_on': ['T-A', 'T-B'],
             'allowed_paths': ['docs/specs/TST-001/evidence/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['true']},
        ])
        harness_path = pathlib.Path(__file__).resolve().parents[1] / 'harness.py'
        (self.root / 'AGENTS.md').write_text('# agents\n')
        agents = self.root / 'docs' / 'agentic-sdd' / 'agents'
        agents.mkdir(parents=True, exist_ok=True)
        (self.root / 'docs' / 'agentic-sdd' / 'constitution.md').write_text('# constitution\n')
        for role in ('builder', 'evaluator'):
            (agents / f'{role}.md').write_text(f'# {role}\n')
        (self.root / 'tooling' / 'agent-harness').mkdir(parents=True, exist_ok=True)
        (self.root / 'tooling' / 'agent-harness' / 'harness.py').write_bytes(harness_path.read_bytes())
        (self.root / '.gitignore').write_text('.agent-state/\n.agent-runs/\ndocs/specs/*/packets/\n')
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'base'], cwd=self.root, check=True)
        t_b = self.root.parent / f'{self.root.name}{harness.WORKTREE_ROOT_SUFFIX}' / 'TST-001' / 'T-B'

        def cli(worktree, *args):
            return subprocess.run(['python3', str(harness_path), *map(str, args)], cwd=worktree,
                                  capture_output=True, text=True, check=True).stdout.strip()

        try:
            cli(self.root, 'worktree-create', feature.relative_to(self.root), 'T-A')
            t_b.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(['git', 'worktree', 'add', '--quiet', str(t_b), '-b', 'agent/TST-001/T-B', 'HEAD'],
                           cwd=self.root, check=True)
            cli(self.root, 'start', feature.relative_to(self.root), 'T-A', '--owner', 'worker-a')
            t_a = harness.worktree_path('TST-001', 'T-A')
            (t_a / 'a.txt').write_text('A output\n')
            doc = harness.load_json(feature / 'tasks.json')
            checkpoint = harness.checkpoint_worktree(
                doc, harness.active_task_contract(feature, doc, 'T-A'), t_a)
            evidence = self.root / 'a-evidence.json'
            state = harness.load_state(feature, doc)
            evidence.write_text(json.dumps(self.passing_completion_evidence(
                feature, doc, 'T-A', state['tasks']['T-A']['attempts'], checkpoint)))
            cli(self.root, 'complete', feature.relative_to(self.root), 'T-A', '--owner', 'worker-a',
                '--evidence', evidence)

            self.assertEqual('completed', json.loads(cli(t_b, 'status',
                              t_b / 'docs/specs/TST-001', '--json'))['tasks']['T-A']['status'])
            self.assertIn('T-B', json.loads(cli(t_b, 'ready', t_b / 'docs/specs/TST-001', '--json')))
            cli(t_b, 'claim', t_b / 'docs/specs/TST-001', 'T-B', '--owner', 'worker-b')
            main_state = json.loads(cli(self.root, 'status', feature.relative_to(self.root), '--json'))
            self.assertEqual('running', main_state['tasks']['T-B']['status'])
            self.assertEqual('worker-b', main_state['tasks']['T-B']['owner'])
        finally:
            subprocess.run(['git', 'worktree', 'remove', '--force', str(t_b)], cwd=self.root, check=False)
            t_a = harness.worktree_path('TST-001', 'T-A')
            subprocess.run(['git', 'worktree', 'remove', '--force', str(t_a)], cwd=self.root, check=False)
            shutil.rmtree(self.root.parent / f'{self.root.name}{harness.WORKTREE_ROOT_SUFFIX}', ignore_errors=True)

    def test_active_design_feature_requires_fresh_gate(self):
        feature = self.feature()
        config = {
            'required_for_orchestration': True, 'grill': 'auto', 'prototype': 'auto',
            'architecture_grill': 'auto', 'prototype_max_parallel': 2, 'prototype_questions': []
        }
        (feature / 'design.json').write_text(json.dumps(config))
        errors = harness.validate(feature)
        self.assertTrue(any('design preflight gate is required' in error for error in errors))

        gate_dir = feature / 'design'
        gate_dir.mkdir()
        gate = {
            'schema_version': 1, 'feature': 'TST-001', 'decision': 'pass',
            'spec_sha256': harness.sha256_bytes((feature / 'spec.md').read_bytes()),
            'plan_sha256': harness.sha256_bytes((feature / 'plan.md').read_bytes()),
            'design_config_sha256': harness.sha256_bytes((feature / 'design.json').read_bytes()),
            'stages': {
                'spec_grill': {'status': 'pass'},
                'prototype': {'status': 'skipped'},
                'architecture_grill': {'status': 'pass'},
            },
        }
        (gate_dir / 'gate.json').write_text(json.dumps(gate))
        self.assertEqual([], harness.validate(feature))

    def test_design_gate_becomes_stale_when_plan_changes(self):
        feature = self.feature()
        config = {'required_for_orchestration': True, 'grill': 'auto', 'prototype': 'auto', 'architecture_grill': 'auto'}
        (feature / 'design.json').write_text(json.dumps(config))
        (feature / 'design').mkdir()
        gate = {
            'schema_version': 1, 'feature': 'TST-001', 'decision': 'pass',
            'spec_sha256': harness.sha256_bytes((feature / 'spec.md').read_bytes()),
            'plan_sha256': harness.sha256_bytes((feature / 'plan.md').read_bytes()),
            'design_config_sha256': harness.sha256_bytes((feature / 'design.json').read_bytes()),
            'stages': {
                'spec_grill': {'status': 'pass'}, 'prototype': {'status': 'skipped'},
                'architecture_grill': {'status': 'pass'}
            },
        }
        (feature / 'design' / 'gate.json').write_text(json.dumps(gate))
        (feature / 'plan.md').write_text('# changed plan\n')
        self.assertTrue(any('plan_sha256' in error and 'stale' in error for error in harness.validate(feature)))

    def test_inactive_maintained_example_does_not_require_live_design_gate(self):
        feature = self.feature()
        inactive = self.root / 'docs' / 'agentic-sdd' / 'examples' / 'TST-001'
        inactive.parent.mkdir(parents=True, exist_ok=True)
        feature.rename(inactive)
        (inactive / 'design.json').write_text(json.dumps({'required_for_orchestration': True}))
        self.assertFalse(any('design preflight gate is required' in error for error in harness.validate(inactive)))


    def test_feature_id_must_match_directory(self):
        feature = self.feature()
        doc = json.loads((feature / 'tasks.json').read_text())
        doc['feature'] = 'OTHER-001'
        (feature / 'tasks.json').write_text(json.dumps(doc))
        self.assertTrue(any('must match feature directory name' in e for e in harness.validate(feature)))

    def test_missing_risk_selected_reviewer_profile_is_rejected(self):
        feature = self.feature()
        doc = json.loads((feature / 'tasks.json').read_text())
        doc['tasks'][0]['risk_tags'] = ['architecture']
        (feature / 'tasks.json').write_text(json.dumps(doc))
        (self.root / 'docs' / 'agentic-sdd' / 'agents' / 'architecture-reviewer.md').unlink()
        self.assertTrue(any('selects missing agent profile: architecture-reviewer' in e for e in harness.validate(feature)))

    def test_all_spec_acceptance_criteria_require_evaluator_coverage(self):
        feature = self.feature()
        doc = json.loads((feature / 'tasks.json').read_text())
        doc['tasks'][1]['acceptance_criteria'] = ['AC-001']
        (feature / 'tasks.json').write_text(json.dumps(doc))
        self.assertTrue(any('spec acceptance criteria not covered by an evaluator' in e for e in harness.validate(feature)))

    def test_missing_acceptance_criterion_is_rejected(self):
        feature = self.feature()
        doc = json.loads((feature / 'tasks.json').read_text())
        doc['tasks'][0]['acceptance_criteria'] = ['AC-999']
        (feature / 'tasks.json').write_text(json.dumps(doc))
        self.assertTrue(any('AC-999' in e for e in harness.validate(feature)))

    def test_cycle_is_rejected(self):
        tasks = [
            {'id': 'T-001', 'title': 'A', 'objective': 'A', 'role': 'builder', 'depends_on': ['T-002'],
             'allowed_paths': ['a/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-002', 'title': 'B', 'objective': 'B', 'role': 'builder', 'depends_on': ['T-001'],
             'allowed_paths': ['b/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-900', 'title': 'E', 'objective': 'E', 'role': 'evaluator', 'depends_on': ['T-001', 'T-002'],
             'allowed_paths': ['e/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
        ]
        self.assertTrue(any('cycle' in e for e in harness.validate(self.feature(tasks))))

    def test_parallel_builder_write_collision_is_rejected(self):
        tasks = [
            {'id': 'T-001', 'title': 'A', 'objective': 'A', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['modules/domain/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-002', 'title': 'B', 'objective': 'B', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['modules/domain/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-900', 'title': 'E', 'objective': 'E', 'role': 'evaluator', 'depends_on': ['T-001', 'T-002'],
             'allowed_paths': ['e/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
        ]
        self.assertTrue(any('overlapping allowed_paths' in e for e in harness.validate(self.feature(tasks))))

    def test_nested_parallel_builder_write_collision_is_rejected(self):
        tasks = [
            {'id': 'T-001', 'title': 'A', 'objective': 'A', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['modules/domain/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-002', 'title': 'B', 'objective': 'B', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['modules/domain/order/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-900', 'title': 'E', 'objective': 'E', 'role': 'evaluator', 'depends_on': ['T-001', 'T-002'],
             'allowed_paths': ['e/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
        ]
        self.assertTrue(any('overlapping allowed_paths' in e for e in harness.validate(self.feature(tasks))))

    def test_protocol_self_spec_is_allowed_during_synthetic_bootstrap(self):
        self.assertTrue(harness.bootstrap_path_allowed(
            'docs/specs/SDD-001/spec.md', pathlib.Path('docs/specs/FEATURE-1')
        ))
        self.assertFalse(harness.bootstrap_path_allowed(
            'docs/specs/OTHER-001/spec.md', pathlib.Path('docs/specs/FEATURE-1')
        ))

    def test_matching_wayfinder_map_is_allowed_during_feature_bootstrap(self):
        self.assertTrue(harness.bootstrap_path_allowed(
            'docs/wayfinder/FEATURE-1/wayfinder.json', pathlib.Path('docs/specs/FEATURE-1')
        ))
        self.assertFalse(harness.bootstrap_path_allowed(
            'docs/wayfinder/OTHER-1/wayfinder.json', pathlib.Path('docs/specs/FEATURE-1')
        ))

    def test_unsafe_allowed_path_is_rejected(self):
        feature = self.feature()
        doc = json.loads((feature / 'tasks.json').read_text())
        doc['tasks'][0]['allowed_paths'] = ['../outside/**']
        (feature / 'tasks.json').write_text(json.dumps(doc))
        self.assertTrue(any('unsafe path' in e for e in harness.validate(feature)))

    def test_packet_is_deterministic_and_immutable(self):
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        task = harness.task_index(doc)['T-001']
        first = harness.write_packet(doc, task, feature)
        original = first.read_text()
        second = harness.write_packet(doc, task, feature)
        self.assertEqual(original, second.read_text())
        (feature / 'plan.md').write_text('# changed plan\n')
        with self.assertRaises(SystemExit):
            harness.write_packet(doc, task, feature)

    def test_state_rejects_spec_drift(self):
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.load_state(feature, doc)
        harness.save_state(feature, state)
        (feature / 'spec.md').write_text('# changed\n- AC-001: works\n- AC-002: verified\n')
        with self.assertRaises(SystemExit):
            harness.load_state(feature, doc)

    def test_claim_prevents_second_owner(self):
        feature = self.feature()
        args = argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker-a')
        harness.cmd_claim(args)
        args2 = argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker-b')
        with self.assertRaises(SystemExit):
            harness.cmd_claim(args2)


    def test_lease_configuration_is_validated(self):
        feature = self.feature()
        doc = json.loads((feature / 'tasks.json').read_text())
        doc['lease_ttl_seconds'] = 100
        doc['heartbeat_interval_seconds'] = 60
        (feature / 'tasks.json').write_text(json.dumps(doc))
        self.assertTrue(any('less than half' in e for e in harness.validate(feature)))

    def test_claim_creates_renewable_lease(self):
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker-a'))
        before = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertIn('heartbeat_at', before)
        self.assertIn('lease_expires_at', before)
        attempts = before['attempts']
        harness.heartbeat(feature, doc, 'T-001', 'worker-a')
        after = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual(attempts, after['attempts'])
        with self.assertRaises(SystemExit):
            harness.heartbeat(feature, doc, 'T-001', 'worker-b')

    def test_real_git_worktrees_share_lifecycle_store_lock_and_claim_state(self):
        import subprocess
        import shutil
        from contextlib import redirect_stdout
        from io import StringIO

        feature = self.feature([
            {'id': 'T-A', 'title': 'A', 'objective': 'A', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['a.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-B', 'title': 'B', 'objective': 'B', 'role': 'builder', 'depends_on': ['T-A'],
             'allowed_paths': ['b.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-900', 'title': 'E', 'objective': 'E', 'role': 'evaluator', 'depends_on': ['T-A', 'T-B'],
             'allowed_paths': ['docs/specs/TST-001/evidence/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['true']},
        ])
        doc = harness.load_json(feature / 'tasks.json')
        (self.root / 'AGENTS.md').write_text('# agents\n')
        (self.root / 'docs' / 'agentic-sdd' / 'constitution.md').write_text('# constitution\n')
        (self.root / 'tooling' / 'agent-harness').mkdir(parents=True, exist_ok=True)
        (self.root / 'tooling' / 'agent-harness' / 'harness.py').write_text('# actual protocol file\n')
        (self.root / '.gitignore').write_text('.agent-state/\n.agent-runs/\ndocs/specs/*/packets/\n')
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'base'], cwd=self.root, check=True)
        t_b = self.root.parent / f'{self.root.name}{harness.WORKTREE_ROOT_SUFFIX}' / 'TST-001' / 'T-B'
        try:
            # Create B from the pre-completion protocol/spec snapshot, as an existing task worktree.
            state = harness.load_state(feature, harness.load_json(feature / 'tasks.json'))
            harness.save_state(feature, state)
            subprocess.run(['git', 'worktree', 'add', '--quiet', str(t_b), '-b', 'agent/TST-001/T-B', 'HEAD'],
                           cwd=self.root, check=True)
            feature_b = t_b / 'docs/specs/TST-001'
            self.assertEqual(harness.state_path(feature), harness.state_path(feature_b))
            self.assertEqual(harness.lock_path(feature), harness.lock_path(feature_b))

            harness.cmd_worktree_create(argparse.Namespace(feature_dir=feature, task_id='T-A'))
            harness.cmd_start(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a'))
            t_a = harness.worktree_path('TST-001', 'T-A')
            (t_a / 'a.txt').write_text('dependency output\n')
            checkpoint = harness.checkpoint_worktree(
                doc, harness.active_task_contract(feature, doc, 'T-A'), t_a)
            evidence = self.root / 'result-a.json'
            state = harness.load_state(feature, doc)
            evidence.write_text(json.dumps(self.passing_completion_evidence(
                feature, doc, 'T-A', state['tasks']['T-A']['attempts'], checkpoint)))
            harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a',
                                                    evidence=str(evidence)))

            out = StringIO()
            with redirect_stdout(out):
                harness.cmd_status(argparse.Namespace(feature_dir=feature_b, json=True))
            self.assertEqual('completed', json.loads(out.getvalue())['tasks']['T-A']['status'])
            out = StringIO()
            with redirect_stdout(out):
                harness.cmd_ready(argparse.Namespace(feature_dir=feature_b, json=True))
            self.assertIn('T-B', json.loads(out.getvalue()))

            harness.cmd_claim(argparse.Namespace(feature_dir=feature_b, task_id='T-B', owner='worker-b'))
            claimed_b = harness.load_state(feature_b, harness.load_json(feature_b / 'tasks.json'))
            claimed_main = harness.load_state(feature, harness.load_json(feature / 'tasks.json'))
            self.assertEqual('running', claimed_b['tasks']['T-B']['status'])
            self.assertEqual('worker-b', claimed_main['tasks']['T-B']['owner'])
            with self.assertRaises(SystemExit):
                harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-B', owner='worker-c'))
            self.assertTrue(harness.heartbeat(feature_b, harness.load_json(feature_b / 'tasks.json'), 'T-B', 'worker-b'))
            with harness.locked_state(feature, harness.load_json(feature / 'tasks.json')) as state:
                self.assertEqual('running', state['tasks']['T-B']['status'])
                state['tasks']['T-B']['status'] = 'completed'
                state['tasks']['T-B'].pop('owner', None)
            with self.assertRaises(SystemExit):
                harness.heartbeat(feature_b, harness.load_json(feature_b / 'tasks.json'), 'T-B', 'worker-b')
        finally:
            subprocess.run(['git', 'worktree', 'remove', '--force', str(t_b)], cwd=self.root, check=False)
            t_a = harness.worktree_path('TST-001', 'T-A')
            subprocess.run(['git', 'worktree', 'remove', '--force', str(t_a)], cwd=self.root, check=False)
            shutil.rmtree(self.root.parent / f'{self.root.name}{harness.WORKTREE_ROOT_SUFFIX}', ignore_errors=True)

    def test_legacy_main_state_migrates_and_conflicting_copy_fails_safe(self):
        import subprocess
        feature = self.feature()
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'base'], cwd=self.root, check=True)
        worktree = self.root.parent / f'{self.root.name}{harness.WORKTREE_ROOT_SUFFIX}' / 'TST-001' / 'T-001'
        subprocess.run(['git', 'worktree', 'add', '--quiet', str(worktree), '-b', 'agent/TST-001/T-001', 'HEAD'],
                       cwd=self.root, check=True)
        main_legacy = harness.main_legacy_state_path(feature)
        main_legacy.parent.mkdir(parents=True, exist_ok=True)
        state = harness.initial_state(feature, harness.load_json(feature / 'tasks.json'))
        state['tasks']['T-001']['status'] = 'failed'
        main_legacy.write_text(json.dumps(state))
        harness.cmd_migrate_state(argparse.Namespace(feature_dir=feature))
        self.assertEqual('failed', harness.load_state(feature, harness.load_json(feature / 'tasks.json'))['tasks']['T-001']['status'])
        canonical_before = json.loads(harness.state_path(feature).read_text())
        conflicting = json.loads(json.dumps(canonical_before))
        conflicting['tasks']['T-001']['status'] = 'completed'
        main_legacy.write_text(json.dumps(conflicting))
        with self.assertRaises(SystemExit):
            harness.load_state(feature, harness.load_json(feature / 'tasks.json'))
        self.assertEqual(canonical_before, json.loads(harness.state_path(feature).read_text()))
        main_legacy.unlink()
        subprocess.run(['git', 'worktree', 'remove', '--force', str(worktree)], cwd=self.root, check=True)

    def test_concurrent_worktree_updates_are_not_lost(self):
        import subprocess
        import threading
        import shutil
        feature = self.feature()
        (self.root / '.gitignore').write_text('.agent-state/\n')
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'base'], cwd=self.root, check=True)
        worktree = self.root.parent / f'{self.root.name}{harness.WORKTREE_ROOT_SUFFIX}' / 'TST-001' / 'T-001'
        subprocess.run(['git', 'worktree', 'add', '--quiet', str(worktree), '-b', 'agent/TST-001/T-001', 'HEAD'],
                       cwd=self.root, check=True)
        feature_wt = worktree / 'docs/specs/TST-001'
        errors = []

        def mutate(path, key):
            try:
                doc = harness.load_json(path / 'tasks.json')
                with harness.locked_state(path, doc) as state:
                    state['tasks']['T-001'][key] = key
                    # Hold the shared lock long enough to force contention with the other checkout.
                    import time
                    time.sleep(0.05)
            except BaseException as exc:
                errors.append(exc)

        threads = [threading.Thread(target=mutate, args=(feature, 'main_mark')),
                   threading.Thread(target=mutate, args=(feature_wt, 'worktree_mark'))]
        try:
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            self.assertEqual([], errors)
            final = harness.load_state(feature, harness.load_json(feature / 'tasks.json'))['tasks']['T-001']
            self.assertEqual('main_mark', final['main_mark'])
            self.assertEqual('worktree_mark', final['worktree_mark'])
        finally:
            subprocess.run(['git', 'worktree', 'remove', '--force', str(worktree)], cwd=self.root, check=False)
            shutil.rmtree(self.root.parent / f'{self.root.name}{harness.WORKTREE_ROOT_SUFFIX}', ignore_errors=True)


    def test_expired_lease_is_recovered_for_resume(self):
        import subprocess
        feature = self.feature()
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        (self.root / '.gitignore').write_text('.agent-state/\n')
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run([
            'git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
            'commit', '-q', '-m', 'base'
        ], cwd=self.root, check=True)
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.load_state(feature, doc)
        state['tasks']['T-001'].update({
            'status': 'running', 'owner': 'dead-worker', 'attempts': 1,
            'lease_expires_at': '2000-01-01T00:00:00+00:00',
            'heartbeat_at': '2000-01-01T00:00:00+00:00',
        })
        state['tasks']['T-900']['status'] = 'pending'
        harness.save_state(feature, state)
        recovered = harness.recover_stale_leases(feature, doc)
        self.assertEqual(['T-001'], recovered)
        entry = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual('failed', entry['status'])
        self.assertNotIn('owner', entry)
        self.assertNotIn('lease_expires_at', entry)
        self.assertEqual(1, entry['attempts'])

    def test_specialist_routing_is_risk_triggered(self):
        task = {'risk_tags': ['messaging', 'security', 'messaging']}
        self.assertEqual(['messaging-reviewer', 'security-reviewer'], harness.reviewers(task))

    def test_ai_risk_routes_to_quality_and_security_reviewers(self):
        task = {'risk_tags': ['ai']}
        self.assertEqual(['ai-reviewer', 'security-reviewer'], harness.reviewers(task))


    def test_checkpoint_composes_dependency_into_clean_worktree(self):
        import shutil
        import subprocess

        tasks = [
            {'id': 'T-001', 'title': 'A', 'objective': 'A', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['a.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-002', 'title': 'B', 'objective': 'B', 'role': 'builder', 'depends_on': ['T-001'],
             'allowed_paths': ['b.txt'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
            {'id': 'T-900', 'title': 'E', 'objective': 'E', 'role': 'evaluator', 'depends_on': ['T-001', 'T-002'],
             'allowed_paths': ['docs/specs/TST-001/evidence/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['true']},
        ]
        feature = self.feature(tasks)
        doc = harness.load_json(feature / 'tasks.json')
        (self.root / 'AGENTS.md').write_text('# agents\n')
        (self.root / 'docs' / 'agentic-sdd').mkdir(parents=True, exist_ok=True)
        (self.root / 'docs' / 'agentic-sdd' / 'constitution.md').write_text('# constitution\n')
        (self.root / 'tooling' / 'agent-harness').mkdir(parents=True, exist_ok=True)
        (self.root / 'tooling' / 'agent-harness' / 'harness.py').write_text('# stub in worktree\n')
        (self.root / '.gitignore').write_text('.agent-state/\ndocs/specs/*/packets/\n')
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run([
            'git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
            'commit', '-q', '-m', 'base'
        ], cwd=self.root, check=True)

        start = argparse.Namespace(feature_dir=feature, task_id='T-001', owner='worker-a')
        harness.cmd_start(start)
        t1 = harness.worktree_path('TST-001', 'T-001')
        (t1 / 'a.txt').write_text('from dependency\n')
        checkpoint = harness.checkpoint_worktree(
            doc, harness.active_task_contract(feature, doc, 'T-001'), t1)
        evidence = self.root / 't1-result.json'
        state = harness.load_state(feature, doc)
        evidence.write_text(json.dumps(self.passing_completion_evidence(
            feature, doc, 'T-001', state['tasks']['T-001']['attempts'], checkpoint)))
        harness.cmd_complete(argparse.Namespace(
            feature_dir=feature, task_id='T-001', owner='worker-a', evidence=str(evidence)
        ))

        harness.cmd_worktree_create(argparse.Namespace(feature_dir=feature, task_id='T-002'))
        t2 = harness.worktree_path('TST-001', 'T-002')
        self.assertEqual('from dependency\n', (t2 / 'a.txt').read_text())
        self.assertEqual([], harness.changed_paths(t2))

        # Explicitly remove external worktrees before the TemporaryDirectory is torn down.
        subprocess.run(['git', 'worktree', 'remove', '--force', str(t2)], cwd=self.root, check=True)
        subprocess.run(['git', 'worktree', 'remove', '--force', str(t1)], cwd=self.root, check=True)
        shutil.rmtree(self.root.parent / f'{self.root.name}{harness.WORKTREE_ROOT_SUFFIX}', ignore_errors=True)

    def test_human_resolution_reopens_escalated_task_with_auditable_retry_grant(self):
        feature = self.feature()
        doc = json.loads((feature / 'tasks.json').read_text())
        state = harness.load_state(feature, doc)
        state['tasks']['T-001'].update({
            'status': 'escalated',
            'attempts': 3,
            'last_failure': 'contract ambiguous',
            'last_failure_evidence': str(self.root / '.agent-runs' / 'TST-001' / 'T-001' / 'result.json'),
        })
        harness.save_state(feature, state)

        harness.cmd_human_resolve(argparse.Namespace(
            feature_dir=feature, task_id='T-001', decision='Use SKU as the stable inventory key.',
            decision_file=None, by='test@example.invalid',
        ))

        updated = harness.load_state(feature, doc)
        entry = updated['tasks']['T-001']
        self.assertEqual('failed', entry['status'])
        self.assertEqual(3, entry['attempts'])
        self.assertEqual(1, entry['human_resume_grants'])
        self.assertEqual('test@example.invalid', entry['human_resolved_by'])
        artifact = pathlib.Path(entry['human_resolution'])
        self.assertTrue(artifact.exists())
        payload = json.loads(artifact.read_text())
        self.assertEqual('retry', payload['action'])
        self.assertEqual('contract ambiguous', payload['prior_reason'])
        self.assertEqual('.agent-runs/TST-001/T-001/result.json', payload['prior_evidence'])
        self.assertEqual('Use SKU as the stable inventory key.', payload['decision'])
        self.assertIn('T-001', harness.ready_ids(doc, updated, feature))

        # The explicit grant permits exactly one start beyond the automatic attempt budget.
        used = harness.consume_attempt_authorization(entry, 'T-001', doc)
        self.assertEqual(str(artifact), used)
        self.assertEqual(0, entry['human_resume_grants'])
        self.assertEqual(str(artifact), entry['active_human_resume'])
        with self.assertRaises(SystemExit):
            harness.consume_attempt_authorization(entry, 'T-001', doc)

    def test_human_resolution_rejects_non_escalated_task(self):
        feature = self.feature()
        with self.assertRaises(SystemExit):
            harness.cmd_human_resolve(argparse.Namespace(
                feature_dir=feature, task_id='T-001', decision='retry', decision_file=None, by='human',
            ))

    def test_rollback_restores_consumed_human_resume_grant(self):
        entry = {'status': 'running', 'attempts': 3, 'human_resume_grants': 0, 'active_human_resume': 'resolution.json'}
        harness.restore_attempt_authorization(entry)
        self.assertEqual(1, entry['human_resume_grants'])
        self.assertNotIn('active_human_resume', entry)


    def test_reopen_refuses_running_descendant_before_pruning_workspace(self):
        from unittest import mock
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.load_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'completed', 'attempts': 1})
        state['tasks']['T-900'].update({
            'status': 'running',
            'attempts': 1,
            'owner': 'live-worker',
            'heartbeat_at': harness.utc_now().isoformat(),
            'lease_expires_at': (harness.utc_now() + harness.dt.timedelta(minutes=5)).isoformat(),
        })
        harness.save_state(feature, state)

        with mock.patch.object(harness, 'prune_task_workspace') as prune:
            with self.assertRaises(SystemExit):
                harness.cmd_reopen(argparse.Namespace(
                    feature_dir=feature,
                    task_id='T-001',
                    reason='evaluation failed',
                    evidence=None,
                ))
            prune.assert_not_called()

        updated = harness.load_state(feature, doc)
        self.assertEqual('completed', updated['tasks']['T-001']['status'])
        self.assertEqual('running', updated['tasks']['T-900']['status'])
        self.assertEqual('live-worker', updated['tasks']['T-900']['owner'])

    def test_reopen_archives_invalidated_descendant_attempt_history(self):
        from unittest import mock
        feature = self.feature()
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.load_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'completed', 'attempts': 2})
        state['tasks']['T-900'].update({
            'status': 'completed',
            'attempts': 3,
            'last_attempt_commit': 'deadbeef',
            'completion_evidence': 'evidence/result.json',
        })
        harness.save_state(feature, state)

        with mock.patch.object(harness, 'prune_task_workspace') as prune:
            harness.cmd_reopen(argparse.Namespace(
                feature_dir=feature,
                task_id='T-001',
                reason='evaluation failed',
                evidence='evidence/evaluator.json',
            ))
            prune.assert_called_once_with('TST-001', 'T-900')

        updated = harness.load_state(feature, doc)
        target = updated['tasks']['T-001']
        descendant = updated['tasks']['T-900']
        self.assertEqual('failed', target['status'])
        self.assertEqual(2, target['attempts'])
        self.assertEqual('pending', descendant['status'])
        self.assertEqual(0, descendant['attempts'])
        self.assertEqual('T-001', descendant['invalidated_by'])
        self.assertEqual(1, len(descendant['attempt_history']))
        archived = descendant['attempt_history'][0]
        self.assertEqual('completed', archived['prior_status'])
        self.assertEqual(3, archived['attempts'])
        self.assertEqual('deadbeef', archived['last_attempt_commit'])
        self.assertEqual('evidence/result.json', archived['completion_evidence'])


    def test_evidence_contract(self):
        evidence = self.root / 'result.json'
        evidence.write_text(json.dumps({
            'status': 'pass', 'summary': 'ok', 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': []
        }))
        self.assertEqual([], harness.validate_evidence(evidence))

    def test_completion_evidence_requires_pass_status(self):
        evidence = self.root / 'result.json'
        evidence.write_text(json.dumps({
            'status': 'needs-human', 'summary': 'ambiguous', 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': ['spec ambiguity']
        }))
        errors = harness.validate_evidence(evidence, require_pass=True)
        self.assertTrue(any('status=pass' in error for error in errors))

    def test_completion_evidence_must_be_json(self):
        evidence = self.root / 'result.md'
        evidence.write_text('# looks good\n')
        self.assertTrue(harness.validate_evidence(evidence, require_pass=True))


class CompletionCorrectionTest(unittest.TestCase):
    """M4 RED-first coverage for completion evidence and exhausted repair."""

    setUp = HarnessTest.setUp
    tearDown = HarnessTest.tearDown
    feature = HarnessTest.feature

    def completion_fixture(self, *, status='running', attempts=1):
        vc_doc = {
            'status': 'pass', 'summary': 'fixture verification contract',
            'criteria': [{
                'id': 'VC-009', 'statement': 'Retry controls are established.',
                'origin': 'spec-derived', 'source_type': 'spec',
                'sources': ['fixture spec'], 'verification_hint': 'Exercise retry controls.',
            }],
            'exemptions': [], 'assumptions': [],
        }
        tasks = [
            {'id': 'T-A', 'title': 'A', 'objective': 'Build A', 'role': 'builder', 'depends_on': [],
             'allowed_paths': ['src/a/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001', 'VC-009'],
             'verification': ['python3 -m unittest test_retry_controls'], 'test_mode': 'red-green-refactor',
             'test_seam': 'test the retry-control proof'},
            {'id': 'T-B', 'title': 'B', 'objective': 'Build B', 'role': 'builder', 'depends_on': ['T-A'],
             'allowed_paths': ['src/b/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'],
             'verification': ['python3 -m unittest test_dependency'], 'test_mode': 'red-green-refactor',
             'test_seam': 'test dependency blocking'},
            {'id': 'T-900', 'title': 'Evaluate', 'objective': 'Evaluate', 'role': 'evaluator',
             'depends_on': ['T-A', 'T-B'], 'allowed_paths': ['evidence/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001', 'VC-009'], 'verification': ['python3 -m unittest'],
             'test_mode': 'existing-suite', 'test_seam': 'evaluate all fixture criteria'},
        ]
        feature = self.feature(tasks, spec_text='# TST-001\n\n- AC-001: completion is validated\n')
        constitution = self.root / 'docs/agentic-sdd/constitution.md'
        constitution.parent.mkdir(parents=True, exist_ok=True)
        constitution.write_text('# fixture constitution\n')
        vc_doc.update({
            'schema_version': 1, 'feature': feature.name, 'status': 'accepted',
            'inputs': {
                'spec_sha256': harness.sha256_bytes((feature / 'spec.md').read_bytes()),
                'plan_sha256': harness.sha256_bytes((feature / 'plan.md').read_bytes()),
                'constitution_sha256': harness.sha256_bytes(
                    (harness.vc.REPO / 'docs/agentic-sdd/constitution.md').read_bytes()),
            },
        })
        (feature / 'verification-contract.json').write_text(json.dumps(vc_doc))
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        harness.write_packet(doc, harness.task_index(doc)['T-A'], feature, state=state)
        entry = state['tasks']['T-A']
        entry.update({'status': status, 'attempts': attempts, 'owner': 'worker-a',
                      'checkpoint_commit': subprocess.check_output(
                          ['git', 'rev-parse', 'HEAD'], cwd=self.root, text=True).strip()})
        harness.save_state(feature, state)
        return feature, doc

    def evidence_doc(self, feature, doc, *, mutate=None):
        state = harness.load_state(feature, doc)
        entry = state['tasks']['T-A']
        active = harness.resolve_active_packet(feature, doc, 'T-A', state=state)
        criteria = harness.active_task_contract(feature, doc, 'T-A', state=state)['acceptance_criteria']
        command_text = 'python3 -m unittest test_retry_controls'
        output_dir = self.root / '.agent-runs' / 'T-A' / 'verify'
        output_dir.mkdir(parents=True, exist_ok=True)
        stdout, stderr = output_dir / 'stdout.log', output_dir / 'stderr.log'
        stdout.write_text('fixture command passed\n', encoding='utf-8')
        stderr.write_text('', encoding='utf-8')
        verification = {'command': command_text, 'exit_code': 0, 'stdout': str(stdout), 'stderr': str(stderr),
                        'sandbox_backend': 'deterministic-test-backend', 'strong_isolation': True}
        receipt = {'command': command_text, 'exit_code': 0,
                   'stdout_sha256': harness.sha256_bytes(stdout.read_bytes()),
                   'stderr_sha256': harness.sha256_bytes(stderr.read_bytes()),
                   'sandbox_backend': verification['sandbox_backend'], 'strong_isolation': True}
        proof_id = 'verify-1'
        proof = {'proof_id': proof_id, 'command_index': 0,
                 'command_sha256': harness.sha256_bytes(command_text.encode()),
                 'status': 'PASS', 'exit_code': 0,
                 'result_sha256': harness.canonical_json_sha256(receipt), 'criteria': list(criteria)}
        results = {criterion: {'status': 'PASS', 'proof_ids': [proof_id]} for criterion in criteria}
        evidence = {
            'schema_version': 1, 'status': 'pass', 'summary': 'fixture passed',
            'repository': str(harness.git_common_dir(feature)), 'feature': 'TST-001', 'task': 'T-A',
            'attempt': entry['attempts'], 'checkpoint': entry['checkpoint_commit'],
            'packet_revision': active['revision_id'], 'contract_fingerprint': active['contract_sha256'],
            'commands': [command_text], 'harness_verification': [verification],
            'proofs': [proof], 'criterion_results': results,
            'changed_paths': [], 'assumptions': [], 'residual_risks': [],
        }
        if mutate:
            mutate(evidence)
        return evidence

    def write_evidence(self, evidence):
        path = self.root / 'completion-evidence.json'
        path.write_text(json.dumps(evidence), encoding='utf-8')
        return path

    def test_m4_01_missing_required_vc_rejects_completion_red(self):
        feature, doc = self.completion_fixture()
        evidence = self.evidence_doc(feature, doc, mutate=lambda e: e['criterion_results'].pop('VC-009'))
        path = self.write_evidence(evidence)
        with self.assertRaises(SystemExit):
            harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a', evidence=str(path)))

    def test_m4_02_vc_named_but_incomplete_proof_rejects_completion_red(self):
        feature, doc = self.completion_fixture()
        def incomplete(e):
            e['criterion_results']['VC-009'] = {'status': 'INCOMPLETE', 'proof_ids': []}
        path = self.write_evidence(self.evidence_doc(feature, doc, mutate=incomplete))
        with self.assertRaises(SystemExit):
            harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a', evidence=str(path)))

    def test_m4_03_complete_required_vc_proof_accepts_completion(self):
        feature, doc = self.completion_fixture()
        path = self.write_evidence(self.evidence_doc(feature, doc))
        harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a', evidence=str(path)))
        self.assertEqual('completed', harness.load_state(feature, doc)['tasks']['T-A']['status'])

    def test_m4_04_missing_required_ac_rejects_completion_red(self):
        feature, doc = self.completion_fixture()
        def missing(e):
            e['criterion_results'].pop('AC-001')
            e['proofs'][0]['criteria'].remove('AC-001')
        path = self.write_evidence(self.evidence_doc(feature, doc, mutate=missing))
        with self.assertRaises(SystemExit):
            harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a', evidence=str(path)))

    def _assert_binding_rejected(self, mutate):
        import shutil
        shutil.rmtree(self.root / 'docs/specs/TST-001', ignore_errors=True)
        shutil.rmtree(self.root / '.agent-state', ignore_errors=True)
        feature, doc = self.completion_fixture()
        path = self.write_evidence(self.evidence_doc(feature, doc, mutate=mutate))
        with self.assertRaises(SystemExit):
            harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a', evidence=str(path)))

    def test_m4_05_stale_revision_rejects_completion(self):
        self._assert_binding_rejected(lambda e: e.update(packet_revision='sha256:' + '0' * 64))

    def test_m4_06_stale_fingerprint_rejects_completion(self):
        self._assert_binding_rejected(lambda e: e.update(contract_fingerprint='0' * 64))

    def test_m4_07_wrong_attempt_rejects_completion(self):
        self._assert_binding_rejected(lambda e: e.update(attempt=2))

    def test_m4_08_wrong_checkpoint_rejects_completion(self):
        self._assert_binding_rejected(lambda e: e.update(checkpoint='f' * 40))

    def test_m4_02_string_only_vc_claim_without_command_proof_rejects_red(self):
        feature, doc = self.completion_fixture()
        def no_command_proof(e):
            e['criterion_results']['VC-009'] = {'status': 'PASS', 'proof_ids': []}
        path = self.write_evidence(self.evidence_doc(feature, doc, mutate=no_command_proof))
        with self.assertRaises(SystemExit):
            harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a', evidence=str(path)))

    def test_m4_02_claimed_result_hash_must_match_harness_output(self):
        feature, doc = self.completion_fixture()
        path = self.write_evidence(self.evidence_doc(
            feature, doc, mutate=lambda e: e['proofs'][0].update(result_sha256='a' * 64)))
        with self.assertRaises(SystemExit):
            harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='worker-a', evidence=str(path)))

    def _completed_fixture(self):
        feature, doc = self.completion_fixture(status='completed', attempts=5)
        state = harness.load_state(feature, doc)
        entry = state['tasks']['T-A']
        entry.update({'completed_at': '2026-01-01T00:00:00+00:00', 'evidence': 'old-evidence.json'})
        harness.save_state(feature, state)
        active = harness.resolve_active_packet(feature, doc, 'T-A', state=state)
        binding = {
            'schema_version': 1, 'binding_version': 1,
            'binding_type': 'historical-legacy-completion',
            'repository_id': str(harness.git_common_dir(feature)), 'feature': feature.name,
            'task': 'T-A', 'attempt': 5, 'historical_status': 'completed',
            'attempt_identity': harness.legacy_attempt_identity(feature, 'T-A', entry),
            'checkpoint': entry['checkpoint_commit'], 'packet_revision': active['revision_id'],
            'contract_fingerprint': active['contract_sha256'], 'evidence_sha256': 'b' * 64,
            'evidence_checkpoint': entry['checkpoint_commit'],
            'source_material': [{'identity': 'fixture:legacy-evidence', 'sha256': 'c' * 64, 'immutable': True}],
            'mode': 'HUMAN_ATTESTED', 'operator': 'fixture operator', 'reason': 'fixture history binding',
            'attested_fields': {'checkpoint': entry['checkpoint_commit'], 'packet_revision': active['revision_id'],
                                'contract_fingerprint': active['contract_sha256'],
                                'repository': str(harness.git_common_dir(feature)), 'feature': feature.name,
                                'task': 'T-A', 'attempt': 5},
            'created_at': '2026-01-02T00:00:00+00:00',
        }
        binding['binding_id'] = harness.legacy_completion_binding_id(binding)
        harness.publish_legacy_binding(feature, binding)
        return feature, doc

    def _correct(self, feature, *, reason='VC-009 proof missing'):
        defect = self.root / 'defect.json'
        defect.write_text('{"finding":"VC-009 proof missing"}', encoding='utf-8')
        args = argparse.Namespace(feature_dir=feature, task_id='T-A',
                                  reason_code=harness.COMPLETION_CORRECTION_REASON,
                                  reason=reason, evidence=str(defect), by='master-review')
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_correct_completion(args)
        return harness.correction_records(feature, 'T-A')[0]

    def _authorize_repair(self, feature, correction, *, reason='repair VC-009 evidence'):
        args = argparse.Namespace(feature_dir=feature, task_id='T-A', correction_id=correction['record_id'],
                                  reason=reason, by='master-review')
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_authorize_completion_repair(args)
        return harness.repair_authorization_records(feature, 'T-A')[0]

    def _claim_repair(self, feature, authorization, *, owner='repair-worker'):
        args = argparse.Namespace(feature_dir=feature, task_id='T-A', authorization=authorization['record_id'],
                                  owner=owner)
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_claim_completion_repair(args)

    def _complete_repair(self, feature, doc, owner='repair-worker', *, mutate=None):
        evidence = self.evidence_doc(feature, doc, mutate=mutate)
        path = self.write_evidence(evidence)
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner=owner,
                                                    evidence=str(path)))
        return harness.load_state(feature, doc)

    def test_m4_09_correction_preserves_historical_completion(self):
        feature, doc = self._completed_fixture()
        before = harness.completion_record_for_entry(
            feature, 'T-A', harness.load_state(feature, doc)['tasks']['T-A'],
            harness.resolve_active_packet(feature, doc, 'T-A'))
        correction = self._correct(feature)
        self.assertEqual(before['record_id'], correction['original_completion_id'])
        self.assertEqual(1, len(harness.completion_records(feature, 'T-A')))

    def test_m4_10_correction_changes_effective_state(self):
        feature, doc = self._completed_fixture()
        self._correct(feature)
        state = harness.load_state(feature, doc)
        self.assertEqual('correction_required', harness.effective_task_status(feature, 'T-A', state['tasks']['T-A']))

    def test_m4_11_correction_does_not_increment_attempts(self):
        feature, doc = self._completed_fixture()
        self._correct(feature)
        self.assertEqual(5, harness.load_state(feature, doc)['tasks']['T-A']['attempts'])

    def test_m4_12_correction_preserves_retry_authorizations(self):
        feature, doc = self._completed_fixture()
        state = harness.load_state(feature, doc)
        state['tasks']['T-A']['retry_authorizations'] = [{'id': 'historical', 'consumed': True}]
        harness.save_state(feature, state)
        self._correct(feature)
        self.assertEqual([{'id': 'historical', 'consumed': True}],
                         harness.load_state(feature, doc)['tasks']['T-A']['retry_authorizations'])

    def test_m4_13_exact_correction_replay_is_idempotent(self):
        feature, _ = self._completed_fixture()
        self._correct(feature)
        first = harness.correction_records(feature, 'T-A')[0]
        self._correct(feature)
        self.assertEqual([first], harness.correction_records(feature, 'T-A'))

    def test_m4_14_conflicting_correction_is_rejected(self):
        feature, _ = self._completed_fixture()
        self._correct(feature)
        with self.assertRaises(SystemExit):
            self._correct(feature, reason='different reason')

    def test_m4_15_corrected_completion_blocks_dependent(self):
        feature, doc = self._completed_fixture()
        self._correct(feature)
        self.assertNotIn('T-B', harness.ready_ids(doc, harness.load_state(feature, doc), feature))

    def test_m4_16_corrected_task_cannot_use_ordinary_claim(self):
        feature, doc = self._completed_fixture()
        self._correct(feature)
        with self.assertRaises(SystemExit):
            harness.cmd_claim(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='ordinary'))

    def test_m4_17_repair_requires_explicit_authorization(self):
        feature, _ = self._completed_fixture()
        correction = self._correct(feature)
        with self.assertRaises(SystemExit):
            self._claim_repair(feature, {'record_id': 'missing'})
        self.assertEqual([], harness.repair_authorization_records(feature, 'T-A'))

    def test_m4_18_repair_authorization_is_exactly_scoped(self):
        feature, _ = self._completed_fixture()
        correction = self._correct(feature)
        authorization = self._authorize_repair(feature, correction)
        changed = dict(authorization)
        changed['attempt'] = 4
        self.assertNotEqual(authorization['record_id'], harness.completion_record_id(
            'completion-repair-authorization', changed))
        self.assertEqual(correction['record_id'], authorization['correction_id'])
        changed['task'] = 'T-B'
        changed['record_id'] = harness.completion_record_id('completion-repair-authorization', changed)
        changed['created_at'] = harness.utc_now().isoformat()
        changed_path = harness.completion_authority_dir(
            feature, 'completion-repair-authorizations', 'T-A') / f"{changed['record_id'].rsplit(':', 1)[-1]}.json"
        harness.publish_completion_authority(changed_path, changed)
        with self.assertRaises(SystemExit):
            harness.repair_authorization_records(feature, 'T-A')

    def test_m4_19_repair_claim_preserves_attempt(self):
        feature, doc = self._completed_fixture()
        correction = self._correct(feature)
        authorization = self._authorize_repair(feature, correction)
        self._claim_repair(feature, authorization)
        self.assertEqual(5, harness.load_state(feature, doc)['tasks']['T-A']['attempts'])

    def test_m4_20_fresh_module_reconstructs_correction_and_authorization(self):
        feature, _ = self._completed_fixture()
        correction = self._correct(feature)
        authorization = self._authorize_repair(feature, correction)
        harness._completion_fault_injector = lambda boundary: (_ for _ in ()).throw(RuntimeError('crash')) \
            if boundary == 'repair-claim-published' else None
        try:
            with self.assertRaises(RuntimeError):
                self._claim_repair(feature, authorization, owner='fresh-owner')
        finally:
            harness._completion_fault_injector = None
        result = subprocess.run([
            os.sys.executable, str(MODULE_PATH), 'claim-completion-repair', str(feature), 'T-A',
            '--authorization', authorization['record_id'], '--owner', 'fresh-owner',
        ], cwd=self.root, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        state = harness.load_state(feature, harness.load_validated(feature))
        self.assertEqual('running', state['tasks']['T-A']['status'])
        self.assertEqual(5, state['tasks']['T-A']['attempts'])

    def test_m4_21_repaired_completion_requires_complete_coverage(self):
        feature, doc = self._completed_fixture()
        correction = self._correct(feature)
        authorization = self._authorize_repair(feature, correction)
        self._claim_repair(feature, authorization)
        with self.assertRaises(SystemExit):
            self._complete_repair(feature, doc, mutate=lambda e: e['criterion_results'].pop('VC-009'))

    def test_m4_22_repaired_completion_preserves_c1_and_k1(self):
        feature, doc = self._completed_fixture()
        correction = self._correct(feature)
        c1 = harness.completion_records(feature, 'T-A')[0]
        authorization = self._authorize_repair(feature, correction)
        self._claim_repair(feature, authorization)
        self._complete_repair(feature, doc)
        records = harness.completion_records(feature, 'T-A')
        self.assertIn(c1, records)
        self.assertIn(correction, harness.correction_records(feature, 'T-A'))
        self.assertEqual(2, len(records))

    def test_m4_23_repaired_completion_restores_dependency_satisfaction(self):
        feature, doc = self._completed_fixture()
        correction = self._correct(feature)
        authorization = self._authorize_repair(feature, correction)
        self._claim_repair(feature, authorization)
        self._complete_repair(feature, doc)
        self.assertIn('T-B', harness.ready_ids(doc, harness.load_state(feature, doc), feature))

    def test_m4_24_stale_repair_authorization_rejected(self):
        feature, _ = self._completed_fixture()
        correction = self._correct(feature)
        authorization = self._authorize_repair(feature, correction)
        stale = dict(authorization)
        stale['contract_fingerprint'] = '0' * 64
        stale['record_id'] = harness.completion_record_id('completion-repair-authorization', stale)
        stale['created_at'] = harness.utc_now().isoformat()
        stale_path = harness.completion_authority_dir(
            feature, 'completion-repair-authorizations', 'T-A') / f"{stale['record_id'].rsplit(':', 1)[-1]}.json"
        harness.publish_completion_authority(stale_path, stale)
        with self.assertRaises(SystemExit):
            self._claim_repair(feature, stale)

    def test_m4_25_concurrent_corrections_have_one_authority_result(self):
        feature, _ = self._completed_fixture()
        barrier = threading.Barrier(2)
        outcomes = []
        def run(reason):
            barrier.wait()
            try:
                outcomes.append(('ok', self._correct(feature, reason=reason)['record_id']))
            except SystemExit:
                outcomes.append(('rejected', reason))
        threads = [threading.Thread(target=run, args=(reason,)) for reason in ('reason-a', 'reason-b')]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        self.assertEqual(1, sum(kind == 'ok' for kind, _ in outcomes))
        self.assertEqual(1, len(harness.correction_records(feature, 'T-A')))

    def test_m4_26_concurrent_repair_claim_has_one_owner(self):
        feature, _ = self._completed_fixture()
        correction = self._correct(feature)
        authorization = self._authorize_repair(feature, correction)
        barrier = threading.Barrier(2)
        outcomes = []
        def run(owner):
            barrier.wait()
            try:
                self._claim_repair(feature, authorization, owner=owner)
                outcomes.append(('ok', owner))
            except SystemExit:
                outcomes.append(('rejected', owner))
        threads = [threading.Thread(target=run, args=(owner,)) for owner in ('owner-a', 'owner-b')]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        self.assertEqual(1, sum(kind == 'ok' for kind, _ in outcomes))

    def test_m4_27_cross_worktree_uses_repository_authority(self):
        import shutil
        feature, doc = self._completed_fixture()
        correction = self._correct(feature)
        linked = self.root / 'linked-fixture'
        subprocess.run(['git', 'worktree', 'add', '--detach', str(linked), 'HEAD'], cwd=self.root, check=True,
                       stdout=subprocess.DEVNULL)
        try:
            linked_feature = linked / 'docs/specs/TST-001'
            linked_feature.mkdir(parents=True)
            for name in ('spec.md', 'plan.md', 'tasks.json', 'verification-contract.json'):
                shutil.copy2(feature / name, linked_feature / name)
            self.assertEqual(harness.git_common_dir(feature), harness.git_common_dir(linked_feature))
            self.assertEqual(harness.state_path(feature), harness.state_path(linked_feature))
            self.assertEqual(correction['record_id'], harness.correction_records(linked_feature, 'T-A')[0]['record_id'])
            linked_state = harness.load_state(linked_feature, doc)
            self.assertEqual('correction_required', harness.effective_task_status(
                linked_feature, 'T-A', linked_state['tasks']['T-A']))
            self.assertEqual(harness.ready_ids(doc, harness.load_state(feature, doc), feature),
                             harness.ready_ids(doc, linked_state, linked_feature))
        finally:
            subprocess.run(['git', 'worktree', 'remove', '--force', str(linked)], cwd=self.root, check=True,
                           stdout=subprocess.DEVNULL)

    def test_m4_28_crash_after_correction_publication_blocks_dependency(self):
        feature, doc = self._completed_fixture()
        harness._completion_fault_injector = lambda boundary: (_ for _ in ()).throw(RuntimeError('crash')) \
            if boundary == 'correction-published' else None
        try:
            with self.assertRaises(RuntimeError): self._correct(feature)
        finally:
            harness._completion_fault_injector = None
        self.assertNotIn('T-B', harness.ready_ids(doc, harness.load_state(feature, doc), feature))

    def test_m4_29_crash_after_c2_publication_reconstructs_completion(self):
        feature, doc = self._completed_fixture()
        correction = self._correct(feature)
        authorization = self._authorize_repair(feature, correction)
        self._claim_repair(feature, authorization)
        harness._completion_fault_injector = lambda boundary: (_ for _ in ()).throw(RuntimeError('crash')) \
            if boundary == 'completion-published' else None
        try:
            evidence = self.write_evidence(self.evidence_doc(feature, doc))
            with self.assertRaises(RuntimeError):
                harness.cmd_complete(argparse.Namespace(feature_dir=feature, task_id='T-A', owner='repair-worker',
                                                        evidence=str(evidence)))
        finally:
            harness._completion_fault_injector = None
        fresh_state = harness.load_state(feature, doc)
        self.assertTrue(harness.task_has_effective_completion(feature, 'T-A', fresh_state['tasks']['T-A']))

    def test_m4_30_repair_flow_never_creates_attempt_six(self):
        feature, doc = self._completed_fixture()
        correction = self._correct(feature)
        authorization = self._authorize_repair(feature, correction)
        self._claim_repair(feature, authorization)
        state = self._complete_repair(feature, doc)
        self.assertEqual(5, state['tasks']['T-A']['attempts'])


class LegacyCompletionBindingTests(unittest.TestCase):
    """M4.1 fixture-only coverage; never reads or writes the real feature state."""

    setUp = HarnessTest.setUp
    tearDown = HarnessTest.tearDown
    feature = HarnessTest.feature

    def legacy_fixture(self, *, checkpoint=None, dependent=False):
        tasks = None
        feature = self.feature(tasks)
        doc = harness.load_json(feature / 'tasks.json')
        state = harness.initial_state(feature, doc)
        task = harness.task_index(doc)['T-001']
        harness.write_packet(doc, task, feature, state=state)
        entry = state['tasks']['T-001']
        entry.update({'status': 'completed', 'attempts': 1, 'owner': None,
                      'checkpoint_commit': checkpoint, 'claimed_at': '2026-01-01T00:00:00Z',
                      'completed_at': '2026-01-02T00:00:00Z', 'evidence': 'legacy-evidence.json'})
        harness.save_state(feature, state)
        active = harness.resolve_active_packet(feature, doc, 'T-001', state=state)
        cp = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.root, text=True).strip()
        evidence = {'schema_version': 1, 'status': 'pass', 'repository': str(harness.git_common_dir(feature)),
                    'feature': feature.name, 'task': 'T-001', 'attempt': 1, 'checkpoint': cp,
                    'packet_revision': active['revision_id'],
                    'contract_fingerprint': active['contract_sha256'], 'changed_paths': []}
        evidence_path = self.root / 'legacy-evidence.json'
        evidence_path.write_text(json.dumps(evidence), encoding='utf-8')
        return feature, doc, evidence_path, evidence, active

    def _sources(self, feature, evidence_path, evidence):
        root = harness.runtime_state_dir(feature) / 'legacy-completion-sources' / feature.name / 'T-001'
        root.mkdir(parents=True, exist_ok=True)
        evidence_hash = harness.sha256_bytes(evidence_path.read_bytes())
        entry = harness.load_state(feature, harness.load_json(feature / 'tasks.json'))['tasks']['T-001']
        for kind in ('checkpoint-record', 'attempt-finalization'):
            source = {'kind': kind, 'repository_id': str(harness.git_common_dir(feature)),
                      'feature': feature.name, 'task': 'T-001', 'attempt': 1,
                      'checkpoint': evidence['checkpoint'], 'evidence_sha256': evidence_hash,
                      'packet_revision': evidence['packet_revision'],
                      'contract_fingerprint': evidence['contract_fingerprint'],
                      'attempt_identity': harness.legacy_attempt_identity(feature, 'T-001', entry),
                      'historical_status': 'completed'}
            harness.publish_legacy_source(feature, 'T-001', source)

    def _cas(self, feature, doc, evidence_path, evidence):
        state = harness._read_state_unlocked_pure(feature, doc)
        entry = state['tasks']['T-001']
        active = harness.resolve_active_packet(feature, doc, 'T-001', state=state)
        return {'stored_status': 'completed', 'attempts': entry['attempts'],
                'attempt_identity': harness.legacy_attempt_identity(feature, 'T-001', entry),
                'canonical_completion_absent': True, 'legacy_checkpoint': entry.get('checkpoint_commit'),
                'packet_revision': active['revision_id'], 'contract_fingerprint': active['contract_sha256'],
                'evidence_sha256': harness.sha256_bytes(evidence_path.read_bytes()),
                'evidence_checkpoint': evidence['checkpoint'],
                'repository_id': str(harness.git_common_dir(feature)), 'feature': feature.name, 'task': 'T-001'}

    def _attest(self, feature, doc, evidence_path, evidence, *, expected=None, operator='reviewer', reason='historical review'):
        args = argparse.Namespace(feature_dir=feature, task_id='T-001', evidence=str(evidence_path),
                                  expected=expected or self._cas(feature, doc, evidence_path, evidence),
                                  attested_fields=json.dumps({'checkpoint': evidence['checkpoint'],
                                                              'packet_revision': evidence['packet_revision'],
                                                              'contract_fingerprint': evidence['contract_fingerprint'],
                                                              'repository': str(harness.git_common_dir(feature)),
                                                              'feature': feature.name, 'task': 'T-001', 'attempt': 1}),
                                  sources=json.dumps([{'identity': 'archive/evidence.json',
                                                      'sha256': harness.sha256_bytes(evidence_path.read_bytes()),
                                                      'immutable': True}]),
                                  operator=operator, reason=reason)
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_attest_legacy_completion(args)

    def _attestation_cli(self, feature, doc, path, evidence, operator='reviewer', reason='historical review'):
        fields = {'checkpoint': evidence['checkpoint'], 'packet_revision': evidence['packet_revision'],
                  'contract_fingerprint': evidence['contract_fingerprint'],
                  'repository': str(harness.git_common_dir(feature)), 'feature': feature.name,
                  'task': 'T-001', 'attempt': 1}
        sources = [{'identity': 'archive/evidence.json',
                    'sha256': harness.sha256_bytes(path.read_bytes()), 'immutable': True}]
        return [os.sys.executable, str(MODULE_PATH), 'attest-legacy-completion', str(feature), 'T-001',
                '--evidence', str(path), '--expected-cas', json.dumps(self._cas(feature, doc, path, evidence)),
                '--attested-fields', json.dumps(fields), '--sources', json.dumps(sources),
                '--operator', operator, '--reason', reason]

    def test_m4_1_01_legacy_completed_without_c1_detected(self):
        feature, doc, evidence_path, evidence, _ = self.legacy_fixture()
        self.assertEqual([], harness.read_completion_authority(feature, 'completion-records', 'T-001', record_type='completion'))
        self.assertEqual('HUMAN_ATTESTATION_REQUIRED', harness.classify_legacy_completion(feature, doc, 'T-001')[0])

    def test_m4_1_02_missing_checkpoint_does_not_copy_evidence_checkpoint(self):
        feature, doc, evidence_path, evidence, _ = self.legacy_fixture()
        result, binding, reasons = harness.classify_legacy_completion(feature, doc, 'T-001')
        self.assertEqual('HUMAN_ATTESTATION_REQUIRED', result)
        self.assertIsNone(binding)
        self.assertTrue(any('immutable historical records' in reason for reason in reasons))

    def test_m4_1_03_sufficient_immutable_corroboration_auto_verified(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        self._sources(feature, path, evidence)
        result, binding, _ = harness.classify_legacy_completion(feature, doc, 'T-001')
        self.assertEqual('AUTO_VERIFIED', result)
        self.assertEqual('AUTO_VERIFIED', binding['mode'])

    def test_m4_1_04_insufficient_corroboration_requires_human(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        self._attest(feature, doc, path, evidence)
        binding = harness.legacy_completion_bindings(feature, 'T-001')[0]
        self.assertEqual('HUMAN_ATTESTED', binding['mode'])

    def test_m4_1_05_wrong_evidence_task_rejected(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        evidence['task'] = 'T-900'; path.write_text(json.dumps(evidence))
        self.assertEqual('CONFLICT', harness.classify_legacy_completion(feature, doc, 'T-001')[0])

    def test_m4_1_06_wrong_repository_rejected(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        evidence['repository'] = '/wrong/repo'; path.write_text(json.dumps(evidence))
        self.assertEqual('CONFLICT', harness.classify_legacy_completion(feature, doc, 'T-001')[0])

    def test_m4_1_07_wrong_attempt_rejected(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        evidence['attempt'] = 2; path.write_text(json.dumps(evidence))
        self.assertEqual('CONFLICT', harness.classify_legacy_completion(feature, doc, 'T-001')[0])

    def test_m4_1_08_conflicting_checkpoint_rejected(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(checkpoint='f' * 40)
        self.assertEqual('CONFLICT', harness.classify_legacy_completion(feature, doc, 'T-001')[0])

    def test_m4_1_09_conflicting_evidence_hash_rejected(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        self._sources(feature, path, evidence)
        evidence['summary'] = 'changed after publication'; path.write_text(json.dumps(evidence))
        self.assertEqual('CONFLICT', harness.classify_legacy_completion(feature, doc, 'T-001')[0])

    def test_m4_1_10_stale_revision_rejected(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        evidence['packet_revision'] = 'sha256:' + 'f' * 64; path.write_text(json.dumps(evidence))
        self.assertEqual('CONFLICT', harness.classify_legacy_completion(feature, doc, 'T-001')[0])

    def test_m4_1_11_stale_fingerprint_rejected(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        evidence['contract_fingerprint'] = 'f' * 64; path.write_text(json.dumps(evidence))
        self.assertEqual('CONFLICT', harness.classify_legacy_completion(feature, doc, 'T-001')[0])

    def test_m4_1_12_attestation_requires_operator(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            self._attest(feature, doc, path, evidence, operator=' ')

    def test_m4_1_13_attestation_requires_reason(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            self._attest(feature, doc, path, evidence, reason=' ')

    def test_m4_1_14_attestation_cas_preconditions(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        expected = self._cas(feature, doc, path, evidence); expected['attempts'] = 7
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            self._attest(feature, doc, path, evidence, expected=expected)

    def test_m4_1_15_immutable_b1_publication(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        binding = harness.legacy_completion_bindings(feature, 'T-001')[0]
        with self.assertRaises(SystemExit): harness.publish_legacy_binding(feature, {**binding, 'reason': 'changed'})

    def test_m4_1_16_exact_replay_idempotent(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        binding = harness.legacy_completion_bindings(feature, 'T-001')[0]
        self.assertFalse(harness.publish_legacy_binding(feature, binding))

    def test_m4_1_17_conflicting_replay_rejected(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        binding = harness.legacy_completion_bindings(feature, 'T-001')[0]
        changed = dict(binding, reason='different attestation')
        changed['binding_id'] = harness.legacy_completion_binding_id(changed)
        with self.assertRaises(SystemExit): harness.publish_legacy_binding(feature, changed)

    def test_m4_1_18_concurrent_identical_binding_safe(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        command = self._attestation_cli(feature, doc, path, evidence)
        workers = [subprocess.Popen(command, cwd=self.root, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                   for _ in range(2)]
        results = [worker.communicate(timeout=20) + (worker.returncode,) for worker in workers]
        self.assertEqual([0, 0], [result[2] for result in results])
        self.assertEqual(1, len(harness.legacy_completion_bindings(feature, 'T-001')))

    def test_m4_1_19_concurrent_conflicting_binding_one_winner(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        commands = [self._attestation_cli(feature, doc, path, evidence, operator=name) for name in ('one', 'two')]
        workers = [subprocess.Popen(command, cwd=self.root, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                   for command in commands]
        results = [worker.communicate(timeout=20) + (worker.returncode,) for worker in workers]
        self.assertCountEqual([0, 2], [result[2] for result in results])
        self.assertEqual(1, len(harness.legacy_completion_bindings(feature, 'T-001')))

    def test_auto_mode_publishes_only_after_classifier_passes(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._sources(feature, path, evidence)
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_bind_legacy_completion_auto(argparse.Namespace(feature_dir=feature, task_id='T-001'))
        binding = harness.legacy_completion_bindings(feature, 'T-001')[0]
        self.assertEqual('AUTO_VERIFIED', binding['mode'])

    def test_auto_failure_does_not_downgrade_to_human_or_publish(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            harness.cmd_bind_legacy_completion_auto(argparse.Namespace(feature_dir=feature, task_id='T-001'))
        self.assertEqual([], harness.legacy_completion_bindings(feature, 'T-001'))

    def test_m4_1_20_fresh_process_c1_reconstruction(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        self.assertFalse((harness.completion_authority_dir(feature, 'completion-records', 'T-001')).exists())
        self.assertNotIn('completion_record_id', harness.load_state(feature, doc)['tasks']['T-001'])
        first = harness.completion_records(feature, 'T-001')[0]['record_id']
        code = ("import importlib.util,pathlib; p=pathlib.Path(%r); s=importlib.util.spec_from_file_location('fresh',p); "
                "m=importlib.util.module_from_spec(s); s.loader.exec_module(m); "
                "f=pathlib.Path(%r); print(m.completion_records(f,'T-001')[0]['record_id'])") % (
                    str(MODULE_PATH), str(feature))
        second = subprocess.check_output([os.sys.executable, '-c', code], cwd=self.root, text=True).strip()
        self.assertEqual(first, second)

    def test_crash_before_durable_b1_publication_leaves_no_authority(self):
        feature, doc, path, evidence, _ = self.legacy_fixture()
        with mock.patch.object(harness.os, 'link', side_effect=OSError('simulated pre-publication crash')):
            with self.assertRaises(OSError):
                self._attest(feature, doc, path, evidence)
        self.assertEqual([], harness.legacy_completion_bindings(feature, 'T-001'))
        binding_dir = harness.completion_authority_dir(feature, 'legacy-completion-bindings', 'T-001')
        self.assertFalse((binding_dir / 'binding.json').exists())

    def test_m4_1_21_cross_worktree_c1_identity_stable(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        subprocess.run(['git', 'add', 'docs/specs/TST-001'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'fixture feature'], cwd=self.root, check=True)
        linked_root = self.root / 'linked'
        subprocess.run(['git', 'worktree', 'add', '--detach', '-q', str(linked_root), 'HEAD'], cwd=self.root, check=True)
        linked_feature = linked_root / 'docs/specs/TST-001'
        stale_local = linked_root / '.agent-state' / 'legacy-completion-bindings' / feature.name / 'T-001'
        stale_local.mkdir(parents=True)
        (stale_local / 'binding.json').write_text('{"unsupported":"stale worktree-local file"}')
        self.assertEqual(harness.completion_records(feature, 'T-001')[0]['record_id'],
                         harness.completion_records(linked_feature, 'T-001')[0]['record_id'])

    def test_m4_1_22_projection_deletion_does_not_remove_binding_or_c1(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        record_id = harness.completion_records(feature, 'T-001')[0]['record_id']
        harness.remove_state_locked(feature)
        self.assertEqual(record_id, harness.completion_records(feature, 'T-001')[0]['record_id'])

    def test_m4_1_23_unsupported_version_fails_closed(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        binding = harness.legacy_completion_bindings(feature, 'T-001')[0]
        binding['binding_version'] = 99
        root = harness.completion_authority_dir(feature, 'legacy-completion-bindings', 'T-001')
        (root / 'binding.json').write_text(json.dumps(binding))
        with self.assertRaises(SystemExit): harness.legacy_completion_bindings(feature, 'T-001')

    def test_classifier_reports_unsupported_future_binding_version(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        binding_path = harness.completion_authority_dir(feature, 'legacy-completion-bindings', 'T-001') / 'binding.json'
        binding = json.loads(binding_path.read_text())
        binding['binding_version'] = 99
        binding_path.write_text(json.dumps(binding))
        self.assertEqual('UNSUPPORTED', harness.classify_legacy_completion(feature, doc, 'T-001')[0])

    def test_m4_1_24_malformed_binding_fails_closed(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        root = harness.completion_authority_dir(feature, 'legacy-completion-bindings', 'T-001')
        (root / 'binding.json').write_text('{')
        with self.assertRaises(SystemExit): harness.legacy_completion_bindings(feature, 'T-001')

    def test_binding_symlink_fails_closed(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        root = harness.completion_authority_dir(feature, 'legacy-completion-bindings', 'T-001')
        target = self.root / 'outside-binding.json'; target.write_text((root / 'binding.json').read_text())
        (root / 'binding.json').unlink(); (root / 'binding.json').symlink_to(target)
        with self.assertRaises(SystemExit): harness.legacy_completion_bindings(feature, 'T-001')

    def test_m4_1_25_b1_alone_preserves_completed_effective_state(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        entry = harness.load_state(feature, doc)['tasks']['T-001']
        self.assertEqual('completed', harness.effective_task_status(feature, 'T-001', entry))

    def test_m4_1_26_b1_does_not_create_k1(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        self.assertEqual([], harness.correction_records(feature, 'T-001'))

    def test_m4_1_27_b1_does_not_create_repair_authorization(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        self.assertEqual([], harness.repair_authorization_records(feature, 'T-001'))
        self.assertEqual([], harness.read_completion_authority(
            feature, 'completion-repair-claims', 'T-001', record_type='completion-repair-claim'))

    def test_m4_1_28_reconstructed_c1_accepted_by_m4_k1(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        defect = self.root / 'defect.json'; defect.write_text('{"defect":"fixture"}')
        args = argparse.Namespace(feature_dir=feature, task_id='T-001', reason_code=harness.COMPLETION_CORRECTION_REASON,
                                  reason='legacy completion was invalid', evidence=str(defect), by='operator')
        with contextlib.redirect_stdout(io.StringIO()): harness.cmd_correct_completion(args)
        correction = harness.correction_records(feature, 'T-001')[0]
        self.assertEqual(harness.completion_records(feature, 'T-001')[0]['record_id'], correction['original_completion_id'])

    def test_m4_1_29_k1_after_b1_blocks_dependency(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        defect = self.root / 'defect.json'; defect.write_text('{}')
        args = argparse.Namespace(feature_dir=feature, task_id='T-001', reason_code=harness.COMPLETION_CORRECTION_REASON,
                                  reason='defect', evidence=str(defect), by='operator')
        with contextlib.redirect_stdout(io.StringIO()): harness.cmd_correct_completion(args)
        state = harness.load_state(feature, doc)
        self.assertEqual('correction_required', harness.effective_task_status(
            feature, 'T-001', state['tasks']['T-001']))
        self.assertNotIn('T-900', harness.ready_ids(doc, state, feature))

    def test_m4_1_30_attempt_count_unchanged(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        self.assertEqual(1, harness.load_state(feature, doc)['tasks']['T-001']['attempts'])

    def test_m4_1_31_retry_authorization_history_unchanged(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); self._attest(feature, doc, path, evidence)
        self.assertEqual([], harness.load_state(feature, doc)['tasks']['T-001'].get('retry_authorizations', []))

    def test_m4_1_32_bridge_packet_lineage_unaffected(self):
        feature, doc, path, evidence, _ = self.legacy_fixture(); before = harness.load_state(feature, doc)
        bridge_dir = harness.runtime_state_dir(feature) / 'packet-identity-bridges' / feature.name / 'T-001'
        before_bridges = sorted((p.name, p.read_bytes()) for p in bridge_dir.glob('*.json')) if bridge_dir.exists() else []
        self._attest(feature, doc, path, evidence); after = harness.load_state(feature, doc)
        self.assertEqual(before['tasks']['T-001'].get('packet_lineage'), after['tasks']['T-001'].get('packet_lineage'))
        after_bridges = sorted((p.name, p.read_bytes()) for p in bridge_dir.glob('*.json')) if bridge_dir.exists() else []
        self.assertEqual(before_bridges, after_bridges)


if __name__ == '__main__':
    unittest.main()
