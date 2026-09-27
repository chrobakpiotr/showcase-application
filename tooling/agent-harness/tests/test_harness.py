import argparse
import importlib.util
import json
import os
import pathlib
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

    def exhausted_authorized_task(self):
        feature = self.feature([
            {'id': 'T-001', 'title': 'Build', 'objective': 'Implement it', 'role': 'builder',
             'depends_on': [], 'allowed_paths': ['modules/domain/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001'], 'verification': ['./gradlew test'],
             'test_mode': 'red-green-refactor', 'test_seam': 'fixture seam'},
            {'id': 'T-900', 'title': 'Evaluate', 'objective': 'Falsify it', 'role': 'evaluator',
             'depends_on': ['T-001'], 'allowed_paths': ['evidence/**'], 'risk_tags': [],
             'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['./gradlew test']},
        ])
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
        self.assertEqual(['T-001'], harness.ready_ids(doc, state, feature))

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
                self.assertNotEqual(grant['binding']['contract_sha256'], harness.retry_binding(feature, doc, 'T-001', 3)['contract_sha256'])
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
            evidence = self.root / 'a-evidence.json'
            evidence.write_text(json.dumps({'status': 'pass', 'summary': 'ok', 'changed_paths': ['a.txt'],
                                            'commands': ['true'], 'assumptions': [], 'residual_risks': []}))
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
            evidence = self.root / 'result-a.json'
            evidence.write_text(json.dumps({'status': 'pass', 'summary': 'ok', 'changed_paths': ['a.txt'],
                                            'commands': ['true'], 'assumptions': [], 'residual_risks': []}))
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
        evidence = self.root / 't1-result.json'
        evidence.write_text(json.dumps({
            'status': 'pass', 'summary': 'ok', 'changed_paths': ['a.txt'], 'commands': ['true'],
            'assumptions': [], 'residual_risks': []
        }))
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


if __name__ == '__main__':
    unittest.main()
