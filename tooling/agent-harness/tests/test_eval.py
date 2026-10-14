import importlib.util, json, pathlib, sys, tempfile, unittest
MODULE_PATH=pathlib.Path(__file__).resolve().parents[1]/'eval.py'
spec=importlib.util.spec_from_file_location('sdd_eval',MODULE_PATH); ev=importlib.util.module_from_spec(spec); assert spec.loader is not None; sys.modules[spec.name]=ev; spec.loader.exec_module(ev)
REPO_ROOT=MODULE_PATH.parents[2]
class EvalTest(unittest.TestCase):
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
    def test_ci_runs_discovery_and_enforces_linux_benchmark_environment(self):
        workflow=(REPO_ROOT/'.github/workflows/agentic-sdd.yml').read_text()
        self.assertIn('python3 -m unittest discover -s tooling/agent-harness/tests -v',workflow)
        harness_job=workflow.split('  harness-tests:',1)[1].split('\n  examples-validation:',1)[0]
        behavioral_job=workflow.split('  behavioral-evaluations:',1)[1].split('\n  agentic-sdd-gate:',1)[0]
        trusted_python='python:3.13.16-slim@sha256:70729b46c69b4f1e97c4822c1af3df53a1476cf5ddc6c087c0c10bc3a5678c2f'
        for job in (harness_job, behavioral_job):
            self.assertIn(f'container: {trusted_python}',job)
            self.assertNotIn('actions/setup-python@',job)
            self.assertIn('apt-get install --no-install-recommends -y git',job)
        self.assertRegex(harness_job,r'(?m)^\s+CI:\s*[\'\"]?true[\'\"]?\s*$')
        gate=workflow.split('  agentic-sdd-gate:',1)[1]
        self.assertIn('      - harness-tests',gate)
        self.assertIn('      - behavioral-evaluations',gate)
        self.assertIn('eval.py run --suite adversarial --repeat 1',workflow)
    def test_adversarial_eval_pins_verification_v2_regression_modules(self):
        suite=json.loads((ev.DEFAULT_SUITES/'adversarial.json').read_text())
        cases={case['id']:case for case in suite['cases']}
        expected={
            'A005-v2-profile-benchmark-parity':(
                'test_verification_profile_showcase.py',
                'test_verification_benchmark.py',
                'test_verification_parity.py'),
            'A006-v2-authority-store':(
                'test_verification_authority.py',
                'test_verification_store.py'),
            'A007-v2-security-qualification':(
                'test_verification_sandbox.py',
                'test_verification_sandbox_doctor.py',
                'test_verification_supervisor.py'),
        }
        for case_id, modules in expected.items():
            self.assertIn(case_id,cases)
            case=cases[case_id]
            self.assertEqual(0,case.get('expected_exit'))
            command=' '.join(case['command'])
            for module in modules:
                self.assertIn(module,command,msg=f'{case_id} does not run {module}')
if __name__=='__main__': unittest.main()
