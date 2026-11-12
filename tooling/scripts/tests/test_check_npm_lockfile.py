import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

import check_npm_lockfile  # noqa: E402

GOOD = {"resolved": "https://registry.npmjs.org/a/-/a-1.0.0.tgz", "integrity": "sha512-abc"}


class CheckNpmLockfileTest(unittest.TestCase):

    def test_accepts_registry_packages_root_and_links(self):
        lock = {"packages": {"": {"name": "app"}, "node_modules/a": GOOD, "node_modules/b": {"link": True}}}

        self.assertEqual([], check_npm_lockfile.problems(lock))

    def test_rejects_other_origins_and_weak_or_missing_integrity(self):
        lock = {"packages": {
            "node_modules/git": {"resolved": "git+ssh://git@github.com/x/y.git#abc", "integrity": "sha512-abc"},
            "node_modules/sha1": {"resolved": GOOD["resolved"], "integrity": "sha1-abc"},
            "node_modules/bare": {},
        }}

        found = check_npm_lockfile.problems(lock)

        self.assertEqual(4, len(found))
        self.assertTrue(any("node_modules/git: resolved outside" in problem for problem in found))
        self.assertTrue(any("node_modules/sha1: integrity is not SHA-512" in problem for problem in found))

    def test_checks_the_committed_frontend_lockfile(self):
        lock = pathlib.Path(__file__).resolve().parents[3] / "apps/ecommerce/frontend/package-lock.json"

        self.assertEqual(0, check_npm_lockfile.main(["check_npm_lockfile.py", str(lock)]))
        self.assertEqual(2, check_npm_lockfile.main(["check_npm_lockfile.py"]))


if __name__ == "__main__":
    unittest.main()
