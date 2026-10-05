import datetime as dt
import unittest

from tooling.quality.dependencycheck.check_accepted_risks import validate_document


def document(until="2026-11-03Z", notes=None, package_url=r"^pkg:javascript/DOMPurify@3\.4\.13$", vulnerability="GHSA-p98j-92pf-mc4p"):
    notes = notes or "Owner: PC. Review/expiry: 2026-11-03. Exact scope only."
    return f"""<suppressions xmlns="urn:dependency-check:test"><suppress until="{until}">
      <notes>{notes}</notes><packageUrl regex="true">{package_url}</packageUrl>
      <vulnerabilityName>{vulnerability}</vulnerabilityName>
    </suppress></suppressions>"""


class AcceptedRiskValidationTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
