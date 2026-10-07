import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from qualification.b_probes import B1_B10, run_b1_b10


class QualificationGradingProbeTest(unittest.TestCase):
    def test_b1_b10_ids_and_names_are_exact(self):
        self.assertEqual([
            ('B1', 'HIDDEN_MATERIAL_ABSENT'),
            ('B2', 'HIDDEN_EXPECTED_VALUES_UNREADABLE'),
            ('B3', 'FRESH_DESTROYED_SANDBOX'),
            ('B4', 'FILESYSTEM_AND_MOUNT_BOUNDARIES'),
            ('B5', 'NO_NETWORK_EGRESS_DNS_OR_METADATA'),
            ('B6', 'EXACT_ENVIRONMENT_NO_INHERITED_CREDENTIALS'),
            ('B7', 'NONROOT_NO_CAPS_NO_NEW_PRIVS_SECCOMP'),
            ('B8', 'RESOURCE_LIMITS_AND_BOUNDED_OUTPUT'),
            ('B9', 'BOUNDED_ARTIFACT_AFTER_SANDBOX_DESTROYED'),
            ('B10', 'PER_GRADE_TARGET_AND_LIMIT_PROVENANCE'),
        ], list(B1_B10.items()))

    def test_failure_and_exception_do_not_skip_checks_and_evidence_is_per_check(self):
        calls = []

        def execute(check_id, name, evidence_dir):
            calls.append(check_id)
            if check_id == 'B2':
                return {'status': 'fail', 'reason_code': 'COUNTEREXAMPLE'}
            if check_id == 'B3':
                raise OSError('probe unavailable')
            return {'status': 'pass', 'reason_code': 'OBSERVED', 'details': {'name': name}}

        with tempfile.TemporaryDirectory() as tmp:
            records = run_b1_b10(execute, pathlib.Path(tmp))
            self.assertEqual(list(B1_B10), calls)
            self.assertEqual(10, len(records))
            self.assertEqual('fail', records[1].status)
            self.assertEqual('not-run', records[2].status)
            self.assertEqual('PROBE_EXCEPTION', records[2].reason_code)
            self.assertEqual(10, len({record.evidence_path for record in records}))
            for record in records:
                evidence = json.loads(record.evidence_path.read_text(encoding='utf-8'))
                self.assertEqual(record.check_id, evidence['check_id'])
                self.assertEqual(record.status, evidence['status'])
                self.assertTrue(record.evidence_path.is_file())

    def test_invalid_outcome_is_not_a_pass(self):
        records = run_b1_b10(
            lambda *_: {'status': 'unsupported', 'reason_code': 'UNKNOWN'},
            pathlib.Path(tempfile.mkdtemp()),
        )
        self.assertEqual({'not-run'}, {record.status for record in records})
        self.assertEqual({'INVALID_PROBE_OUTCOME'}, {record.reason_code for record in records})


if __name__ == '__main__':
    unittest.main()
