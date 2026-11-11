from pathlib import Path
import contextlib
import io
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import report_uncovered_lines as reporter


REPORT = (
    '<report name="web"><package name="com/example">'
    '<sourcefile name="Covered.java"><line nr="1" mi="0" ci="3"/></sourcefile>'
    '<sourcefile name="Missed.java"><line nr="7" mi="2" ci="0"/><line nr="9" mi="1" ci="1"/></sourcefile>'
    "</package></report>"
)


class ReportUncoveredLinesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def write_report(self, module: str, content: str) -> Path:
        path = self.root / module / "build/reports/jacoco/test/jacocoTestReport.xml"
        path.parent.mkdir(parents=True)
        path.write_text(content, encoding="utf-8")
        return path

    def run_main(self) -> str:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(reporter.main(["report", str(self.root)]), 0)
        return output.getvalue()

    def test_lists_only_lines_with_missed_instructions(self):
        report = self.write_report("web", REPORT)

        self.assertEqual(reporter.uncovered(report), ["com/example/Missed.java: lines 7, 9"])
        self.assertIn("com/example/Missed.java: lines 7, 9", self.run_main())

    def test_reports_when_nothing_is_missed(self):
        self.write_report("web", '<report name="web"><package name="p"></package></report>')

        self.assertIn("No missed instructions", self.run_main())


if __name__ == "__main__":
    unittest.main()
