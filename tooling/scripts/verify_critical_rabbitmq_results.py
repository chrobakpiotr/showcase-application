#!/usr/bin/env python3
"""Critical RabbitMQ evidence validation extracted from its gate script."""
import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path


def load_manifest(path):
    data = json.loads(path.read_text())
    if data.get("version") != 1:
        raise SystemExit("ERROR: critical RabbitMQ manifest version must be 1")
    if data.get("inventoryPolicy") != "explicit-rabbitmq-delivery-evidence":
        raise SystemExit("ERROR: unexpected critical RabbitMQ inventory policy")
    requirements = data.get("requirements")
    if not isinstance(requirements, list) or not requirements:
        raise SystemExit("ERROR: critical RabbitMQ manifest has no requirements")
    suites = sorted({item["suite"] for item in requirements})
    if len(suites) != 1:
        raise SystemExit(f"ERROR: expected exactly one critical RabbitMQ suite, got {suites}")
    return data


def list_suites(manifest_path):
    data = load_manifest(manifest_path)
    return sorted({item["suite"] for item in data["requirements"]})


def verify(manifest_path, result_root, start_marker, source_root, source_sha):
    data = load_manifest(manifest_path)
    requirements = data["requirements"]

    required_suites = sorted({item["suite"] for item in requirements})
    if len(required_suites) != 1:
        raise SystemExit(f"ERROR: expected one RabbitMQ suite, got {required_suites}")
    suite = required_suites[0]

    source_file = source_root / Path(*suite.split(".")).with_suffix(".java")
    if not source_file.is_file():
        raise SystemExit(f"ERROR: RabbitMQ suite source is missing: {source_file}")

    files = sorted(result_root.glob("TEST-*.xml"))
    if not files:
        raise SystemExit(f"ERROR: no JUnit XML results found in {result_root}")

    marker_ns = start_marker.stat().st_mtime_ns
    parsed = []
    for file in files:
        if file.stat().st_mtime_ns < marker_ns:
            raise SystemExit(f"ERROR: stale critical RabbitMQ report predates gate start: {file}")
        root = ET.parse(file).getroot()
        parsed.append((file, root))

    matches = [(file, root) for file, root in parsed if root.attrib.get("name", "").strip() == suite]
    unexpected = sorted(
        root.attrib.get("name", "").strip()
        for _, root in parsed
        if root.attrib.get("name", "").strip() != suite
    )
    if unexpected:
        raise SystemExit("ERROR: unexpected suites in isolated RabbitMQ results: " + ", ".join(unexpected))
    if len(matches) != 1:
        raise SystemExit(f"ERROR: required RabbitMQ suite expected exactly once, found {len(matches)}")

    file, root = matches[0]
    for element in root.iter():
        tag = element.tag.rsplit("}", 1)[-1].lower()
        attrs = " ".join(element.attrib).lower()
        if "flaky" in tag or "rerun" in tag or "flaky" in attrs or "rerun" in attrs:
            raise SystemExit(f"ERROR: RabbitMQ evidence contains retry/flaky metadata: {file}")

    def as_int(name):
        try:
            return int(root.attrib.get(name, "0"))
        except ValueError as error:
            raise SystemExit(f"ERROR: non-integer {name} in {file}") from error

    tests = as_int("tests")
    skipped = as_int("skipped")
    failures = as_int("failures")
    errors = as_int("errors")
    if tests <= 0 or skipped or failures or errors:
        raise SystemExit(
            f"ERROR: RabbitMQ suite not clean: tests={tests} skipped={skipped} failures={failures} errors={errors}"
        )

    actual = []
    for testcase in root.findall(".//testcase"):
        name = testcase.attrib.get("name", "").strip()
        if name.endswith("()"):
            name = name[:-2]
        if not name:
            raise SystemExit(f"ERROR: unnamed RabbitMQ testcase in {file}")
        if testcase.find("skipped") is not None:
            raise SystemExit(f"ERROR: RabbitMQ testcase skipped: {name}")
        if testcase.find("failure") is not None or testcase.find("error") is not None:
            raise SystemExit(f"ERROR: RabbitMQ testcase failed: {name}")
        actual.append(name)

    required = {item["testMethod"] for item in requirements}
    if set(actual) != required:
        raise SystemExit(
            "ERROR: RabbitMQ mapped cases differ; "
            f"missing={sorted(required - set(actual))} unexpected={sorted(set(actual) - required)}"
        )
    if tests != len(actual):
        raise SystemExit(f"ERROR: JUnit tests={tests} but testcase nodes={len(actual)}")

    evidence = {
        "version": 1,
        "sourceSha": source_sha,
        "manifestSha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "suiteCount": 1,
        "testCount": tests,
        "mappedCaseCount": len(required),
        "skipped": skipped,
        "failures": failures,
        "errors": errors,
        "suite": suite,
        "cases": sorted(required),
    }
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--list-suites', action='store_true')
    parser.add_argument('--results', type=Path)
    parser.add_argument('--started-after', type=Path)
    parser.add_argument('--source-root', type=Path)
    parser.add_argument('--source-sha')
    parser.add_argument('--evidence-output', type=Path)
    args = parser.parse_args()
    if args.list_suites:
        for suite in list_suites(args.manifest):
            print(suite)
        return
    for option in ('results', 'started_after', 'source_root', 'source_sha', 'evidence_output'):
        if getattr(args, option) is None:
            parser.error('--' + option.replace('_', '-') + ' is required for result validation')
    evidence = verify(args.manifest, args.results, args.started_after, args.source_root, args.source_sha)
    args.evidence_output.write_text(json.dumps(evidence, indent=2) + "\n")
    print(
        f"Critical RabbitMQ gate: 1 suite / {evidence['testCount']} tests / {evidence['mappedCaseCount']} mapped cases; "
        f"{evidence['skipped']} skipped / {evidence['failures']} failed / {evidence['errors']} errors"
    )
    print(f"Evidence: {args.evidence_output}")


if __name__ == '__main__':
    main()
