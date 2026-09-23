#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


class VerificationError(RuntimeError):
    pass


def verify(
    xml_path: Path,
    html_path: Path,
    *,
    started_after: Path | None = None,
    source_sha: str = "",
    java_version: str = "",
    target_classes: str = "",
    mutation_threshold: int = 100,
    metadata_output: Path | None = None,
) -> dict[str, object]:
    if not xml_path.is_file():
        raise VerificationError(f"PIT XML report is missing: {xml_path}")
    if not html_path.is_file():
        raise VerificationError(f"PIT HTML report is missing: {html_path}")
    if xml_path.stat().st_size <= 0 or html_path.stat().st_size <= 0:
        raise VerificationError("PIT report exists but is empty")

    if started_after is not None:
        marker = started_after.stat().st_mtime_ns
        if xml_path.stat().st_mtime_ns < marker or html_path.stat().st_mtime_ns < marker:
            raise VerificationError("PIT report is stale and predates the current run")

    root = ET.parse(xml_path).getroot()
    mutations = list(root.findall(".//mutation"))
    if not mutations:
        raise VerificationError("PIT produced zero mutants")

    statuses: Counter[str] = Counter()
    undetected = []
    for mutation in mutations:
        status = mutation.attrib.get("status", "<missing>")
        statuses[status] += 1
        detected = mutation.attrib.get("detected", "").lower() == "true"
        if not detected:
            undetected.append(status)

    if undetected:
        raise VerificationError(
            "PIT report contains surviving/undetected mutants: " + ", ".join(sorted(Counter(undetected)))
        )

    evidence: dict[str, object] = {
        "sourceSha": source_sha,
        "javaVersion": java_version,
        "targetClasses": target_classes,
        "mutationThreshold": mutation_threshold,
        "mutationCount": len(mutations),
        "detectedMutationCount": len(mutations),
        "statuses": dict(sorted(statuses.items())),
        "verifiedAtUtc": datetime.now(timezone.utc).isoformat(),
        "xmlReport": str(xml_path),
        "htmlReport": str(html_path),
    }

    if metadata_output is not None:
        metadata_output.parent.mkdir(parents=True, exist_ok=True)
        metadata_output.write_text(json.dumps(evidence, indent=2) + "\n")

    return evidence


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--xml", required=True, type=Path)
    parser.add_argument("--html", required=True, type=Path)
    parser.add_argument("--started-after", type=Path)
    parser.add_argument("--source-sha", default="")
    parser.add_argument("--java-version", default="")
    parser.add_argument("--target-classes", default="")
    parser.add_argument("--mutation-threshold", type=int, default=100)
    parser.add_argument("--metadata-output", type=Path)
    args = parser.parse_args()

    try:
        evidence = verify(
            args.xml,
            args.html,
            started_after=args.started_after,
            source_sha=args.source_sha,
            java_version=args.java_version,
            target_classes=args.target_classes,
            mutation_threshold=args.mutation_threshold,
            metadata_output=args.metadata_output,
        )
        print(
            f"PIT evidence: {evidence['detectedMutationCount']}/{evidence['mutationCount']} "
            f"mutants detected; threshold={evidence['mutationThreshold']}%"
        )
        return 0
    except (VerificationError, ET.ParseError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
