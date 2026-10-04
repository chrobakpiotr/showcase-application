import json
import pathlib
import subprocess
import sys
import unittest

REPO = pathlib.Path(__file__).resolve().parents[3]
SCRIPT = REPO / "tooling" / "scripts" / "verify_required_status_policy.py"


class VerifyRequiredStatusPolicyTest(unittest.TestCase):

    def test_repository_policy_is_consistent_with_workflow_job_names(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT)],
            cwd=REPO,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("PASS:", result.stdout)

    def test_policy_records_current_read_only_ruleset_snapshot(self):
        policy = json.loads(
            (REPO / "tooling/quality/github-required-status-policy.json").read_text()
        )
        self.assertEqual("read-only-verified", policy["deploymentStatus"])
        observed = policy["observed"]
        self.assertEqual("active", observed["enforcement"])
        self.assertEqual(
            ["CI quality gate", "Agentic SDD quality gate"],
            observed["requiredStatusChecks"],
        )
        self.assertIsNone(observed["bypassActors"])
        self.assertEqual("not-inspected", observed["branchProtectionEndpoint"])
        self.assertEqual("2026-10-04", observed["snapshotDate"])
        self.assertEqual("GET /repos/user99987/showcase-application/rulesets/21939086", observed["apiEndpoint"])
        self.assertEqual("chrobakpiotr/showcase-application", observed["effectiveSource"])
        self.assertEqual("2026-09-23T16:32:18.015Z", observed["rulesetUpdatedAt"])
        self.assertEqual([], policy["desired"]["recommendedBypassActors"])


if __name__ == "__main__":
    unittest.main()
