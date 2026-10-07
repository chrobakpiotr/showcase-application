import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from qualification.q_probes import Q01_Q10, run_q01_q10


class QualificationProbeTest(unittest.TestCase):
    def test_q01_q10_ids_and_names_match_the_accepted_qualification_contract(self):
        self.assertEqual(
            [
                ('Q01', 'PERMITTED_WRITE'),
                ('Q02', 'PROTECTED_WRITE_DIRECT'),
                ('Q03', 'PROTECTED_WRITE_CHILD'),
                ('Q04', 'PROTECTED_WRITE_GRANDCHILD'),
                ('Q05', 'CONTAINMENT_CHILD'),
                ('Q06', 'CONTAINMENT_GRANDCHILD'),
                ('Q07', 'CONTAINMENT_PARENT_EXIT'),
                ('Q08', 'CONTAINMENT_NEW_PROCESS_GROUP'),
                ('Q09', 'CONTAINMENT_NEW_SESSION'),
                ('Q10', 'CONTAINMENT_BACKGROUND_SHELL'),
            ],
            list(Q01_Q10.items()),
        )

    def test_probe_failure_does_not_skip_later_checks_and_each_has_unique_evidence(self):
        calls = []

        def execute(check_id, name, evidence_dir):
            calls.append(check_id)
            return {
                'status': 'fail' if check_id == 'Q02' else 'pass',
                'reason_code': 'COUNTEREXAMPLE' if check_id == 'Q02' else 'OBSERVED',
                'stdout': f'{check_id} stdout',
                'stderr': '',
                'details': {'name': name},
            }

        with tempfile.TemporaryDirectory() as tmp:
            result = run_q01_q10(execute, pathlib.Path(tmp))
            self.assertEqual(list(Q01_Q10), calls)
            self.assertEqual(10, len(result))
            self.assertEqual('fail', result[1].status)
            paths = [record.evidence_path for record in result]
            self.assertEqual(10, len(set(paths)))
            for record in result:
                doc = json.loads(pathlib.Path(record.evidence_path).read_text(encoding='utf-8'))
                self.assertEqual(record.check_id, doc['check_id'])
                self.assertEqual(record.status, doc['status'])
                self.assertTrue(pathlib.Path(record.evidence_path).is_file())

    def test_probe_exception_is_not_pass_and_remaining_checks_still_run(self):
        calls = []

        def execute(check_id, _name, _evidence_dir):
            calls.append(check_id)
            if check_id == 'Q03':
                raise OSError('probe unavailable')
            return {'status': 'pass', 'reason_code': 'OBSERVED', 'stdout': '', 'stderr': '', 'details': {}}

        with tempfile.TemporaryDirectory() as tmp:
            result = run_q01_q10(execute, pathlib.Path(tmp))
        self.assertEqual(list(Q01_Q10), calls)
        self.assertEqual('not-run', result[2].status)
        self.assertEqual('PROBE_EXCEPTION', result[2].reason_code)

    def test_probe_rejects_unknown_outcome_as_not_run(self):
        def execute(_check_id, _name, _evidence_dir):
            return {'status': 'unsupported', 'reason_code': 'unknown', 'stdout': '', 'stderr': '', 'details': {}}

        with tempfile.TemporaryDirectory() as tmp:
            result = run_q01_q10(execute, pathlib.Path(tmp))
        self.assertEqual({'not-run'}, {record.status for record in result})


if __name__ == '__main__':
    unittest.main()
