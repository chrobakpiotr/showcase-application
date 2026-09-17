import importlib.util, json, pathlib, sys, tempfile, unittest
MODULE_PATH=pathlib.Path(__file__).resolve().parents[1]/'eval.py'
spec=importlib.util.spec_from_file_location('sdd_eval',MODULE_PATH); ev=importlib.util.module_from_spec(spec); assert spec.loader is not None; sys.modules[spec.name]=ev; spec.loader.exec_module(ev)
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
if __name__=='__main__': unittest.main()
