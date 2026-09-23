import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

import verify_ci_quality_gate as gate


class VerifyCiQualityGateTest(unittest.TestCase):

    def successful_results(self):
        results = {key: "success" for _, key in gate.REQUIRED_RESULTS}
        results[gate.DEPENDENCY_REVIEW_KEY] = "success"
        return results

    def test_pull_request_requires_every_job_including_dependency_review(self):
        self.assertEqual([], gate.evaluate("pull_request", self.successful_results()))

    def test_push_allows_dependency_review_to_be_skipped(self):
        results = self.successful_results()
        results[gate.DEPENDENCY_REVIEW_KEY] = "skipped"
        self.assertEqual([], gate.evaluate("push", results))

    def test_workflow_dispatch_allows_dependency_review_to_be_skipped(self):
        results = self.successful_results()
        results[gate.DEPENDENCY_REVIEW_KEY] = "skipped"
        self.assertEqual([], gate.evaluate("workflow_dispatch", results))

    def test_pull_request_rejects_skipped_dependency_review(self):
        results = self.successful_results()
        results[gate.DEPENDENCY_REVIEW_KEY] = "skipped"
        errors = gate.evaluate("pull_request", results)
        self.assertTrue(any("pull_request requires success" in error for error in errors))

    def test_required_failure_fails_closed(self):
        results = self.successful_results()
        results["BACKEND_RESULT"] = "failure"
        errors = gate.evaluate("push", results)
        self.assertTrue(any("Backend build" in error for error in errors))

    def test_required_skipped_job_fails_closed(self):
        results = self.successful_results()
        results["E2E_RESULT"] = "skipped"
        errors = gate.evaluate("pull_request", results)
        self.assertTrue(any("End-to-end tests" in error for error in errors))

    def test_required_cancelled_job_fails_closed(self):
        results = self.successful_results()
        results["FRONTEND_RESULT"] = "cancelled"
        errors = gate.evaluate("push", results)
        self.assertTrue(any("Frontend build" in error for error in errors))

    def test_empty_event_fails_closed(self):
        results = self.successful_results()
        results[gate.DEPENDENCY_REVIEW_KEY] = "skipped"
        errors = gate.evaluate("", results)
        self.assertTrue(any("unsupported or missing event" in error for error in errors))

    def test_unknown_event_fails_closed(self):
        results = self.successful_results()
        results[gate.DEPENDENCY_REVIEW_KEY] = "skipped"
        errors = gate.evaluate("repository_dispatch", results)
        self.assertTrue(any("unsupported or missing event" in error for error in errors))

    def test_missing_result_fails_closed(self):
        results = self.successful_results()
        del results["DEPENDENCY_CHECK_RESULT"]
        errors = gate.evaluate("push", results)
        self.assertTrue(any("<missing>" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
