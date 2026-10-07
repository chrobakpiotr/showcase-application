import json
import pathlib
import shutil
import sys
import subprocess
import tempfile
import unittest
from copy import deepcopy

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from qualification.report import CHECK_IDS, assemble_report, capability_report


class QualificationReportTest(unittest.TestCase):
    def records(self, root, *, failed=False):
        records = []
        for check_id in CHECK_IDS:
            status = 'not-run' if failed and check_id == 'B10' else (
                'fail' if failed and check_id == 'B8' else 'pass'
            )
            evidence = root / check_id / 'probe.json'
            evidence.parent.mkdir(parents=True)
            evidence.write_text(json.dumps({
                'check_id': check_id,
                'status': status,
                'job_id': 'job-123-attempt-1',
            }) + '\n', encoding='utf-8')
            records.append({'check_id': check_id, 'status': status, 'evidence_path': evidence})
        return records

    def metadata(self):
        return {
            'target': 'showcase-docker-desktop',
            'policy_digest': 'sha256:' + 'a' * 64,
            'author': 'showcase-driver',
            'job_id': 'job-123-attempt-1',
            'host': 'macos-arm64-docker-desktop',
            'kernel': '6.12.1-linuxkit',
            'engine': '29.8.2',
            'workload_image': 'sha256:' + 'b' * 64,
            'created_at': '2026-10-07T12:00:00Z',
        }

    def test_report_binds_every_check_to_distinct_job_named_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            report = assemble_report(self.records(root), self.metadata(), root)
            self.assertEqual(set(CHECK_IDS), {check['id'] for check in report['checks']})
            self.assertEqual(26, len({ref['sha256'] for check in report['checks'] for ref in check['evidence']}))
            for check in report['checks']:
                self.assertIn('job-123-attempt-1', (root / check['evidence'][0]['path']).read_text())

    def test_failure_or_not_run_never_creates_qualified_capability(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            report = assemble_report(self.records(root, failed=True), self.metadata(), root)
            capability = capability_report(report)
            self.assertFalse(capability['qualified'])
            self.assertFalse(capability['launch_ready'])
            self.assertEqual('NOT_QUALIFIED', capability['refusal'])

    def test_target_with_no_observable_check_result_is_not_supported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            records = self.records(root)
            for record in records:
                record['status'] = 'not-run'
                evidence = pathlib.Path(record['evidence_path'])
                raw = json.loads(evidence.read_text(encoding='utf-8'))
                raw['status'] = 'not-run'
                evidence.write_text(json.dumps(raw), encoding='utf-8')
            capability = capability_report(assemble_report(records, self.metadata(), root))
            self.assertFalse(capability['supported'])
            self.assertFalse(capability['qualified'])
            self.assertEqual('BACKEND_UNAVAILABLE', capability['refusal'])

    def test_assembly_rejects_missing_duplicate_and_unknown_checks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            records = self.records(root)
            with self.assertRaises(ValueError):
                assemble_report(records[:-1], self.metadata(), root)
            with self.assertRaises(ValueError):
                assemble_report(records[:-1] + [records[0]], self.metadata(), root)
            unknown = list(records)
            unknown[0] = {**unknown[0], 'check_id': 'Q99'}
            with self.assertRaises(ValueError):
                assemble_report(unknown, self.metadata(), root)

    def test_assembly_rejects_invalid_fallback_digest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            metadata = self.metadata()
            metadata['docker_desktop_report_sha256'] = 'not-a-digest'
            with self.assertRaisesRegex(ValueError, 'docker_desktop_report_sha256'):
                assemble_report(self.records(root), metadata, root)

    def test_pinned_cli_rejects_invalid_mutations_and_never_passes_unreviewed_reports(self):
        cli = shutil.which('agent-harness')
        self.assertIsNotNone(cli, 'install the pinned agent-harness v0.3.0 before this contract test')
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            report = assemble_report(self.records(root, failed=True), self.metadata(), root)
            capability = capability_report(report)

            def check(candidate, cap=capability, job='job-123-attempt-1'):
                rp, cp = root / 'report.json', root / 'capability.json'
                rp.write_text(json.dumps(candidate), encoding='utf-8')
                cp.write_text(json.dumps(cap), encoding='utf-8')
                return subprocess.run(
                    [cli, 'qualification', '--check', str(rp), '--evidence-root', str(root),
                     '--capability-report', str(cp), '--job-id', job], capture_output=True, text=True,
                ).returncode

            self.assertEqual(1, check(report))  # valid shape, but failed/not-run and no independent review
            self.assertEqual(1, check(report, job='job-123-attempt-10'))

            for field, value in (
                ('host', 'another-valid-host'),
                ('workload_image', 'sha256:' + 'c' * 64),
                ('policy_digest', 'sha256:' + 'd' * 64),
            ):
                altered = deepcopy(report)
                altered['tuple'][field] = value
                expected_cap = deepcopy(capability)
                if field == 'policy_digest':
                    expected_cap['policy_digest'] = value
                self.assertEqual(1, check(altered, expected_cap))

            altered = deepcopy(report)
            altered['checks'].pop()
            self.assertEqual(2, check(altered))
            altered = deepcopy(report)
            altered['checks'][-1] = deepcopy(altered['checks'][0])
            self.assertEqual(2, check(altered))

            for change in ('path', 'sha256', 'size', 'missing'):
                altered = deepcopy(report)
                evidence = altered['checks'][0]['evidence'][0]
                if change == 'path':
                    evidence['path'] = '../outside.json'
                elif change == 'sha256':
                    evidence['sha256'] = 'sha256:' + 'e' * 64
                elif change == 'size':
                    evidence['size'] += 1
                else:
                    evidence['path'] = 'missing.json'
                self.assertEqual(2, check(altered), change)

            mismatched = deepcopy(capability)
            mismatched['target'] = 'different-target'
            self.assertEqual(1, check(report, mismatched))
            mismatched = deepcopy(capability)
            mismatched['policy_digest'] = 'sha256:' + 'f' * 64
            self.assertEqual(1, check(report, mismatched))
            forged = deepcopy(capability)
            forged['qualified'] = True
            forged['refusal'] = 'CAPABILITY_UNSUPPORTED'
            self.assertEqual(1, check(report, forged))


if __name__ == '__main__':
    unittest.main()
