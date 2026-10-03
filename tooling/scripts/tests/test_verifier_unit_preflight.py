"""Verifier scripts must run their unit tests before touching heavy-gate state."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

SCRIPTS = pathlib.Path(__file__).resolve().parents[1]


class VerifierUnitPreflightTest(unittest.TestCase):
    def assert_failed_unit_preserves_gate_state(self, script_name, unit_name, reports, marker):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            scripts = root / 'tooling/scripts'
            (scripts / 'tests').mkdir(parents=True)
            script = scripts / script_name
            shutil.copyfile(SCRIPTS / script_name, script)
            (scripts / 'tests' / unit_name).write_text(
                'import unittest\n'
                'class DeliberatelyFailingValidatorTest(unittest.TestCase):\n'
                '    def test_validator_contract(self):\n'
                '        self.fail("UNIT_PREFLIGHT_FAILURE_SENTINEL")\n')
            report = root / reports / 'prior-evidence.json'
            report.parent.mkdir(parents=True)
            report.write_text('prior evidence must survive\n')
            start = root / marker
            start.write_text('prior start marker must survive\n')
            prior_stat = start.stat().st_mtime_ns
            gradle_marker = root / 'heavy-gate-launched'
            gradlew = root / 'gradlew'
            gradlew.write_text('#!/usr/bin/env bash\ntouch heavy-gate-launched\nexit 97\n')
            gradlew.chmod(0o755)
            result = subprocess.run(['bash', str(script)], cwd=temporary,
                                    capture_output=True, text=True, check=False)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('UNIT_PREFLIGHT_FAILURE_SENTINEL', result.stderr)
            self.assertIn('Ran 1 test', result.stderr)
            self.assertFalse(gradle_marker.exists())
            self.assertEqual('prior evidence must survive\n', report.read_text())
            self.assertEqual('prior start marker must survive\n', start.read_text())
            self.assertEqual(prior_stat, start.stat().st_mtime_ns)

    def test_pit_unit_failure_precedes_java_cleanup_and_gradle(self):
        self.assert_failed_unit_preserves_gate_state(
            'verify-domain-pitest.sh', 'test_verify_pit_report.py',
            'modules/domain/build/reports/pitest', 'modules/domain/build/reports/pitest.started')

    def test_postgres_unit_failure_precedes_cleanup_and_gradle(self):
        self.assert_failed_unit_preserves_gate_state(
            'verify-critical-postgres-tests.sh', 'test_verify_critical_postgres_results.py',
            'apps/ecommerce/backend/build/critical-postgres-results',
            'apps/ecommerce/backend/build/critical-postgres.started')

    def test_rabbit_unit_failure_precedes_cleanup_and_gradle(self):
        self.assert_failed_unit_preserves_gate_state(
            'verify-critical-rabbitmq-tests.sh', 'test_verify_critical_rabbitmq_results.py',
            'apps/ecommerce/backend/build/critical-rabbitmq-results',
            'apps/ecommerce/backend/build/critical-rabbitmq.started')
