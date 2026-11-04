import argparse
import contextlib
import io
import importlib.util
import pathlib
import tempfile
import unittest
from unittest import mock
import json
import os
import shutil
import subprocess

import structured_agent_fake  # tests/ is on the path under discovery, as for test_harness

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'design.py'
spec = importlib.util.spec_from_file_location('sdd_design', MODULE_PATH)
design = importlib.util.module_from_spec(spec)
assert spec.loader is not None
import sys
sys.modules[spec.name] = design
spec.loader.exec_module(design)


class DesignTest(unittest.TestCase):
    def test_codex_design_output_schema_uses_closed_objects_and_required_properties(self):
        schema_path = MODULE_PATH.parent / 'schemas' / 'design-result.schema.json'
        schema = json.loads(schema_path.read_text(encoding='utf-8'))
        object_nodes = []

        def visit(node):
            if isinstance(node, dict):
                node_type = node.get('type')
                if node_type == 'object' or isinstance(node_type, list) and 'object' in node_type:
                    object_nodes.append(node)
                for value in node.values():
                    visit(value)
            elif isinstance(node, list):
                for value in node:
                    visit(value)

        visit(schema)
        self.assertEqual(3, len(object_nodes))
        for node in object_nodes:
            self.assertIs(node.get('additionalProperties'), False)
            self.assertEqual(set(node.get('properties', {})), set(node.get('required', [])))

    def test_design_result_contract_accepts_representative_grill_results(self):
        base = {
            'summary': 'Review completed.', 'blocking_questions': [], 'non_blocking_risks': [],
            'prototype_recommendations': [], 'findings': [], 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': [], 'recommendation': None,
        }
        for status in ('pass', 'fail'):
            design.validate_design_result({'status': status, **base})

        with_recommendation = {
            **base,
            'prototype_recommendations': [{
                'id': None,
                'question': 'Which approach meets the latency target?',
                'rationale': None,
                'decision_criteria': None,
                'candidates': [{'id': None, 'approach': 'Measure candidate A'}],
            }],
        }
        design.validate_design_result({'status': 'pass', **with_recommendation})

    def test_validate_config_accepts_bakeoff(self):
        config = {
            'required_for_orchestration': True,
            'grill': 'always',
            'prototype': 'auto',
            'architecture_grill': 'always',
            'prototype_max_parallel': 3,
            'prototype_questions': [{
                'id': 'P-001', 'question': 'Which strategy?', 'rationale': 'Reduce uncertainty',
                'decision_criteria': ['correctness', 'complexity'],
                'candidates': [
                    {'id': 'A', 'approach': 'one'},
                    {'id': 'B', 'approach': 'two'},
                ],
            }],
        }
        self.assertEqual([], design.validate_config(config))

    def test_validate_config_rejects_duplicate_candidate(self):
        config = {
            'prototype_questions': [{
                'id': 'P-001', 'question': 'Q', 'rationale': 'R',
                'decision_criteria': ['correctness'],
                'candidates': [{'id': 'A', 'approach': 'one'}, {'id': 'A', 'approach': 'two'}],
            }]
        }
        self.assertTrue(any('duplicate candidate' in error for error in design.validate_config(config)))

    def test_medium_risk_auto_runs_grills_but_not_empty_prototype(self):
        with tempfile.TemporaryDirectory() as tmp:
            feature = pathlib.Path(tmp)
            (feature / 'spec.md').write_text('# X\nRisk: medium\n')
            config = design.load_config(feature)
            args = argparse.Namespace(grill=None, prototype=None, architecture_grill=None)
            plan = design.stage_plan(feature, config, args)
            self.assertTrue(plan.spec_grill)
            self.assertFalse(plan.prototype)
            self.assertTrue(plan.architecture_grill)

    def test_low_risk_auto_skips_grills(self):
        with tempfile.TemporaryDirectory() as tmp:
            feature = pathlib.Path(tmp)
            (feature / 'spec.md').write_text('# X\nRisk: low\n')
            config = design.load_config(feature)
            args = argparse.Namespace(grill=None, prototype=None, architecture_grill=None)
            plan = design.stage_plan(feature, config, args)
            self.assertFalse(plan.spec_grill)
            self.assertFalse(plan.prototype)
            self.assertFalse(plan.architecture_grill)

    def test_explicit_prototype_question_enables_auto_prototype(self):
        with tempfile.TemporaryDirectory() as tmp:
            feature = pathlib.Path(tmp)
            (feature / 'spec.md').write_text('# X\nRisk: low\n')
            config = {
                'grill': 'auto', 'prototype': 'auto', 'architecture_grill': 'auto',
                'prototype_questions': [{
                    'id': 'P-001', 'question': 'Q', 'rationale': 'R',
                    'decision_criteria': ['correctness'],
                    'candidates': [{'id': 'A', 'approach': 'one'}],
                }],
            }
            args = argparse.Namespace(grill=None, prototype=None, architecture_grill=None)
            self.assertTrue(design.stage_plan(feature, config, args).prototype)

    def test_stage_off_requires_explicit_waiver_reason(self):
        plan = design.StagePlan(False, False, False, 'off', 'auto', 'auto')
        with self.assertRaises(SystemExit):
            design.require_waiver(plan, argparse.Namespace(waiver_reason=None))
        design.require_waiver(plan, argparse.Namespace(waiver_reason='human accepted low risk'))

    def test_normalize_generated_prototype_recommendation(self):
        result = design.normalize_recommendation({
            'question': 'Does the client preserve ordering?',
            'candidates': ['A approach', 'B approach'],
        }, 1)
        self.assertEqual('P-AUTO-001', result['id'])
        self.assertEqual(['A', 'B'], [item['id'] for item in result['candidates']])
        self.assertTrue(result['decision_criteria'])


    def test_full_design_loop_with_fake_codex_produces_gate_and_cleans_worktrees(self):
        source_root = pathlib.Path(__file__).resolve().parents[3]
        with structured_agent_fake.agent_test_root() as tmp:
            root = pathlib.Path(tmp) / 'repo'
            root.mkdir()
            shutil.copytree(source_root / 'tooling' / 'agent-harness', root / 'tooling' / 'agent-harness')
            (root / 'docs' / 'agentic-sdd').mkdir(parents=True)
            shutil.copytree(source_root / 'docs' / 'agentic-sdd' / 'agents', root / 'docs' / 'agentic-sdd' / 'agents')
            (root / 'AGENTS.md').write_text('# map\n')
            (root / 'CLAUDE.md').write_text('# claude\n')
            (root / 'gradlew').write_text('#!/bin/sh\nexit 0\n')
            (root / 'gradlew').chmod(0o755)
            feature = root / 'docs' / 'specs' / 'TST-DESIGN'
            feature.mkdir(parents=True)
            (feature / 'spec.md').write_text('# TST-DESIGN\nRisk: high\n\n## Acceptance criteria\n- AC-001: preserve correctness\n')
            (feature / 'plan.md').write_text('# TST-DESIGN plan\nStatus: DRAFT\n')
            (feature / 'design.json').write_text(json.dumps({
                'required_for_orchestration': True,
                'grill': 'always',
                'prototype': 'always',
                'architecture_grill': 'always',
                'prototype_max_parallel': 2,
                'prototype_questions': [{
                    'id': 'P-001', 'question': 'Which approach?', 'rationale': 'Need evidence',
                    'decision_criteria': ['correctness', 'complexity'],
                    'candidates': [
                        {'id': 'A', 'approach': 'approach A'},
                        {'id': 'B', 'approach': 'approach B'},
                    ],
                }],
            }))
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.invalid'], cwd=root, check=True)
            subprocess.run(['git', 'add', '.'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)

            fake_bin = pathlib.Path(tmp) / 'bin'
            structured_agent_fake.install(fake_bin, "{'status': 'pass', 'summary': 'fake pass', 'blocking_questions': [], "
                                          "'non_blocking_risks': [], 'prototype_recommendations': [], "
                                          "'findings': ['fake evidence'], 'changed_paths': [], 'commands': [], "
                                          "'assumptions': [], 'residual_risks': [], "
                                          "'recommendation': ('A' if 'PROTOTYPE BAKE-OFF EVALUATION' in prompt else None)}")
            env = os.environ.copy()
            env['PATH'] = str(fake_bin) + os.pathsep + env.get('PATH', '')
            proc = subprocess.run(
                ['python3', 'tooling/agent-harness/design.py', 'docs/specs/TST-DESIGN', '--provider', 'codex',
                 '--model', 'fake-model', '--run-id', 'test-run'],
                cwd=root, env=env, text=True, capture_output=True, check=False,
            )
            self.assertEqual(0, proc.returncode, msg=proc.stdout + '\n' + proc.stderr)
            gate = json.loads((feature / 'design' / 'gate.json').read_text())
            self.assertEqual('pass', gate['decision'])
            self.assertEqual('A', gate['prototype_recommendations'][0]['recommendation'])
            self.assertTrue((feature / 'design' / 'prototypes' / 'P-001' / 'candidate-A.json').exists())
            self.assertTrue((feature / 'design' / 'prototypes' / 'P-001' / 'candidate-B.json').exists())
            worktrees = subprocess.run(['git', 'worktree', 'list', '--porcelain'], cwd=root, text=True, capture_output=True, check=True).stdout
            self.assertNotIn('_design', worktrees)



class LaunchStatusTest(unittest.TestCase):
    def test_a_failed_launch_records_its_status_and_raises(self):
        """AH5-16a: the launch's own status (refused, failed, withheld) is recorded instead of harness-error."""
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', '-c', 'user.name=T', '-c', 'user.email=t@example.invalid', 'commit', '-q',
                            '--allow-empty', '-m', 'base'], cwd=root, check=True)
            schema = design.REPO / 'tooling' / 'agent-harness' / 'schemas' / 'design-result.schema.json'
            copy = root / schema.relative_to(design.REPO)
            copy.parent.mkdir(parents=True)
            copy.write_text(schema.read_text())
            args = argparse.Namespace(provider='codex', model='m', run_id='run', reasoning='high', max_turns=5,
                                      max_budget_usd=None, agent_timeout=60)
            failure = design.r.AgentLaunchFailed('output-withheld', 'codex output withheld')
            with mock.patch.object(design, 'REPO', root), \
                    mock.patch.object(design.r, 'launch_structured_agent', side_effect=failure), \
                    self.assertRaises(design.r.AgentLaunchFailed):
                design.run_provider(feature='F', stage='grill', prompt='p', worktree=root, profile='grill-reviewer', args=args, read_only=True)
            [record] = (root / '.agent-runs').rglob('provenance.json')
            provenance = json.loads(record.read_text())
            self.assertEqual(('output-withheld', 'codex output withheld'),
                             (provenance['status'], provenance['error']['message']))


