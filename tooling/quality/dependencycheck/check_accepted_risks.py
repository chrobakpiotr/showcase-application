#!/usr/bin/env python3
"""Validate metadata and expiry for time-limited OWASP Dependency-Check risks."""

from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


DEFAULT_FILE = Path(__file__).with_name("accepted-risks.xml")
OWNER_PATTERN = re.compile(r"\bOwner:\s*([A-Za-z0-9._@-]+)\b", re.IGNORECASE)
REVIEW_PATTERN = re.compile(r"\bReview/expiry:\s*(\d{4}-\d{2}-\d{2})\b", re.IGNORECASE)


@dataclass(frozen=True)
class ValidationResult:
    errors: list[str]
    warnings: list[str]


def _expiry_date(value: str | None, index: int) -> tuple[dt.date | None, str | None]:
    if not value:
        return None, f"risk {index}: missing suppression expiry"
    normalized = value.removesuffix("Z")
    try:
        return dt.date.fromisoformat(normalized), None
    except ValueError:
        return None, f"risk {index}: invalid suppression expiry {value!r}; expected YYYY-MM-DDZ"


def _child(risk: ET.Element, name: str) -> ET.Element | None:
    return next((element for element in risk if element.tag.rsplit("}", 1)[-1] == name), None)


def validate_document(xml_text: str, today: dt.date, warn_days: int = 30) -> ValidationResult:
    errors: list[str] = []
    warnings: list[str] = []
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as error:
        return ValidationResult([f"invalid accepted-risks XML: {error}"], [])

    risks = [element for element in root.iter() if element.tag.rsplit("}", 1)[-1] == "suppress"]
    if not risks:
        return ValidationResult(["accepted-risks XML contains no suppress entries"], [])

    for index, risk in enumerate(risks, start=1):
        expiry, expiry_error = _expiry_date(risk.get("until"), index)
        if expiry_error:
            errors.append(expiry_error)

        notes_element = _child(risk, "notes")
        notes = " ".join("".join(notes_element.itertext() if notes_element is not None else "").split())
        if not OWNER_PATTERN.search(notes):
            errors.append(f"risk {index}: notes must name an Owner")
        review_match = REVIEW_PATTERN.search(notes)
        if not review_match:
            errors.append(f"risk {index}: notes must include Review/expiry: YYYY-MM-DD")
        elif expiry is not None and review_match.group(1) != expiry.isoformat():
            errors.append(f"risk {index}: Review/expiry date does not match suppression until date")
        if not re.search(r"\b(exact|only)\b", notes, re.IGNORECASE):
            errors.append(f"risk {index}: notes must describe the accepted risk's narrow scope")

        package_element = _child(risk, "packageUrl")
        vulnerability_element = _child(risk, "vulnerabilityName")
        package_url = (package_element.text or "").strip() if package_element is not None else ""
        vulnerability = (vulnerability_element.text or "").strip() if vulnerability_element is not None else ""
        if not package_url or not vulnerability:
            errors.append(f"risk {index}: packageUrl and vulnerabilityName are required")
        if package_url and package_element is not None and package_element.get("regex") == "true":
            if not package_url.startswith("^") or not package_url.endswith("$"):
                errors.append(f"risk {index}: regex packageUrl must be anchored at both ends")
            literal_pattern = re.sub(r"\\.", "", package_url[1:-1])
            if re.search(r"[.*+?{}()\[\]|]", literal_pattern):
                errors.append(f"risk {index}: regex packageUrl must match one exact package version")

        if expiry is not None:
            remaining = (expiry - today).days
            if remaining < 0:
                errors.append(f"risk {index}: accepted risk expired on {expiry.isoformat()}")
            elif remaining <= warn_days:
                warnings.append(f"risk {index}: accepted risk expires in {remaining} day(s), on {expiry.isoformat()}")

    return ValidationResult(errors, warnings)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", type=Path, default=DEFAULT_FILE, help="accepted-risks.xml path")
    parser.add_argument("--warn-days", type=int, default=30, help="warn when expiry is this close")
    parser.add_argument("--today", help=argparse.SUPPRESS, metavar="YYYY-MM-DD")
    args = parser.parse_args(argv)
    if args.warn_days < 0:
        parser.error("--warn-days must be non-negative")
    try:
        today = dt.date.fromisoformat(args.today) if args.today else dt.datetime.now(dt.timezone.utc).date()
        result = validate_document(args.file.read_text(encoding="utf-8"), today, args.warn_days)
    except (OSError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    for warning in result.warnings:
        print(f"WARNING: {warning}")
    for error in result.errors:
        print(f"ERROR: {error}", file=sys.stderr)
    if result.errors:
        return 1
    if not result.warnings:
        checked = sum(1 for element in ET.parse(args.file).getroot().iter() if element.tag.rsplit("}", 1)[-1] == "suppress")
        print(f"Accepted-risk metadata valid; {checked} risk(s) checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
