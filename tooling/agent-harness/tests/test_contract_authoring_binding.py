"""The generate package: Showcase's verification_contract.py delegates to agent_harness.contract_authoring through its
own live namespace.

Runs inside Showcase, where the original source is no longer present: the shim keeps the layout `shim_layout`
checks, with its paths (`HERE`, `REPO`, `SCHEMA`) and the validation aliases it binds from
`agent_harness.verification_contract`; a call through the shim runs the library with the shim's namespace; a patch on
the shim (as Showcase's tests patch `verification_contract.REPO`) is what the library reads; the validation still is
the library's; and the script entry point still answers through the library.
"""
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

HERE = pathlib.Path(__file__).resolve().parents[1]
for path in (HERE, pathlib.Path(__file__).resolve().parent):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

import shim_layout
import verification_contract

from agent_harness import contract_authoring as library
from agent_harness import verification_contract as validation

ALIASES = ('CONSTITUTION',)
PATHS = ('lifecycle', 'HERE', 'REPO', 'SCHEMA')
BINDINGS = ('VALID_REQUIREMENT', 'VALID_ORIGINS', 'VALID_SOURCE_TYPES', 'die', 'sha', 'load_json', 'requirement',
            'criterion_ids')


class ContractAuthoringBindingTest(unittest.TestCase):
    def test_the_shim_keeps_only_aliases_bindings_and_delegates(self):
        shim_layout.check_shim_layout(self, verification_contract, library, ALIASES, PATHS, BINDINGS)
        import harness
        self.assertIs(harness, verification_contract.lifecycle())  # AH5-17: resolved at call time
        here = verification_contract.HERE
        self.assertEqual(pathlib.Path(verification_contract.__file__).resolve().parent, here)
        self.assertEqual((here.parents[1], here / 'schemas' / 'verification-contract.schema.json'),
                         (verification_contract.REPO, verification_contract.SCHEMA))
        for name in BINDINGS:  # the validation objects are the library's own
            with self.subTest(name):
                self.assertIs(getattr(validation, name), getattr(verification_contract, name))
        self.assertIs(verification_contract.r, verification_contract._NS.r)  # the live module namespace
        self.assertIs(validation, verification_contract._NS._impl)

    def test_a_call_runs_in_the_library_with_the_shim_namespace(self):
        real = library.is_active_feature_dir
        with mock.patch.object(library, 'is_active_feature_dir', side_effect=real) as spy:
            self.assertTrue(verification_contract.is_active_feature_dir(pathlib.Path('/r/docs/specs/F-1')))
        self.assertIs(verification_contract._NS, spy.call_args.kwargs['ns'])

    def test_a_star_args_delegate_passes_its_arguments_through(self):
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(['git', 'init', '-q', tmp], check=True)
            root = verification_contract._git_path(pathlib.Path(tmp), '--show-toplevel')
        self.assertEqual(pathlib.Path(tmp).resolve(), root)

    def test_a_patch_on_the_shim_is_what_the_library_reads(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(verification_contract, 'REPO', pathlib.Path(tmp)), \
                self.assertRaises(validation.RepositoryUnconfirmed) as caught:
            verification_contract.binding_repo_root(pathlib.Path(tmp))
        self.assertIn(tmp, str(caught.exception))

    def test_the_script_entry_point_answers_through_the_library(self):
        run = subprocess.run([sys.executable, str(HERE / 'verification_contract.py'), '--help'], capture_output=True,
                             text=True, env=dict(os.environ), cwd=HERE.parents[1], check=False)
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertIn('generate', run.stdout)


if __name__ == '__main__':
    unittest.main()
