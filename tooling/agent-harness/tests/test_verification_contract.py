import argparse
import contextlib
import io
import importlib.util
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'verification_contract.py'
spec = importlib.util.spec_from_file_location('sdd_verification_contract', MODULE_PATH)
vc = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = vc
spec.loader.exec_module(vc)


class VerificationContractTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        subprocess.run(['git','init','-q'], cwd=self.root, check=True)
        (self.root/'docs/agentic-sdd').mkdir(parents=True)
        (self.root/'docs/agentic-sdd/constitution.md').write_text('# constitution\n')
        self.feature = self.root/'docs/specs/VC-TEST-001'; self.feature.mkdir(parents=True)
        (self.feature/'spec.md').write_text('# spec\nRisk: high\n- AC-001: works\n')
        (self.feature/'plan.md').write_text('# plan\n')
        (self.feature/'design.json').write_text(json.dumps({'verification_contract':'required'}))
        (self.feature/'design').mkdir(); (self.feature/'design/gate.json').write_text('{}')
        old_repo = vc.REPO; vc.REPO = self.root
        self.addCleanup(setattr, vc, 'REPO', old_repo)

    def tearDown(self):
        self.tmp.cleanup()

    def contract(self):
        inputs = vc.input_hashes(self.feature)
        return {
            'schema_version':1,'feature':'VC-TEST-001','status':'accepted','inputs':inputs,
            'criteria':[{'id':'VC-001','statement':'Preserve invariant','origin':'independent','source_type':'architecture-invariant','sources':['constitution'],'verification_hint':'architecture test'}],
            'exemptions':[],'summary':'ok','assumptions':[]
        }

    def test_required_contract_must_exist(self):
        self.assertIn('missing required verification-contract.json', vc.validate_contract(self.feature))

    def test_valid_required_contract_has_independent_criterion(self):
        (self.feature/'verification-contract.json').write_text(json.dumps(self.contract()))
        self.assertEqual([], vc.validate_contract(self.feature))
        self.assertEqual({'VC-001'}, vc.criterion_ids(self.feature))

    def test_contract_becomes_stale_when_plan_changes(self):
        (self.feature/'verification-contract.json').write_text(json.dumps(self.contract()))
        (self.feature/'plan.md').write_text('# changed\n')
        self.assertTrue(any('stale' in e for e in vc.validate_contract(self.feature)))

    def test_agent_cannot_self_accept_exemption(self):
        doc=self.contract(); doc['exemptions']=[{'id':'VX-001','scope':'load','reason':'expensive','status':'accepted','approved_by':'verification-author'}]
        (self.feature/'verification-contract.json').write_text(json.dumps(doc))
        self.assertTrue(any('human' in e for e in vc.validate_contract(self.feature)))



