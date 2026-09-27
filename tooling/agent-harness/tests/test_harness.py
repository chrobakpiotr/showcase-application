import argparse
import importlib.util
import json
import os
import pathlib
import tempfile
import unittest

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

    def feature(self, tasks=None, spec_text=None):
        feature = self.root / 'docs' / 'specs' / 'TST-001'
        feature.mkdir(parents=True)
        roles = self.root / 'docs' / 'agentic-sdd' / 'agents'
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

    def test_stale_worktree_legacy_state_cannot_override_or_seed_authority(self):
        import subprocess
        feature = self.feature()
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-q', '-m', 'base'], cwd=self.root, check=True)
        worktree = self.root.parent / f'{self.root.name}{harness.WORKTREE_ROOT_SUFFIX}' / 'TST-001' / 'T-001'
        subprocess.run(['git', 'worktree', 'add', '--quiet', str(worktree), '-b', 'agent/TST-001/T-001', 'HEAD'],
                       cwd=self.root, check=True)
        stale = harness.initial_state(feature, harness.load_json(feature / 'tasks.json'))
        stale['tasks']['T-001']['status'] = 'completed'
        legacy = harness.legacy_worktree_state_candidates(feature)[0]
        legacy.parent.mkdir(parents=True)
        legacy.write_text(json.dumps(stale))
        with self.assertRaises(SystemExit):
            harness.load_state(feature, harness.load_json(feature / 'tasks.json'))
        self.assertFalse(harness.state_path(feature).exists())
        subprocess.run(['git', 'worktree', 'remove', '--force', str(worktree)], cwd=self.root, check=True)

    def test_legacy_main_state_is_adopted_and_conflicting_worktree_copy_fails_safe(self):
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
        adopted = harness.load_state(feature, harness.load_json(feature / 'tasks.json'))
        self.assertEqual('failed', adopted['tasks']['T-001']['status'])
        canonical_before = harness.state_path(feature).read_text()
        stale = dict(state)
        stale['tasks'] = dict(state['tasks'])
        stale['tasks']['T-001'] = {'status': 'completed', 'attempts': 1}
        legacy = harness.legacy_worktree_state_candidates(feature)[0]
        legacy.parent.mkdir(parents=True)
        legacy.write_text(json.dumps(stale))
        self.assertEqual('failed', harness.load_state(feature, harness.load_json(feature / 'tasks.json'))['tasks']['T-001']['status'])
        self.assertEqual(canonical_before, harness.state_path(feature).read_text())
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
        self.assertIn('T-001', harness.ready_ids(doc, updated))

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
