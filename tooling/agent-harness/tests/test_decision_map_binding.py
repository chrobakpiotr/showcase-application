"""AH5-16c: Showcase's decision_map.py delegates to agent_harness.decision_map (ADR 0006) through its own live
namespace.

Runs inside Showcase, where the original source is no longer present: the shim keeps the layout `shim_layout`
checks, with the decision_map paths (`HERE`, `REPO` and the three schema paths); a call through the shim runs the
library with the shim's namespace; a patch on the shim is what the library reads; the design module it reads is
Showcase's `design` shim; and the script entry point still answers through the library.
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

import decision_map
import shim_layout

from agent_harness import decision_map as library

ALIASES = ('MAP_FILE', 'PROTOCOL_VERSION', 'SUPPORTED_PROTOCOL_VERSIONS', 'VALID_LEDGER_TYPES',
           'VALID_LEDGER_STATUSES', 'TERMINAL_FOG_STATUSES', 'VALID_DECISION_TYPES', 'VALID_STATUSES')
PATHS = ('HERE', 'REPO', 'RESULT_SCHEMA', 'HANDOFF_SCHEMA', 'TASKS_SCHEMA')


class DecisionMapBindingTest(unittest.TestCase):
    def test_the_shim_keeps_only_aliases_and_delegates(self):
        shim_layout.check_shim_layout(self, decision_map, library, ALIASES, PATHS)
        here = decision_map.HERE
        self.assertEqual(pathlib.Path(decision_map.__file__).resolve().parent, here)
        self.assertEqual((here.parents[1], here / 'schemas' / 'decision-map-result.schema.json',
                          here / 'schemas' / 'decision-map-handoff.schema.json',
                          here / 'schemas' / 'decision-map-tasks-result.schema.json'),
                         (decision_map.REPO, decision_map.RESULT_SCHEMA, decision_map.HANDOFF_SCHEMA, decision_map.TASKS_SCHEMA))
        self.assertIs(decision_map.h, decision_map._NS.h)  # the live module namespace
        self.assertIs(decision_map.d, decision_map._NS.d)
        self.assertEqual('design', decision_map.d.__name__)  # Showcase's design module, itself a shim

    def test_a_call_runs_in_the_library_with_the_shim_namespace(self):
        real = library.map_path
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch.object(library, 'map_path', side_effect=real) as spy:
            self.assertEqual(pathlib.Path(tmp) / 'decision-map.json', decision_map.map_path(pathlib.Path(tmp)))
        self.assertIs(decision_map._NS, spy.call_args.kwargs['ns'])

    def test_a_patch_on_the_shim_is_what_the_library_reads(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(decision_map, 'MAP_FILE', 'other.json'):
            self.assertEqual(pathlib.Path(tmp) / 'other.json', decision_map.map_path(pathlib.Path(tmp)))

    def test_the_script_entry_point_answers_through_the_library(self):
        run = subprocess.run([sys.executable, str(HERE / 'decision_map.py'), '--help'], capture_output=True, text=True,
                             env=dict(os.environ), cwd=HERE.parents[1], check=False)
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertIn('to-spec', run.stdout)


if __name__ == '__main__':
    unittest.main()
