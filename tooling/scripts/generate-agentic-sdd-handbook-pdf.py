#!/usr/bin/env python3
"""Render the canonical Agentic SDD handbook to a deterministic, dependency-free PDF."""

from __future__ import annotations

import argparse
import re
import sys
import unicodedata
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs/agentic-sdd/handbook.md"
OUTPUT = ROOT / "docs/agentic-sdd/AI_Harness_Agentic_SDD.pdf"
PAGE_WIDTH, PAGE_HEIGHT = 612, 792
LEFT, TOP, BOTTOM = 48, 744, 48
BODY_SIZE, CODE_SIZE, LEADING = 9, 8, 12


def ascii_text(value: str) -> str:
    value = value.replace("->", " to ").replace("→", " to ").replace("—", "-").replace("–", "-")
    value = value.replace("’", "'").replace("‘", "'").replace("“", '"').replace("”", '"')
    return unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")


def inline_text(value: str) -> str:
    value = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r"\1 (\2)", value)
    return ascii_text(value.replace("**", "").replace("`", "").replace("*", ""))


def layout(markdown: str) -> list[list[tuple[str, str, int]]]:
    """Return pages of (text, font, size), retaining headings and code indentation."""
    pages: list[list[tuple[str, str, int]]] = [[]]
    y = TOP
    in_code = False

    def emit(text: str, font: str = "F1", size: int = BODY_SIZE, *, gap: int = 0) -> None:
        nonlocal y
        if y - LEADING < BOTTOM:
            pages.append([])
            y = TOP
        pages[-1].append((text, font, size))
        y -= LEADING + gap

    for raw in markdown.splitlines():
        if raw.strip().startswith("```"):
            in_code = not in_code
            y -= 3
            continue
        if not raw.strip():
            # Markdown paragraph breaks are preserved as a compact single-line gap
            # in the PDF output, so they must not consume extra pagination space.
            y -= 0
            continue
        if in_code:
            text = ascii_text(raw).expandtabs(2)
            width = 112
            chunks = [text[i:i + width] for i in range(0, len(text), width)] or [""]
            for chunk in chunks:
                emit(chunk, "F2", CODE_SIZE)
            continue

        if raw.lstrip().startswith("|"):
            cells = [cell.strip() for cell in raw.strip().strip("|").split("|")]
            if cells and all(not cell or set(cell) <= {"-", ":"} for cell in cells):
                continue
            if cells and cells[0] == "Work":
                emit("Workflow choices (work | flow | verification)", "F3", BODY_SIZE)
                continue
            labels = ("Work", "Flow", "Verification")
            text = "; ".join(f"{labels[i]}: {cell}" for i, cell in enumerate(cells) if cell)
            for chunk in wrap(inline_text(text), 104):
                emit(chunk, "F1", BODY_SIZE)
            continue

        match = re.match(r"^(#{1,3})\s+(.*)$", raw)
        if match:
            level = len(match.group(1))
            text = inline_text(match.group(2))
            size = {1: 16, 2: 12, 3: 10}[level]
            y -= 4
            for chunk in wrap(text, 88 if level == 1 else 100):
                emit(chunk, "F3", size, gap=2 if level == 1 else 0)
            y -= 1
            continue

        text = inline_text(raw)
        indent = len(text) - len(text.lstrip())
        if text.lstrip().startswith("| "):
            text = "  " + text.lstrip()
        width = 104 - min(indent, 12)
        for index, chunk in enumerate(wrap(text, width)):
            emit(chunk if index == 0 else "  " + chunk, "F1", BODY_SIZE)
    return pages


def wrap(text: str, width: int) -> list[str]:
    words = text.split()
    if not words:
        return [""]
    lines: list[str] = []
    current = words[0]
    for word in words[1:]:
        if len(current) + 1 + len(word) <= width:
            current += " " + word
        else:
            lines.append(current)
            current = word
    lines.append(current)
    return lines


def pdf_escape(text: str) -> bytes:
    data = text.encode("cp1252", errors="replace")
    return data.replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)")


def make_pdf(markdown: str) -> bytes:
    pages = layout(markdown)
    objects: list[bytes] = []

    def add(value: bytes) -> int:
        objects.append(value)
        return len(objects)

    catalog_id = add(b"")
    pages_id = add(b"")
    regular_id = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    mono_id = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
    bold_id = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
    page_ids: list[int] = []

    for page_number, lines in enumerate(pages, 1):
        content = bytearray(b"BT\n")
        y = TOP
        for text, font, size in lines:
            content += f"/{font} {size} Tf\n1 0 0 1 {LEFT} {y} Tm\n".encode("ascii")
            content += b"(" + pdf_escape(text) + b") Tj\n"
            y -= LEADING
            # Headings use a larger visual size; compensate in cursor advance.
            if font == "F3" and size > BODY_SIZE:
                y -= (size - BODY_SIZE) // 2
        content += b"ET\nBT\n/F1 8 Tf\n1 0 0 1 48 30 Tm\n(Agentic SDD Verification Handbook) Tj\n"
        content += f"1 0 0 1 540 30 Tm\n({page_number}) Tj\nET\n".encode("ascii")
        stream = bytes(content)
        content_id = add(b"<< /Length " + str(len(stream)).encode("ascii") + b" >>\nstream\n" + stream + b"endstream")
        page_id = add((f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 {PAGE_WIDTH} {PAGE_HEIGHT}] "
                       f"/Resources << /Font << /F1 {regular_id} 0 R /F2 {mono_id} 0 R /F3 {bold_id} 0 R >> >> "
                       f"/Contents {content_id} 0 R >>").encode("ascii"))
        page_ids.append(page_id)

    objects[catalog_id - 1] = f"<< /Type /Catalog /Pages {pages_id} 0 R >>".encode("ascii")
    kids = b" ".join(f"{item} 0 R".encode("ascii") for item in page_ids)
    objects[pages_id - 1] = b"<< /Type /Pages /Count " + str(len(page_ids)).encode("ascii") + b" /Kids [" + kids + b"] >>"

    pdf = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0]
    for number, obj in enumerate(objects, 1):
        offsets.append(len(pdf))
        pdf += f"{number} 0 obj\n".encode("ascii") + obj + b"\nendobj\n"
    xref = len(pdf)
    pdf += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode("ascii")
    for offset in offsets[1:]:
        pdf += f"{offset:010d} 00000 n \n".encode("ascii")
    pdf += (f"trailer\n<< /Size {len(objects) + 1} /Root {catalog_id} 0 R >>\n"
            f"startxref\n{xref}\n%%EOF\n").encode("ascii")
    return bytes(pdf)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true", help="fail if the generated PDF differs from the checked-in file")
    args = parser.parse_args()
    generated = make_pdf(args.source.read_text(encoding="utf-8"))
    if args.check:
        try:
            current = args.output.read_bytes()
        except FileNotFoundError:
            print(f"missing generated PDF: {args.output}", file=sys.stderr)
            return 1
        if current != generated:
            print(f"stale generated PDF: {args.output}", file=sys.stderr)
            return 1
        print(f"PDF matches canonical source: {args.output}")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(generated)
    print(f"wrote {args.output} ({len(generated)} bytes, {generated.count(b'/Type /Page ')} pages)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
