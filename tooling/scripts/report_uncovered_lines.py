#!/usr/bin/env python3
"""Print classes and lines with missed instructions from JaCoCo XML reports.

CI uploads the HTML coverage reports as artifacts, but they are not always reachable. When the 100% instruction
coverage rule fails, this script puts the missed lines into the job log instead.
Usage: report_uncovered_lines.py [root]   (default: current directory)
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path


def uncovered(report: Path) -> list[str]:
    root = ElementTree.parse(report).getroot()
    rows: list[str] = []
    for package in root.iter("package"):
        for source in package.iter("sourcefile"):
            lines = [line.get("nr") for line in source.iter("line") if int(line.get("mi", "0")) > 0]
            if lines:
                rows.append(f"{package.get('name')}/{source.get('name')}: lines {', '.join(lines)}")
    return rows


def main(argv: list[str]) -> int:
    base = Path(argv[1]) if len(argv) > 1 else Path(".")
    found = False
    for report in sorted(base.glob("**/build/reports/jacoco/test/jacocoTestReport.xml")):
        rows = uncovered(report)
        if rows:
            found = True
            print(f"== {report.parent.parent.parent.parent.parent}")
            for row in rows:
                print(f"  {row}")
    if not found:
        print("No missed instructions found in JaCoCo XML reports.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
