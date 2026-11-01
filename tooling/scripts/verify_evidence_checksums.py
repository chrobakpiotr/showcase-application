#!/usr/bin/env python3
"""Fail when a tracked file no longer matches a SHA-256 that committed evidence records for it.

Checked bindings (both resolved relative to the recording file's directory first, then to the repository root, and
only against files tracked by Git):
- every `<hash>  <path>` line of a tracked `SHA256SUMS`, `SHA256SUMS-<date>` or `OWNER-PACK-SHA256SUMS[-<date>]`;
- every JSON object with a `path` string and a `sha256` hex digest (optionally `sha256:`-prefixed) in a tracked
  `.json` file (closure and evidence manifests, source manifests, verification records).

Evidence is append-only (constitution rule 9). A repository-wide edit (for example a typographic clean-up) must leave
the bytes of every file such a record binds unchanged; re-hashing the record instead would rewrite the evidence.

Known historical exceptions live in `tooling/quality/evidence-checksum-baseline.json`, each with a reason. The check
is a ratchet: a binding that is not OK and not in the baseline fails, and a baseline entry that is OK again (or no
longer exists) fails too, so the list only shrinks deliberately.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
from collections.abc import Iterator
from typing import NamedTuple

SUMS_NAME = re.compile(r"(?:^|/)(?:OWNER-PACK-)?SHA256SUMS(?:-[0-9-]+)?$")
SUMS_LINE = re.compile(r"([0-9a-f]{64}) [ *](.+)")
DIGEST = re.compile(r"(?:sha256:)?([0-9a-f]{64})")
BASELINE = pathlib.Path("tooling/quality/evidence-checksum-baseline.json")


class Binding(NamedTuple):
    record: str  # the tracked file that records the digest
    location: str  # `line N` in a sums file, a JSON path in a JSON record
    path: str  # the path as written in the record
    status: str  # ok | mismatch | missing

    def key(self) -> tuple[str, str, str]:
        return self.record, self.location, self.path


def tracked_files(root: pathlib.Path) -> set[str]:
    out = subprocess.run(["git", "-C", str(root), "ls-files", "-z"], check=True, capture_output=True).stdout
    return {name for name in out.decode().split("\0") if name}


def _resolve(root: pathlib.Path, tracked: set[str], record: str, ref: str) -> str | None:
    base = pathlib.PurePosixPath(record).parent
    for candidate in (base / ref, pathlib.PurePosixPath(ref)):
        try:
            relative = (root / candidate).resolve().relative_to(root).as_posix()
        except ValueError:  # outside the repository
            continue
        if relative in tracked:
            return relative
    return None


def _status(root: pathlib.Path, target: str | None, digest: str) -> str:
    if target is None:
        return "missing"
    return "ok" if hashlib.sha256((root / target).read_bytes()).hexdigest() == digest else "mismatch"


def _json_bindings(value: object, where: str) -> Iterator[tuple[str, str, str]]:
    if isinstance(value, dict):
        path, digest = value.get("path"), value.get("sha256")
        if isinstance(path, str) and isinstance(digest, str) and (match := DIGEST.fullmatch(digest)):
            yield where, path, match.group(1)
        for key, child in value.items():
            yield from _json_bindings(child, f"{where}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from _json_bindings(child, f"{where}[{index}]")


def collect(root: pathlib.Path) -> list[Binding]:
    root = root.resolve()
    tracked = tracked_files(root)
    bindings: list[Binding] = []
    for record in sorted(tracked):
        if SUMS_NAME.search(record):
            text = (root / record).read_text(encoding="utf-8")
            for number, line in enumerate(text.splitlines(), 1):
                if match := SUMS_LINE.fullmatch(line.strip()):
                    target = _resolve(root, tracked, record, match.group(2))
                    bindings.append(Binding(record, f"line {number}", match.group(2),
                                            _status(root, target, match.group(1))))
        elif record.endswith(".json"):
            try:
                document = json.loads((root / record).read_text(encoding="utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                continue
            for where, path, digest in _json_bindings(document, "$"):
                target = _resolve(root, tracked, record, path)
                if target == record:  # a record describing its own (pre-write) content
                    continue
                bindings.append(Binding(record, where, path, _status(root, target, digest)))
    return bindings


def load_baseline(path: pathlib.Path) -> dict[tuple[str, str, str], dict]:
    entries = json.loads(path.read_text(encoding="utf-8"))["exceptions"]
    baseline = {}
    for entry in entries:
        if not entry.get("reason"):
            raise ValueError(f"baseline entry without a reason: {entry}")
        baseline[(entry["record"], entry["location"], entry["path"])] = entry
    return baseline


def evaluate(bindings: list[Binding], baseline: dict[tuple[str, str, str], dict]) -> list[str]:
    errors = []
    seen = {}
    for binding in bindings:
        seen[binding.key()] = binding
        known = baseline.get(binding.key())
        if binding.status == "ok":
            if known:
                errors.append(f"{binding.record} {binding.location} {binding.path}: verifies again; "
                              "remove it from the baseline")
        elif known is None or known["status"] != binding.status:
            errors.append(f"{binding.record} {binding.location} {binding.path}: {binding.status} "
                          "(a file bound by committed evidence changed or disappeared; restore its exact bytes)")
    for key in baseline.keys() - seen.keys():
        errors.append(f"{' '.join(key)}: baseline entry no longer matches any recorded binding; remove it")
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[2])
    parser.add_argument("--baseline", type=pathlib.Path)
    args = parser.parse_args(argv)
    root = args.root.resolve()
    bindings = collect(root)
    errors = evaluate(bindings, load_baseline(args.baseline or root / BASELINE))
    counts = {status: sum(b.status == status for b in bindings) for status in ("ok", "mismatch", "missing")}
    print(f"evidence checksum bindings: {counts['ok']} ok, {counts['mismatch']} mismatch, "
          f"{counts['missing']} missing (baseline exceptions apply to the last two)")
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
