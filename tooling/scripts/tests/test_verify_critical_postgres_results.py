import os
import sys
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

import verify_critical_postgres_results as verifier


class VerifyCriticalPostgresResultsTest(unittest.TestCase):

    SUITE = "com.cp.ExamplePostgresIntegrationTest"
    CASE = "criticalCase"

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.manifest = self.root / "manifest.json"
        self.results = self.root / "results"
        self.results.mkdir()
        self.manifest.write_text(
            """
{
  "version": 1,
  "inventoryPolicy": "all-postgres-integration-tests-required",
  "requirements": [
    {
      "ac": "AC-01",
      "scenario": "critical scenario",
      "suite": "com.cp.ExamplePostgresIntegrationTest",
      "testMethod": "criticalCase"
    }
  ]
}
""".strip()
            + "\n"
        )

    def tearDown(self):
        self.temp.cleanup()

    def write_suite(
        self,
        *,
        file_name="TEST-example.xml",
        suite=None,
        cases=None,
        tests=None,
        skipped=0,
        failures=0,
        errors=0,
        retry_tag=None,
    ):
        suite = suite or self.SUITE
        cases = [self.CASE] if cases is None else cases
        tests = len(cases) if tests is None else tests
        root = ET.Element(
            "testsuite",
            name=suite,
            tests=str(tests),
            skipped=str(skipped),
            failures=str(failures),
            errors=str(errors),
        )
        for case in cases:
            ET.SubElement(root, "testcase", name=f"{case}()", classname=suite)
        if retry_tag is not None:
            ET.SubElement(root, retry_tag)
        path = self.results / file_name
        ET.ElementTree(root).write(path, encoding="unicode")
        return path

    def assert_invalid(self, expected):
        with self.assertRaises(verifier.VerificationError) as error:
            verifier.verify(self.manifest, self.results)
        self.assertIn(expected, str(error.exception))

    def test_accepts_exact_clean_manifest(self):
        self.write_suite()
        evidence = verifier.verify(self.manifest, self.results)
        self.assertEqual(1, evidence["suiteCount"])
        self.assertEqual(1, evidence["testCount"])
        self.assertEqual(1, evidence["requiredCaseCount"])

    def test_no_results_fails(self):
        self.assert_invalid("No JUnit XML results found")

    def test_missing_suite_fails(self):
        data = self.manifest.read_text()
        needle = '"testMethod": "criticalCase"'
        replacement = '"testMethod": "criticalCase"\n    },\n    {\n      "ac": "AC-02",\n      "scenario": "second critical scenario",\n      "suite": "com.cp.MissingPostgresIntegrationTest",\n      "testMethod": "missingCase"'
        if needle not in data:
            self.fail("manifest fixture anchor missing")
        self.manifest.write_text(data.replace(needle, replacement, 1))
        self.write_suite()
        self.assert_invalid("expected exactly once, found 0")

    def test_missing_required_case_fails(self):
        self.write_suite(cases=["differentCase"])
        self.assert_invalid("Required Postgres cases missing")

    def test_zero_tests_fails(self):
        self.write_suite(cases=[], tests=0)
        self.assert_invalid("executed zero tests")

    def test_skipped_fails(self):
        self.write_suite(skipped=1)
        self.assert_invalid("is not clean")

    def test_failure_and_error_fail(self):
        for field in ("failures", "errors"):
            with self.subTest(field=field):
                for path in self.results.glob("*.xml"):
                    path.unlink()
                self.write_suite(**{field: 1})
                self.assert_invalid("is not clean")

    def test_duplicate_suite_fails(self):
        self.write_suite(file_name="TEST-one.xml")
        self.write_suite(file_name="TEST-two.xml")
        self.assert_invalid("expected exactly once")

    def test_duplicate_case_fails(self):
        self.write_suite(cases=[self.CASE, self.CASE], tests=2)
        self.assert_invalid("Duplicate testcase")

    def test_stale_report_fails(self):
        marker = self.root / "started"
        marker.touch()
        report = self.write_suite()
        marker_mtime = marker.stat().st_mtime
        os.utime(report, (marker_mtime - 10, marker_mtime - 10))
        with self.assertRaises(verifier.VerificationError) as error:
            verifier.verify(self.manifest, self.results, started_after=marker)
        self.assertIn("Stale critical Postgres report", str(error.exception))

    def test_flaky_and_rerun_metadata_fail(self):
        for tag in ("flakyFailure", "rerunFailure", "flakyError", "rerunError"):
            with self.subTest(tag=tag):
                for path in self.results.glob("*.xml"):
                    path.unlink()
                self.write_suite(retry_tag=tag)
                self.assert_invalid("flaky/rerun metadata")

    def test_unmanifested_postgres_suite_source_fails(self):
        self.write_suite()
        source_root = self.root / "src"
        package = source_root / "com" / "cp"
        package.mkdir(parents=True)
        (package / "ExamplePostgresIntegrationTest.java").write_text(
            "package com.cp; class ExamplePostgresIntegrationTest {}"
        )
        (package / "AnotherPostgresIntegrationTest.java").write_text(
            "package com.cp; class AnotherPostgresIntegrationTest {}"
        )
        with self.assertRaises(verifier.VerificationError) as error:
            verifier.verify(self.manifest, self.results, source_root=source_root)
        self.assertIn("missing from critical manifest", str(error.exception))


if __name__ == "__main__":
    unittest.main()
