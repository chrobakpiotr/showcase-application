from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import check_markdown_links as checker


class MarkdownLinkCheckerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "docs").mkdir()
        self.readme = self.root / "README.md"

    def tearDown(self):
        self.temp.cleanup()

    def write(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def check(self, *files):
        return checker.check_markdown_links(self.root, list(files))

    def test_accepts_paths_images_references_and_github_style_anchors(self):
        guide = self.write(
            "docs/guide.md",
            "# Guide\n\n"
            "## API response design (HATEOAS & pagination)\n\n"
            "## Same\n## Same\n\n"
            "## `Inline Code`\n",
        )
        self.write("docs/picture.png", "not really a png")
        self.readme.write_text(
            "[guide](docs/guide.md#api-response-design-hateoas--pagination)\n"
            "[duplicate](/docs/guide.md#same-1)\n"
            "[inline code heading](docs/guide.md#inline-code)\n"
            "![image](docs/picture.png)\n"
            "[ref][guide-ref]\n"
            "[guide-ref]: docs/guide.md#guide\n",
            encoding="utf-8",
        )

        self.assertEqual([], self.check(self.readme, guide))

    def test_reports_missing_file_and_missing_anchor(self):
        guide = self.write("docs/guide.md", "# Existing\n")
        self.readme.write_text(
            "[missing](docs/missing.md)\n"
            "[bad anchor](docs/guide.md#missing)\n",
            encoding="utf-8",
        )

        errors = self.check(self.readme, guide)

        self.assertEqual(2, len(errors))
        self.assertIn("missing target: docs/missing.md", errors[0])
        self.assertIn("missing Markdown anchor #missing", errors[1])

    def test_ignores_external_links_and_links_inside_code(self):
        self.readme.write_text(
            "[web](https://example.com/x)\n"
            "[mail](mailto:test@example.com)\n"
            "`[inline](docs/missing.md)`\n"
            "```md\n[code](docs/missing.md)\n```\n",
            encoding="utf-8",
        )

        self.assertEqual([], self.check(self.readme))

    def test_decodes_percent_encoded_local_paths_and_supports_html_links(self):
        target = self.write("docs/my guide.md", "# Section Name\n")
        self.readme.write_text(
            "[encoded](docs/my%20guide.md#section-name)\n"
            '<a href="docs/my%20guide.md#section-name">HTML</a>\n',
            encoding="utf-8",
        )

        self.assertEqual([], self.check(self.readme, target))

    def test_rejects_links_that_escape_repository(self):
        outside = self.root.parent / "outside.md"
        outside.write_text("# Outside\n", encoding="utf-8")
        self.addCleanup(lambda: outside.unlink(missing_ok=True))
        self.readme.write_text("[outside](../outside.md)\n", encoding="utf-8")

        errors = self.check(self.readme)

        self.assertEqual(1, len(errors))
        self.assertIn("link escapes repository", errors[0])

    def test_unknown_uri_scheme_is_not_treated_as_local_file(self):
        self.readme.write_text("[custom](urn:problem-type:example)\n", encoding="utf-8")
        self.assertEqual([], self.check(self.readme))

    def test_anchor_only_link_checks_current_document(self):
        self.readme.write_text("# Local Heading\n\n[local](#local-heading)\n", encoding="utf-8")
        self.assertEqual([], self.check(self.readme))

        self.readme.write_text("# Local Heading\n\n[local](#missing)\n", encoding="utf-8")
        errors = self.check(self.readme)
        self.assertEqual(1, len(errors))
        self.assertIn("missing Markdown anchor #missing", errors[0])


if __name__ == "__main__":
    unittest.main()
