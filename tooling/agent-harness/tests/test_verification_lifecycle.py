"""End-to-end checks for plan admission, launch and lifecycle terminalization."""
from __future__ import annotations

import argparse
import contextlib
import io
import pathlib
import subprocess
import sys
import unittest
from unittest import mock

TESTS = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(TESTS))
sys.path.insert(0, str(TESTS.parent))

import test_harness
import test_telemetry
import test_verification_supervisor
import harness as control_harness
from verification.authority import prepare_task_plan, resolve_execution
from verification.executor import execute_plan
from verification.store import VerificationStore


class VerificationLifecycleTest(unittest.TestCase):
    def setUp(self):
        self.original_cwd = pathlib.Path.cwd()
        self.original_state_dir = control_harness.STATE_DIR
        self.fixture = test_harness.HarnessTest()
        self.fixture.setUp()
        self.original_fixture_state_dir = test_harness.harness.STATE_DIR
        control_harness.STATE_DIR = self.fixture.root / '.agent-state'
        test_harness.harness.STATE_DIR = control_harness.STATE_DIR

        # The task fixture's accepted verification command resolves through a
        # committed executable just as the production resolver requires.
        gradlew = self.fixture.root / 'gradlew'
        gradlew.write_text('#!/bin/sh\nexit 0\n', encoding='utf-8')
        gradlew.chmod(0o700)
        subprocess.run(['git', 'add', 'gradlew'], cwd=self.fixture.root, check=True)
        subprocess.run(['git', 'commit', '-qm', 'add bound test executable'],
                       cwd=self.fixture.root, check=True)
        self.feature = self.fixture.feature()
        self.doc = control_harness.load_validated(self.feature)
        control_harness.write_packet(
            self.doc, control_harness.task_index(self.doc)['T-001'], self.feature)
        with contextlib.redirect_stdout(io.StringIO()):
            control_harness.cmd_claim(argparse.Namespace(
                feature_dir=self.feature, task_id='T-001', owner='lifecycle-test'))
        checkpoint = subprocess.check_output(
            ['git', 'rev-parse', 'HEAD'], cwd=self.fixture.root, text=True).strip()
        self.accepted = prepare_task_plan(
            self.fixture.root, self.feature, 'T-001', 1, checkpoint,
            self.doc['tasks'][0]['verification'])
        self.addCleanup(self._cleanup)

    def _cleanup(self):
        control_harness.STATE_DIR = self.original_state_dir
        test_harness.harness.STATE_DIR = self.original_fixture_state_dir
        self.fixture.tearDown()
        if pathlib.Path.cwd() != self.original_cwd:
            import os
            os.chdir(self.original_cwd)

    def test_executor_uses_real_admission_consumption_and_terminal_cas(self):
        accepted, profile, plan, units = resolve_execution(
            self.fixture.root, self.accepted['plan_id'])
        self.assertEqual(self.accepted, accepted)
        self.assertTrue(units)

        store = VerificationStore(self.fixture.root)
        backend_root = self.fixture.root.parent / 'lifecycle-controlled-backend'
        backend = test_verification_supervisor.LifecycleBackend(store, backend_root)
        with mock.patch('verification.executor.CommandExecutionBackend', return_value=backend):
            result = execute_plan(self.fixture.root, profile, plan, store=store,
                attempt_id='lifecycle-end-to-end', sandbox_mode='required',
                authority_context=accepted)

        self.assertEqual('PASS', result.outcome, result.to_record())
        self.assertEqual(len(plan.decisions), backend.events.count('launch'))
        current = control_harness.load_state(self.feature, self.doc)
        authority = current['verification_authority']
        self.assertEqual(len(units), len(authority['admissions']))
        self.assertEqual(len(units), len(authority['launch_consumptions']))
        self.assertTrue(all(item['status'] == 'execution_terminal'
                            for item in authority['launch_reservations'].values()))
        self.assertTrue(all(item['status'] == 'execution_terminal'
                            for item in authority['launch_consumptions'].values()))
        terminals = VerificationStore(self.fixture.root).reconstruct_execution_terminals(
            rebuild=False)
        self.assertEqual(len(units), len(terminals))
        self.assertTrue(all(item.get('launch_consumption_id') for item in terminals))

    def test_signed_manual_registration_reaches_coverage_verification_without_stubs(self):
        fixture = test_telemetry.TelemetryTest()._signed_manual_fixture()
        previous_state_dir = control_harness.STATE_DIR
        control_harness.STATE_DIR = fixture['root'] / '.agent-state'
        try:
            checkpoint = fixture['requested_checkpoint']
            telemetry = test_telemetry.telemetry
            original_generation = control_harness.load_state(
                fixture['feature_dir'], control_harness.load_validated(fixture['feature_dir'])
            ).get('feature_generation', 1)
            observation_path = telemetry.record_manual(
                repo=fixture['root'], feature='TST-MANUAL', role='evaluator',
                provider='manual', checkpoint=checkpoint, verdict='PASS',
                report=fixture['report_path'].relative_to(fixture['root']).as_posix(),
                attestation=fixture['attestation_path'].relative_to(fixture['root']).as_posix(),
            )
            observation = __import__('json').loads(observation_path.read_text(encoding='utf-8'))
            state = control_harness.load_state(
                fixture['feature_dir'], control_harness.load_validated(fixture['feature_dir'])
            )
            self.assertEqual(original_generation, state.get('feature_generation', 1))
            ledger = state.get('manual_observation_ledger', [])
            self.assertEqual(1, sum(item.get('observation_id') == observation['observation_id']
                                    for item in ledger))
            proof = telemetry.verify_manual_observation_for_coverage(fixture['root'], observation)
            self.assertEqual(observation['attestation_sha256'], proof['attestation_sha256'])
            self.assertEqual(observation['report_sha256'], proof['report_sha256'])
        finally:
            control_harness.STATE_DIR = previous_state_dir
            fixture['temporary'].cleanup()


if __name__ == '__main__':
    unittest.main()
