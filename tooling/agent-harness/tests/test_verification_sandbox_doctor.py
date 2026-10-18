"""Read-only discovery cannot claim qualified verification launch readiness."""
import pathlib
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import verification_sandbox as sandbox


class SandboxDoctorTest(unittest.TestCase):
    def diagnose(self, system, candidates):
        with mock.patch.object(sandbox.platform, 'system', return_value=system), \
             mock.patch.object(sandbox.shutil, 'which', return_value='/installed/helper'), \
             mock.patch.object(sandbox, 'discover_backends', return_value=tuple(candidates)), \
             mock.patch.object(sandbox, 'qualify_backend') as qualify:
            result = sandbox.doctor()
            qualify.assert_not_called()
            return result






if __name__ == '__main__':
    unittest.main()
