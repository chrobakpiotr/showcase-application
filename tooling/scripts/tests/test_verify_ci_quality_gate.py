import re
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parents[1]
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

    def test_each_backend_gate_is_required_on_its_own(self):
        for key, name in (("BACKEND_POSTGRES_GATE_RESULT", "Backend critical Postgres gate"),
                          ("BACKEND_RABBITMQ_GATE_RESULT", "Backend critical RabbitMQ gate")):
            with self.subTest(key):
                results = self.successful_results()
                results[key] = "failure"
                errors = gate.evaluate("push", results)
                self.assertTrue(any(name in error for error in errors), errors)
                del results[key]
                self.assertTrue(any("<missing>" in error for error in gate.evaluate("push", results)))

    def test_the_workflow_wires_every_required_result(self):
        """Every required key is passed to the gate step by ci.yml from a job the gate `needs`."""
        import pathlib
        import re
        workflow = (pathlib.Path(__file__).resolve().parents[3] / ".github" / "workflows" / "ci.yml").read_text()
        gate_job = workflow[workflow.index("  ci-quality-gate:"):]
        for display_name, key in gate.REQUIRED_RESULTS + ((None, gate.DEPENDENCY_REVIEW_KEY),):
            with self.subTest(key):
                match = re.search(rf"{key}: \$\{{\{{ needs\['([a-z0-9-]+)'\]\.result \}}\}}", gate_job)
                self.assertIsNotNone(match, key)
                self.assertRegex(gate_job, rf"needs:\n(      - [a-z0-9-]+\n)*      - {match.group(1)}\n")
                if key.startswith("BACKEND"):
                    self.assertIn(f"    name: {display_name}\n", workflow)

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

    def test_critical_evidence_uploads_skip_when_no_evidence_was_created(self):
        workflow = (REPOSITORY / ".github/workflows/ci.yml").read_text()
        for step_name, evidence_path in (
            (
                "Publish isolated critical PostgreSQL evidence",
                "apps/ecommerce/backend/build/critical-postgres-results/**",
            ),
            (
                "Publish isolated critical RabbitMQ evidence",
                "apps/ecommerce/backend/build/critical-rabbitmq-results/**",
            ),
        ):
            with self.subTest(step=step_name):
                step = re.search(
                    rf"- name: {re.escape(step_name)}(?P<body>.*?)(?=\n      - name:|\n  [a-z-]+:|\Z)",
                    workflow,
                    flags=re.DOTALL,
                )
                self.assertIsNotNone(step)
                self.assertIn(
                    f"if: always() && hashFiles('{evidence_path}') != ''",
                    step.group("body"),
                )
                self.assertIn("if-no-files-found: error", step.group("body"))


if __name__ == "__main__":
    unittest.main()
