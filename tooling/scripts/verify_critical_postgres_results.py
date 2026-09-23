#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


class VerificationError(RuntimeError):
    pass


@dataclass(frozen=True)
class Requirement:
    ac: str
    scenario: str
    suite: str
    test_method: str


def _require_text(item: dict[str, object], key: str) -> str:
    value = item.get(key)
    if not isinstance(value, str) or not value.strip():
        raise VerificationError(f"Manifest field {key!r} must be a non-empty string")
    return value.strip()


def load_manifest(path: Path) -> list[Requirement]:
    data = json.loads(path.read_text())
    if data.get("version") != 1:
        raise VerificationError("Critical Postgres manifest version must be 1")
    if data.get("inventoryPolicy") != "all-postgres-integration-tests-required":
        raise VerificationError("Critical Postgres manifest must require the complete Postgres suite inventory")

    raw = data.get("requirements")
    if not isinstance(raw, list) or not raw:
        raise VerificationError("Critical Postgres manifest has no requirements")

    requirements: list[Requirement] = []
    seen_ac: set[str] = set()
    seen_case: set[tuple[str, str]] = set()
    for item in raw:
        if not isinstance(item, dict):
            raise VerificationError("Every critical Postgres requirement must be an object")
        requirement = Requirement(
            ac=_require_text(item, "ac"),
            scenario=_require_text(item, "scenario"),
            suite=_require_text(item, "suite"),
            test_method=_require_text(item, "testMethod"),
        )
        if requirement.ac in seen_ac:
            raise VerificationError(f"Duplicate acceptance criterion id: {requirement.ac}")
        pair = (requirement.suite, requirement.test_method)
        if pair in seen_case:
            raise VerificationError(
                f"Duplicate required test case: {requirement.suite}#{requirement.test_method}"
            )
        seen_ac.add(requirement.ac)
        seen_case.add(pair)
        requirements.append(requirement)
    return requirements


def suites(requirements: Iterable[Requirement]) -> list[str]:
    return sorted({requirement.suite for requirement in requirements})


def discover_postgres_suites(source_root: Path) -> set[str]:
    discovered: set[str] = set()
    package_pattern = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
    class_pattern = re.compile(r"\bclass\s+(\w+PostgresIntegrationTest)\b")

    for source in source_root.rglob("*PostgresIntegrationTest.java"):
        text = source.read_text()
        package_match = package_pattern.search(text)
        class_match = class_pattern.search(text)
        if package_match is None or class_match is None:
            raise VerificationError(f"Cannot derive suite identity from {source}")
        discovered.add(f"{package_match.group(1)}.{class_match.group(1)}")
    return discovered


def _normalize_case_name(name: str) -> str:
    return name[:-2] if name.endswith("()") else name


def _parse_int(value: str | None, field: str, suite: str) -> int:
    try:
        return int(value or "0")
    except ValueError as error:
        raise VerificationError(f"Suite {suite} has non-integer {field}={value!r}") from error


def _contains_retry_metadata(root: ET.Element) -> bool:
    for element in root.iter():
        tag = element.tag.rsplit("}", 1)[-1].lower()
        if "flaky" in tag or "rerun" in tag:
            return True
        for key in element.attrib:
            lowered = key.lower()
            if "flaky" in lowered or "rerun" in lowered:
                return True
    return False


