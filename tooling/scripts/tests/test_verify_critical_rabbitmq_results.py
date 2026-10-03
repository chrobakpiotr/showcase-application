"""Characterize the critical RabbitMQ gate's existing evidence contract."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import verify_critical_rabbitmq_results as verifier


class VerifyCriticalRabbitResultsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.manifest = self.root / 'manifest.json'
        self.data = json.loads((SCRIPTS.parent / 'quality/critical-rabbitmq-manifest.json').read_text())
        self.manifest.write_text(json.dumps(self.data))
        self.suite = self.data['requirements'][0]['suite']
        self.cases = [item['testMethod'] for item in self.data['requirements']]
        self.results = self.root / 'results'
        self.results.mkdir()
        self.marker = self.root / 'started'
        self.marker.touch()
        self.source = self.root / 'source'
        self.source_file = self.source / Path(*self.suite.split('.')).with_suffix('.java')
        self.source_file.parent.mkdir(parents=True)
        self.source_file.write_text('class Fixture {}\n')
        self.report = self.results / 'TEST-rabbit.xml'
        self.write_suite()

    def write_suite(self, **attributes):
        root = ET.Element('testsuite', name=self.suite, tests=str(len(self.cases)),
                          skipped='0', failures='0', errors='0')
        root.attrib.update(attributes)
        for name in self.cases:
            ET.SubElement(root, 'testcase', name=name + '()')
        ET.ElementTree(root).write(self.report, encoding='unicode')
        return root

    def verify(self):
        return verifier.verify(self.manifest, self.results, self.marker, self.source, 'a' * 40)

    def test_clean_real_manifest_keeps_two_mapped_cases_and_evidence_fields(self):
        self.assertEqual([self.suite], verifier.list_suites(self.manifest))
        evidence = self.verify()
        self.assertEqual({'version', 'sourceSha', 'manifestSha256', 'generatedAt', 'suiteCount',
            'testCount', 'mappedCaseCount', 'skipped', 'failures', 'errors', 'suite', 'cases'}, set(evidence))
        self.assertEqual(1, evidence['version'])
        self.assertEqual(2, evidence['mappedCaseCount'])
        self.assertEqual(2, evidence['testCount'])
        self.assertEqual(sorted(self.cases), evidence['cases'])
        self.assertEqual(hashlib.sha256(self.manifest.read_bytes()).hexdigest(), evidence['manifestSha256'])

    def test_invalid_manifest_rejects_suite_selection(self):
        for key, value in [('version', 2), ('inventoryPolicy', 'unknown'), ('requirements', [])]:
            with self.subTest(key=key):
                self.manifest.write_text(json.dumps({**self.data, key: value}))
                with self.assertRaises(SystemExit):
                    verifier.list_suites(self.manifest)
        data = json.loads(json.dumps(self.data))
        data['requirements'][1]['suite'] = 'other.Suite'
        self.manifest.write_text(json.dumps(data))
        with self.assertRaisesRegex(SystemExit, 'exactly one'):
            verifier.list_suites(self.manifest)

    def test_missing_source_or_results_reject(self):
        self.source_file.unlink()
        with self.assertRaisesRegex(SystemExit, 'source is missing'):
            self.verify()
        self.source_file.write_text('class Fixture {}')
        self.report.unlink()
        with self.assertRaisesRegex(SystemExit, 'no JUnit XML'):
            self.verify()

    def test_stale_report_rejects(self):
        stale = self.marker.stat().st_mtime_ns - 1_000_000_000
        os.utime(self.report, ns=(stale, stale))
        with self.assertRaisesRegex(SystemExit, 'stale'):
            self.verify()

    def test_duplicate_and_unexpected_suites_reject(self):
        duplicate = self.results / 'TEST-copy.xml'
        duplicate.write_bytes(self.report.read_bytes())
        with self.assertRaisesRegex(SystemExit, 'exactly once'):
            self.verify()
        duplicate.unlink()
        self.write_suite(name='unexpected.Suite')
        with self.assertRaisesRegex(SystemExit, 'unexpected suites'):
            self.verify()

    def test_zero_skipped_failed_error_and_noninteger_counts_reject(self):
        for key, value in [('tests', '0'), ('skipped', '1'), ('failures', '1'),
                           ('errors', '1'), ('tests', 'not-an-integer')]:
            with self.subTest(key=key):
                self.write_suite(**{key: value})
                with self.assertRaises(SystemExit):
                    self.verify()

    def test_skipped_failed_and_error_testcases_reject(self):
        for tag in ('skipped', 'failure', 'error'):
            with self.subTest(tag=tag):
                root = self.write_suite()
                ET.SubElement(root.find('testcase'), tag)
                ET.ElementTree(root).write(self.report, encoding='unicode')
                with self.assertRaises(SystemExit):
                    self.verify()

    def test_flaky_and_rerun_tags_and_attributes_reject(self):
        for name in ('flakyFailure', 'rerunFailure'):
            with self.subTest(name=name):
                root = self.write_suite()
                ET.SubElement(root.find('testcase'), name)
                ET.ElementTree(root).write(self.report, encoding='unicode')
                with self.assertRaisesRegex(SystemExit, 'retry/flaky'):
                    self.verify()
                root = self.write_suite()
                root.find('testcase').set(name, 'true')
                ET.ElementTree(root).write(self.report, encoding='unicode')
                with self.assertRaisesRegex(SystemExit, 'retry/flaky'):
                    self.verify()

    def test_missing_unexpected_unnamed_cases_and_count_mismatch_reject(self):
        for mode in ('missing', 'unexpected', 'unnamed', 'count'):
            with self.subTest(mode=mode):
                root = self.write_suite()
                if mode == 'missing':
                    root.remove(root.find('testcase'))
                elif mode == 'unexpected':
                    root.find('testcase').set('name', 'unexpectedCase')
                elif mode == 'unnamed':
                    root.find('testcase').set('name', '')
                else:
                    root.set('tests', '3')
                ET.ElementTree(root).write(self.report, encoding='unicode')
                with self.assertRaises(SystemExit):
                    self.verify()

    def test_cli_writes_evidence_only_for_clean_results(self):
        output = self.root / 'evidence.json'
        argv = [sys.executable, str(SCRIPTS / 'verify_critical_rabbitmq_results.py'),
            '--manifest', str(self.manifest), '--results', str(self.results),
            '--started-after', str(self.marker), '--source-root', str(self.source),
            '--source-sha', 'a' * 40, '--evidence-output', str(output)]
        clean = subprocess.run(argv, capture_output=True, text=True, check=False)
        self.assertEqual(0, clean.returncode, clean.stderr)
        self.assertEqual(2, json.loads(output.read_text())['testCount'])
        output.unlink()
        self.write_suite(skipped='1')
        invalid = subprocess.run(argv, capture_output=True, text=True, check=False)
        self.assertNotEqual(0, invalid.returncode)
        self.assertFalse(output.exists())
