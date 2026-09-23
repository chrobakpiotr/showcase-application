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

    def test_policy_declares_remote_deployment_pending(self):
        policy = json.loads(
            (REPO / "tooling/quality/github-required-status-policy.json").read_text()
        )
        self.assertEqual("pending-human-authorization", policy["deploymentStatus"])
        self.assertEqual([], policy["desired"]["recommendedBypassActors"])


if __name__ == "__main__":
    unittest.main()
