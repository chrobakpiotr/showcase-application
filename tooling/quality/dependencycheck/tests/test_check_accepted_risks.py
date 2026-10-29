import datetime as dt
import xml.etree.ElementTree as ET
import unittest
from pathlib import Path

from tooling.quality.dependencycheck.check_accepted_risks import validate_document


def document(until="2026-11-03Z", notes=None, package_url=r"^pkg:javascript/DOMPurify@3\.4\.13$", vulnerability="GHSA-p98j-92pf-mc4p"):
    notes = notes or "Owner: PC. Review/expiry: 2026-11-03. Exact scope only."
    return f"""<suppressions xmlns="urn:dependency-check:test"><suppress until="{until}">
      <notes>{notes}</notes><packageUrl regex="true">{package_url}</packageUrl>
      <vulnerabilityName>{vulnerability}</vulnerabilityName>
    </suppress></suppressions>"""


class AcceptedRiskValidationTest(unittest.TestCase):
    def test_checked_in_risks_match_owner_approved_scope_and_expire(self):
        accepted_risks_path = Path(__file__).parents[1] / "accepted-risks.xml"
        root = ET.parse(accepted_risks_path).getroot()
        suppressions = [element for element in root.iter()
                        if element.tag.rsplit("}", 1)[-1] == "suppress"]
        ns = {"dc": "https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd"}
        self.assertEqual(3, len(suppressions))
        self.assertEqual(
            {"CVE-2026-116802", "CVE-2026-116803", "CVE-2026-116804"},
            {item.findtext("dc:vulnerabilityName", namespaces=ns) for item in suppressions},
        )
        for item in suppressions:
            self.assertEqual("2026-11-12Z", item.get("until"))
            package = item.find("dc:packageUrl", ns)
            self.assertEqual("pkg:javascript/DOMPurify@3.4.16", package.text)
            self.assertNotEqual("true", package.get("regex"))
        xml_text = accepted_risks_path.read_text(encoding="utf-8")
        self.assertEqual([], validate_document(xml_text, dt.date(2026, 10, 29)).errors)
        self.assertEqual(3, len(validate_document(xml_text, dt.date(2026, 11, 13)).errors))

    def test_warns_inside_review_window_and_passes_before_expiry(self):
        result = validate_document(document(), dt.date(2026, 10, 20), warn_days=30)
        self.assertEqual([], result.errors)
        self.assertEqual(1, len(result.warnings))

    def test_passes_on_expiry_date_without_premature_expiration(self):
        result = validate_document(document(), dt.date(2026, 11, 3), warn_days=30)
        self.assertEqual([], result.errors)
        self.assertEqual(1, len(result.warnings))

    def test_fails_after_expiry(self):
        result = validate_document(document(), dt.date(2026, 11, 4), warn_days=30)
        self.assertEqual(1, len(result.errors))

    def test_fails_when_risk_metadata_is_incomplete(self):
        result = validate_document(document(notes="temporary exception only"), dt.date(2026, 10, 1))
        self.assertTrue(any("owner" in error.lower() for error in result.errors))
        self.assertTrue(any("review/expiry" in error.lower() for error in result.errors))

    def test_fails_when_suppression_scope_is_broad(self):
        result = validate_document(
            document(package_url=r"^pkg:javascript/.*$", vulnerability=""), dt.date(2026, 10, 1)
        )
        self.assertGreaterEqual(len(result.errors), 2)

    def test_fails_on_invalid_expiry(self):
        result = validate_document(document(until="not-a-date"), dt.date(2026, 10, 1))
        self.assertTrue(any("expiry" in error.lower() for error in result.errors))

    def test_empty_risk_file_is_valid_when_no_exceptions_are_accepted(self):
        result = validate_document('<suppressions xmlns="urn:dependency-check:test"/>', dt.date(2026, 10, 15))
        self.assertEqual([], result.errors)
        self.assertEqual([], result.warnings)


if __name__ == "__main__":
    unittest.main()
