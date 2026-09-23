#!/usr/bin/env python3
# Fail-closed policy for the aggregate GitHub Actions CI quality gate.

from __future__ import annotations

import os
import sys
from collections.abc import Mapping

REQUIRED_RESULTS = (
    ("Documentation links", "DOCUMENTATION_RESULT"),
    ("Repository guards", "REPOSITORY_GUARDS_RESULT"),
    ("Backend build, quality gates & tests", "BACKEND_RESULT"),
    ("Domain mutation testing (PIT)", "MUTATION_TESTING_RESULT"),
    ("OWASP dependency vulnerability scan", "DEPENDENCY_CHECK_RESULT"),
    ("CycloneDX SBOM generation", "SBOM_RESULT"),
    ("Infra-as-config validation", "INFRA_VALIDATION_RESULT"),
    ("Container image vulnerability scan", "CONTAINER_IMAGE_SCAN_RESULT"),
    ("Frontend build, lint & test", "FRONTEND_RESULT"),
    ("End-to-end tests (Playwright)", "E2E_RESULT"),
)

DEPENDENCY_REVIEW_KEY = "DEPENDENCY_REVIEW_RESULT"
ALLOWED_EVENTS = frozenset({"push", "pull_request", "workflow_dispatch"})


def evaluate(event_name: str, results: Mapping[str, str]) -> list[str]:
    errors: list[str] = []

    if event_name not in ALLOWED_EVENTS:
        errors.append(f"Event: unsupported or missing event {event_name or '<missing>'}")

    for display_name, key in REQUIRED_RESULTS:
        result = results.get(key, "")
        if result != "success":
            errors.append(f"{display_name}: expected success, got {result or '<missing>'}")

    dependency_review = results.get(DEPENDENCY_REVIEW_KEY, "")
    if event_name == "pull_request":
        if dependency_review != "success":
            errors.append(
                "Dependency review: pull_request requires success, "
                f"got {dependency_review or '<missing>'}"
            )
    elif event_name in {"push", "workflow_dispatch"} and dependency_review not in {"success", "skipped"}:
        errors.append(
            "Dependency review: non-PR run allows only success/skipped, "
            f"got {dependency_review or '<missing>'}"
        )

    return errors


def main() -> int:
    event_name = os.environ.get("EVENT_NAME", "")
    keys = [key for _, key in REQUIRED_RESULTS] + [DEPENDENCY_REVIEW_KEY]
    results = {key: os.environ.get(key, "") for key in keys}

    for display_name, key in REQUIRED_RESULTS:
        print(f"{display_name:<44} {results[key] or '<missing>'}")
    print(f"{'Dependency review':<44} {results[DEPENDENCY_REVIEW_KEY] or '<missing>'}")
    print(f"{'Event':<44} {event_name or '<missing>'}")

    errors = evaluate(event_name, results)
    if errors:
        for error in errors:
            print(f"::error::{error}")
        return 1

    print("CI quality gate: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
