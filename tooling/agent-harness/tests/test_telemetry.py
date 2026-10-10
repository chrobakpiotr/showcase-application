import importlib.util
import io
import json
import pathlib
import tempfile
import unittest
import subprocess
import sys
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'telemetry.py'
sys.path.insert(0, str(MODULE_PATH.parent))
spec = importlib.util.spec_from_file_location('sdd_telemetry', MODULE_PATH)
telemetry = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(telemetry)


class TelemetryTest(unittest.TestCase):
    def test_candidate_binding_refusal_uses_blocked_exit_five(self):
        stderr = io.StringIO()
        with mock.patch('sys.argv', ['telemetry.py', '--repo', '.', 'record-manual',
                '--feature', 'TST-MANUAL', '--role', 'evaluator', '--provider', 'manual',
                '--checkpoint', 'a' * 40, '--verdict', 'PASS', '--report', 'report.md',
                '--attestation', 'attestation.json']), \
             mock.patch.object(telemetry, 'record_manual',
                side_effect=ValueError('MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED')), \
             mock.patch('sys.stderr', stderr):
            with self.assertRaises(SystemExit) as exited:
                telemetry.main()
        self.assertEqual(5, exited.exception.code)
        self.assertIn('MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED', stderr.getvalue())

    def test_manual_coverage_rejects_malformed_trusted_plan_binding(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_ATTESTATION_INVALID'):
                telemetry.verify_manual_observation_for_coverage(
                    pathlib.Path(tmp), {}, plan_binding={'plan_id': 'caller-selected-plan'})

    def test_manual_coverage_plan_binding_must_match_signed_envelope(self):
        import datetime as dt
        import hashlib
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            subprocess.run(['git', '-C', str(root), 'config', 'user.name', 'Telemetry Test'], check=True)
            subprocess.run(['git', '-C', str(root), 'config', 'user.email', 'telemetry@example.invalid'], check=True)
            (root / 'seed.txt').write_text('seed\n', encoding='utf-8')
            subprocess.run(['git', '-C', str(root), 'add', 'seed.txt'], check=True)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'seed'], check=True)
            checkpoint = 'a' * 40
            completed = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=1)).replace(
                microsecond=0).strftime('%Y-%m-%dT%H:%M:%SZ')
            report = (f'Feature: `TST-MANUAL`\nReviewed checkpoint: `{checkpoint}`\n'
                      'Task: `T-900`\nVerdict: **PASS**\n'
                      f'Completed at: `{completed}`\n').encode()
            binding = {
                'plan_id': 'verification-plan-v2:sha256:' + '1' * 64,
                'family_id': 'family-test', 'plan_acceptance_transition_id': 'transition-test',
                'lifecycle_generation': 2, 'task_id': 'T-900', 'task_attempt': 1,
                'candidate_identity': '2' * 64, 'final_surface_identity': '3' * 64,
                'obligation_ids': ['verification-obligation-v2:sha256:' + '4' * 64],
                'reviewer_principal': 'human:test-reviewer',
            }
            envelope = {
                'attestation_id': 'signed-id', 'role': 'evaluator', 'verdict': 'PASS',
                'report_sha256': 'sha256:' + hashlib.sha256(report).hexdigest(),
                'completed_at': completed, 'repository_id': 'repo-id', 'feature_id': 'TST-MANUAL',
                'checkpoint_id': checkpoint, 'candidate_identity': binding['candidate_identity'],
                'final_surface_identity': binding['final_surface_identity'], 'task_id': 'T-900',
                'attempt': 1, 'plan_id': binding['plan_id'], 'family_id': binding['family_id'],
                'plan_acceptance_transition_id': binding['plan_acceptance_transition_id'],
                'lifecycle_generation': 2, 'obligation_ids': binding['obligation_ids'],
                'reviewer_principal': binding['reviewer_principal'],
            }
            attestation = json.dumps({'envelope': envelope, 'signature': 'fixture'},
                                     sort_keys=True, separators=(',', ':')).encode()
            attestation_digest = hashlib.sha256(attestation).hexdigest()
            report_digest = hashlib.sha256(report).hexdigest()
            from verification.store import VerificationStore
            control_root = VerificationStore(root).root
            attestation_path = control_root / 'manual-attestations' / f'{attestation_digest}.json'
            report_path = control_root / 'manual-report-snapshots' / f'{report_digest}.md'
            attestation_path.parent.mkdir(parents=True)
            report_path.parent.mkdir(parents=True)
            attestation_path.write_bytes(attestation)
            report_path.write_bytes(report)
            observation = {
                'record_type': 'manual-observation', 'schema_version': 1,
                'attestation_sha256': 'sha256:' + attestation_digest,
                'report_sha256': 'sha256:' + report_digest, 'role': 'evaluator',
                'repository_id': 'repo-id', 'feature_id': 'TST-MANUAL',
                'scope': {'task_id': 'T-900', 'task_attempt': 1}, 'plan_binding': binding,
            }

            verified_expected = {}
            def verify(_payload, *, repository, expected):
                verified_expected.update(expected)
                if any(envelope.get(key) != value for key, value in expected.items()):
                    raise ValueError('MANUAL_EVIDENCE_ATTESTATION_INVALID')
                return envelope, attestation_digest

            with mock.patch.object(telemetry, '_verify_manual_attestation', side_effect=verify):
                proof = telemetry.verify_manual_observation_for_coverage(
                    root, observation, plan_binding=binding)
                self.assertEqual(binding, proof['plan_binding'])
                self.assertEqual(verified_expected,
                    {key: envelope[key] for key in verified_expected})
                for field, changed_value in (
                        ('plan_id', 'verification-plan-v2:sha256:' + '5' * 64),
                        ('obligation_ids', ['verification-obligation-v2:sha256:' + '6' * 64]),
                        ('candidate_identity', '7' * 64), ('final_surface_identity', '8' * 64)):
                    changed = {**binding, field: changed_value}
                    with self.subTest(field=field), self.assertRaisesRegex(
                            ValueError, 'MANUAL_EVIDENCE_ATTESTATION_INVALID'):
                        telemetry.verify_manual_observation_for_coverage(
                            root, {**observation, 'plan_binding': changed}, plan_binding=changed)

    def test_manual_coverage_reverifies_exact_signed_plan_scope(self):
        import hashlib
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        obligation = 'verification-obligation-v2:sha256:' + '1' * 64
        binding = {
            'plan_id': 'verification-plan-v2:sha256:' + '2' * 64,
            'family_id': 'family-test',
            'plan_acceptance_transition_id': 'transition-test',
            'lifecycle_generation': 2,
            'task_id': 'T-900', 'task_attempt': 1,
            'candidate_identity': '3' * 64,
            'final_surface_identity': '4' * 64,
            'obligation_ids': [obligation],
            'reviewer_principal': 'human:test-reviewer',
        }
        fixture['report_path'].write_bytes(
            fixture['report_path'].read_bytes().replace(b'Verdict:', b'Task: `T-900`\nVerdict:'))
        from verification.serialization import canonical_jcs
        fixture['envelope']['report_sha256'] = 'sha256:' + hashlib.sha256(
            fixture['report_path'].read_bytes()).hexdigest()
        fixture['envelope'].update({
            'candidate_identity': binding['candidate_identity'],
            'final_surface_identity': binding['final_surface_identity'],
            'task_id': binding['task_id'], 'attempt': binding['task_attempt'],
            'plan_id': binding['plan_id'], 'family_id': binding['family_id'],
            'plan_acceptance_transition_id': binding['plan_acceptance_transition_id'],
            'lifecycle_generation': binding['lifecycle_generation'],
            'obligation_ids': binding['obligation_ids'],
        })
        from verification.serialization import canonical_jcs
        signature = fixture['key'].sign(canonical_jcs(fixture['envelope']))
        import base64
        attestation = canonical_jcs({'envelope': fixture['envelope'],
            'signature': base64.urlsafe_b64encode(signature).decode().rstrip('=')})
        attestation_digest = hashlib.sha256(attestation).hexdigest()
        report = fixture['report_path'].read_bytes()
        report_digest = hashlib.sha256(report).hexdigest()
        attestation_snapshot = fixture['store'].root / 'manual-attestations' / f'{attestation_digest}.json'
        report_snapshot = fixture['store'].root / 'manual-report-snapshots' / f'{report_digest}.md'
        attestation_snapshot.parent.mkdir(parents=True, exist_ok=True)
        report_snapshot.parent.mkdir(parents=True, exist_ok=True)
        attestation_snapshot.write_bytes(attestation)
        report_snapshot.write_bytes(report)
        observation = {
            'record_type': 'manual-observation', 'schema_version': 1,
            'attestation_sha256': 'sha256:' + attestation_digest,
            'report_sha256': 'sha256:' + report_digest,
            'role': 'evaluator', 'repository_id': fixture['envelope']['repository_id'],
            'feature_id': 'TST-MANUAL', 'scope': {'task_id': 'T-900', 'task_attempt': 1},
            'plan_binding': binding,
        }
        proof = telemetry.verify_manual_observation_for_coverage(
            fixture['root'], observation, plan_binding=binding)
        self.assertEqual(binding, proof['plan_binding'])
        report_snapshot = (fixture['store'].root / 'manual-report-snapshots' /
                           f"{observation['report_sha256'].removeprefix('sha256:')}.md")
        original_report = report_snapshot.read_bytes()
        report_snapshot.write_bytes(original_report + b'\nchanged after registration')
        with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_ATTESTATION_INVALID'):
            telemetry.verify_manual_observation_for_coverage(
                fixture['root'], observation, plan_binding=binding)
        report_snapshot.write_bytes(original_report)
        for field, value in (('plan_id', 'verification-plan-v2:sha256:' + '5' * 64),
                             ('obligation_ids', ['verification-obligation-v2:sha256:' + '6' * 64]),
                             ('candidate_identity', '7' * 64), ('final_surface_identity', '8' * 64),
                             ('reviewer_principal', 'human:other-reviewer')):
            changed = {**binding, field: value}
            changed_observation = {**observation, 'plan_binding': changed}
            with self.subTest(field=field), self.assertRaisesRegex(
                    ValueError, 'MANUAL_EVIDENCE_ATTESTATION_INVALID'):
                telemetry.verify_manual_observation_for_coverage(
                    fixture['root'], changed_observation, plan_binding=changed)

    def _signed_manual_fixture(self):
        import base64
        import datetime as dt
        import hashlib
        import os
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        from verification.serialization import canonical_jcs
        from verification.store import VerificationStore

        temporary = tempfile.TemporaryDirectory()
        root = pathlib.Path(temporary.name)
        subprocess.run(['git', 'init', '-q', str(root)], check=True)
        env = dict(os.environ, GIT_AUTHOR_NAME='Telemetry Test', GIT_AUTHOR_EMAIL='telemetry@example.invalid',
                   GIT_COMMITTER_NAME='Telemetry Test', GIT_COMMITTER_EMAIL='telemetry@example.invalid')
        (root / '.gitignore').write_text('.agent-runs/\n.agent-state/\n', encoding='utf-8')
        (root / 'tracked.txt').write_text('safe\n', encoding='utf-8')
        feature_dir = root / 'docs' / 'specs' / 'TST-MANUAL'
        feature_dir.mkdir(parents=True)
        (feature_dir / 'spec.md').write_text(
            '# TST-MANUAL\n\n- AC-001: a review is recorded\n- AC-002: it is independently checked\n', encoding='utf-8')
        (feature_dir / 'plan.md').write_text('# plan\n', encoding='utf-8')
        roles_dir = root / 'docs' / 'agentic-sdd' / 'agents'
        roles_dir.mkdir(parents=True)
        for role_name in ('builder', 'evaluator'):
            (roles_dir / f'{role_name}.md').write_text(f'# {role_name}\n', encoding='utf-8')
        (feature_dir / 'tasks.json').write_text(json.dumps({'feature': 'TST-MANUAL', 'max_parallel': 1,
            'tasks': [{'id': 'T-001', 'title': 'Build', 'objective': 'Build fixture', 'role': 'builder',
                'depends_on': [], 'allowed_paths': ['src/**'], 'risk_tags': ['domain'],
                'acceptance_criteria': ['AC-001'], 'verification': ['true']},
                {'id': 'T-900', 'title': 'Evaluate', 'objective': 'Evaluate fixture', 'role': 'evaluator',
                'depends_on': ['T-001'], 'allowed_paths': ['docs/specs/TST-MANUAL/evidence/**'],
                'risk_tags': ['evaluation'], 'acceptance_criteria': ['AC-001', 'AC-002'],
                'verification': ['true']}]}), encoding='utf-8')
        key = Ed25519PrivateKey.generate()
        public = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        registry = {'schema_version': 1, 'issuers': [{
            'issuer_id': 'test-issuer', 'reviewer_principal': 'human:test-reviewer',
            'public_key_ed25519': base64.b64encode(public).decode('ascii'),
            'key_fingerprint': 'sha256:' + hashlib.sha256(public).hexdigest(),
            'enabled': True, 'revoked': False, 'actions': ['manual-review'],
        }]}
        registry_path = root / 'tooling' / 'agent-harness' / 'human-issuer-registry.json'
        registry_path.parent.mkdir(parents=True)
        registry_path.write_text(json.dumps(registry, sort_keys=True, separators=(',', ':')), encoding='utf-8')
        subprocess.run(['git', '-C', str(root), 'add', '.'], check=True, env=env)
        subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'trusted test checkpoint'], check=True, env=env)
        store = VerificationStore(root)
        report_path = store.root / 'manual-reports' / 'review.md'
        report_path.parent.mkdir(parents=True)
        attestation_path = store.root / 'manual-attestations' / 'proof.json'
        attestation_path.parent.mkdir(parents=True)
        completed_text = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=1)).replace(
            microsecond=0).strftime('%Y-%m-%dT%H:%M:%SZ')
        fixture = {'temporary': temporary, 'root': root, 'env': env, 'feature_dir': feature_dir,
                   'key': key, 'public': public, 'registry': registry, 'registry_path': registry_path,
                   'store': store, 'report_path': report_path, 'attestation_path': attestation_path,
                   'completed_text': completed_text}
        self._refresh_signed_fixture(fixture)
        return fixture

    def _refresh_signed_fixture(self, fixture, *, envelope_changes=None):
        import base64
        import hashlib
        import uuid
        from verification.serialization import canonical_jcs

        root = fixture['root']
        checkpoint = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
        raw_registry = fixture['registry_path'].read_bytes()
        common = subprocess.check_output(['git', '-C', str(root), 'rev-parse', '--git-common-dir'], text=True).strip()
        common_path = pathlib.Path(common)
        if not common_path.is_absolute():
            common_path = (root / common_path).resolve()
        task_line = f"Task: `{(envelope_changes or {}).get('task_id')}`\n" if (envelope_changes or {}).get('task_id') else ''
        report = (f"Feature: `TST-MANUAL`\nReviewed checkpoint: `{checkpoint}`\n{task_line}"
                  f"Verdict: **PASS**\nCompleted at: `{fixture['completed_text']}`\n").encode()
        fixture['report_path'].write_bytes(report)
        fixture['requested_checkpoint'] = checkpoint
        envelope = {
            'schema_version': 1, 'attestation_type': 'manual-review-attestation-v1',
            'attestation_id': str(uuid.uuid4()), 'issuer_registry_id': 'trusted-human-issuer-registry-v1',
            'issuer_registry_checkpoint': subprocess.check_output(
                ['git', '-C', str(root), 'log', '-1', '--format=%H', 'HEAD', '--',
                 'tooling/agent-harness/human-issuer-registry.json'], text=True).strip(),
            'issuer_registry_sha256': 'sha256:' + hashlib.sha256(raw_registry).hexdigest(),
            'issuer_id': 'test-issuer', 'reviewer_principal': 'human:test-reviewer',
            'key_fingerprint': fixture['registry']['issuers'][0]['key_fingerprint'],
            'signature_algorithm': 'Ed25519', 'role': 'evaluator', 'verdict': 'PASS',
            'report_sha256': 'sha256:' + hashlib.sha256(report).hexdigest(),
            'completed_at': fixture['completed_text'],
            'repository_id': hashlib.sha256(__import__('os').fsencode(common_path)).hexdigest(),
            'feature_id': 'TST-MANUAL', 'checkpoint_id': checkpoint,
            'candidate_identity': None, 'final_surface_identity': None,
            'task_id': None, 'attempt': None, 'plan_id': None, 'family_id': None,
            'plan_acceptance_transition_id': None, 'lifecycle_generation': None, 'obligation_ids': [],
        }
        envelope.update(envelope_changes or {})
        signature = base64.urlsafe_b64encode(fixture['key'].sign(canonical_jcs(envelope))).decode().rstrip('=')
        fixture['envelope'] = envelope
        fixture['attestation_path'].write_bytes(canonical_jcs({'envelope': envelope, 'signature': signature}))

    def _resign_envelope_only(self, fixture, **changes):
        """Keep report evidence valid while mutating only signed attestation scope."""
        import base64
        import hashlib
        from verification.serialization import canonical_jcs

        envelope = dict(fixture['envelope'])
        envelope.update(changes)
        report = fixture['report_path'].read_bytes()
        envelope['report_sha256'] = 'sha256:' + hashlib.sha256(report).hexdigest()
        signature = base64.urlsafe_b64encode(fixture['key'].sign(canonical_jcs(envelope))).decode().rstrip('=')
        fixture['envelope'] = envelope
        fixture['attestation_path'].write_bytes(canonical_jcs({'envelope': envelope, 'signature': signature}))

    def test_plan_bound_manual_registration_flows_into_coverage_cas(self):
        from types import SimpleNamespace
        import harness as lifecycle
        from verification import authority
        from verification.candidate import CandidateSealError
        from verification.store import VerificationStore

        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        root = fixture['root']
        feature_dir = fixture['feature_dir']
        doc = lifecycle.load_validated(feature_dir)
        state = lifecycle.load_state(feature_dir, doc)
        task_id, attempt = 'T-900', 1
        checkpoint = fixture['requested_checkpoint']
        state['tasks'][task_id].update({'status': 'running', 'attempts': attempt,
            'attempt_bindings': [{'attempt': attempt, 'binding_status': 'proven',
                'packet_revision': 'sha256:' + 'a' * 64, 'contract_sha256': 'b' * 64}]})
        plan_id = 'verification-plan-v2:sha256:' + '1' * 64
        obligation_id = 'verification-obligation-v2:sha256:' + '2' * 64
        family = {'id': 'family-test', 'base_sha': checkpoint,
                  'origin_policy': 'task-completion'}
        plan = {'schema_version': 2, 'plan_id': plan_id, 'feature_id': 'TST-MANUAL',
            'task_id': task_id, 'task_attempt': attempt, 'lifecycle_generation': 1,
            'feature_fingerprint': lifecycle.feature_fingerprint(feature_dir),
            'profile_id': 'showcase', 'profile_hash': '3' * 64,
            'policy_checkpoint': 'd' * 40, 'origin_binding': 'task-completion',
            'family': family, 'candidate_identity': '4' * 64,
            'final_changed_surface_id': '5' * 64,
            'obligations': [{'obligation_id': obligation_id, 'profile_gate_id': 'manual-gate',
                'requirement_source': 'profile', 'required_origin': 'manual',
                'required_manual_reviewer_principal': None}]}
        transition_id = '11111111-1111-4111-8111-111111111111'
        authority_binding = {key: plan[key] for key in (
            'schema_version', 'profile_id', 'plan_id', 'task_id', 'task_attempt',
            'feature_fingerprint', 'lifecycle_generation', 'family', 'profile_hash',
            'policy_checkpoint', 'candidate_identity', 'final_changed_surface_id', 'origin_binding')}
        state['verification_authority'] = {'accepted_plan_id': plan_id, 'generation': 1,
            'binding': authority_binding, 'plan_acceptance_transition_id': transition_id}
        lifecycle.save_state(feature_dir, state)
        self.assertTrue(list((root / '.agent-state').glob('TST-MANUAL-*.json')))
        fixture['report_path'].write_bytes(
            fixture['report_path'].read_bytes().replace(b'Verdict:', b'Task: `T-900`\nVerdict:'))
        fixture['envelope'].update({
            'candidate_identity': plan['candidate_identity'],
            'final_surface_identity': plan['final_changed_surface_id'],
            'task_id': task_id, 'attempt': attempt, 'plan_id': plan_id,
            'family_id': family['id'], 'plan_acceptance_transition_id': transition_id,
            'lifecycle_generation': 1, 'obligation_ids': [obligation_id]})
        self._refresh_signed_fixture(fixture, envelope_changes={key: fixture['envelope'][key]
            for key in ('task_id', 'candidate_identity', 'final_surface_identity', 'attempt',
                        'plan_id', 'family_id', 'plan_acceptance_transition_id',
                        'lifecycle_generation', 'obligation_ids')})
        gate = SimpleNamespace(id='manual-gate', required_origin='manual',
                               required_manual_reviewer_principal='human:test-reviewer')
        seal = SimpleNamespace(head_sha=checkpoint, candidate_identity=plan['candidate_identity'],
                               changed_surface_id=plan['final_changed_surface_id'])
        with mock.patch.object(VerificationStore, 'load_plan_record', return_value=plan), \
             mock.patch.object(authority, 'validate_plan_record'), \
             mock.patch.object(authority, '_load_trusted_profile', return_value=SimpleNamespace(gates=(gate,))), \
             mock.patch('verification.candidate.seal_candidate', return_value=seal):
            record = telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                provider='manual', checkpoint=checkpoint, verdict='PASS',
                report=fixture['report_path'].relative_to(root.resolve()).as_posix(),
                task=task_id, task_attempt='1', plan_id=plan_id,
                attestation=fixture['attestation_path'].relative_to(root.resolve()).as_posix())
            observation = json.loads(record.read_text(encoding='utf-8'))
            accepted = lifecycle.accept_manual_observation_coverage(root, 'TST-MANUAL',
                plan_id=plan_id, observation_id=observation['observation_id'],
                obligation_id=obligation_id, expected_feature_generation=1)
        self.assertEqual(plan_id, accepted['plan_id'])
        self.assertEqual(checkpoint, accepted['reviewed_checkpoint'])

        # The accepted transition is part of the observation's semantic identity:
        # identical review facts under a later acceptance must not replay the old ID.
        second_transition_id = '22222222-2222-4222-8222-222222222222'
        state = lifecycle.load_state(feature_dir, doc)
        state['verification_authority']['plan_acceptance_transition_id'] = second_transition_id
        lifecycle.save_state(feature_dir, state)
        fixture['envelope']['plan_acceptance_transition_id'] = second_transition_id
        self._refresh_signed_fixture(fixture, envelope_changes={
            'task_id': task_id, 'candidate_identity': plan['candidate_identity'],
            'final_surface_identity': plan['final_changed_surface_id'], 'attempt': attempt,
            'plan_id': plan_id, 'family_id': family['id'],
            'plan_acceptance_transition_id': second_transition_id,
            'lifecycle_generation': 1, 'obligation_ids': [obligation_id],
        })
        with mock.patch.object(VerificationStore, 'load_plan_record', return_value=plan), \
             mock.patch.object(authority, 'validate_plan_record'), \
             mock.patch.object(authority, '_load_trusted_profile', return_value=SimpleNamespace(gates=(gate,))), \
             mock.patch('verification.candidate.seal_candidate', return_value=seal):
            second_record = telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                provider='manual', checkpoint=checkpoint, verdict='PASS',
                report=fixture['report_path'].relative_to(root.resolve()).as_posix(),
                task=task_id, task_attempt='1', plan_id=plan_id,
                attestation=fixture['attestation_path'].relative_to(root.resolve()).as_posix())
        second_observation = json.loads(second_record.read_text(encoding='utf-8'))
        self.assertNotEqual(observation['observation_id'], second_observation['observation_id'])

    def _assert_rejected_before_lifecycle_register(self, fixture, expected='MANUAL_EVIDENCE_ATTESTATION_INVALID'):
        import harness as lifecycle
        with mock.patch.object(lifecycle, 'register_manual_observation') as register:
            with self.assertRaisesRegex(ValueError, expected):
                telemetry.record_manual(repo=fixture['root'], feature='TST-MANUAL', role='evaluator',
                    provider='manual', checkpoint=fixture['requested_checkpoint'], verdict='PASS',
                    report=fixture['report_path'].relative_to(fixture['root'].resolve()).as_posix(),
                    attestation=fixture['attestation_path'].relative_to(fixture['root'].resolve()).as_posix())
            register.assert_not_called()

    def test_manual_attestation_rejects_uncommitted_registry_change_before_register(self):
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        fixture['registry_path'].write_bytes(fixture['registry_path'].read_bytes() + b' ')
        self._assert_rejected_before_lifecycle_register(fixture)

    def test_manual_attestation_rejects_revoked_issuer_before_register(self):
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        fixture['registry']['issuers'][0]['revoked'] = True
        fixture['registry_path'].write_text(json.dumps(fixture['registry'], sort_keys=True, separators=(',', ':')))
        subprocess.run(['git', '-C', str(fixture['root']), 'add', str(fixture['registry_path'])], check=True)
        subprocess.run(['git', '-C', str(fixture['root']), 'commit', '-qm', 'revoke test issuer'],
                       check=True, env=fixture['env'])
        self._refresh_signed_fixture(fixture)
        self._assert_rejected_before_lifecycle_register(fixture)

    def test_manual_attestation_rejects_issuer_without_manual_review_action_before_register(self):
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        fixture['registry']['issuers'][0]['actions'] = ['critical-gate-retry']
        fixture['registry_path'].write_text(json.dumps(fixture['registry'], sort_keys=True, separators=(',', ':')))
        subprocess.run(['git', '-C', str(fixture['root']), 'add', str(fixture['registry_path'])], check=True)
        subprocess.run(['git', '-C', str(fixture['root']), 'commit', '-qm', 'remove manual review action'],
                       check=True, env=fixture['env'])
        self._refresh_signed_fixture(fixture)
        self._assert_rejected_before_lifecycle_register(fixture)

    def test_manual_attestation_rejects_noncanonical_reserialization_before_register(self):
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        outer = json.loads(fixture['attestation_path'].read_text(encoding='utf-8'))
        fixture['attestation_path'].write_text(json.dumps(outer, indent=2), encoding='utf-8')
        self._assert_rejected_before_lifecycle_register(fixture)

    def test_manual_attestation_rejects_valid_signature_for_different_feature_before_register(self):
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        self._refresh_signed_fixture(fixture, envelope_changes={'feature_id': 'OTHER-FEATURE'})
        self._assert_rejected_before_lifecycle_register(fixture)

    def test_manual_attestation_rejects_valid_signature_for_different_task_before_register(self):
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        self._resign_envelope_only(fixture, task_id='T-900')
        self._assert_rejected_before_lifecycle_register(fixture)

    def test_manual_attestation_rejects_valid_signature_for_different_attempt_before_register(self):
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        self._resign_envelope_only(fixture, task_id='T-001', attempt=3)
        self._assert_rejected_before_lifecycle_register(fixture)

    def test_manual_attestation_rejects_valid_signature_for_different_report_digest_before_register(self):
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        self._refresh_signed_fixture(fixture, envelope_changes={'report_sha256': 'sha256:' + 'f' * 64})
        self._assert_rejected_before_lifecycle_register(fixture)

    def test_manual_attestation_rejects_valid_signature_for_different_checkpoint_before_register(self):
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        self._refresh_signed_fixture(fixture, envelope_changes={'checkpoint_id': 'a' * 40})
        self._assert_rejected_before_lifecycle_register(fixture)

    def test_manual_registration_fails_closed_without_signed_attestation(self):
        from verification.store import VerificationStore
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            env = dict(__import__('os').environ, GIT_AUTHOR_NAME='Telemetry Test',
                       GIT_AUTHOR_EMAIL='telemetry@example.invalid', GIT_COMMITTER_NAME='Telemetry Test',
                       GIT_COMMITTER_EMAIL='telemetry@example.invalid')
            (root / 'tracked.txt').write_text('safe\n', encoding='utf-8')
            (root / '.gitignore').write_text('.agent-runs/\n.agent-state/\n', encoding='utf-8')
            feature_dir = root / 'docs' / 'specs' / 'TST-MANUAL'
            feature_dir.mkdir(parents=True)
            (feature_dir / 'spec.md').write_text('# TST-MANUAL\n\n- AC-001: a review is recorded\n- AC-002: it is independently checked\n', encoding='utf-8')
            (feature_dir / 'plan.md').write_text('# plan\n', encoding='utf-8')
            roles_dir = root / 'docs' / 'agentic-sdd' / 'agents'
            roles_dir.mkdir(parents=True)
            (roles_dir / 'builder.md').write_text('# builder\n', encoding='utf-8')
            (roles_dir / 'evaluator.md').write_text('# evaluator\n', encoding='utf-8')
            (feature_dir / 'tasks.json').write_text(json.dumps({'feature': 'TST-MANUAL', 'max_parallel': 1,
                'tasks': [{'id': 'T-001', 'title': 'Build', 'objective': 'Build fixture', 'role': 'builder',
                    'depends_on': [], 'allowed_paths': ['src/**'], 'risk_tags': ['domain'],
                    'acceptance_criteria': ['AC-001'], 'verification': ['true']},
                    {'id': 'T-900', 'title': 'Evaluate', 'objective': 'Evaluate fixture', 'role': 'evaluator',
                    'depends_on': ['T-001'], 'allowed_paths': ['docs/specs/TST-MANUAL/evidence/**'],
                    'risk_tags': ['evaluation'], 'acceptance_criteria': ['AC-001', 'AC-002'],
                    'verification': ['true']}]}), encoding='utf-8')
            subprocess.run(['git', '-C', str(root), 'add', 'tracked.txt', '.gitignore', 'docs'], check=True, env=env)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'checkpoint'], check=True, env=env)
            checkpoint = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            store = VerificationStore(root)
            report_path = store.root / 'manual-reports' / 'review.md'
            report_path.parent.mkdir(parents=True)
            report_path.write_text(
                f'Feature: `TST-MANUAL`\nReviewed checkpoint: `{checkpoint}`\n'
                'Verdict: **PASS**\nCompleted at: `2026-09-29T12:00:00Z`\n', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_ATTESTATION_INVALID'):
                telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                    provider='manual', checkpoint=checkpoint, verdict='PASS',
                    report=report_path.relative_to(root.resolve()).as_posix())
            self.assertFalse((root / '.agent-state').exists())

    def test_record_manual_verifies_ed25519_attestation_before_lifecycle_registration(self):
        import base64
        import datetime as dt
        import uuid
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        from verification.serialization import canonical_jcs
        from verification.store import VerificationStore

        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            env = dict(__import__('os').environ, GIT_AUTHOR_NAME='Telemetry Test',
                       GIT_AUTHOR_EMAIL='telemetry@example.invalid', GIT_COMMITTER_NAME='Telemetry Test',
                       GIT_COMMITTER_EMAIL='telemetry@example.invalid')
            (root / '.gitignore').write_text('.agent-runs/\n.agent-state/\n', encoding='utf-8')
            (root / 'tracked.txt').write_text('safe\n', encoding='utf-8')
            feature_dir = root / 'docs' / 'specs' / 'TST-MANUAL'
            feature_dir.mkdir(parents=True)
            (feature_dir / 'spec.md').write_text('# TST-MANUAL\n\n- AC-001: a review is recorded\n- AC-002: it is independently checked\n', encoding='utf-8')
            (feature_dir / 'plan.md').write_text('# plan\n', encoding='utf-8')
            roles_dir = root / 'docs' / 'agentic-sdd' / 'agents'
            roles_dir.mkdir(parents=True)
            (roles_dir / 'builder.md').write_text('# builder\n', encoding='utf-8')
            (roles_dir / 'evaluator.md').write_text('# evaluator\n', encoding='utf-8')
            (feature_dir / 'tasks.json').write_text(json.dumps({'feature': 'TST-MANUAL', 'max_parallel': 1,
                'tasks': [{'id': 'T-001', 'title': 'Build', 'objective': 'Build fixture', 'role': 'builder',
                    'depends_on': [], 'allowed_paths': ['src/**'], 'risk_tags': ['domain'],
                    'acceptance_criteria': ['AC-001'], 'verification': ['true']},
                    {'id': 'T-900', 'title': 'Evaluate', 'objective': 'Evaluate fixture', 'role': 'evaluator',
                    'depends_on': ['T-001'], 'allowed_paths': ['docs/specs/TST-MANUAL/evidence/**'],
                    'risk_tags': ['evaluation'], 'acceptance_criteria': ['AC-001', 'AC-002'],
                    'verification': ['true']}]}), encoding='utf-8')
            key = Ed25519PrivateKey.generate()
            public = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
            registry = {'schema_version': 1, 'issuers': [{
                'issuer_id': 'test-issuer', 'reviewer_principal': 'human:test-reviewer',
                'public_key_ed25519': base64.b64encode(public).decode('ascii'),
                'key_fingerprint': 'sha256:' + __import__('hashlib').sha256(public).hexdigest(),
                'enabled': True, 'revoked': False, 'actions': ['manual-review'],
            }]}
            registry_path = root / 'tooling' / 'agent-harness' / 'human-issuer-registry.json'
            registry_path.parent.mkdir(parents=True)
            registry_path.write_text(json.dumps(registry, sort_keys=True, separators=(',', ':')), encoding='utf-8')
            subprocess.run(['git', '-C', str(root), 'add', '.'], check=True, env=env)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'trusted test checkpoint'], check=True, env=env)
            checkpoint = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            store = VerificationStore(root)
            report_path = store.root / 'manual-reports' / 'review.md'
            report_path.parent.mkdir(parents=True)
            completed_at = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=1)).replace(microsecond=0)
            completed_text = completed_at.strftime('%Y-%m-%dT%H:%M:%SZ')
            report = (f'Feature: `TST-MANUAL`\nReviewed checkpoint: `{checkpoint}`\n'
                      f'Verdict: **PASS**\nCompleted at: `{completed_text}`\n').encode()
            report_path.write_bytes(report)
            registry_checkpoint = subprocess.check_output(
                ['git', '-C', str(root), 'log', '-1', '--format=%H', 'HEAD', '--',
                 'tooling/agent-harness/human-issuer-registry.json'], text=True).strip()
            git_common = subprocess.check_output(['git', '-C', str(root), 'rev-parse', '--git-common-dir'], text=True).strip()
            common_path = pathlib.Path(git_common)
            if not common_path.is_absolute():
                common_path = (root / common_path).resolve()
            repository_id = __import__('hashlib').sha256(__import__('os').fsencode(common_path)).hexdigest()
            envelope = {
                'schema_version': 1, 'attestation_type': 'manual-review-attestation-v1',
                'attestation_id': str(uuid.uuid4()), 'issuer_registry_id': 'trusted-human-issuer-registry-v1',
                'issuer_registry_checkpoint': registry_checkpoint,
                'issuer_registry_sha256': 'sha256:' + __import__('hashlib').sha256(registry_path.read_bytes()).hexdigest(),
                'issuer_id': 'test-issuer', 'reviewer_principal': 'human:test-reviewer',
                'key_fingerprint': registry['issuers'][0]['key_fingerprint'], 'signature_algorithm': 'Ed25519',
                'role': 'evaluator', 'verdict': 'PASS',
                'report_sha256': 'sha256:' + __import__('hashlib').sha256(report).hexdigest(),
                'completed_at': completed_text, 'repository_id': repository_id,
                'feature_id': 'TST-MANUAL', 'checkpoint_id': checkpoint,
                'candidate_identity': None, 'final_surface_identity': None,
                'task_id': None, 'attempt': None, 'plan_id': None, 'family_id': None,
                'plan_acceptance_transition_id': None, 'lifecycle_generation': None, 'obligation_ids': [],
            }
            signature = base64.urlsafe_b64encode(key.sign(canonical_jcs(envelope))).decode().rstrip('=')
            attestation_path = store.root / 'manual-attestations' / 'proof.json'
            attestation_path.parent.mkdir(parents=True)
            attestation_path.write_bytes(canonical_jcs({'envelope': envelope, 'signature': signature}))
            with mock.patch.object(telemetry, '_manual_registry_context', wraps=telemetry._manual_registry_context):
                record = telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                    provider='manual', checkpoint=checkpoint, verdict='PASS',
                    report=report_path.relative_to(root.resolve()).as_posix(),
                    attestation=attestation_path.relative_to(root.resolve()).as_posix())
            saved = json.loads(record.read_text(encoding='utf-8'))
            self.assertEqual('sha256:' + __import__('hashlib').sha256(attestation_path.read_bytes()).hexdigest(),
                             saved['attestation_sha256'])
            attestation_snapshot_path = (store.root / 'manual-attestations' /
                (saved['attestation_sha256'].removeprefix('sha256:') + '.json'))
            self.assertTrue(attestation_snapshot_path.is_file())
            from verification.candidate import seal_candidate
            seal_candidate(root, checkpoint, {'family_id': 'manual-storage-test',
                'profile_hash': 'a' * 64, 'policy_checkpoint': 'b' * 40,
                'origin_policy': 'task-completion'})
            import harness as lifecycle
            state_doc = lifecycle.load_validated(feature_dir)
            state = lifecycle.load_state(feature_dir, state_doc)
            self.assertEqual(1, len(state.get('manual_observation_ledger', [])))
            coverage_proof = telemetry.verify_manual_observation_for_coverage(root, saved)
            self.assertEqual(saved['attestation_sha256'], coverage_proof['attestation_sha256'])
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_ATTESTATION_INVALID'):
                telemetry.verify_manual_observation_for_coverage(
                    root, saved, plan_binding={'plan_id': 'caller-selected-plan'})
            original = attestation_snapshot_path.read_bytes()
            attestation_snapshot_path.write_bytes(original + b' ')
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_ATTESTATION_INVALID'):
                telemetry.verify_manual_observation_for_coverage(root, saved)
            attestation_snapshot_path.write_bytes(original)

            malformed_outer = json.loads(original)
            malformed_outer['signature'] = ('A' if malformed_outer['signature'][0] != 'A' else 'B') + malformed_outer['signature'][1:]
            bad_attestation = store.root / 'manual-attestations' / 'bad-proof.json'
            bad_attestation.write_bytes(canonical_jcs(malformed_outer))
            with mock.patch.object(lifecycle, 'register_manual_observation') as register:
                with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_ATTESTATION_INVALID'):
                    telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                        provider='manual', checkpoint=checkpoint, verdict='PASS',
                        report=report_path.relative_to(root.resolve()).as_posix(),
                        attestation=bad_attestation.relative_to(root.resolve()).as_posix())
                register.assert_not_called()

    def test_manual_registration_rejects_untrusted_report_location_and_secret(self):
        from verification.store import VerificationStore
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            env = dict(__import__('os').environ, GIT_AUTHOR_NAME='Telemetry Test',
                       GIT_AUTHOR_EMAIL='telemetry@example.invalid', GIT_COMMITTER_NAME='Telemetry Test',
                       GIT_COMMITTER_EMAIL='telemetry@example.invalid')
            (root / 'tracked.txt').write_text('safe\n', encoding='utf-8')
            (root / '.gitignore').write_text('.agent-runs/\n', encoding='utf-8')
            feature_dir = root / 'docs' / 'specs' / 'TST-MANUAL'
            feature_dir.mkdir(parents=True)
            (feature_dir / 'tasks.json').write_text(json.dumps({'feature': 'TST-MANUAL', 'tasks': []}), encoding='utf-8')
            subprocess.run(['git', '-C', str(root), 'add', 'tracked.txt', '.gitignore', 'docs'], check=True, env=env)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'checkpoint'], check=True, env=env)
            checkpoint = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            store = VerificationStore(root)
            report_path = store.root / 'manual-reports' / 'review.md'
            report_path.parent.mkdir(parents=True)
            report_path.write_text(f'Feature: `TST-MANUAL`\nReviewed checkpoint: `{checkpoint}`\n'
                                   'Verdict: **PASS**\nCompleted at: `2026-09-29T12:00:00Z`\n'
                                   'api_key=sk-abcdefghijklmnopqrstuvwxyz0123456789\n', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_REPORT_SECRET'):
                telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                    provider='manual', checkpoint=checkpoint, verdict='PASS',
                    report=report_path.relative_to(root.resolve()).as_posix())
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_REPORT_INVALID'):
                telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                    provider='manual', checkpoint=checkpoint, verdict='PASS', report='README.md')

    def test_builder_task_rejects_reviewer_role_before_registration(self):
        import harness as lifecycle
        fixture = self._signed_manual_fixture()
        self.addCleanup(fixture['temporary'].cleanup)
        self._refresh_signed_fixture(fixture, envelope_changes={'task_id': 'T-001'})
        fixture['envelope']['role'] = 'reviewer'
        self._resign_envelope_only(fixture)
        with mock.patch.object(lifecycle, 'register_manual_observation') as register:
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_REPORT_INVALID'):
                telemetry.record_manual(repo=fixture['root'], feature='TST-MANUAL', role='reviewer',
                    provider='manual', checkpoint=fixture['requested_checkpoint'], verdict='PASS',
                    report=fixture['report_path'].relative_to(fixture['root'].resolve()).as_posix(),
                    task='T-001', attestation=fixture['attestation_path'].relative_to(
                        fixture['root'].resolve()).as_posix())
            register.assert_not_called()

    def test_manual_report_rejects_future_or_non_whole_second_completion_time(self):
        template = ('Feature: `TST-MANUAL`\nReviewed checkpoint: `{}`\n'
                    'Verdict: **PASS**\nCompleted at: `{}`\n')
        checkpoint = 'a' * 40
        future = (telemetry.utc_now() + __import__('datetime').timedelta(days=1))
        future = future.replace(microsecond=0).isoformat().replace('+00:00', 'Z')
        with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_CHRONOLOGY_INVALID'):
            telemetry._manual_report_fields(template.format(checkpoint, future).encode())
        with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_REPORT_INVALID'):
            telemetry._manual_report_fields(template.format(checkpoint, '2026-09-29T12:00:00.123Z').encode())

    def test_codex_jsonl_usage_is_extracted_without_cost_guessing(self):
        stream = '\n'.join([
            json.dumps({'type': 'thread.started', 'thread_id': 'abc'}),
            json.dumps({'type': 'turn.completed', 'usage': {'input_tokens': 10, 'cached_input_tokens': 4, 'output_tokens': 2}}),
        ])
        data = telemetry.parse_codex_jsonl(stream)
        self.assertEqual('abc', data['thread_id'])
        self.assertEqual(10, data['usage']['input_tokens'])
        self.assertIsNone(data['cost_usd'])

    def test_provider_parsers_drop_nested_unrecognized_metadata_and_secret_ids(self):
        canary = 'telemetry-canary-secret-91d3'
        stream = '\n'.join([
            json.dumps({'type': 'thread.started', 'thread_id': canary}),
            json.dumps({'type': 'turn.completed', 'usage': {
                'input_tokens': 10, canary: canary, 'provider_extension': {'value': canary},
            }}),
        ])
        with mock.patch.dict(__import__('os').environ, {'TELEMETRY_TEST_SECRET': canary}):
            codex = telemetry.parse_codex_jsonl(stream)
            claude = telemetry.parse_claude_envelope({
                'session_id': canary, 'usage': {'output_tokens': 3, canary: canary},
                'provider_extension': {'secret': canary}, 'total_cost_usd': None,
            })

        self.assertNotIn(canary, json.dumps({'codex': codex, 'claude': claude}))
        self.assertIsNone(codex['thread_id'])
        self.assertEqual({'input_tokens': 10}, codex['usage'])
        self.assertIsNone(claude['session_id'])
        self.assertEqual({'output_tokens': 3}, claude['usage'])
        self.assertIsNone(claude['cost_usd'])

    def test_claude_cost_metadata_is_preserved(self):
        data = telemetry.parse_claude_envelope({
            'session_id': 's1', 'total_cost_usd': 0.123, 'duration_ms': 55, 'num_turns': 3,
            'usage': {'input_tokens': 12}
        })
        self.assertEqual(0.123, data['cost_usd'])
        self.assertEqual(3, data['num_turns'])

    def test_claude_parser_drops_unrepresentably_large_duration_integers(self):
        data = telemetry.parse_claude_envelope({
            'duration_ms': 10**1000,
            'duration_api_ms': 10**1000,
        })

        self.assertIsNone(data['duration_ms'])
        self.assertIsNone(data['duration_api_ms'])

    def test_summary_can_filter_orchestration(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            for idx, run in enumerate(('r1', 'r2')):
                path = root / '.agent-runs' / 'F-1' / f'T-{idx}' / run / 'x' / 'provenance.json'
                path.parent.mkdir(parents=True)
                path.write_text(json.dumps({
                    'provider': 'claude', 'status': 'pass', 'orchestration_id': run,
                    'duration_ms': 10, 'provider_metadata': {'cost_usd': 0.1, 'usage': {'input_tokens': 5}}
                }))
            summary = telemetry.summarize(root, 'F-1', 'r1')
            self.assertEqual(1, summary['runs'])
            self.assertEqual(0.1, summary['known_cost_usd'])
            self.assertEqual(5, summary['tokens']['input_tokens'])

    def test_summary_never_renders_nested_provider_metadata_or_unknown_cost(self):
        canary = 'telemetry-canary-secret-91d3'
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            path = root / '.agent-runs' / 'F-1' / 'T-1' / 'run' / 'x' / 'provenance.json'
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({
                'schema_version': 2, 'invocation_id': 'safe-invocation',
                'provider': 'codex', 'status': 'pass', 'orchestration_id': 'run',
                'duration_ms': 10, 'provider_metadata': {
                    'cost_usd': None,
                    'usage': {'input_tokens': 5, canary: canary},
                    'diagnostic': {'nested': canary},
                },
            }))

            summary = telemetry.summarize(root, 'F-1')
            encoded = json.dumps(summary, sort_keys=True)

            self.assertNotIn(canary, encoded)
            self.assertEqual(1, summary['runs'])
            self.assertEqual(0, summary['known_cost_runs'])
            self.assertEqual(1, summary['unknown_cost_runs'])
            self.assertIsNone(summary['known_cost_usd'])
            self.assertEqual(5, summary['tokens']['input_tokens'])
            self.assertEqual(1, summary['known_usage_runs'])
            self.assertEqual(0, summary['unknown_usage_runs'])

    def test_summary_does_not_treat_invalid_cost_or_token_shapes_as_known(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            path = root / '.agent-runs' / 'F-1' / 'T-1' / 'run' / 'x' / 'provenance.json'
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({
                'provider': 'codex', 'status': 'pass',
                'provider_metadata': {
                    'cost_usd': float('nan'),
                    'usage': {'input_tokens': 4, 'api_key': 'not-a-token-count'},
                },
            }))

            summary = telemetry.summarize(root, 'F-1')

            self.assertEqual(0, summary['known_cost_runs'])
            self.assertEqual(1, summary['unknown_cost_runs'])
            self.assertIsNone(summary['known_cost_usd'])
            self.assertEqual({'input_tokens': 4}, summary['tokens'])

    def test_summary_marks_missing_usage_and_cost_unknown(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            path = root / '.agent-runs' / 'F-1' / 'T-1' / 'run' / 'x' / 'provenance.json'
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({'provider': 'codex', 'status': 'pass'}))

            summary = telemetry.summarize(root, 'F-1')

            self.assertEqual(1, summary['unknown_cost_runs'])
            self.assertEqual(1, summary['unknown_usage_runs'])
            self.assertEqual(0, summary['known_usage_runs'])
            self.assertEqual({}, summary['tokens'])
            self.assertNotIn('saved_cost_usd', summary)
            self.assertNotIn('saved_time_ms', summary)

    def test_reconcile_running_marks_only_owned_orphans_abandoned(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            a = root / '.agent-runs' / 'X' / 'T-1' / 'run-a' / 'one' / 'provenance.json'
            b = root / '.agent-runs' / 'X' / 'T-2' / 'run-a' / 'two' / 'provenance.json'
            c = root / '.agent-runs' / 'X' / 'T-1' / 'run-b' / 'three' / 'provenance.json'
            for path, doc in (
                (a, {'orchestration_id':'run-a','task':'T-1','status':'running'}),
                (b, {'orchestration_id':'run-a','task':'T-2','status':'running'}),
                (c, {'orchestration_id':'run-b','task':'T-1','status':'running'}),
            ):
                telemetry.atomic_write_json(path, doc)
            changed = telemetry.reconcile_running(root, 'X', 'run-a', {'T-1'}, reason='test')
            self.assertEqual([str(a)], changed)
            self.assertEqual('abandoned', json.loads(a.read_text())['status'])
            self.assertEqual('running', json.loads(b.read_text())['status'])
            self.assertEqual('running', json.loads(c.read_text())['status'])


if __name__ == '__main__':
    unittest.main()
