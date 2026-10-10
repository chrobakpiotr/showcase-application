"""Cross-module tests for durable lifecycle evidence in disposable repositories."""
from __future__ import annotations

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import test_harness
import test_telemetry
import test_verification_admission


def run_fixture_test(test_case):
    import harness as control_harness
    original_state_dir = control_harness.STATE_DIR
    original_fixture_state_dir = test_harness.harness.STATE_DIR
    test_case.setUp()
    try:
        getattr(test_case, test_case._testMethodName)()
    finally:
        test_case.tearDown()
        test_case.doCleanups()
        control_harness.STATE_DIR = original_state_dir
        test_harness.harness.STATE_DIR = original_fixture_state_dir


class VerificationLifecycleTest(unittest.TestCase):
    def test_real_plan_registration_and_coverage_proof(self):
        run_fixture_test(test_telemetry.TelemetryTest(
            'test_real_plan_registration_and_coverage_use_committed_profile_and_signature'))

    def test_signed_plan_bound_registration_reaches_coverage_cas(self):
        run_fixture_test(test_telemetry.TelemetryTest(
            'test_plan_bound_manual_registration_flows_into_coverage_cas'))

    def test_real_agent_state_registration_is_immutable_and_idempotent(self):
        run_fixture_test(test_harness.HarnessTest(
            'test_register_manual_observation_is_trusted_scoped_and_idempotent'))

    @unittest.skipUnless(hasattr(__import__('os'), 'fork'), 'requires POSIX process termination semantics')
    def test_sigkill_between_consumption_and_launch_marker(self):
        run_fixture_test(test_verification_admission.PlanExecutionAdmissionTest(
            'test_sigkill_after_consumption_before_authority_journal_is_recoverable'))

    @unittest.skipUnless(hasattr(__import__('os'), 'fork'), 'requires POSIX process termination semantics')
    def test_sigkill_between_reservation_and_consumption(self):
        run_fixture_test(test_verification_admission.PlanExecutionAdmissionTest(
            'test_sigkill_after_reservation_before_consumption_is_terminalized'))

    def test_consume_then_exception_terminalizes_without_stranding(self):
        run_fixture_test(test_verification_admission.PlanExecutionAdmissionTest(
            'test_exception_after_consumption_is_terminalized_without_stranding_admission'))

    def test_reserve_then_exception_terminalizes_without_stranding(self):
        run_fixture_test(test_verification_admission.PlanExecutionAdmissionTest(
            'test_exception_after_reservation_is_terminalized_without_stranding_admission'))

    def test_executor_uses_lifecycle_launch_authorizer(self):
        run_fixture_test(test_verification_admission.PlanExecutionAdmissionTest(
            'test_execute_plan_wires_exact_lifecycle_launch_authority'))


if __name__ == '__main__':
    unittest.main()
