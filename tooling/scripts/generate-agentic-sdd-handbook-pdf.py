#!/usr/bin/env python3
"""Render the canonical Agentic SDD Markdown handbook as a deterministic PDF.

Uses only Python's standard library. Markdown is authoritative; --check
compares the complete generated PDF bytes and validates required source text.
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys
import textwrap

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'docs/agentic-sdd/handbook.md'
OUTPUT = ROOT / 'docs/agentic-sdd/AI_Harness_Agentic_SDD.pdf'
REQUIRED = (
    'M5.3 history', 'Source Resolution', 'Candidate sealing',
    'Human retry authorization', 'Manual evidence', 'M5.3 implementation history',
    'MANUAL TELEMETRY REQUIRED', 'verification-blocked', 'verification-owned',
)


def ascii_text(value: str) -> str:
    value = value.replace('→', '->').replace('←', '<-').replace('—', '-').replace('–', '-')
    value = value.replace('’', "'").replace('‘', "'").replace('“', '"').replace('”', '"')
    value = value.replace('≥', '>=').replace('≤', '<=').replace('!=', '!=')
    value = value.replace('•', '*').replace('…', '...')
    return value.encode('ascii', 'ignore').decode('ascii')


def inline_text(value: str) -> str:
    value = re.sub(r'!?\[([^\]]+)\]\(([^)]+)\)', r'\1 (\2)', value)
    value = re.sub(r'`([^`]+)`', r'\1', value)
    value = re.sub(r'\*\*([^*]+)\*\*', r'\1', value)
    value = re.sub(r'(?<!\*)\*([^*]+)\*(?!\*)', r'\1', value)
    return ascii_text(value)


def source_lines(markdown: str) -> list[tuple[str, float, str]]:
    result: list[tuple[str, float, str]] = []
    in_code = False
    for raw in markdown.splitlines():
        line = raw.rstrip()
        if line.startswith('```'):
            in_code = not in_code
            result.append(('', 5.0, 'F1'))
            continue
        if in_code:
            value = ascii_text(line.expandtabs(4))
            wrapped = textwrap.wrap(value, width=92, subsequent_indent='    ',
                                    break_long_words=True, break_on_hyphens=False) or ['']
            result.extend((item, 8.3, 'F2') for item in wrapped)
            continue
        if not line.strip():
            result.append(('', 4.0, 'F1'))
            continue
        if line.startswith('#'):
            level = len(line) - len(line.lstrip('#'))
            title = inline_text(line[level:].strip())
            size = 19.0 if level == 1 else 15.0 if level == 2 else 12.0
            result.extend([(title, size, 'F1'), ('', 6.0, 'F1')])
            continue
        if line.startswith('>'):
            line = 'NOTE: ' + line.lstrip('> ').strip()
        if line.startswith('|'):
            cells = [cell.strip() for cell in line.strip('|').split('|')]
            if all(re.fullmatch(r':?-{2,}:?', cell or '-') for cell in cells):
                continue
            if len(cells) == 3 and cells[0].casefold() == 'category':
                continue
            if len(cells) == 3 and cells[2].isdigit():
                line = f'{cells[0]} - {cells[1]} (CLI exit: {cells[2]})'
            else:
                line = '  |  '.join(cells)
        line = re.sub(r'^\s*[-*+]\s+', '* ', line)
        line = re.sub(r'^\s*(\d+)\.\s+', r'\1. ', line)
        value = inline_text(line)
        width = 84 if value.startswith('* ') else 94
        wrapped = textwrap.wrap(value, width=width, subsequent_indent='  ',
                                break_long_words=True, break_on_hyphens=False) or ['']
        result.extend((item, 9.2, 'F1') for item in wrapped)
    if in_code:
        raise ValueError('unclosed fenced code block in handbook')
    return result


def pdf_escape(text: str) -> bytes:
    raw = text.encode('cp1252', errors='replace')
    return b'(' + raw.replace(b'\\', b'\\\\').replace(b'(', b'\\(').replace(b')', b'\\)') + b')'


def make_pdf(lines: list[tuple[str, float, str]]) -> bytes:
    pages: list[list[tuple[str, float, str, float]]] = [[]]
    y = 744.0
    for text, size, font in lines:
        step = max(size * 1.45, 7.5)
        if y - step < 54:
            pages.append([])
            y = 744.0
        pages[-1].append((text, size, font, y))
        y -= step
    if not pages[-1]:
        pages.pop()

    objects: list[bytes] = [b'']

    def add(value: bytes) -> int:
        objects.append(value)
        return len(objects) - 1

    catalog_id = add(b'')
    pages_id = add(b'')
    helvetica_id = add(b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>')
    courier_id = add(b'<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>')
    page_ids = []
    for page_number, page in enumerate(pages, start=1):
        commands = [b'0.15 0.20 0.30 rg']
        for text, size, font, y_pos in page:
            font_id = courier_id if font == 'F2' else helvetica_id
            commands.append(b'BT /F%d %.1f Tf 52 %.1f Td ' % (font_id, size, y_pos) + pdf_escape(text) + b' Tj ET')
        commands.extend([
            b'0.45 0.45 0.45 rg',
            b'BT /F%d 8 Tf 52 34 Td ' % helvetica_id + pdf_escape('Agentic SDD Harness Handbook') + b' Tj ET',
            b'BT /F%d 8 Tf 550 34 Td ' % helvetica_id + pdf_escape(f'{page_number}/{len(pages)}') + b' Tj ET',
        ])
        stream = b'\n'.join(commands) + b'\n'
        content_id = add(b'<< /Length %d >>\nstream\n' % len(stream) + stream + b'endstream')
        page_id = add(b'<< /Type /Page /Parent %d 0 R /MediaBox [0 0 612 792] '
                      b'/Resources << /Font << /F%d %d 0 R /F%d %d 0 R >> >> /Contents %d 0 R >>'
                      % (pages_id, helvetica_id, helvetica_id, courier_id, courier_id, content_id))
        page_ids.append(page_id)
    objects[catalog_id] = b'<< /Type /Catalog /Pages %d 0 R >>' % pages_id
    objects[pages_id] = b'<< /Type /Pages /Kids [%s] /Count %d >>' % (
        b' '.join(f'{page_id} 0 R'.encode() for page_id in page_ids), len(page_ids))

    output = bytearray(b'%PDF-1.4\n%\xe2\xe3\xcf\xd3\n')
    offsets = [0]
    for index, obj in enumerate(objects[1:], start=1):
        offsets.append(len(output))
        output.extend(f'{index} 0 obj\n'.encode())
        output.extend(obj)
        output.extend(b'\nendobj\n')
    xref_offset = len(output)
    output.extend(f'xref\n0 {len(objects)}\n'.encode())
    output.extend(b'0000000000 65535 f \n')
    for offset in offsets[1:]:
        output.extend(f'{offset:010d} 00000 n \n'.encode())
    output.extend(f'trailer\n<< /Size {len(objects)} /Root {catalog_id} 0 R >>\n'.encode())
    output.extend(f'startxref\n{xref_offset}\n%%EOF\n'.encode())
    return bytes(output)


def extracted_text(pdf: bytes) -> list[str]:
    """Extract this generator's literal PDF text strings for content checking."""
    values = []
    for match in re.finditer(rb'\(((?:\\.|[^\\)])*)\) Tj ET', pdf):
        raw = match.group(1).replace(b'\\(', b'(').replace(b'\\)', b')').replace(b'\\\\', b'\\')
        values.append(raw.decode('cp1252', errors='replace'))
    return values


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='verify checked-in PDF matches handbook source')
    args = parser.parse_args(argv)
    if not SOURCE.is_file():
        print(f'missing handbook source: {SOURCE}', file=sys.stderr)
        return 2
    try:
        markdown = SOURCE.read_text(encoding='utf-8')
        plain = '\n'.join(text for text, _size, _font in source_lines(markdown))
        missing = [item for item in REQUIRED if item.casefold() not in plain.casefold()]
        if missing:
            raise ValueError('required handbook coverage missing: ' + ', '.join(missing))
        lines = source_lines(markdown)
        generated = make_pdf(lines)
    except (OSError, UnicodeError, ValueError) as exc:
        print(f'handbook generation failed: {exc}', file=sys.stderr)
        return 2
    if args.check:
        if not OUTPUT.is_file():
            print(f'missing generated PDF: {OUTPUT}', file=sys.stderr)
            return 1
        expected_text = [line for line, _size, _font in lines]
        # The first document title is also repeated in each page footer.
        if expected_text and expected_text[0] == 'Agentic SDD Harness Handbook':
            expected_text = expected_text[1:]
        actual_text = extracted_text(OUTPUT.read_bytes())
        actual_body = [item for item in actual_text if item != 'Agentic SDD Harness Handbook' and
                       not re.fullmatch(r'\d+/\d+', item)]
        page_count = len([item for item in actual_text if re.fullmatch(r'\d+/\d+', item)])
        expected_footer = [label for page in range(1, page_count + 1)
                           for label in ('Agentic SDD Harness Handbook', f'{page}/{page_count}')]
        actual_footer = [item for item in actual_text if item == 'Agentic SDD Harness Handbook' or
                         re.fullmatch(r'\d+/\d+', item)]
        if actual_footer and actual_footer[0] == 'Agentic SDD Harness Handbook':
            actual_footer = actual_footer[1:]
        if actual_body != expected_text or actual_footer != expected_footer or page_count == 0:
            print('checked-in PDF content is stale or incomplete; regenerate it from handbook.md', file=sys.stderr)
            return 1
        print(f'Handbook PDF is current ({len(generated)} bytes).')
        return 0
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    temp_output = OUTPUT.with_suffix('.pdf.tmp')
    temp_output.write_bytes(generated)
    temp_output.replace(OUTPUT)
    print(f'Generated {OUTPUT.relative_to(ROOT)} ({len(generated)} bytes).')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