RESULT = {'status': 'pass', 'summary': 'ok', 'blocking_questions': [], 'non_blocking_risks': [],
          'prototype_recommendations': [], 'findings': [], 'changed_paths': [], 'commands': [], 'assumptions': [],
          'residual_risks': []}


class PrototypeSignalTest(unittest.TestCase):
    """Delta review N-6: the prototype candidates' agents are awaited in worker threads, where no signal handler runs,
    so the pool is entered together with the runner's main-thread cancellation. The check scans `with` blocks around a
    ThreadPoolExecutor; a thread started any other way that launches an agent needs the same context."""

    def test_the_candidate_pool_is_covered_by_cancel_launches_on_signal(self):
        import ast
        source = getattr(design, '_design', design)  # after AH5-16b the definitions live in agent_harness.design
        tree = ast.parse(pathlib.Path(source.__file__).read_text(encoding='utf-8'))
        pools = []
        for node in ast.walk(tree):
            if isinstance(node, ast.With):
                calls = [ast.unparse(item.context_expr.func) for item in node.items
                         if isinstance(item.context_expr, ast.Call)]
                if any(call.endswith('ThreadPoolExecutor') for call in calls):
                    pools.append(calls)
        self.assertTrue(pools)
        for calls in pools:
            expected = 'r.cancel_launches_on_signal' if source is design else 'ns.r.cancel_launches_on_signal'
            self.assertEqual(expected, calls[0])


