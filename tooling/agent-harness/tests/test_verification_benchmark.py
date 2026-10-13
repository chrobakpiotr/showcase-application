import pathlib
import os
import sys
import unittest

BENCHMARKS = pathlib.Path(__file__).resolve().parents[1] / 'benchmarks'
sys.path.insert(0, str(BENCHMARKS))
from verification_planning import run_benchmark


class VerificationBenchmarkTest(unittest.TestCase):
    def test_representative_planning_benchmark_stays_within_contract(self):
        result = run_benchmark()
        self.assertEqual({'gates': 100, 'files': 5000, 'bytes_per_file': 256,
                          'disjoint_matches_per_gate': 50, 'bytes_total': 1280000}, result['fixture'])
        self.assertEqual(3, result['runs'])
        self.assertLessEqual(result['git_subprocess_calls_per_run'], 12)
        # The accepted timing target is specifically Agentic SDD Linux CI.
        # Developer machines still exercise and report the same fixture/operation
        # counts without turning host speed into a local correctness failure.
        if sys.platform == 'linux' and os.environ.get('CI', '').lower() == 'true':
            self.assertLessEqual(result['median_seconds'], 10)
            self.assertLessEqual(result['maximum_seconds'], 20)


if __name__ == '__main__':
    unittest.main()
