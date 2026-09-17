import argparse
import importlib.util
import pathlib
import tempfile
import unittest
import json
import os
import shutil
import subprocess

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'design.py'
spec = importlib.util.spec_from_file_location('sdd_design', MODULE_PATH)
design = importlib.util.module_from_spec(spec)
assert spec.loader is not None
import sys
sys.modules[spec.name] = design
spec.loader.exec_module(design)


class DesignTest(unittest.TestCase):
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
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'
            root.mkdir()
            shutil.copytree(source_root / 'etc' / 'agent-harness', root / 'etc' / 'agent-harness')
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
            fake_bin.mkdir()
            fake = fake_bin / 'codex'
            fake_lines = [
                '#!/usr/bin/env python3',
                'import json, pathlib, sys',
                'args = sys.argv[1:]',
                "out = pathlib.Path(args[args.index('--output-last-message') + 1])",
                'prompt = args[-1]',
                "result = {'status': 'pass', 'summary': 'fake pass', 'blocking_questions': [], 'non_blocking_risks': [], 'prototype_recommendations': [], 'findings': ['fake evidence'], 'changed_paths': [], 'commands': [], 'assumptions': [], 'residual_risks': [], 'recommendation': ('A' if 'PROTOTYPE BAKE-OFF EVALUATION' in prompt else None)}",
                'out.parent.mkdir(parents=True, exist_ok=True)',
                'out.write_text(json.dumps(result))',
                "print(json.dumps({'type': 'result', 'usage': {'input_tokens': 1, 'output_tokens': 1}}))",
            ]
            fake.write_text('\n'.join(fake_lines) + '\n')
            fake.chmod(0o755)
            env = os.environ.copy()
            env['PATH'] = str(fake_bin) + os.pathsep + env.get('PATH', '')
            proc = subprocess.run(
                ['python3', 'tooling/agent-harness/design.py', 'docs/specs/TST-DESIGN', '--provider', 'codex', '--run-id', 'test-run'],
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


if __name__ == '__main__':
    unittest.main()
