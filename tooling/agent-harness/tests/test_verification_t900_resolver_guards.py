"""Evaluator coverage for executable exclusion guards missed by builder suites."""
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

HARNESS = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HARNESS))
import verification_sandbox as sandbox


class T900ResolverExclusionGuardTest(unittest.TestCase):
    def test_declared_search_directory_inside_excluded_root_is_refused(self):
        with tempfile.TemporaryDirectory(dir=pathlib.Path.home()) as temporary:
            root = pathlib.Path(temporary).resolve()
            worktree = root / 'worktree'
            search = worktree / 'toolchain'
            search.mkdir(parents=True, mode=0o700)
            search.chmod(0o700)

            with self.assertRaisesRegex(sandbox.ExecutableResolutionError,
                                        '^executable-search-path-excluded$'):
                sandbox.trusted_executable_directories(
                    toolchain_directories=(str(search),), worktree=worktree)



if __name__ == '__main__':
    unittest.main()
