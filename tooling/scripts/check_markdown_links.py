#!/usr/bin/env python3
"""Validate repository-local links in README.md and docs/**/*.md without network access."""

from __future__ import annotations

import argparse
import html
import re
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import unquote, urlsplit

FENCE_RE = re.compile(r"^\s{0,3}(`{3,}|~{3,})")
REFERENCE_RE = re.compile(r"^\s{0,3}\[[^\]]+\]:\s*(?P<target><[^>]+>|\S+)")
HTML_LINK_RE = re.compile(r"""(?i)\b(?:href|src)\s*=\s*(["'])(.*?)\1""")
HEADING_RE = re.compile(r"^\s{0,3}#{1,6}\s+(?P<title>.*?)\s*#*\s*$")
EXPLICIT_ANCHOR_RE = re.compile(r"""(?i)<a\s+[^>]*(?:id|name)\s*=\s*(["'])(.*?)\1[^>]*>""")
INLINE_LINK_LABEL_RE = re.compile(r"!\[([^\]]*)\]\([^)]+\)|\[([^\]]+)\]\([^)]+\)")
HTML_TAG_RE = re.compile(r"<[^>]+>")
INLINE_CODE_RE = re.compile(r"(`+)(.*?)(\1)")
EXTERNAL_SCHEMES = {"http", "https", "mailto", "tel", "data", "javascript", "ftp"}


@dataclass(frozen=True)
class MarkdownLink:
    source: Path
    line: int
    target: str


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def default_markdown_files(root: Path) -> list[Path]:
    files: list[Path] = []
    readme = root / "README.md"
    if readme.is_file():
        files.append(readme)
    docs = root / "docs"
    if docs.is_dir():
        files.extend(sorted(path for path in docs.rglob("*.md") if path.is_file()))
    return files


def mask_fenced_code(text: str) -> str:
    masked: list[str] = []
    fence_char: str | None = None

    for line in text.splitlines(keepends=True):
        match = FENCE_RE.match(line)
        if match:
            marker = match.group(1)
            if fence_char is None:
                fence_char = marker[0]
            elif marker[0] == fence_char:
                fence_char = None
            masked.append("\n" if line.endswith("\n") else "")
            continue

        if fence_char is not None:
            masked.append("\n" if line.endswith("\n") else "")
            continue

        masked.append(line)

    return "".join(masked)


def mask_code(text: str) -> str:
    fenced = mask_fenced_code(text)
    masked: list[str] = []
    for line in fenced.splitlines(keepends=True):
        body = line[:-1] if line.endswith("\n") else line
        body = INLINE_CODE_RE.sub(lambda match: " " * len(match.group(0)), body)
        masked.append(body + ("\n" if line.endswith("\n") else ""))
    return "".join(masked)


def split_destination(body: str) -> str:
    body = body.strip()
    if not body:
        return ""
    if body.startswith("<"):
        end = body.find(">")
        return body[1:end] if end >= 0 else body[1:]

    escaped = False
    nested = 0
    chars: list[str] = []
    for char in body:
        if escaped:
            chars.append(char)
            escaped = False
            continue
        if char == "\\":
            escaped = True
            chars.append(char)
            continue
        if char == "(":
            nested += 1
        elif char == ")" and nested > 0:
            nested -= 1
        elif char.isspace() and nested == 0:
            break
        chars.append(char)
    return "".join(chars).strip()


def inline_links(source: Path, text: str) -> list[MarkdownLink]:
    links: list[MarkdownLink] = []
    cursor = 0
    while True:
        start = text.find("](", cursor)
        if start < 0:
            break

        body_start = start + 2
        index = body_start
        depth = 1
        escaped = False
        while index < len(text):
            char = text[index]
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    break
            index += 1

        if depth != 0:
            cursor = body_start
            continue

        target = split_destination(text[body_start:index])
        if target:
            links.append(
                MarkdownLink(
                    source=source,
                    line=text.count("\n", 0, start) + 1,
                    target=target,
                )
            )
        cursor = index + 1
    return links


def extract_links(source: Path, raw_text: str) -> list[MarkdownLink]:
    text = mask_code(raw_text)
    links = inline_links(source, text)

    for line_no, line in enumerate(text.splitlines(), 1):
        reference = REFERENCE_RE.match(line)
        if reference:
            target = split_destination(reference.group("target"))
            if target:
                links.append(MarkdownLink(source, line_no, target))

        for match in HTML_LINK_RE.finditer(line):
            target = match.group(2).strip()
            if target:
                links.append(MarkdownLink(source, line_no, target))

    return links


