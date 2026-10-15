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

    def test_executable_nested_under_excluded_worktree_is_refused(self):
        with tempfile.TemporaryDirectory(dir=pathlib.Path.home()) as temporary:
            root = pathlib.Path(temporary).resolve()
            trusted = root / 'trusted'
            worktree = trusted / 'excluded-worktree'
            worktree.mkdir(parents=True, mode=0o700)
            trusted.chmod(0o700)
            worktree.chmod(0o700)
            executable = worktree / 'candidate'
            executable.write_text('#!/bin/sh\nexit 0\n')
            executable.chmod(0o700)
            (trusted / 'candidate').symlink_to(executable)

            # Isolate resolve_executable's resolved-candidate guard: the first
            # test owns the separate trusted-search-directory overlap guard.
            with mock.patch.object(sandbox, 'trusted_executable_directories',
                                   return_value=(trusted,)):
                with self.assertRaisesRegex(sandbox.ExecutableResolutionError,
                                            '^executable-path-excluded$'):
                    sandbox.resolve_executable('candidate',
                        toolchain_directories=(str(trusted),), worktree=worktree,
                        tmpdir=str(root / 'unrelated-tmp'))


if __name__ == '__main__':
    unittest.main()
