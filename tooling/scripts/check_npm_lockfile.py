#!/usr/bin/env python3
"""Fail unless every package in an npm lockfile comes from the public registry with a SHA-512 integrity.

`npm ci` checks a tarball against the lockfile's hash but not where the tarball comes from, and lockfile changes reach
`main` by direct push, so a git, URL, file or alias resolution copied into the lockfile would otherwise go unnoticed.
"""
from __future__ import annotations

import json
import pathlib
import sys

REGISTRY = "https://registry.npmjs.org/"


def problems(lock: dict) -> list[str]:
    found = []
    for path, entry in sorted(lock.get("packages", {}).items()):
        if not path or entry.get("link"):
            continue
        resolved = entry.get("resolved", "")
        integrity = entry.get("integrity", "")
        if not resolved.startswith(REGISTRY):
            found.append(f"{path}: resolved outside {REGISTRY}: {resolved or '(missing)'}")
        if not integrity.startswith("sha512-"):
            found.append(f"{path}: integrity is not SHA-512: {integrity or '(missing)'}")
    return found


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: check_npm_lockfile.py <package-lock.json>", file=sys.stderr)
        return 2
    lock = json.loads(pathlib.Path(argv[1]).read_text(encoding="utf-8"))
    found = problems(lock)
    for problem in found:
        print(problem, file=sys.stderr)
    count = sum(1 for path in lock.get("packages", {}) if path)
    print(f"npm lockfile origin check: {'FAIL' if found else 'PASS'} ({count} packages)")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
