import importlib.util
import json
import pathlib
import tempfile
import unittest
import subprocess
import sys
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'telemetry.py'
sys.path.insert(0, str(MODULE_PATH.parent))
spec = importlib.util.spec_from_file_location('sdd_telemetry', MODULE_PATH)
telemetry = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(telemetry)


class TelemetryTest(unittest.TestCase):
    def test_manual_registration_fails_closed_without_lifecycle_authority(self):
        from verification.store import VerificationStore
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            env = dict(__import__('os').environ, GIT_AUTHOR_NAME='Telemetry Test',
                       GIT_AUTHOR_EMAIL='telemetry@example.invalid', GIT_COMMITTER_NAME='Telemetry Test',
                       GIT_COMMITTER_EMAIL='telemetry@example.invalid')
            (root / 'tracked.txt').write_text('safe\n', encoding='utf-8')
            (root / '.gitignore').write_text('.agent-runs/\n', encoding='utf-8')
            feature_dir = root / 'docs' / 'specs' / 'TST-MANUAL'
            feature_dir.mkdir(parents=True)
            (feature_dir / 'tasks.json').write_text(json.dumps({'feature': 'TST-MANUAL', 'tasks': []}), encoding='utf-8')
            subprocess.run(['git', '-C', str(root), 'add', 'tracked.txt', '.gitignore', 'docs'], check=True, env=env)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'checkpoint'], check=True, env=env)
            checkpoint = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            store = VerificationStore(root)
            report_path = store.root / 'manual-reports' / 'review.md'
            report_path.parent.mkdir(parents=True)
            report_path.write_text(
                f'Feature: `TST-MANUAL`\nReviewed checkpoint: `{checkpoint}`\n'
                'Verdict: **PASS**\nCompleted at: `2026-09-29T12:00:00Z`\n', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_AUTHORITY_UNAVAILABLE'):
                telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                    provider='manual', checkpoint=checkpoint, verdict='PASS',
                    report=report_path.relative_to(root.resolve()).as_posix())
            self.assertFalse((store.root / 'manual').exists())

    def test_manual_registration_rejects_untrusted_report_location_and_secret(self):
        from verification.store import VerificationStore
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            env = dict(__import__('os').environ, GIT_AUTHOR_NAME='Telemetry Test',
                       GIT_AUTHOR_EMAIL='telemetry@example.invalid', GIT_COMMITTER_NAME='Telemetry Test',
                       GIT_COMMITTER_EMAIL='telemetry@example.invalid')
            (root / 'tracked.txt').write_text('safe\n', encoding='utf-8')
            (root / '.gitignore').write_text('.agent-runs/\n', encoding='utf-8')
            feature_dir = root / 'docs' / 'specs' / 'TST-MANUAL'
            feature_dir.mkdir(parents=True)
            (feature_dir / 'tasks.json').write_text(json.dumps({'feature': 'TST-MANUAL', 'tasks': []}), encoding='utf-8')
            subprocess.run(['git', '-C', str(root), 'add', 'tracked.txt', '.gitignore', 'docs'], check=True, env=env)
            subprocess.run(['git', '-C', str(root), 'commit', '-qm', 'checkpoint'], check=True, env=env)
            checkpoint = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            store = VerificationStore(root)
            report_path = store.root / 'manual-reports' / 'review.md'
            report_path.parent.mkdir(parents=True)
            report_path.write_text(f'Feature: `TST-MANUAL`\nReviewed checkpoint: `{checkpoint}`\n'
                                   'Verdict: **PASS**\nCompleted at: `2026-09-29T12:00:00Z`\n'
                                   'api_key=sk-abcdefghijklmnopqrstuvwxyz0123456789\n', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_REPORT_SECRET'):
                telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                    provider='manual', checkpoint=checkpoint, verdict='PASS',
                    report=report_path.relative_to(root.resolve()).as_posix())
            with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_REPORT_INVALID'):
                telemetry.record_manual(repo=root, feature='TST-MANUAL', role='evaluator',
                    provider='manual', checkpoint=checkpoint, verdict='PASS', report='README.md')

    def test_manual_report_rejects_future_or_non_whole_second_completion_time(self):
        template = ('Feature: `TST-MANUAL`\nReviewed checkpoint: `{}`\n'
                    'Verdict: **PASS**\nCompleted at: `{}`\n')
        checkpoint = 'a' * 40
        future = (telemetry.utc_now() + __import__('datetime').timedelta(days=1))
        future = future.replace(microsecond=0).isoformat().replace('+00:00', 'Z')
        with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_CHRONOLOGY_INVALID'):
            telemetry._manual_report_fields(template.format(checkpoint, future).encode())
        with self.assertRaisesRegex(ValueError, 'MANUAL_EVIDENCE_REPORT_INVALID'):
            telemetry._manual_report_fields(template.format(checkpoint, '2026-09-29T12:00:00.123Z').encode())

    def test_codex_jsonl_usage_is_extracted_without_cost_guessing(self):
        stream = '\n'.join([
            json.dumps({'type': 'thread.started', 'thread_id': 'abc'}),
            json.dumps({'type': 'turn.completed', 'usage': {'input_tokens': 10, 'cached_input_tokens': 4, 'output_tokens': 2}}),
        ])
        data = telemetry.parse_codex_jsonl(stream)
        self.assertEqual('abc', data['thread_id'])
        self.assertEqual(10, data['usage']['input_tokens'])
        self.assertIsNone(data['cost_usd'])

    def test_provider_parsers_drop_nested_unrecognized_metadata_and_secret_ids(self):
        canary = 'telemetry-canary-secret-91d3'
        stream = '\n'.join([
            json.dumps({'type': 'thread.started', 'thread_id': canary}),
            json.dumps({'type': 'turn.completed', 'usage': {
                'input_tokens': 10, canary: canary, 'provider_extension': {'value': canary},
            }}),
        ])
        with mock.patch.dict(__import__('os').environ, {'TELEMETRY_TEST_SECRET': canary}):
            codex = telemetry.parse_codex_jsonl(stream)
            claude = telemetry.parse_claude_envelope({
                'session_id': canary, 'usage': {'output_tokens': 3, canary: canary},
                'provider_extension': {'secret': canary}, 'total_cost_usd': None,
            })

        self.assertNotIn(canary, json.dumps({'codex': codex, 'claude': claude}))
        self.assertIsNone(codex['thread_id'])
        self.assertEqual({'input_tokens': 10}, codex['usage'])
        self.assertIsNone(claude['session_id'])
        self.assertEqual({'output_tokens': 3}, claude['usage'])
        self.assertIsNone(claude['cost_usd'])

    def test_claude_cost_metadata_is_preserved(self):
        data = telemetry.parse_claude_envelope({
            'session_id': 's1', 'total_cost_usd': 0.123, 'duration_ms': 55, 'num_turns': 3,
            'usage': {'input_tokens': 12}
        })
        self.assertEqual(0.123, data['cost_usd'])
        self.assertEqual(3, data['num_turns'])

    def test_claude_parser_drops_unrepresentably_large_duration_integers(self):
        data = telemetry.parse_claude_envelope({
            'duration_ms': 10**1000,
            'duration_api_ms': 10**1000,
        })

        self.assertIsNone(data['duration_ms'])
        self.assertIsNone(data['duration_api_ms'])

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

    def test_summary_never_renders_nested_provider_metadata_or_unknown_cost(self):
        canary = 'telemetry-canary-secret-91d3'
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            path = root / '.agent-runs' / 'F-1' / 'T-1' / 'run' / 'x' / 'provenance.json'
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({
                'schema_version': 2, 'invocation_id': 'safe-invocation',
                'provider': 'codex', 'status': 'pass', 'orchestration_id': 'run',
                'duration_ms': 10, 'provider_metadata': {
                    'cost_usd': None,
                    'usage': {'input_tokens': 5, canary: canary},
                    'diagnostic': {'nested': canary},
                },
            }))

            summary = telemetry.summarize(root, 'F-1')
            encoded = json.dumps(summary, sort_keys=True)

            self.assertNotIn(canary, encoded)
            self.assertEqual(1, summary['runs'])
            self.assertEqual(0, summary['known_cost_runs'])
            self.assertEqual(1, summary['unknown_cost_runs'])
            self.assertIsNone(summary['known_cost_usd'])
            self.assertEqual(5, summary['tokens']['input_tokens'])
            self.assertEqual(1, summary['known_usage_runs'])
            self.assertEqual(0, summary['unknown_usage_runs'])

    def test_summary_does_not_treat_invalid_cost_or_token_shapes_as_known(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            path = root / '.agent-runs' / 'F-1' / 'T-1' / 'run' / 'x' / 'provenance.json'
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({
                'provider': 'codex', 'status': 'pass',
                'provider_metadata': {
                    'cost_usd': float('nan'),
                    'usage': {'input_tokens': 4, 'api_key': 'not-a-token-count'},
                },
            }))

            summary = telemetry.summarize(root, 'F-1')

            self.assertEqual(0, summary['known_cost_runs'])
            self.assertEqual(1, summary['unknown_cost_runs'])
            self.assertIsNone(summary['known_cost_usd'])
            self.assertEqual({'input_tokens': 4}, summary['tokens'])

    def test_summary_marks_missing_usage_and_cost_unknown(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            path = root / '.agent-runs' / 'F-1' / 'T-1' / 'run' / 'x' / 'provenance.json'
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({'provider': 'codex', 'status': 'pass'}))

            summary = telemetry.summarize(root, 'F-1')

            self.assertEqual(1, summary['unknown_cost_runs'])
            self.assertEqual(1, summary['unknown_usage_runs'])
            self.assertEqual(0, summary['known_usage_runs'])
            self.assertEqual({}, summary['tokens'])
            self.assertNotIn('saved_cost_usd', summary)
            self.assertNotIn('saved_time_ms', summary)

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
