import argparse
import importlib.util
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parents[1]
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
MODULE_PATH = HERE / 'orchestrate.py'
spec = importlib.util.spec_from_file_location('sdd_orchestrate', MODULE_PATH)
orchestrate = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = orchestrate
spec.loader.exec_module(orchestrate)


class OrchestrateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        self.old_cwd = pathlib.Path.cwd()
        os.chdir(self.root)
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        (self.root / '.gitignore').write_text('.agent-state/\n.agent-runs/\ndocs/specs/*/packets/\n')
        subprocess.run(['git', 'add', '.gitignore'], cwd=self.root, check=True)
        subprocess.run([
            'git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
            'commit', '-q', '-m', 'base'
        ], cwd=self.root, check=True)
        orchestrate.h.STATE_DIR = self.root / '.agent-state'
        roles = self.root / 'docs' / 'agentic-sdd' / 'agents'
        roles.mkdir(parents=True, exist_ok=True)
        for profile in ('builder', 'evaluator', 'integration'):
            (roles / f'{profile}.md').write_text(f'# {profile}\n')

    def tearDown(self):
        os.chdir(self.old_cwd)
        self.tmp.cleanup()

    def feature(self):
        feature = self.root / 'docs' / 'specs' / 'TST-002'
        feature.mkdir(parents=True)
        (feature / 'spec.md').write_text('# TST-002\n- AC-001: built\n- AC-002: evaluated\n')
        (feature / 'plan.md').write_text('# plan\n')
        doc = {
            'feature': 'TST-002', 'max_parallel': 2, 'max_rework_attempts': 2,
            'tasks': [
                {'id': 'T-001', 'title': 'Build', 'objective': 'Build', 'role': 'builder', 'depends_on': [],
                 'allowed_paths': ['domain/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
                {'id': 'T-900', 'title': 'Evaluate', 'objective': 'Evaluate', 'role': 'evaluator', 'depends_on': ['T-001'],
                 'allowed_paths': ['tests/**'], 'risk_tags': ['evaluation'], 'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['true']},
            ],
        }
        (feature / 'tasks.json').write_text(json.dumps(doc))
        return feature, doc

    def args(self):
        return argparse.Namespace(
            provider='codex', evaluator_provider='claude', review_provider=None,
            model='builder-model', evaluator_model='eval-model', review_model=None,
            reasoning='medium', max_turns=30, max_budget_usd=None,
            owner_prefix='orch', run_id='testrun123456', verification_timeout=900, verification_sandbox='off'
        )

    def test_owner_is_scoped_by_orchestration_run(self):
        self.assertEqual('orch-testrun1-t-001', orchestrate.owner_for(self.args(), 'T-001'))

    def test_provider_routing_separates_evaluator(self):
        args = self.args()
        self.assertEqual('codex', orchestrate.choice_for_role('builder', args).provider)
        self.assertEqual('claude', orchestrate.choice_for_role('evaluator', args).provider)
        self.assertEqual('eval-model', orchestrate.choice_for_role('integration', args).model)

    def test_evaluator_failure_reopens_named_builder_with_feedback(self):
        feature, doc = self.feature()
        state = orchestrate.h.load_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'completed', 'attempts': 1})
        state['tasks']['T-900'].update({'status': 'running', 'attempts': 1, 'owner': 'orch-testrun1-t-900'})
        orchestrate.h.save_state(feature, state)
        evidence = self.root / 'evaluation.json'
        evidence.write_text(json.dumps({
            'status': 'fail', 'summary': 'AC-001 counterexample', 'changed_paths': [], 'commands': ['true'],
            'assumptions': [], 'residual_risks': [], 'rework_tasks': ['T-001']
        }))
        outcome = orchestrate.TaskOutcome(
            'T-900', 'fail', evidence=evidence, summary='AC-001 counterexample', rework_tasks=['T-001']
        )
        orchestrate.apply_outcome(feature, doc, outcome, self.args())
        updated = orchestrate.h.load_state(feature, doc)
        self.assertEqual('failed', updated['tasks']['T-001']['status'])
        self.assertEqual(str(evidence), updated['tasks']['T-001']['rework_evidence'])
        self.assertEqual('pending', updated['tasks']['T-900']['status'])

    def test_needs_human_escalates_immediately(self):
        feature, doc = self.feature()
        state = orchestrate.h.load_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'running', 'attempts': 1, 'owner': 'orch-testrun1-t-001'})
        orchestrate.h.save_state(feature, state)
        evidence = self.root / 'builder.json'
        evidence.write_text(json.dumps({
            'status': 'needs-human', 'summary': 'contract ambiguous', 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': ['ambiguity']
        }))
        outcome = orchestrate.TaskOutcome('T-001', 'needs-human', evidence=evidence, summary='contract ambiguous')
        orchestrate.apply_outcome(feature, doc, outcome, self.args())
        updated = orchestrate.h.load_state(feature, doc)
        self.assertEqual('escalated', updated['tasks']['T-001']['status'])
        self.assertEqual(1, updated['tasks']['T-001']['attempts'])


    def test_human_resolution_makes_escalated_task_resumable_with_feedback(self):
        feature, doc = self.feature()
        state = orchestrate.h.load_state(feature, doc)
        state['tasks']['T-001'].update({
            'status': 'escalated', 'attempts': 3, 'last_failure': 'contract ambiguous',
            'last_failure_evidence': str(self.root / '.agent-runs' / 'result.json'),
        })
        orchestrate.h.save_state(feature, state)

        orchestrate.h.cmd_human_resolve(argparse.Namespace(
            feature_dir=feature, task_id='T-001', decision='Keep the existing API contract and retry.',
            decision_file=None, by='architect@example.invalid',
        ))

        updated = orchestrate.h.load_state(feature, doc)
        self.assertEqual('failed', updated['tasks']['T-001']['status'])
        self.assertIn('T-001', orchestrate.h.ready_ids(doc, updated))
        feedback = orchestrate.prior_feedback(feature, doc, 'T-001')
        self.assertIsNotNone(feedback)
        self.assertTrue(feedback.exists())
        self.assertIn('Keep the existing API contract and retry.', feedback.read_text())



if __name__ == '__main__':
    unittest.main()
