import importlib.util
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

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


if __name__ == '__main__': unittest.main()