class LaunchSiteTest(unittest.TestCase):
    """AH5-16a: what the design site passes to the structured launch, the postconditions it keeps after it, and the
    refusals of its entry point before anything exists."""

    SCHEMA = pathlib.Path(design.__file__).resolve().parent / 'schemas' / 'design-result.schema.json'

    def setUp(self):
        self.fresh()

    def fresh(self):
        """A new REPO (where run directories go) and a new worktree with the result schema, committed."""
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = pathlib.Path(tmp.name) / 'repo'
        self.wt = pathlib.Path(tmp.name) / 'wt'
        self.root.mkdir()
        self.wt.mkdir()
        self.git('init', '-q')
        copy = self.wt / 'tooling' / 'agent-harness' / 'schemas' / self.SCHEMA.name
        copy.parent.mkdir(parents=True)
        copy.write_text(self.SCHEMA.read_text())
        self.git('add', '.')
        self.git('commit', '-q', '-m', 'base')
        self.args = argparse.Namespace(provider='codex', model='m', run_id='run', reasoning='high', max_turns=5,
                                       max_budget_usd=None, agent_timeout=60)
        patch = mock.patch.object(design, 'REPO', self.root)
        patch.start()
        self.addCleanup(patch.stop)

    def git(self, *args):
        subprocess.run(['git', '-c', 'user.name=T', '-c', 'user.email=t@example.invalid', *args], cwd=self.wt,
                       check=True, capture_output=True)

    def run_stage(self, read_only=True, mutate=lambda root: None, **result):
        def launch(args, prompt, worktree, out, schema, *, read_only, provenance):
            mutate(worktree)
            return dict(RESULT, **result)

        with mock.patch.object(design.r, 'launch_structured_agent', side_effect=launch) as spy:
            try:
                design.run_provider(feature='F', stage='grill', prompt='p', worktree=self.wt, profile='grill-reviewer',
                                    args=self.args, read_only=read_only)
                failure = None
            except RuntimeError as exc:
                failure = str(exc)
        [record] = (self.root / '.agent-runs').rglob('provenance.json')
        return spy, failure, json.loads(record.read_text())['status']

    def test_the_stage_s_read_only_flag_reaches_the_launch(self):
        for read_only in (True, False):
            with self.subTest(read_only=read_only):
                spy, failure, status = self.run_stage(read_only=read_only)
                self.assertEqual((None, 'pass'), (failure, status))
                self.assertIs(read_only, spy.call_args.kwargs['read_only'])
                self.assertEqual('p', spy.call_args.args[1])  # the prompt, as the launch's stdin

    def test_every_postcondition_still_fails_the_stage(self):
        cases = {
            'a commit': (dict(mutate=lambda root: self.git('commit', '-q', '--allow-empty', '-m', 'x')),
                         'design agent changed HEAD'),
            'a remote': (dict(mutate=lambda root: self.git('remote', 'add', 'origin', 'https://example.invalid/r')),
                         'design agent changed git remotes'),
            'a write in a read-only stage': (dict(mutate=lambda root: (root / 'new.txt').write_text('x')),
                                             'read-only design reviewer modified worktree'),
            'unreported changes': (dict(read_only=False, mutate=lambda root: (root / 'new.txt').write_text('x')),
                                   'design changed_paths differs from git diff'),
        }
        for name, (kwargs, message) in cases.items():
            with self.subTest(name):
                self.fresh()
                spy, failure, status = self.run_stage(**kwargs)
                self.assertIn(message, failure or '')
                self.assertEqual('harness-error', status)

    def test_the_entry_point_refuses_a_bad_timeout_or_a_missing_model_before_anything_exists(self):
        feature = self.root / 'docs' / 'specs' / 'F-1'
        feature.mkdir(parents=True)
        (feature / 'spec.md').write_text('# spec\nRisk: high\n')
        (feature / 'plan.md').write_text('# plan\n')
        for extra in (['--agent-timeout', '0', '--model', 'm'], ['--agent-timeout', '86401', '--model', 'm'], []):
            argv = ['design.py', str(feature), '--provider', 'codex', *extra]
            with self.subTest(extra), mock.patch.object(sys, 'argv', argv), \
                    mock.patch.object(design.shutil, 'which', return_value='/usr/bin/true'), \
                    mock.patch.object(design.r, 'DEFAULT_MODELS', {}), \
                    mock.patch.object(design, 'design_base', side_effect=AssertionError('reached')), \
                    contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()), \
                    self.assertRaises(SystemExit) as caught:
                design.main()
            self.assertEqual(2, caught.exception.code)
            self.assertFalse((feature / 'design').exists())
            self.assertFalse((self.root / '.agent-runs').exists())


if __name__ == '__main__':
    unittest.main()