class GenerateLaunchTest(unittest.TestCase):
    """AH5-16a: contract authoring launches through the runner's structured launch, read-only, and records a status
    for every way it ends instead of leaving `running`."""

    def setUp(self):
        import harness as h  # as generate imports it

        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = pathlib.Path(tmp.name).resolve()
        git = lambda *a: subprocess.run(['git', '-c', 'user.name=T', '-c', 'user.email=t@example.invalid', *a],
                                        cwd=self.root, check=True, capture_output=True)
        git('init', '-q')
        schema = self.root / vc.SCHEMA.relative_to(vc.REPO)
        schema.parent.mkdir(parents=True)
        schema.write_text(vc.SCHEMA.read_text())
        git('add', '.')
        git('commit', '-q', '-m', 'base')
        self.base = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=self.root, check=True, capture_output=True,
                                   text=True).stdout.strip()
        self.feature = self.root / 'docs' / 'specs' / 'VC-1'
        self.feature.mkdir(parents=True)
        (self.feature / 'spec.md').write_text('# spec\n')
        (self.feature / 'plan.md').write_text('# plan\n')
        for patch in (mock.patch.object(h, 'repo_root', return_value=self.root),
                      mock.patch.object(h, 'design_gate_errors', return_value=[]),
                      mock.patch.object(h, 'execution_base', return_value=self.base),
                      mock.patch.object(vc, 'prompt', return_value='author the contract')):
            patch.start()
            self.addCleanup(patch.stop)
        self.h = h

    def args(self, **overrides):
        values = dict(provider='codex', model='m', agent_timeout=60, run_id='run', force=False, reasoning='high',
                      max_turns=5, max_budget_usd=None)
        values.update(overrides)
        return argparse.Namespace(**values)

    def generate(self, launch, **args):
        with mock.patch.object(vc.r, 'launch_structured_agent', side_effect=launch) as spy, \
                contextlib.redirect_stderr(io.StringIO()):
            try:
                vc.generate(self.feature, self.args(**args))
                outcome = 0
            except SystemExit as exc:
                outcome = exc.code
            except Exception as exc:  # noqa: BLE001 - an unexpected error is part of what is tested
                outcome = type(exc).__name__
        records = list((self.root / '.agent-runs').rglob('provenance.json'))
        status = json.loads(records[0].read_text())['status'] if records else None
        return spy, outcome, status

    def test_every_failure_records_its_status(self):
        def raises(error):
            def launch(*args, **kwargs):
                raise error
            return launch

        def returns(raw):
            return lambda *args, **kwargs: raw

        cases = {
            'a refused launch': (raises(vc.r.AgentLaunchFailed('preflight-refused', 'refused')), 2, 'preflight-refused'),
            'a failed launch': (raises(vc.r.AgentLaunchFailed('provider-error', 'ended')), 2, 'provider-error'),
            'withheld output': (raises(vc.r.AgentLaunchFailed('output-withheld', 'withheld')), 2, 'output-withheld'),
            'no readable result': (raises(SystemExit(2)), 2, 'harness-error'),
            'an unexpected error': (raises(ValueError('backend')), 'ValueError', 'harness-error'),
            'an author that failed': (returns({'status': 'fail', 'summary': 'no'}), 2, 'fail'),
            'an author that needs a human': (returns({'status': 'needs-human', 'summary': '?'}), 2, 'needs-human'),
            'an author status out of vocabulary': (returns({'status': 'maybe'}), 2, 'harness-error'),
            'no criteria': (returns({'status': 'pass', 'criteria': []}), 2, 'harness-error'),
        }
        for name, (launch, outcome, status) in cases.items():
            with self.subTest(name):
                for record in (self.root / '.agent-runs').rglob('provenance.json'):
                    record.unlink()
                spy, actual_outcome, actual_status = self.generate(launch)
                self.assertEqual((outcome, status), (actual_outcome, actual_status))
                self.assertIs(True, spy.call_args.kwargs['read_only'])
                self.assertEqual('author the contract', spy.call_args.args[1])
                self.assertFalse((self.feature / 'verification-contract.json').exists())

    def test_an_invalid_contract_is_removed_and_recorded(self):
        raw = {'status': 'pass', 'summary': 'ok', 'criteria': [{'id': 'VC-001'}]}
        with mock.patch.object(vc, 'validate_contract', return_value=['bad criterion']):
            _, outcome, status = self.generate(lambda *a, **k: raw)
        self.assertEqual((2, 'harness-error'), (outcome, status))
        self.assertFalse((self.feature / 'verification-contract.json').exists())

    def test_a_bad_timeout_or_a_missing_model_is_refused_before_any_worktree_or_run(self):
        self.h.execution_base.side_effect = AssertionError('reached')
        for overrides in ({'agent_timeout': 0}, {'agent_timeout': 86401}, {'model': None}):
            with self.subTest(overrides), mock.patch.object(vc.r, 'DEFAULT_MODELS', {}):
                spy, outcome, status = self.generate(lambda *a, **k: {}, **overrides)
                self.assertEqual((2, None), (outcome, status))
                spy.assert_not_called()
        self.assertFalse((self.root / '.agent-runs').exists())


if __name__ == '__main__':
    unittest.main()
