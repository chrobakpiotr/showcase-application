import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parents[1]
sys.path.insert(0, str(SCRIPTS))

import verify_evidence_checksums as guard


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class EvidenceChecksumTest(unittest.TestCase):

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name).resolve()
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        report = "Result — PASS\n".encode()  # an em dash, as recorded
        self.write("evidence/report.md", report)
        self.write("docs/spec.md", b"spec\n")
        self.write("evidence/SHA256SUMS", f"{sha(report)}  report.md\n".encode())
        self.write("evidence/manifest.json", json.dumps({
            "files": [{"path": "docs/spec.md", "sha256": "sha256:" + sha(b"spec\n")}],
            "self": {"path": "manifest.json", "sha256": "0" * 64},  # describes itself: not a binding
        }).encode())

    def write(self, name: str, data: bytes) -> None:
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        subprocess.run(["git", "add", name], cwd=self.root, check=True)

    def errors(self, baseline=None):
        return guard.evaluate(guard.collect(self.root), baseline or {})

    def test_sums_and_json_bindings_verify(self):
        bindings = guard.collect(self.root)
        self.assertEqual([("evidence/SHA256SUMS", "line 1", "report.md", "ok"),
                          ("evidence/manifest.json", "$.files[0]", "docs/spec.md", "ok")],
                         [tuple(b) for b in bindings])
        self.assertEqual([], self.errors())

    def test_a_typographic_edit_of_bound_evidence_fails(self):
        self.write("evidence/report.md", b"Result - PASS\n")  # the 2026-11-01 dash replacement, in miniature
        self.assertEqual(1, len(errors := self.errors()))
        self.assertIn("evidence/SHA256SUMS line 1 report.md: mismatch", errors[0])

    def test_an_edited_json_bound_file_fails_and_a_deleted_one_is_missing(self):
        self.write("docs/spec.md", b"spec, edited\n")
        self.assertIn("docs/spec.md: mismatch", self.errors()[0])
        subprocess.run(["git", "rm", "-q", "-f", "docs/spec.md"], cwd=self.root, check=True)
        self.assertIn("docs/spec.md: missing", self.errors()[0])

    def test_an_untracked_file_does_not_count_as_present(self):
        subprocess.run(["git", "rm", "-q", "--cached", "docs/spec.md"], cwd=self.root, check=True)
        self.assertIn("docs/spec.md: missing", self.errors()[0])

    def test_baseline_excuses_only_the_recorded_status_and_must_shrink(self):
        self.write("docs/spec.md", b"spec, edited\n")
        key = ("evidence/manifest.json", "$.files[0]", "docs/spec.md")
        entry = {"record": key[0], "location": key[1], "path": key[2], "reason": "historical"}
        self.assertEqual([], self.errors({key: {**entry, "status": "mismatch"}}))
        self.assertTrue(self.errors({key: {**entry, "status": "missing"}}))  # a different failure is not excused
        self.write("docs/spec.md", b"spec\n")
        self.assertIn("verifies again", self.errors({key: {**entry, "status": "mismatch"}})[0])
        stale = ("evidence/manifest.json", "$.files[9]", "gone.md")
        self.assertIn("no longer matches", self.errors({stale: {**entry, "status": "missing"}})[0])

    def test_baseline_entries_need_a_reason(self):
        path = self.root / "baseline.json"
        path.write_text(json.dumps({"exceptions": [{"record": "a", "location": "b", "path": "c", "status": "missing"}]}))
        with self.assertRaisesRegex(ValueError, "without a reason"):
            guard.load_baseline(path)

    def test_paths_outside_the_repository_are_not_resolved(self):
        self.write("evidence/escape.json", json.dumps({"path": "../../../etc/passwd", "sha256": "0" * 64}).encode())
        escaped = [b for b in guard.collect(self.root) if b.record == "evidence/escape.json"]
        self.assertEqual(["missing"], [b.status for b in escaped])

    def test_the_repository_matches_its_baseline(self):
        self.assertEqual(0, guard.main(["--root", str(REPOSITORY)]))


if __name__ == "__main__":
    unittest.main()
