import os
import sys
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

import verify_pit_report as verifier


class VerifyPitReportTest(unittest.TestCase):

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.xml = self.root / "mutations.xml"
        self.html = self.root / "index.html"
        self.html.write_text("<html>PIT</html>")

    def tearDown(self):
        self.temp.cleanup()

    def write_mutations(self, mutations):
        root = ET.Element("mutations")
        for detected, status in mutations:
            ET.SubElement(
                root,
                "mutation",
                detected="true" if detected else "false",
                status=status,
                numberOfTestsRun="1",
            )
        ET.ElementTree(root).write(self.xml, encoding="unicode")

    def test_accepts_nonempty_fully_detected_report(self):
        self.write_mutations([(True, "KILLED"), (True, "TIMED_OUT")])
        evidence = verifier.verify(self.xml, self.html)
        self.assertEqual(2, evidence["mutationCount"])
        self.assertEqual(2, evidence["detectedMutationCount"])

    def test_missing_report_fails(self):
        with self.assertRaises(verifier.VerificationError):
            verifier.verify(self.xml, self.html)

    def test_zero_mutants_fails(self):
        self.write_mutations([])
        with self.assertRaisesRegex(verifier.VerificationError, "zero mutants"):
            verifier.verify(self.xml, self.html)

    def test_surviving_mutant_fails(self):
        self.write_mutations([(True, "KILLED"), (False, "SURVIVED")])
        with self.assertRaisesRegex(verifier.VerificationError, "undetected mutants"):
            verifier.verify(self.xml, self.html)

    def test_stale_report_fails(self):
        marker = self.root / "started"
        marker.touch()
        self.write_mutations([(True, "KILLED")])
        old = marker.stat().st_mtime - 10
        os.utime(self.xml, (old, old))
        os.utime(self.html, (old, old))
        with self.assertRaisesRegex(verifier.VerificationError, "stale"):
            verifier.verify(self.xml, self.html, started_after=marker)


if __name__ == "__main__":
    unittest.main()