def visible_heading_text(title: str) -> str:
    title = html.unescape(title)
    title = INLINE_CODE_RE.sub(lambda match: match.group(2), title)
    title = INLINE_LINK_LABEL_RE.sub(lambda match: match.group(1) or match.group(2) or "", title)
    title = HTML_TAG_RE.sub("", title)
    return title.replace("*", "").replace("~", "")


def github_slug(title: str) -> str:
    value = visible_heading_text(title).strip().lower()
    kept: list[str] = []
    for char in value:
        category = unicodedata.category(char)
        if char in {"-", "_"}:
            kept.append(char)
        elif char.isspace():
            kept.append("-")
        elif category.startswith("L") or category.startswith("N"):
            kept.append(char)
    return "".join(kept)


def anchors_for(markdown_file: Path) -> set[str]:
    text = mask_fenced_code(markdown_file.read_text(encoding="utf-8"))
    anchors: set[str] = set()
    counts: dict[str, int] = {}

    for line in text.splitlines():
        line_without_inline_code = INLINE_CODE_RE.sub(lambda match: " " * len(match.group(0)), line)
        explicit = EXPLICIT_ANCHOR_RE.search(line_without_inline_code)
        if explicit:
            anchors.add(html.unescape(explicit.group(2)))

        heading = HEADING_RE.match(line)
        if not heading:
            continue
        base = github_slug(heading.group("title"))
        if not base:
            continue
        occurrence = counts.get(base, 0)
        counts[base] = occurrence + 1
        anchors.add(base if occurrence == 0 else f"{base}-{occurrence}")

    return anchors


def is_external(target: str) -> bool:
    if target.startswith("//"):
        return True
    parsed = urlsplit(target)
    return bool(parsed.scheme and parsed.scheme.lower() in EXTERNAL_SCHEMES)


def within_root(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def validate_link(root: Path, link: MarkdownLink, anchor_cache: dict[Path, set[str]]) -> str | None:
    target = html.unescape(link.target.strip())
    if not target or is_external(target):
        return None

    parsed = urlsplit(target)
    if parsed.scheme:
        # Unknown explicit schemes are not repository-local and must not trigger filesystem access.
        return None

    path_part = re.sub(r"\\([\\() ])", r"\1", unquote(parsed.path))
    fragment = re.sub(r"\\([\\() ])", r"\1", unquote(parsed.fragment))

    if path_part.startswith("/"):
        resolved = (root / path_part.lstrip("/")).resolve()
    elif path_part:
        resolved = (link.source.parent / path_part).resolve()
    else:
        resolved = link.source.resolve()

    if not within_root(resolved, root):
        return f"{link.source.relative_to(root)}:{link.line}: link escapes repository: {link.target}"

    if path_part and not resolved.exists():
        return f"{link.source.relative_to(root)}:{link.line}: missing target: {link.target}"

    if fragment and resolved.is_file() and resolved.suffix.lower() == ".md":
        anchors = anchor_cache.setdefault(resolved, anchors_for(resolved))
        if fragment not in anchors:
            return (
                f"{link.source.relative_to(root)}:{link.line}: "
                f"missing Markdown anchor #{fragment} in {resolved.relative_to(root)}"
            )

    return None


def check_markdown_links(root: Path, markdown_files: list[Path]) -> list[str]:
    root = root.resolve()
    errors: list[str] = []
    anchor_cache: dict[Path, set[str]] = {}

    for source in markdown_files:
        source = source.resolve()
        if not source.is_file():
            errors.append(f"{source}: Markdown source does not exist")
            continue
        try:
            text = source.read_text(encoding="utf-8")
        except UnicodeDecodeError as exc:
            errors.append(f"{source.relative_to(root)}: not valid UTF-8: {exc}")
            continue

        for link in extract_links(source, text):
            error = validate_link(root, link, anchor_cache)
            if error:
                errors.append(error)

    return errors


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check repository-local links in README.md and docs/**/*.md without network access."
    )
    parser.add_argument(
        "paths",
        nargs="*",
        help="Optional Markdown files/directories to check instead of README.md + docs/**/*.md.",
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=repository_root(),
        help="Repository root (defaults to the checkout containing this script).",
    )
    return parser.parse_args(argv)


def selected_files(root: Path, requested: list[str]) -> list[Path]:
    if not requested:
        return default_markdown_files(root)

    files: list[Path] = []
    for raw in requested:
        candidate = Path(raw)
        path = (root / candidate).resolve() if not candidate.is_absolute() else candidate.resolve()
        if path.is_dir():
            files.extend(sorted(item for item in path.rglob("*.md") if item.is_file()))
        else:
            files.append(path)
    return files


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = args.root.resolve()
    files = selected_files(root, args.paths)
    errors = check_markdown_links(root, files)

    if errors:
        print("Markdown link check: FAILED", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1

    print(f"Markdown link check: PASS ({len(files)} files, local targets only)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