def verify(
    manifest_path: Path,
    results_dir: Path,
    *,
    started_after: Path | None = None,
    source_root: Path | None = None,
    source_sha: str = "",
    evidence_output: Path | None = None,
) -> dict[str, object]:
    requirements = load_manifest(manifest_path)
    required_suites = suites(requirements)

    if source_root is not None:
        discovered = discover_postgres_suites(source_root)
        required = set(required_suites)
        missing_from_manifest = sorted(discovered - required)
        stale_manifest = sorted(required - discovered)
        if missing_from_manifest:
            raise VerificationError(
                "Postgres integration suites missing from critical manifest: "
                + ", ".join(missing_from_manifest)
            )
        if stale_manifest:
            raise VerificationError(
                "Critical manifest references missing Postgres suites: "
                + ", ".join(stale_manifest)
            )

    files = sorted(results_dir.glob("TEST-*.xml"))
    if not files:
        raise VerificationError(f"No JUnit XML results found in {results_dir}")

    marker_mtime_ns = started_after.stat().st_mtime_ns if started_after is not None else None
    by_name: dict[str, list[tuple[Path, ET.Element]]] = {}
    for file in files:
        if marker_mtime_ns is not None and file.stat().st_mtime_ns < marker_mtime_ns:
            raise VerificationError(f"Stale critical Postgres report predates gate start: {file}")
        root = ET.parse(file).getroot()
        suite_name = root.attrib.get("name", "").strip()
        if not suite_name:
            raise VerificationError(f"JUnit XML has no suite name: {file}")
        by_name.setdefault(suite_name, []).append((file, root))

    unexpected_suites = sorted(set(by_name) - set(required_suites))
    if unexpected_suites:
        raise VerificationError(
            "Unexpected suites found in isolated critical Postgres results: "
            + ", ".join(unexpected_suites)
        )

    requirements_by_suite: dict[str, list[Requirement]] = {}
    for requirement in requirements:
        requirements_by_suite.setdefault(requirement.suite, []).append(requirement)

    total_tests = 0
    verified_cases = 0
    for suite_name in required_suites:
        matches = by_name.get(suite_name, [])
        if len(matches) != 1:
            raise VerificationError(
                f"Required Postgres suite {suite_name} expected exactly once, found {len(matches)}"
            )

        file, root = matches[0]
        if _contains_retry_metadata(root):
            raise VerificationError(
                f"Required Postgres suite {suite_name} contains flaky/rerun metadata: {file}"
            )

        tests = _parse_int(root.attrib.get("tests"), "tests", suite_name)
        skipped = _parse_int(root.attrib.get("skipped"), "skipped", suite_name)
        failures = _parse_int(root.attrib.get("failures"), "failures", suite_name)
        errors = _parse_int(root.attrib.get("errors"), "errors", suite_name)

        if tests <= 0:
            raise VerificationError(f"Required Postgres suite {suite_name} executed zero tests: {file}")
        if skipped or failures or errors:
            raise VerificationError(
                f"Required Postgres suite {suite_name} is not clean: "
                f"tests={tests} skipped={skipped} failures={failures} errors={errors}"
            )

        actual_cases: list[str] = []
        for testcase in root.findall(".//testcase"):
            name = _normalize_case_name(testcase.attrib.get("name", "").strip())
            if not name:
                raise VerificationError(f"Unnamed testcase in {file}")
            if testcase.find("skipped") is not None:
                raise VerificationError(f"Required Postgres case {suite_name}#{name} was skipped")
            if testcase.find("failure") is not None or testcase.find("error") is not None:
                raise VerificationError(f"Required Postgres case {suite_name}#{name} failed")
            actual_cases.append(name)

        if len(actual_cases) != len(set(actual_cases)):
            raise VerificationError(f"Duplicate testcase entries in suite {suite_name}: {actual_cases}")

        required_cases = {item.test_method for item in requirements_by_suite[suite_name]}
        actual_case_set = set(actual_cases)
        missing_cases = sorted(required_cases - actual_case_set)
        unexpected_cases = sorted(actual_case_set - required_cases)
        if missing_cases:
            raise VerificationError(
                f"Required Postgres cases missing from {suite_name}: " + ", ".join(missing_cases)
            )
        if unexpected_cases:
            raise VerificationError(
                f"Unmapped Postgres cases found in {suite_name}: " + ", ".join(unexpected_cases)
            )
        if tests != len(actual_cases):
            raise VerificationError(
                f"Suite {suite_name} declares tests={tests} but contains {len(actual_cases)} testcase nodes"
            )

        total_tests += tests
        verified_cases += len(required_cases)

    manifest_sha256 = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
    evidence: dict[str, object] = {
        "sourceSha": source_sha,
        "manifestSha256": manifest_sha256,
        "suiteCount": len(required_suites),
        "testCount": total_tests,
        "requiredCaseCount": verified_cases,
        "skipped": 0,
        "failed": 0,
        "verifiedAtUtc": datetime.now(timezone.utc).isoformat(),
        "resultsDirectory": str(results_dir),
    }

    if evidence_output is not None:
        evidence_output.parent.mkdir(parents=True, exist_ok=True)
        evidence_output.write_text(json.dumps(evidence, indent=2) + "\n")

    return evidence


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--results", type=Path)
    parser.add_argument("--started-after", type=Path)
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--source-sha", default="")
    parser.add_argument("--evidence-output", type=Path)
    parser.add_argument("--list-suites", action="store_true")
    args = parser.parse_args()

    try:
        requirements = load_manifest(args.manifest)
        if args.list_suites:
            for suite in suites(requirements):
                print(suite)
            return 0

        if args.results is None:
            raise VerificationError("--results is required unless --list-suites is used")

        evidence = verify(
            args.manifest,
            args.results,
            started_after=args.started_after,
            source_root=args.source_root,
            source_sha=args.source_sha,
            evidence_output=args.evidence_output,
        )
        print(
            "Required Postgres gate: "
            f"{evidence['suiteCount']} suites / {evidence['testCount']} tests / "
            f"{evidence['requiredCaseCount']} mapped cases, 0 skipped, 0 failed"
        )
        return 0
    except (VerificationError, ET.ParseError, OSError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
