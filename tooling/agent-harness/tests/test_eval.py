import importlib.util, json, pathlib, sys, tempfile, unittest
MODULE_PATH=pathlib.Path(__file__).resolve().parents[1]/'eval.py'
spec=importlib.util.spec_from_file_location('sdd_eval',MODULE_PATH); ev=importlib.util.module_from_spec(spec); assert spec.loader is not None; sys.modules[spec.name]=ev; spec.loader.exec_module(ev)
class EvalTest(unittest.TestCase):
    def test_verification_v2_regressions_and_benchmark_are_required_by_ci(self):
        root=pathlib.Path(__file__).resolve().parents[3]
        suite_path=root/'tooling/agent-harness/evals/verification-v2.json'
        suite=json.loads(suite_path.read_text())
        cases={case['id']:case for case in suite['cases']}
        required={
            'V2-compatibility-and-contract',
            'V2-authority-and-isolation',
            'V2-planning-benchmark',
        }
        self.assertEqual(required,set(cases))
        commands=' '.join(part for case in cases.values() for part in case['command'])
        for path in (
            'tooling/agent-harness/tests/test_telemetry.py',
            'tooling/agent-harness/tests/test_verification_contract.py',
            'tooling/agent-harness/tests/test_verification_authority.py',
            'tooling/agent-harness/tests/test_verification_admission.py',
            'tooling/agent-harness/tests/test_verification_sandbox.py',
            'tooling/agent-harness/tests/test_verification_backend_qualification.py',
            'tooling/agent-harness/tests/test_verification_benchmark.py',
        ):
            self.assertIn(path,commands)
        self.assertTrue((root/'tooling/agent-harness/tests/test_verification_completion_boundary.py').is_file())
        workflow=(root/'.github/workflows/agentic-sdd.yml').read_text()
        self.assertIn('python3 -m unittest discover -s tooling/agent-harness/tests -v',workflow)
        self.assertIn('python3 tooling/agent-harness/eval.py run --suite verification-v2 --repeat 1',workflow)

    def test_rejects_destructive_remote_command(self):
        doc={'schema_version':1,'name':'x','cases':[{'id':'E1','command':['git','push','origin','main']}]}
        self.assertTrue(any('forbidden' in x for x in ev.validate_suite(doc)))
    def test_substitution_requires_target(self):
        with self.assertRaises(SystemExit): ev.substitute(['python3','x.py','{target}'],None,None)
    def test_summary_computes_pass_rate(self):
        s=ev.summarize([{'case':'A','status':'pass','duration_ms':10},{'case':'A','status':'fail','duration_ms':20}])
        self.assertEqual(0.5,s['pass_rate']); self.assertEqual(15,s['median_duration_ms'])
    def test_checked_in_suites_are_valid(self):
        for path in ev.DEFAULT_SUITES.glob('*.json'):
            self.assertEqual([],ev.validate_suite(json.loads(path.read_text())),msg=str(path))
if __name__=='__main__': unittest.main()
