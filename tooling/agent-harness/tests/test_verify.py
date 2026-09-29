import dataclasses
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from verification.model import Family, InvalidPolicy
from verification.fingerprint import changed_surface, observe_inputs, observe_artifacts
from verification.planner import build_plan, evaluate_ready_gate, seal_pass, valid_evidence
from verification.serialization import SafetyPolicy, evidence_record, digest, safe_record
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from test_verification_profile import gate, profile


class PlannerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('config', 'user.name', 'Test')
        (self.root / 'src').mkdir()
        (self.root / 'src/a.py').write_text('before')
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture')
        self.base = self.git('rev-parse', 'HEAD').strip()
        self.p = profile(gate())

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True)

    def family(self, p=None, policy='integration'):
        return Family('family-1', self.base, policy, (p or self.p).content_hash, 'a' * 40)

    def plan(self, p=None, **kwargs):
        return build_plan(self.root, p or self.p, self.family(p), **kwargs)

    def receipt(self, decision, p=None, evidence_id='receipt-1'):
        return seal_pass(decision, decision, family=self.family(p), evidence_id=evidence_id,
                         ownership_token='owner-1', started_at=1, ended_at=2,
                         artifacts=observe_artifacts(self.root, decision.node.gate.produces))

    def test_changed_surface_overlays_and_rename(self):
        (self.root / 'src/a.py').rename(self.root / 'src/renamed.py')
        self.git('add', '.')
        self.git('commit', '-qm', 'rename')
        (self.root / 'staged').write_text('s')
        self.git('add', 'staged')
        (self.root / 'src/renamed.py').write_text('dirty')
        (self.root / 'untracked 雪').write_text('u')
        surface = changed_surface(self.root, self.base)
        self.assertEqual({'src/a.py', 'src/renamed.py', 'staged', 'untracked 雪'}, set(surface.paths))
        self.assertIn('src/a.py', surface.deleted)
        for bad in ['missing', self.git('rev-parse', 'HEAD:src/renamed.py').strip()]:
            with self.assertRaises(InvalidPolicy): changed_surface(self.root, bad)

    def test_ignored_candidate_files_are_visible_but_only_exact_runtime_is_excluded(self):
        (self.root / '.gitignore').write_text('ignored-output/\n', encoding='utf-8')
        ignored = self.root / 'ignored-output' / 'report.json'
        ignored.parent.mkdir(); ignored.write_text('{}', encoding='utf-8')
        other_runtime = self.root / '.agent-runs' / 'untrusted-output' / 'result.log'
        other_runtime.parent.mkdir(parents=True); other_runtime.write_text('x', encoding='utf-8')
        from verification.store import resolve_control_root
        canonical_runtime, _ = resolve_control_root(self.root)
        canonical_runtime.mkdir(parents=True, exist_ok=True)
        (canonical_runtime / 'run.log').write_text('runtime', encoding='utf-8')
        surface = changed_surface(self.root, self.base)
        self.assertIn('ignored-output/report.json', surface.paths)
        self.assertIn('.agent-runs/untrusted-output/result.log', surface.paths)
        self.assertNotIn('.agent-runs/control/verification-v2/run.log', surface.paths)

    def test_occurrences_preserve_duplicates_and_order(self):
        p = profile(gate('dep', command='python3 -V', mandatory=False),
                    gate('unit', depends_on=['dep']))
        commands = ['python3 -m unittest', 'python3 -m unittest', 'python3  -m unittest']
        plan = build_plan(self.root, p, self.family(p, 'task-completion'), task_commands=commands)
        self.assertEqual(['dep', 'unit', 'unit', None], [x.node.profile_gate_id for x in plan.decisions])
        self.assertEqual(4, len({x.node.id for x in plan.decisions}))
        self.assertTrue(all(x.decision == 'RUN_NOW' for x in plan.decisions))
        self.assertTrue(plan.decisions[-1].node.id.startswith('legacy-task-command:0002:'))

    def test_reuse_input_change_and_unrelated_edit(self):
        first = self.plan().decisions[0]
        evidence = {'unit': self.receipt(first)}
        self.assertEqual('ALREADY_GREEN', self.plan(evidence=evidence).decisions[0].decision)
        (self.root / 'unrelated').write_text('x')
        self.assertEqual(first.fingerprint, self.plan().decisions[0].fingerprint)
        for change in ['edit', 'add', 'delete', 'executable']:
            with self.subTest(change=change):
                path = self.root / 'src/a.py'
                if change == 'edit': path.write_text('after!')
                elif change == 'add': (self.root / 'src/new').write_text('x')
                elif change == 'delete': path.unlink()
                else: path.write_text('before'); path.chmod(0o755)
                decision = self.plan(evidence=evidence).decisions[0]
                self.assertEqual('INVALIDATED_BY_THIS_PATCH', decision.decision)
                self.assertEqual('RUN', decision.action)

    def test_symlink_ancestors_never_followed(self):
        (self.root / 'linked').symlink_to(self.root / 'src', target_is_directory=True)
        (self.root / 'broken').symlink_to(self.root / 'missing')
        for pattern in ['linked/a.py', 'linked/**/*.py', '**/*.py', 'broken/child', 'broken']:
            with self.subTest(pattern=pattern):
                observation = observe_inputs(self.root, (pattern,), changed_surface(self.root, self.base))
                self.assertIn('non-cacheable-symlink-input', observation.reasons)

    def test_sensitive_identity_disables_fingerprint(self):
        safety = SafetyPolicy(('CANARY-sensitive-name',))
        (self.root / 'src/CANARY-sensitive-name').write_text('x')
        decision = self.plan(safety=safety).decisions[0]
        self.assertIsNone(decision.fingerprint)
        self.assertEqual('non-cacheable-sensitive-identity', decision.reason)
        self.assertNotIn('CANARY', json.dumps(decision.to_record(safety=safety)))

    def test_ready_rebinds_dependency_receipt_and_artifacts(self):
        p = profile(gate('producer', produces=['out/data']),
                    gate('consumer', command='python3 -V', depends_on=['producer'],
                         consumes=[{'producer': 'producer', 'path': 'out/data'}]))
        (self.root / 'out').mkdir()
        (self.root / 'out/data').write_text('full')
        plan = self.plan(p)
        producer = plan.decisions[0]
        pe = self.receipt(producer, p)
        ready = evaluate_ready_gate(self.root, p, self.family(p), plan.decisions[1].node,
                                    evidence={'producer': pe})
        ce = self.receipt(ready, p, 'consumer-receipt')
        evidence = {'producer': pe, 'consumer': ce}
        self.assertEqual('ALREADY_GREEN', self.plan(p, evidence=evidence).decisions[1].decision)
        (self.root / 'out/data').write_text('focused')
        invalid = self.plan(p, evidence=evidence)
        self.assertTrue(all(x.action == 'RUN' for x in invalid.decisions))
        (self.root / 'out/data').write_text('full')
        self.assertEqual('ALREADY_GREEN', self.plan(p, evidence=evidence).decisions[1].decision)
        evidence['producer'] = self.receipt(producer, p, 'producer-rerun')
        rebound = evaluate_ready_gate(self.root, p, self.family(p), ready.node, evidence=evidence)
        self.assertNotEqual(ready.fingerprint, rebound.fingerprint)

    def test_drift_same_size_mtime_and_corrupt_receipt(self):
        before = self.plan().decisions[0]
        path = self.root / 'src/a.py'
        stat = path.stat()
        path.write_text('AFTER!')
        os.utime(path, ns=(stat.st_atime_ns, stat.st_mtime_ns))
        after = self.plan().decisions[0]
        with self.assertRaisesRegex(ValueError, 'stale-input'):
            seal_pass(before, after, family=self.family(), evidence_id='r', ownership_token='o',
                      started_at=1, ended_at=2, artifacts=())
        self.assertEqual('RUN_NOW', self.plan(evidence={'unit': {'status': 'pass'}}).decisions[0].decision)

    def test_continuation_and_cross_repository(self):
        family = self.family(policy='task-completion')
        plan = build_plan(self.root, self.p, family, task_commands=['python3 -m unittest'])
        first = plan.decisions[0]
        receipt = seal_pass(first, first, family=family, evidence_id='r', ownership_token='o',
                            started_at=1, ended_at=2, artifacts=())
        args = dict(task_commands=['python3 -m unittest'], evidence={first.node.id: receipt})
        self.assertEqual('RUN_NOW', build_plan(self.root, self.p, family, **args).decisions[0].decision)
        self.assertEqual('ALREADY_GREEN', build_plan(self.root, self.p, family, continuation=True,
                                                    **args).decisions[0].decision)
        other = dataclasses.replace(family, id='other-family')
        self.assertEqual('RUN_NOW', build_plan(self.root, self.p, other, continuation=True,
                                              **args).decisions[0].decision)

    def test_fingerprint_policy_and_probe_dimensions(self):
        baseline = self.plan().decisions[0].fingerprint
        for changes in [dict(command='python3 -V'), dict(cwd='src'), dict(sandbox='off'),
                        dict(retry_policy='allow'), dict(retry_controls=['disable-retries']),
                        dict(inputs=['src/a.py']), dict(critical=True)]:
            with self.subTest(changes=changes):
                self.assertNotEqual(baseline, self.plan(profile(gate(**changes))).decisions[0].fingerprint)
        p = profile(gate(probes=[{'id': 'python', 'format': 'version'}]))
        one = self.plan(p, probes={'unit': {'python': '3.14.0'}}).decisions[0]
        two = self.plan(p, probes={'unit': {'python': '3.14.1'}}).decisions[0]
        self.assertNotEqual(one.fingerprint, two.fingerprint)
        unsafe = self.plan(p, probes={'unit': {'python': 'CANARY-probe'}},
                           safety=SafetyPolicy(('CANARY-probe',))).decisions[0]
        self.assertIsNone(unsafe.fingerprint)
        self.assertEqual('non-cacheable-sensitive-identity', unsafe.reason)
        changed = dataclasses.replace(self.family(), policy_checkpoint='b'*40)
        self.assertNotEqual(baseline, build_plan(self.root, self.p, changed).decisions[0].fingerprint)
        for changes in [dict(opaque_environment=True), dict(opaque_external_state=True)]:
            self.assertIsNone(self.plan(profile(gate(**changes))).decisions[0].fingerprint)

    def test_non_ancestor_base_rejected(self):
        self.git('checkout', '-qb', 'side')
        (self.root / 'side').write_text('side')
        self.git('add', '.')
        self.git('commit', '-qm', 'side')
        side = self.git('rev-parse', 'HEAD').strip()
        self.git('checkout', '-q', '--detach', self.base)
        with self.assertRaises(InvalidPolicy): changed_surface(self.root, side)

    def test_invalid_receipts_never_reuse(self):
        first = self.plan().decisions[0]
        good = self.receipt(first)
        for changes in [dict(schema_version=1), dict(schema_version=99), dict(status='running'),
                        dict(ownership_token=''), dict(process_invocations=2), dict(exit_code=1),
                        dict(receipt_hash='0'*64), dict(post_fingerprint='f'*64),
                        dict(repository_id='0'*64), dict(ended_at=0)]:
            with self.subTest(changes=changes):
                bad = dataclasses.replace(good, **changes)
                self.assertEqual('RUN', self.plan(evidence={'unit': bad}).decisions[0].action)

    def test_repository_identity_prevents_cross_repository_reuse(self):
        first = self.plan().decisions[0]
        good = self.receipt(first)
        with tempfile.TemporaryDirectory() as other:
            subprocess.run(['git', 'clone', '-q', str(self.root), other], check=True)
            decision = build_plan(pathlib.Path(other), self.p, self.family(), evidence={'unit': good}).decisions[0]
            self.assertNotEqual(first.repository_id, decision.repository_id)
            self.assertEqual('RUN', decision.action)

    def test_artifact_missing_symlink_and_noncacheable_producer(self):
        p = profile(gate('producer', cacheable=False, produces=['out/data']),
                    gate('consumer', command='python3 -V', consumes=[{'producer': 'producer', 'path': 'out/data'}]))
        decisions = self.plan(p).decisions
        self.assertEqual(['producer', 'consumer'], [d.node.id for d in decisions])
        self.assertEqual('dependency-not-reusable', decisions[1].reason)
        (self.root / 'out').mkdir()
        (self.root / 'out/data').symlink_to(self.root / 'src/a.py')
        with self.assertRaises(ValueError): observe_artifacts(self.root, ('out/data',))
        (self.root / 'out/data').unlink()
        with self.assertRaises(ValueError): observe_artifacts(self.root, ('out/data',))
        with self.assertRaises(InvalidPolicy): observe_artifacts(self.root, ('../escape',))

    def test_selection_union_and_order(self):
        p = profile(gate('dep', command='python3 -V', mandatory=False),
                    gate('selected', command='python3 -m unittest selected', mandatory=True,
                         applicability=['src/**'], depends_on=['dep']),
                    gate('other', command='python3 -m unittest other', applicability=['frontend/**']),
                    gate('task', command='python3 -m unittest task', mandatory=False))
        (self.root / 'src/a.py').write_text('changed')
        plan = build_plan(self.root, p, self.family(p, 'task-completion'),
                          task_commands=['python3 -m unittest task'])
        self.assertEqual(['dep', 'selected', 'task'], [d.node.profile_gate_id for d in plan.decisions])

    def test_sensitive_environment_and_secret_files(self):
        with mock.patch.dict(os.environ, {'API_TOKEN': 'CANARY-environment'}):
            (self.root / 'src/CANARY-environment').write_text('x')
            decision = self.plan().decisions[0]
            self.assertIsNone(decision.fingerprint)
            self.assertNotIn('CANARY-environment', json.dumps(decision.to_record()))
        (self.root / 'src/.env').write_text('private')
        decision = self.plan().decisions[0]
        self.assertIsNone(decision.fingerprint)

    def test_observations_share_content_reads_but_postcheck_is_fresh(self):
        from verification import fingerprint
        p = profile(*(gate('g'+str(i), command='python3 -m unittest t'+str(i)) for i in range(10)))
        real_open = fingerprint.os.open
        with mock.patch.object(fingerprint.os, 'open', wraps=real_open) as opened:
            self.plan(p)
            self.assertEqual(1, sum(str(c.args[0]) == str(self.root / 'src/a.py') for c in opened.call_args_list))
            self.plan(p)
            self.assertEqual(2, sum(str(c.args[0]) == str(self.root / 'src/a.py') for c in opened.call_args_list))

    def test_initial_freshness_cannot_hide_unresolved_dependency(self):
        p = profile(gate('producer', command='python3 -V', mandatory=False),
                    gate('unit', depends_on=['producer']))
        family = self.family(p, 'task-completion')
        plan = build_plan(self.root, p, family, task_commands=['python3 -m unittest'])
        decision = plan.decisions[-1]
        self.assertEqual('fresh-task-completion', decision.reason)
        with self.assertRaisesRegex(ValueError, 'dependency-not-reusable'):
            seal_pass(decision, decision, family=family, evidence_id='r', ownership_token='o',
                      started_at=1, ended_at=2, artifacts=())

    def test_unmapped_integration_command_stays_required(self):
        plan = self.plan(task_commands=['python3 -m unittest legacy'])
        self.assertEqual(2, len(plan.decisions))
        self.assertEqual('legacy-task-command', plan.decisions[-1].reason)
        self.assertFalse(plan.decisions[-1].node.occurrence)

    def test_secret_content_and_cli_error_are_not_structured(self):
        canary = 'CANARY-content-123456'
        (self.root / 'src/a.py').write_text(canary)
        decision = self.plan(safety=SafetyPolicy((canary,))).decisions[0]
        self.assertIsNone(decision.fingerprint)
        self.assertEqual('non-cacheable-sensitive-identity', decision.reason)
        cli = pathlib.Path(__file__).resolve().parents[1] / 'verify.py'
        result = subprocess.run([sys.executable, str(cli), canary], capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertNotIn(canary, result.stdout + result.stderr)

    def test_malformed_receipt_identities_never_reuse(self):
        first = self.plan().decisions[0]
        good = self.receipt(first)
        identities = ('evidence_id', 'family_id', 'ownership_token', 'gate_id',
                      'repository_id', 'profile_hash', 'policy_checkpoint', 'command_hash',
                      'pre_fingerprint', 'post_fingerprint', 'receipt_hash')
        for field in identities:
            for value in (None, '', 42, [], 'bad identity'):
                with self.subTest(field=field, value=value):
                    raw = evidence_record(good, include_receipt=False)
                    if field != 'receipt_hash': raw[field] = value
                    bad = dataclasses.replace(good, **{field: value})
                    if field != 'receipt_hash':
                        bad = dataclasses.replace(bad, receipt_hash=digest(raw))
                    self.assertFalse(valid_evidence(bad))
                    self.assertEqual('RUN', self.plan(evidence={'unit': bad}).decisions[0].action)
                    with self.assertRaises(ValueError): evidence_record(bad)
        for value in (None, '', 42, [], 'bad identity'):
            with self.subTest(seal_id=value), self.assertRaises(ValueError):
                seal_pass(first, first, family=self.family(), evidence_id=value,
                          ownership_token='owner', started_at=1, ended_at=2, artifacts=())

    def test_receipt_structured_boundary_rejects_missing_fields(self):
        from verification.serialization import validate_evidence_record
        good = evidence_record(self.receipt(self.plan().decisions[0]))
        self.assertEqual(good, validate_evidence_record(good))
        for field in good:
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_evidence_record({k: v for k, v in good.items() if k != field})
        for changes in ({'schema_version': True}, {'exit_code': False},
                        {'repository_id': 'a' * 40}, {'started_at': True},
                        {'artifacts': [{'path': 'out/data', 'kind': 'file',
                                        'content_hash': None, 'executable': False}]},
                        {'dependencies': [{'gate_id': 'dep', 'evidence_id': None,
                                           'fingerprint': 'a' * 64}]}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                validate_evidence_record(dict(good, **changes))

    def test_artifact_reads_are_shared_only_within_evaluation(self):
        from verification import fingerprint
        p = profile(gate('producer', inputs=[], produces=['out/data']),
                    *(gate('consumer' + str(i), inputs=[], command='python3 -m unittest c' + str(i),
                           consumes=[{'producer': 'producer', 'path': 'out/data'}]) for i in range(100)))
        (self.root / 'out').mkdir()
        path = self.root / 'out/data'
        path.write_text('full')
        producer = self.plan(p).decisions[0]
        evidence = {'producer': self.receipt(producer, p)}
        with mock.patch.object(fingerprint.os, 'open', wraps=fingerprint.os.open) as opened:
            plan = self.plan(p, evidence=evidence)
            def reads(): return sum(str(c.args[0]) == str(path) for c in opened.call_args_list)
            self.assertEqual(1, reads())
            self.plan(p, evidence=evidence)
            self.assertEqual(2, reads())
            consumer = plan.decisions[1]
            evidence[consumer.node.id] = self.receipt(consumer, p, 'consumer-pass')
            ready = evaluate_ready_gate(self.root, p, self.family(p), consumer.node, evidence=evidence)
            self.assertEqual(3, reads())
            self.assertEqual('ALREADY_GREEN', ready.decision)
            path.write_text('edit')
            after = evaluate_ready_gate(self.root, p, self.family(p), consumer.node, evidence=evidence)
            self.assertEqual(4, reads())
            self.assertEqual('RUN', after.action)
            self.assertNotEqual(ready.fingerprint, after.fingerprint)
            with self.assertRaisesRegex(ValueError, 'stale-input'):
                seal_pass(ready, after, family=self.family(p), evidence_id='post',
                          ownership_token='owner', started_at=1, ended_at=2, artifacts=())

    def test_rejected_artifact_observation_is_shared_and_fresh(self):
        from verification import fingerprint
        p = profile(gate('producer', inputs=[], produces=['out/data']),
                    *(gate('consumer' + str(i), inputs=[], command='python3 -m unittest c' + str(i),
                           consumes=[{'producer': 'producer', 'path': 'out/data'}]) for i in range(100)))
        (self.root / 'out').mkdir()
        path = self.root / 'out/data'
        path.write_text('harmless synthetic phrase')
        initial = self.plan(p)
        evidence = {'producer': self.receipt(initial.decisions[0], p)}
        safety = SafetyPolicy(('harmless synthetic phrase',))
        with mock.patch.object(fingerprint.os, 'open', wraps=fingerprint.os.open) as opened:
            def reads(): return sum(str(c.args[0]) == str(path) for c in opened.call_args_list)
            for expected in (1, 2):
                plan = self.plan(p, evidence=evidence, safety=safety)
                self.assertEqual(expected, reads())
                self.assertEqual(100, len(plan.decisions[1:]))
                for consumer in plan.decisions[1:]:
                    self.assertEqual('RUN', consumer.action)
                    self.assertEqual('dependency-not-reusable', consumer.reason)
                    self.assertFalse(consumer.dependencies_reusable)
                    with self.assertRaisesRegex(ValueError, 'dependency-not-reusable'):
                        self.receipt(consumer, p)
            ready = evaluate_ready_gate(self.root, p, self.family(p), consumer.node,
                                        evidence=evidence, safety=safety)
            self.assertEqual(3, reads())
            self.assertEqual('dependency-not-reusable', ready.reason)
            self.assertEqual('RUN', ready.action)
            path.write_text('safe replacement')
            rebound = evaluate_ready_gate(self.root, p, self.family(p), consumer.node,
                                          evidence=evidence, safety=safety)
            self.assertEqual(4, reads())
            self.assertNotEqual(ready.fingerprint, rebound.fingerprint)
            self.assertEqual('RUN', rebound.action)
            self.assertFalse(rebound.dependencies_reusable)
        session = fingerprint.ObservationSession(self.root, changed_surface(self.root, self.base), safety)
        path.write_text('harmless synthetic phrase')
        with mock.patch.object(fingerprint.os, 'open', wraps=fingerprint.os.open) as opened:
            for _ in range(100):
                self.assertEqual((None, 'non-cacheable-sensitive-identity'), session.identity('out/data'))
            self.assertEqual(1, sum(str(c.args[0]) == str(path) for c in opened.call_args_list))
        inputs = profile(*(gate('g' + str(i), command='python3 -m unittest g' + str(i))
                           for i in range(100)))
        (self.root / 'src/a.py').write_text('harmless synthetic phrase')
        with mock.patch.object(fingerprint.os, 'open', wraps=fingerprint.os.open) as opened:
            for decision in self.plan(inputs, safety=safety).decisions:
                self.assertEqual('RUN', decision.action)
                self.assertEqual('non-cacheable-sensitive-identity', decision.reason)
                self.assertFalse(decision.cacheable)
                self.assertIsNone(decision.fingerprint)
            self.assertEqual(1, sum(str(c.args[0]) == str(self.root / 'src/a.py')
                                    for c in opened.call_args_list))

    def test_root_and_nested_secret_paths_are_never_read(self):
        from verification import fingerprint
        for name in ('.env', 'src/.env', '.env.local', 'src/.env.local',
                     'credentials.json', 'src/credentials.json'):
            with self.subTest(path=name):
                path = self.root / name
                path.write_text('opaque-private-fixture')
                p = profile(gate(inputs=[name]))
                with mock.patch.object(fingerprint.os, 'open', wraps=fingerprint.os.open) as opened:
                    decision = self.plan(p).decisions[0]
                    self.assertEqual('non-cacheable-sensitive-identity', decision.reason)
                    self.assertFalse(decision.cacheable)
                    self.assertIsNone(decision.fingerprint)
                    self.assertFalse(any(str(c.args[0]) == str(path) for c in opened.call_args_list))
                with self.assertRaises(ValueError): safe_record({'path': name})
                with self.assertRaises(ValueError): observe_artifacts(self.root, (name,))
        (self.root / '.editorconfig').write_text('root = true')
        control = self.plan(profile(gate(inputs=['.editorconfig']))).decisions[0]
        self.assertTrue(control.cacheable)
        self.assertIsNotNone(control.fingerprint)
        self.assertEqual({'path': '.editorconfig'}, safe_record({'path': '.editorconfig'}))

    def test_receipt_validation_and_artifact_index_are_evaluation_local(self):
        from verification import planner
        from verification.model import FileIdentity
        paths = [f'out/data{i:04d}' for i in range(1000)]
        p = profile(gate('producer', inputs=[], produces=paths),
                    *(gate('c' + str(i), inputs=[], command='python3 -m unittest c' + str(i),
                           consumes=[{'producer': 'producer', 'path': paths[-1]}]) for i in range(100)))
        (self.root / 'out').mkdir()
        for path in paths: (self.root / path).write_text('safe artifact')
        producer = self.plan(p).decisions[0]
        good = self.receipt(producer, p)
        evidence = {'producer': good}
        real_valid, real_digest, real_hash = planner.valid_evidence, planner.digest, FileIdentity.__hash__
        counts = {'valid': 0, 'digest': 0, 'artifact_hash': 0}
        def validate(value, **kwargs):
            if value is not None: counts['valid'] += 1
            return real_valid(value, **kwargs)
        def digest(value):
            if isinstance(value, dict) and 'ownership_token' in value: counts['digest'] += 1
            return real_digest(value)
        def artifact_hash(value):
            counts['artifact_hash'] += 1
            return real_hash(value)
        with mock.patch.object(planner, 'valid_evidence', side_effect=validate), \
             mock.patch.object(planner, 'digest', side_effect=digest), \
             mock.patch.object(FileIdentity, '__hash__', artifact_hash):
            for iteration in (1, 2):
                plan = self.plan(p, evidence=evidence)
                self.assertTrue(all(d.dependencies_reusable for d in plan.decisions))
                self.assertEqual(iteration, counts['valid'])
                self.assertEqual(iteration, counts['digest'])
                # A index insertions plus C constant-time set lookups, per evaluation.
                self.assertEqual(iteration * 1100, counts['artifact_hash'])
            ready = evaluate_ready_gate(self.root, p, self.family(p), plan.decisions[-1].node,
                                        evidence=evidence)
            self.assertTrue(ready.dependencies_reusable)
            self.assertEqual({'valid': 3, 'digest': 3, 'artifact_hash': 3201}, counts)
            evidence['producer'] = dataclasses.replace(good, receipt_hash='0' * 64)
            invalid = self.plan(p, evidence=evidence)
            self.assertEqual(4, counts['valid'])
            self.assertEqual(4, counts['digest'])
            self.assertEqual(3201, counts['artifact_hash'])
            self.assertTrue(all(d.action == 'RUN' for d in invalid.decisions))
            self.assertTrue(all(not d.dependencies_reusable for d in invalid.decisions[1:]))
            # A validly sealed manifest that omits the consumed artifact stays absent.
            missing = dataclasses.replace(good, artifacts=good.artifacts[:-1])
            missing = dataclasses.replace(missing, receipt_hash=real_digest(
                evidence_record(missing, include_receipt=False)))
            evidence['producer'] = missing
            absent = self.plan(p, evidence=evidence)
            self.assertEqual(5, counts['valid'])
            self.assertEqual(5, counts['digest'])
            self.assertEqual(4300, counts['artifact_hash'])
            self.assertTrue(all(not d.dependencies_reusable for d in absent.decisions[1:]))
            evidence['producer'] = good
            (self.root / paths[-1]).write_text('drift')
            drift = evaluate_ready_gate(self.root, p, self.family(p), ready.node, evidence=evidence)
            self.assertEqual(6, counts['valid'])
            self.assertEqual(6, counts['digest'])
            self.assertEqual('RUN', drift.action)
            self.assertFalse(drift.dependencies_reusable)
            self.assertNotEqual(ready.fingerprint, drift.fingerprint)


    def test_raw_log_profile_boundary(self):
        for name in ('.agent-runs/provider-1/stdout.log',
                     '.agent-runs/provider-1/stderr.log',
                     '.agent-runs/run/verify-00.stdout.log',
                     '.agent-runs/control/verification-v2/runs/f/a/logs/g/a.log'):
            for field in ('inputs', 'produces'):
                with self.subTest(path=name, field=field), self.assertRaises(InvalidPolicy):
                    profile(gate(**{field: [name]}))
        for pattern in ('.agent-runs/**/stdout.log', '.agent-runs/**/*.log', '.agent-runs/**'):
                with self.subTest(pattern=pattern), self.assertRaises(InvalidPolicy):
                    profile(gate(inputs=[pattern]))

    def test_provider_envelope_profile_boundary_and_controls(self):
        name = '.agent-runs/provider-1/provider-envelope.json'
        for field in ('inputs', 'produces'):
            with self.subTest(field=field), self.assertRaises(InvalidPolicy):
                profile(gate(**{field: [name]}))
        self.assertTrue(profile(gate(produces=['out/result.json'])).gates[0].cacheable)
        self.assertTrue(profile(gate(produces=['out/product.log'])).gates[0].cacheable)

    def test_raw_logs_bypass_observation_and_sealing(self):
        from verification import fingerprint
        from verification.model import FileIdentity
        for name in ('.agent-runs/provider-1/stdout.log',
                     '.agent-runs/provider-1/stderr.log',
                     '.agent-runs/run/verify-00.stderr.log',
                     '.agent-runs/control/verification-v2/runs/f/a/logs/g/a.log'):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('ordinary diagnostic output')
            for field in ('inputs', 'produces'):
                with self.subTest(path=name, field=field):
                    g = dataclasses.replace(self.p.gates[0], **{field: (name,)})
                    p = dataclasses.replace(self.p, gates=(g,))
                    with mock.patch.object(fingerprint.os, 'open', wraps=fingerprint.os.open) as opened:
                        observation = observe_inputs(self.root, (name,), changed_surface(self.root, self.base))
                        self.assertEqual((), observation.manifest)
                        self.assertTrue(observation.reasons)
                        with self.assertRaises(ValueError): observe_artifacts(self.root, (name,))
                        decision = self.plan(p).decisions[0]
                        self.assertIsNone(decision.fingerprint)
                        self.assertFalse(decision.cacheable)
                        self.assertEqual('RUN', decision.action)
                        self.assertFalse(any(str(c.args[0]) == str(path) for c in opened.call_args_list))
                    forged_artifacts = (FileIdentity(name, 'file', 'a' * 64),) if field == 'produces' else ()
                    # Even caller-forged decisions cannot bypass sealing policy.
                    forged = dataclasses.replace(decision, fingerprint='b' * 64, cacheable=True)
                    with self.assertRaises(ValueError):
                        seal_pass(forged, forged, family=self.family(p), evidence_id='r',
                                  ownership_token='o', started_at=1, ended_at=2,
                                  artifacts=forged_artifacts)
                    good = self.receipt(self.plan().decisions[0])
                    bad = dataclasses.replace(good, artifacts=(FileIdentity(name, 'file', 'a' * 64),))
                    with self.assertRaises(ValueError): evidence_record(bad)
                    with self.assertRaises(ValueError): safe_record({'path': name})
                    self.assertFalse(valid_evidence(bad))
                    self.assertEqual('RUN', self.plan(p, evidence={'unit': bad}).decisions[0].action)
                    ready = evaluate_ready_gate(self.root, p, self.family(p), decision.node,
                                                evidence={'unit': bad})
                    self.assertEqual('RUN', ready.action)
            path.unlink()
            self.assertEqual('RUN', self.plan(p).decisions[0].action)
        # Ignored runtime paths must not turn an in-memory glob into an empty reusable identity.
        p = dataclasses.replace(self.p, gates=(dataclasses.replace(self.p.gates[0],
                               inputs=('.agent-runs/**/*.log',)),))
        self.assertIsNone(self.plan(p).decisions[0].fingerprint)

    def test_generated_log_artifact_remains_reusable(self):
        name = 'out/product.log'
        path = self.root / name
        path.parent.mkdir()
        path.write_text('generated product artifact')
        p = profile(gate(produces=[name]))
        good = self.receipt(self.plan(p).decisions[0], p)
        evidence = {'unit': good}
        self.assertEqual('ALREADY_GREEN', self.plan(p, evidence=evidence).decisions[0].decision)
        diagnostic = self.root / '.agent-runs/provider-1/stdout.log'
        diagnostic.parent.mkdir(parents=True)
        diagnostic.write_text('diagnostic output')
        self.assertEqual('ALREADY_GREEN', self.plan(p, evidence=evidence).decisions[0].decision)
        diagnostic.unlink()
        self.assertEqual('ALREADY_GREEN', self.plan(p, evidence=evidence).decisions[0].decision)
        control = profile(gate(inputs=[name]))
        receipt = self.receipt(self.plan(control).decisions[0], control)
        self.assertEqual('ALREADY_GREEN', self.plan(control, evidence={'unit': receipt}).decisions[0].decision)

    def test_provider_envelope_excluded_at_all_authority_boundaries(self):
        from verification import fingerprint
        from verification.model import FileIdentity
        name = '.agent-runs/provider-1/provider-envelope.json'
        raw = self.root / name
        raw.parent.mkdir(parents=True)
        raw.write_text('{"result":"untrusted raw provider output"}')

        for field in ('inputs', 'produces'):
            g = dataclasses.replace(self.p.gates[0], **{field: (name,)})
            p = dataclasses.replace(self.p, gates=(g,))
            with self.subTest(field=field):
                with mock.patch.object(fingerprint.os, 'open', wraps=fingerprint.os.open) as opened:
                    decision = build_plan(self.root, p, self.family(p)).decisions[0]
                    self.assertIsNone(decision.fingerprint)
                    self.assertFalse(decision.cacheable)
                    self.assertNotEqual('ALREADY_GREEN', decision.decision)
                    self.assertFalse(any(str(c.args[0]) == str(raw) for c in opened.call_args_list))
                with self.assertRaises(ValueError): observe_artifacts(self.root, (name,))
                direct_gate = dataclasses.replace(g, cacheable=False)
                direct_profile = dataclasses.replace(p, gates=(direct_gate,))
                direct_decision = build_plan(self.root, direct_profile, self.family(direct_profile)).decisions[0]
                self.assertIsNone(direct_decision.fingerprint)
                forged = dataclasses.replace(direct_decision, fingerprint='b' * 64, cacheable=True)
                with self.assertRaises(ValueError):
                    seal_pass(forged, forged, family=self.family(direct_profile), evidence_id='r',
                              ownership_token='o', started_at=1, ended_at=2,
                              artifacts=(FileIdentity(name, 'file', 'a' * 64),) if field == 'produces' else ())
                good = self.receipt(self.plan().decisions[0])
                bad = dataclasses.replace(good, artifacts=(FileIdentity(name, 'file', 'a' * 64),))
                with self.assertRaises(ValueError): evidence_record(bad)
                self.assertFalse(valid_evidence(bad))
                with self.assertRaises(ValueError): safe_record({'path': name})

        p = profile(gate())
        baseline = self.plan(p).decisions[0]
        receipt = self.receipt(baseline, p)
        evidence = {'unit': receipt}
        before = self.plan(p, evidence=evidence).decisions[0]
        self.assertEqual('ALREADY_GREEN', before.decision)
        raw.unlink()
        after = self.plan(p, evidence=evidence).decisions[0]
        self.assertEqual('ALREADY_GREEN', after.decision)
        self.assertEqual(before.fingerprint, after.fingerprint)
        raw.write_text('{"other":"diagnostic"}')
        self.assertEqual('ALREADY_GREEN', self.plan(p, evidence=evidence).decisions[0].decision)
        for name in ('out/product.log', 'out/result.json'):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('legitimate product artifact')
            control = profile(gate(produces=[name]))
            good = self.receipt(self.plan(control).decisions[0], control)
            self.assertEqual('ALREADY_GREEN', self.plan(control, evidence={'unit': good}).decisions[0].decision)

    def test_direct_plan_rejects_caller_selected_profile_path(self):
        path = self.root / 'profile.json'
        path.write_text(json.dumps({'schema_version': 1, 'gates': [gate()]}))
        from verification.profile import load_profile
        p = load_profile(path)
        cli = pathlib.Path(__file__).resolve().parents[1] / 'verify.py'
        result = subprocess.run([sys.executable, str(cli), 'plan', '--repo', str(self.root),
                                 '--profile', str(path), '--base-sha', self.base,
                                 '--family-id', 'family-1', '--policy-checkpoint', 'a'*40],
                                capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertEqual({'status': 'invalid-policy'}, json.loads(result.stdout))


class VerificationGrantCliTest(unittest.TestCase):
    def test_signed_retry_grant_import_uses_verifier_only_store_api(self):
        import contextlib
        import io
        import verify
        envelope = {'schema_version': 1, 'protocol': 'critical-gate-retry-grant-v1',
                    'grant_id': 'signed-grant', 'signature': 'fixture'}
        with tempfile.TemporaryDirectory() as temp:
            grant_path = pathlib.Path(temp) / 'grant.json'
            grant_path.write_text(json.dumps(envelope), encoding='utf-8')
            store = mock.Mock()
            store.import_failure_grant.return_value = {'grant_id': 'signed-grant'}
            output = io.StringIO()
            with mock.patch('verification.store.VerificationStore', return_value=store), \
                 contextlib.redirect_stdout(output):
                result = verify.main(['grant-import', '--repo', temp,
                                      '--grant-file', str(grant_path)])
            self.assertEqual(0, result)
            self.assertEqual({'status': 'grant-imported', 'grant_id': 'signed-grant'},
                             json.loads(output.getvalue()))
            store.import_failure_grant.assert_called_once_with(envelope)

    def test_integration_cli_resolves_exact_plan_before_executor(self):
        import contextlib
        import io
        import types
        import verify
        record = {'plan_id': 'verification-plan-v1:sha256:' + 'a' * 64,
                  'lifecycle_generation': 4}
        profile = object()
        plan = object()
        unit = {'unit_id': 'unit-current', 'obligation_ids': ['obligation-current']}
        result = types.SimpleNamespace(outcome='PASS', to_record=lambda: {'outcome': 'PASS'})
        output = io.StringIO()
        with mock.patch('verification.authority.resolve_execution',
                        return_value=(record, profile, plan, [unit])) as resolve, \
             mock.patch('verification.executor.execute_plan', return_value=result) as execute, \
             mock.patch('verification.store.VerificationStore'), \
             contextlib.redirect_stdout(output):
            code = verify.main(['run', '--mode', 'integration', '--repo', '.',
                '--plan-id', record['plan_id'], '--unit-id', 'unit-current'])
        self.assertEqual(0, code)
        resolve.assert_called_once()
        self.assertEqual(record['plan_id'], resolve.call_args.args[1])
        self.assertEqual('unit-current', resolve.call_args.kwargs['unit_id'])
        execute.assert_called_once()
        self.assertEqual(record, execute.call_args.kwargs['authority_context'])
        self.assertEqual([unit['unit_id']], json.loads(output.getvalue())['execution_units'])


if __name__ == '__main__': unittest.main()
