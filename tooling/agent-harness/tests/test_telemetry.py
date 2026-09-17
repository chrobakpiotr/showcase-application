import importlib.util
import json
import pathlib
import tempfile
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'telemetry.py'
spec = importlib.util.spec_from_file_location('sdd_telemetry', MODULE_PATH)
telemetry = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(telemetry)


class TelemetryTest(unittest.TestCase):
    def test_codex_jsonl_usage_is_extracted_without_cost_guessing(self):
        stream = '\n'.join([
            json.dumps({'type': 'thread.started', 'thread_id': 'abc'}),
            json.dumps({'type': 'turn.completed', 'usage': {'input_tokens': 10, 'cached_input_tokens': 4, 'output_tokens': 2}}),
        ])
        data = telemetry.parse_codex_jsonl(stream)
        self.assertEqual('abc', data['thread_id'])
        self.assertEqual(10, data['usage']['input_tokens'])
        self.assertIsNone(data['cost_usd'])

    def test_claude_cost_metadata_is_preserved(self):
        data = telemetry.parse_claude_envelope({
            'session_id': 's1', 'total_cost_usd': 0.123, 'duration_ms': 55, 'num_turns': 3,
            'usage': {'input_tokens': 12}
        })
        self.assertEqual(0.123, data['cost_usd'])
        self.assertEqual(3, data['num_turns'])

    def test_summary_can_filter_orchestration(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            for idx, run in enumerate(('r1', 'r2')):
                path = root / '.agent-runs' / 'F-1' / f'T-{idx}' / run / 'x' / 'provenance.json'
                path.parent.mkdir(parents=True)
                path.write_text(json.dumps({
                    'provider': 'claude', 'status': 'pass', 'orchestration_id': run,
                    'duration_ms': 10, 'provider_metadata': {'cost_usd': 0.1, 'usage': {'input_tokens': 5}}
                }))
            summary = telemetry.summarize(root, 'F-1', 'r1')
            self.assertEqual(1, summary['runs'])
            self.assertEqual(0.1, summary['known_cost_usd'])
            self.assertEqual(5, summary['tokens']['input_tokens'])

    def test_reconcile_running_marks_only_owned_orphans_abandoned(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            a = root / '.agent-runs' / 'X' / 'T-1' / 'run-a' / 'one' / 'provenance.json'
            b = root / '.agent-runs' / 'X' / 'T-2' / 'run-a' / 'two' / 'provenance.json'
            c = root / '.agent-runs' / 'X' / 'T-1' / 'run-b' / 'three' / 'provenance.json'
            for path, doc in (
                (a, {'orchestration_id':'run-a','task':'T-1','status':'running'}),
                (b, {'orchestration_id':'run-a','task':'T-2','status':'running'}),
                (c, {'orchestration_id':'run-b','task':'T-1','status':'running'}),
            ):
                telemetry.atomic_write_json(path, doc)
            changed = telemetry.reconcile_running(root, 'X', 'run-a', {'T-1'}, reason='test')
            self.assertEqual([str(a)], changed)
            self.assertEqual('abandoned', json.loads(a.read_text())['status'])
            self.assertEqual('running', json.loads(b.read_text())['status'])
            self.assertEqual('running', json.loads(c.read_text())['status'])


if __name__ == '__main__':
    unittest.main()
