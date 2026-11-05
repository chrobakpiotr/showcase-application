"""The probe modules are the library's; these names only forward (their behaviour tests live in agent-harness)."""
import pathlib
import subprocess
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))


class QualificationBindingTest(unittest.TestCase):
    def test_each_name_is_the_library_module(self):
        import importlib
        for name in ('b_probes', 'q_lifecycle', 'q_probes'):
            with self.subTest(name):
                self.assertIs(importlib.import_module(f'agent_harness.qualification.{name}'),
                              importlib.import_module(f'qualification.{name}'))

    def test_running_a_forwarding_file_as_a_script_refuses(self):
        proc = subprocess.run([sys.executable, str(HERE / 'qualification' / 'q_lifecycle.py'), '--controller', 'x'],
                              capture_output=True, text=True, check=False)
        self.assertNotEqual(0, proc.returncode)
        self.assertIn('library module', proc.stderr)


if __name__ == '__main__':
    unittest.main()
