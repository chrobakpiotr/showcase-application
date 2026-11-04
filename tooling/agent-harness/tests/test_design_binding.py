"""AH5-16b: Showcase's design.py delegates to agent_harness.design through its own live namespace.

Runs inside Showcase, where the original source is no longer present: the shim keeps the layout `shim_layout`
checks, with the design paths (`HERE`, `REPO`, `DESIGN_SCHEMA`); a call through the shim runs the library with the
shim's namespace; a patch on the shim (as Showcase's tests patch `design.<name>`) is what the library reads; and the
script entry point still answers through the library.
"""
import argparse
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

import design
import shim_layout

from agent_harness import design as library

ALIASES = ('StagePlan', 'VALID_MODES', 'RISK_ORDER', 'DESIGN_PROTOCOL_VERSION', '_WORKTREE_LOCK')
PATHS = ('HERE', 'REPO', 'DESIGN_SCHEMA')


class DesignBindingTest(unittest.TestCase):
    def test_the_shim_keeps_only_aliases_and_delegates(self):
        shim_layout.check_shim_layout(self, design, library, ALIASES, PATHS)
        self.assertEqual(pathlib.Path(design.__file__).resolve().parent, design.HERE)
        self.assertEqual((design.HERE.parents[1], design.HERE / 'schemas' / 'design-result.schema.json'),
                         (design.REPO, design.DESIGN_SCHEMA))
        self.assertIs(design.h, design._NS.h)  # the live module namespace
        self.assertIs(design.r, design._NS.r)

    def test_a_call_runs_in_the_library_with_the_shim_namespace(self):
        real = library.risk_level
        with mock.patch.object(library, 'risk_level', side_effect=real) as spy:
            self.assertEqual('high', design.risk_level('Title\nRisk: High\n'))
        self.assertIs(design._NS, spy.call_args.kwargs['ns'])

    def test_a_patch_on_the_shim_is_what_the_library_reads(self):
        args = argparse.Namespace(grill=None, prototype=None, architecture_grill=None)
        with tempfile.TemporaryDirectory() as tmp:
            feature = pathlib.Path(tmp)
            (feature / 'spec.md').write_text('# Spec\nRisk: medium\n', encoding='utf-8')
            self.assertTrue(design.stage_plan(feature, {}, args).spec_grill)
            with mock.patch.object(design, 'risk_level', return_value='low'):
                self.assertFalse(design.stage_plan(feature, {}, args).spec_grill)

    def test_the_script_entry_point_answers_through_the_library(self):
        run = subprocess.run([sys.executable, str(HERE / 'design.py'), '--help'], capture_output=True, text=True,
                             env=dict(os.environ), cwd=HERE.parents[1], check=False)
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertIn('--agent-timeout', run.stdout)


if __name__ == '__main__':
    unittest.main()
