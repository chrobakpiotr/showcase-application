"""Adversarial checks for job-bound target qualification reports."""
from __future__ import annotations

import argparse
import copy
import json
import pathlib
import re
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from qualification.report import (
    CHECK_IDS, DEFAULT_WORKLOAD_IMAGE, _build_probe_targets, _checker_command,
    _measurement_exit_code, _target_tuple,
    assemble_report, capability_report, policy_digest, run,
)


class QualificationReportTest(unittest.TestCase):
    def _report_set(self, root: pathlib.Path, *, review: bool = True):
        evidence_root = root / 'evidence'
        evidence_root.mkdir()
        job_id = 'job-123-attempt-1'
        checks = []
        for check_id in CHECK_IDS:
            check_dir = evidence_root / check_id
            check_dir.mkdir()
            raw = check_dir / 'probe.json'
            raw.write_text(json.dumps({'job_id': job_id, 'check_id': check_id}) + '\n')
            checks.append({'check_id': check_id, 'status': 'pass', 'evidence_path': raw})

        report = assemble_report(
            target='showcase-docker-desktop-linux-guest',
            policy_digest='sha256:' + 'a' * 64,
            author='showcase-qualifier',
            target_tuple={
                'job_id': job_id, 'host': 'DockerDesktop_4_55', 'kernel': 'linux_6_12',
                'engine': 'DockerEngine_29_8', 'workload_image': 'sha256:' + 'b' * 64,
            },
            checks=checks, evidence_root=evidence_root,
        )
        if review:
            from agent_harness import contract

            subject = contract.review_subject(report)
            review_path = evidence_root / 'review.json'
            review_path.write_text(json.dumps({'review_subject': subject, 'reviewer': 'independent'}) + '\n')
            report['independent_review'] = {
                'reviewer': 'independent', 'subject': subject, 'verdict': 'pass',
                'evidence': {
                    'path': 'review.json',
                    'sha256': 'sha256:' + __import__('hashlib').sha256(review_path.read_bytes()).hexdigest(),
                    'size': review_path.stat().st_size,
                },
            }
        capability = capability_report(report, evidence_root)
        report_path, capability_path = root / 'report.json', root / 'capability.json'
        report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n')
        capability_path.write_text(json.dumps(capability, indent=2, sort_keys=True) + '\n')
        return report, capability, evidence_root, report_path, capability_path

    def _check(self, report, capability, evidence_root, report_path, capability_path, *, job_id='job-123-attempt-1'):
        report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n')
        capability_path.write_text(json.dumps(capability, indent=2, sort_keys=True) + '\n')
        env = dict(__import__('os').environ)
        env['PYTHONPATH'] = str(pathlib.Path(__file__).resolve().parents[1]) + __import__('os').pathsep + env.get('PYTHONPATH', '')
        return subprocess.run(
            [sys.executable, '-m', 'agent_harness', 'qualification', '--check', str(report_path),
             '--evidence-root', str(evidence_root), '--capability-report', str(capability_path),
             '--job-id', job_id], capture_output=True, text=True, env=env, check=False,
        )

    def test_report_requires_unique_evidence_for_all_26_checks_and_valid_binding(self):
        from agent_harness import contract

        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            report, capability, evidence_root, report_path, capability_path = self._report_set(root)
            self.assertEqual(set(contract.QUALIFICATION_CHECKS), {c['id'] for c in report['checks']})
            self.assertTrue(capability['discovered'])
            self.assertTrue(capability['supported'])
            self.assertTrue(capability['qualified'])
            self.assertFalse(capability['launch_ready'], 'qualification must not grant execution authority')
            self.assertEqual('BACKEND_UNAVAILABLE', capability['refusal'])
            self.assertEqual(0, self._check(
                report, capability, evidence_root, report_path, capability_path).returncode)

    def test_no_independent_review_or_failed_check_never_qualifies(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            report, capability, evidence_root, report_path, capability_path = self._report_set(root, review=False)
            self.assertFalse(capability['qualified'])
            self.assertFalse(capability['launch_ready'])
            self.assertEqual('NOT_QUALIFIED', capability['refusal'])
            self.assertEqual(1, self._check(
                report, capability, evidence_root, report_path, capability_path).returncode)

            report['checks'][0]['result'] = 'fail'
            capability = capability_report(report, evidence_root)
            self.assertFalse(capability['qualified'])
            self.assertEqual(1, self._check(
                report, capability, evidence_root, report_path, capability_path).returncode)

    def test_target_policy_job_and_host_tuple_mutations_invalidate_review_binding(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            report, capability, evidence_root, report_path, capability_path = self._report_set(root)
            mutations = (
                lambda doc: doc.update(target='other-target'),
                lambda doc: doc.update(policy_digest='sha256:' + 'c' * 64),
                lambda doc: doc['tuple'].update(job_id='job-123-attempt-2'),
                lambda doc: doc['tuple'].update(host='other-host'),
                lambda doc: doc['tuple'].update(kernel='other-kernel'),
                lambda doc: doc['tuple'].update(engine='other-engine'),
                lambda doc: doc['tuple'].update(workload_image='sha256:' + 'd' * 64),
            )
            for mutate in mutations:
                changed = copy.deepcopy(report)
                mutate(changed)
                with self.subTest(tuple=changed['tuple']):
                    self.assertNotEqual(0, self._check(
                        changed, capability, evidence_root, report_path, capability_path).returncode)

    def test_missing_duplicate_and_tampered_evidence_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            report, capability, evidence_root, report_path, capability_path = self._report_set(root)
            missing = copy.deepcopy(report)
            missing['checks'].pop()
            self.assertEqual(2, self._check(
                missing, capability, evidence_root, report_path, capability_path).returncode)
            duplicate = copy.deepcopy(report)
            duplicate['checks'][-1] = copy.deepcopy(duplicate['checks'][0])
            self.assertEqual(2, self._check(
                duplicate, capability, evidence_root, report_path, capability_path).returncode)
            for field, value in (('path', '../outside.json'), ('sha256', 'sha256:' + 'f' * 64), ('size', 1)):
                changed = copy.deepcopy(report)
                changed['checks'][0]['evidence'][0][field] = value
                with self.subTest(field=field):
                    self.assertEqual(2, self._check(
                        changed, capability, evidence_root, report_path, capability_path).returncode)

    def test_report_rejects_shared_evidence_between_checks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            _, _, evidence_root, _, _ = self._report_set(root)
            raw = evidence_root / 'Q01' / 'probe.json'
            checks = [
                {'check_id': check_id, 'status': 'fail', 'evidence_path': raw}
                for check_id in CHECK_IDS
            ]
            with self.assertRaisesRegex(ValueError, 'distinct evidence'):
                assemble_report(
                    target='showcase-docker-desktop-linux-guest',
                    policy_digest='sha256:' + 'a' * 64,
                    author='showcase-qualifier',
                    target_tuple={
                        'job_id': 'job-123-attempt-1', 'host': 'DockerDesktop_4_55',
                        'kernel': 'linux_6_12', 'engine': 'DockerEngine_29_8',
                        'workload_image': 'sha256:' + 'b' * 64,
                    },
                    checks=checks, evidence_root=evidence_root,
                )

    def test_unavailable_docker_identity_still_emits_all_nonpassing_checks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            evidence_root = root / 'evidence'
            report_path, capability_path = root / 'report.json', root / 'capability.json'

            class Probe:
                def execute(self, check_id, name, check_dir):
                    return {'status': 'not-run', 'reason_code': 'TARGET_IDENTITY_UNAVAILABLE',
                            'stdout': '', 'stderr': 'docker daemon unavailable', 'details': {}}

            args = argparse.Namespace(
                workload_image=DEFAULT_WORKLOAD_IMAGE,
                target_id='showcase-docker-desktop-linux-guest',
                job_id='job-unavailable-attempt-1', target='docker-desktop',
                evidence_root=evidence_root, report=report_path,
                capability_report=capability_path, sandbox_source=pathlib.Path('/unused'),
                docker='docker', timeout_seconds=30,
                repository_root=pathlib.Path(__file__).resolve().parents[3],
                author='showcase-qualifier',
            )
            probe = SimpleNamespace(
                execute_q01_q10=Probe().execute,
                execute_q11_q16=Probe().execute,
                execute_b1_b10=Probe().execute,
            )
            with mock.patch('qualification.report._docker_text', side_effect=RuntimeError('daemon unavailable')):
                with mock.patch('qualification.report._build_probe_targets',
                                return_value=(probe, probe, probe)):
                    result = run(args)

            report = json.loads(report_path.read_text())
            capability = json.loads(capability_path.read_text())
            self.assertEqual(0, result)
            self.assertEqual(set(CHECK_IDS), {item['id'] for item in report['checks']})
            self.assertTrue(all(item['result'] == 'not-run' for item in report['checks']))
            self.assertEqual('unavailable', report['tuple']['engine'])
            self.assertFalse(capability['qualified'])
            self.assertFalse(capability['launch_ready'])
            evidence_paths = [item['evidence'][0]['path'] for item in report['checks']]
            self.assertEqual(len(CHECK_IDS), len(set(evidence_paths)))
            for item in report['checks']:
                evidence = json.loads((evidence_root / item['evidence'][0]['path']).read_text())
                self.assertEqual('TARGET_IDENTITY_UNAVAILABLE', evidence['reason_code'])
                self.assertEqual('job-unavailable-attempt-1', evidence['job_id'])
                self.assertIn('docker_info_unavailable', evidence['details']['target_identity_errors'])

    def test_capability_target_policy_and_current_job_must_match(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            report, capability, evidence_root, report_path, capability_path = self._report_set(root)
            for field, value in (('target', 'other-target'), ('policy_digest', 'sha256:' + 'c' * 64)):
                changed = copy.deepcopy(capability)
                changed[field] = value
                with self.subTest(field=field):
                    self.assertEqual(1, self._check(
                        report, changed, evidence_root, report_path, capability_path).returncode)
            self.assertEqual(1, self._check(
                report, capability, evidence_root, report_path, capability_path,
                job_id='job-123-attempt-2').returncode)

    def test_policy_digest_changes_when_an_accepted_input_changes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            spec, verification = root / 'spec.md', root / 'verification.json'
            spec.write_text('accepted policy A')
            verification.write_text('{"version":1}')
            first = policy_digest(spec, verification)
            spec.write_text('accepted policy B')
            self.assertNotEqual(first, policy_digest(spec, verification))
            self.assertEqual(71, len(first))

    def test_docker_report_and_b10_candidate_share_the_exact_target_id(self):
        target_id = 'showcase-docker-desktop-linux-guest'
        _q_target, _lifecycle, grading = _build_probe_targets(
            DEFAULT_WORKLOAD_IMAGE, target_id, 'docker', 30)
        self.assertEqual(target_id, grading.target_id)

    def test_target_tuple_uses_daemon_identity_and_exact_pinned_image_digest(self):
        image = DEFAULT_WORKLOAD_IMAGE
        calls = [
            subprocess.CompletedProcess([], 0, 'linux|Docker Desktop|7.0.14-linuxkit', ''),
            subprocess.CompletedProcess([], 0, '29.8.2', ''),
            subprocess.CompletedProcess([], 0, json.dumps(['python@sha256:' + '9d72651cf7018c1f6a1dd6fd02bd68286631c33620bc0f37b0675b21aab915d5']), ''),
        ]
        with mock.patch('qualification.report.subprocess.run', side_effect=calls):
            observed = _target_tuple('docker-desktop', 'docker', image, 'job-123-attempt-1-local')
        self.assertEqual('Docker_Desktop', observed['host'])
        self.assertEqual('7.0.14-linuxkit', observed['kernel'])
        self.assertEqual('DockerEngine_29.8.2', observed['engine'])
        self.assertEqual('sha256:' + '9d72651cf7018c1f6a1dd6fd02bd68286631c33620bc0f37b0675b21aab915d5', observed['workload_image'])

    def test_release_pin_and_workflow_actions_are_immutable(self):
        import agent_harness

        repo_root = pathlib.Path(__file__).resolve().parents[3]
        requirements = (repo_root / 'tooling/agent-harness/requirements.txt').read_text()
        self.assertIn('@16bb93821292096b94889306288ee2caec5f4006', requirements)
        self.assertEqual('0.5.0', agent_harness.__version__)
        workflow = (repo_root / '.github/workflows/verification-sandbox-qualification.yml').read_text()
        refs = re.findall(r'^\s+uses:\s+([^@\s]+)@([0-9a-f]{40})\b', workflow, re.MULTILINE)
        self.assertEqual({
            ('actions/checkout', '3d3c42e5aac5ba805825da76410c181273ba90b1'),
            ('actions/setup-python', 'e797f83bcb11b83ae66e0230d6156d7c80228e7c'),
            ('actions/upload-artifact', '043fb46d1a93c77aae656e7c1c64a875d1fc6a0a'),
        }, set(refs))

    def test_valid_nonpassing_measurement_is_complete_but_invalid_report_fails(self):
        self.assertEqual(0, _measurement_exit_code(0))
        self.assertEqual(0, _measurement_exit_code(1))
        self.assertEqual(2, _measurement_exit_code(2))
        self.assertEqual(3, _measurement_exit_code(3))
        self.assertEqual(2, _measurement_exit_code(-9))

    def test_checker_uses_the_installed_pinned_module_and_rejects_other_versions(self):
        import qualification.report as report_module

        self.assertEqual('0.5.0', report_module.HARNESS_VERSION)
        self.assertEqual(
            [sys.executable, '-m', 'agent_harness', 'qualification', '--check', 'report.json'],
            _checker_command('qualification', '--check', 'report.json'),
        )
        with mock.patch.object(report_module, 'HARNESS_VERSION', '0.4.0'):
            with self.assertRaisesRegex(RuntimeError, 'requires agent-harness 0.5.0'):
                _checker_command('qualification')


if __name__ == '__main__':
    unittest.main()
