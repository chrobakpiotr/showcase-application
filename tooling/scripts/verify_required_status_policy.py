#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

POLICY = Path("tooling/quality/github-required-status-policy.json")


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def job_name(workflow: Path, job_id: str) -> str:
    text = workflow.read_text(encoding="utf-8")
    pattern = re.compile(
        rf"^  {re.escape(job_id)}:\s*\n"
        rf"(?:^[ \t]+[^\n]*\n)*?"
        rf"^    name:\s*(.+?)\s*$",
        re.MULTILINE,
    )
    match = pattern.search(text)
    if match is None:
        fail(f"workflow job {job_id!r} not found in {workflow}")
    return match.group(1).strip().strip("'\"")


def main() -> int:
    data = json.loads(POLICY.read_text(encoding="utf-8"))

    if data.get("version") != 1:
        fail("required-status policy version must be 1")
    if data.get("deploymentStatus") != "read-only-verified":
        fail("deployment status must reflect the latest read-only API snapshot")

    observed = data.get("observed", {})
    if observed.get("rulesetId") != 21939086 or observed.get("rulesetName") != "Admin rules":
        fail("observed ruleset identity drifted")
    if observed.get("enforcement") != "active":
        fail("observed ruleset must be active")
    if observed.get("snapshotDate") != "2026-10-04":
        fail("observed snapshot date must match the checked-in API evidence")
    if observed.get("apiEndpoint") != "GET /repos/user99987/showcase-application/rulesets/21939086":
        fail("observed API endpoint must match the checked-in API evidence")
    if observed.get("effectiveSource") != "chrobakpiotr/showcase-application":
        fail("observed effective source must match the checked-in API evidence")
    if observed.get("rulesetUpdatedAt") != "2026-09-23T16:32:18.015Z":
        fail("observed ruleset timestamp must match the checked-in API evidence")
    if observed.get("bypassActors") not in (None, []):
        fail("observed bypass actor snapshot must match the reviewed API response")
    if observed.get("branchProtectionEndpoint") != "not-inspected":
        fail("separate branch protection inspection status must remain explicit")

    desired = data.get("desired", {})
    checks = desired.get("requiredStatusChecks")
    if not isinstance(checks, list) or len(checks) != 2:
        fail("exactly two aggregate required checks are expected")

    contexts = []
    for check in checks:
        context = check.get("context")
        workflow = Path(check.get("workflow", ""))
        job_id = check.get("jobId")
        if not context or not job_id or not workflow.is_file():
            fail(f"invalid required-status mapping: {check}")
        actual = job_name(workflow, job_id)
        if actual != context:
            fail(
                f"required-status context {context!r} does not match "
                f"{workflow}:{job_id} job name {actual!r}"
            )
        contexts.append(context)

    if observed.get("requiredStatusChecks") != contexts:
        fail("observed deployed contexts do not match the reviewed API snapshot")

    if len(contexts) != len(set(contexts)):
        fail("required-status contexts must be unique")

    rule = desired.get("rulesetRule", {})
    parameters = rule.get("parameters", {})
    rule_contexts = [
        item.get("context")
        for item in parameters.get("required_status_checks", [])
        if isinstance(item, dict)
    ]
    if rule.get("type") != "required_status_checks" or rule_contexts != contexts:
        fail("ruleset rule fragment does not match required aggregate contexts")
    if parameters.get("strict_required_status_checks_policy") is not True:
        fail("required status checks must require an up-to-date branch")

    if desired.get("recommendedBypassActors") != []:
        fail("recommended policy must not preserve an unconditional bypass actor")

    print("PASS: required-status policy matches stable aggregate workflow job names")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
