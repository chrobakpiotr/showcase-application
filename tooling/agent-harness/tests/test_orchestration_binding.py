"""AH5-15: Showcase's orchestrate.py delegates to agent_harness.orchestration through its own live namespace.

Runs inside Showcase, where the original source is no longer present, so the shim is checked on its own terms: at
module level it holds only imports that shadow none of its delegation layer and bind each name once (no star import),
its paths (the runner beside it, the repository two levels up), the sys.path guard, aliases of the library's classes
and constants, one
delegate per library function with that function's signature (`ns` aside) that passes its own parameters through in
order with `ns=_NS`, the namespace binding after all of them, and the script entry point. A call through the shim runs the
library with the shim's namespace; a patch on the shim (as Showcase's tests patch `orchestrate.invoke_preflight` or
`orchestrate.REPO`) is what the library reads; the orchestrator's blockage vocabulary equals the lifecycle module's; and
the script entry point still plans a feature.
"""
import argparse
import ast
import inspect
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

HERE = pathlib.Path(__file__).resolve().parents[1]
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import orchestrate

from agent_harness import orchestration

LIBRARY_FUNCTIONS = sorted(n.name for n in ast.parse(inspect.getsource(orchestration)).body
                           if isinstance(n, ast.FunctionDef) and n.name != 'bind')
ALIASES = ('ProviderChoice', 'TaskOutcome', 'AgentPreflightRefused', 'VERIFICATION_BLOCKAGE_OUTCOMES',
           'VERIFICATION_OUTCOMES', 'MAX_SAFE_JSON_INTEGER', 'MAX_PROCESS_EXIT_CODE')
PATHS = ('HERE', 'RUNNER', 'REPO')
BINDING = '_orchestration.bind(_transitions.ModuleNamespace(globals()))'
PATH_GUARD = 'if str(HERE) not in sys.path:\n    sys.path.insert(0, str(HERE))'
ENTRY_POINT = "if __name__ == '__main__':\n    main()"


def cli_args():
    return argparse.Namespace(provider='codex', model=None, evaluator_provider=None, evaluator_model=None,
                              review_provider='claude', review_model=None, reasoning='medium', max_turns=30,
                              max_budget_usd=None, agent_timeout=1800, verification_timeout=900,
                              verification_sandbox='auto', owner_prefix='orchestrator', run_id='0123456789ab')


def pass_through(name, arguments):
    """The one call a delegate may make: its own parameters, in order, and the shim's namespace."""
    parts = ([a.arg for a in arguments.posonlyargs + arguments.args] + [f'{a.arg}={a.arg}' for a in arguments.kwonlyargs]
             + ['ns=_NS'] + ([f'**{arguments.kwarg.arg}'] if arguments.kwarg else []))
    return f"_orchestration.{name}({', '.join(parts)})"


class OrchestrationBindingTest(unittest.TestCase):
    def test_the_shim_keeps_only_aliases_and_delegates(self):
        tree = ast.parse(inspect.getsource(orchestrate))
        layer = set(LIBRARY_FUNCTIONS) | set(ALIASES) | set(PATHS) | {'_NS'}
        defined, places, imported = [], {}, []
        for index, node in enumerate(tree.body):
            text = ast.unparse(node)
            if index == 0 and isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant):
                continue  # the module docstring
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                bound = [(a.asname or a.name).split('.')[0] for a in node.names]
                self.assertFalse(set(bound) & layer | ({'*'} & set(bound)), text)  # no shadowing, no star import
                imported += bound
                continue
            if isinstance(node, ast.If):
                self.assertIn(text, (PATH_GUARD, ENTRY_POINT))
                places[text] = index
                continue
            if isinstance(node, ast.FunctionDef):
                with self.subTest(node.name):
                    self.assertIn(node.name, LIBRARY_FUNCTIONS)
                    self.assertEqual([], node.decorator_list)
                    self.assertEqual(1, len(node.body))
                    self.assertIsInstance(node.body[0], ast.Return)
                    self.assertEqual(pass_through(node.name, node.args), ast.unparse(node.body[0].value))
                defined.append(node.name)
                places.setdefault('layer', []).append(index)
                continue
            self.assertIsInstance(node, ast.Assign, text)
            self.assertEqual(1, len(node.targets), text)
            name = ast.unparse(node.targets[0])
            if name in ALIASES:
                self.assertEqual(f'_orchestration.{name}', ast.unparse(node.value))
                places.setdefault('layer', []).append(index)
            elif name == '_NS':
                self.assertEqual(BINDING, ast.unparse(node.value))
                places['_NS'] = index
            else:
                self.assertIn(name, PATHS, text)
            defined.append(name)
        self.assertEqual(sorted(LIBRARY_FUNCTIONS + list(ALIASES) + list(PATHS) + ['_NS']), sorted(defined))
        self.assertEqual(sorted(set(imported)), sorted(imported))  # a second import would rebind a name the library reads
        self.assertIn(PATH_GUARD, places)
        self.assertLess(max(places['layer']), places['_NS'])
        self.assertLess(places['_NS'], places[ENTRY_POINT])
        for name in ALIASES:
            with self.subTest(name):
                self.assertIs(getattr(orchestration, name), getattr(orchestrate, name))
        for name in LIBRARY_FUNCTIONS:  # each delegate has the library function's signature, `ns` aside
            with self.subTest(name):
                library = inspect.signature(getattr(orchestration, name))
                library = library.replace(parameters=[p for p in library.parameters.values() if p.name != 'ns'])
                self.assertEqual(library, inspect.signature(getattr(orchestrate, name)))
        self.assertEqual(pathlib.Path(orchestrate.__file__).resolve().parent, orchestrate.HERE)
        self.assertEqual((orchestrate.HERE / 'runner.py', orchestrate.HERE.parents[1]),
                         (orchestrate.RUNNER, orchestrate.REPO))
        self.assertIs(orchestrate.h, orchestrate._NS.h)  # the live module namespace

    def test_a_call_runs_in_the_library_with_the_shim_namespace(self):
        real = orchestration.verification_evidence_summary
        with mock.patch.object(orchestration, 'verification_evidence_summary', side_effect=real) as spy:
            summary = orchestrate.verification_evidence_summary([], {'plan_id': 'p', 'family': {'id': 'f'},
                'lifecycle_generation': 1, 'profile_id': 'showcase', 'profile_hash': 'h', 'policy_checkpoint': 'c'}, 0)
        self.assertIs(orchestrate._NS, spy.call_args.kwargs['ns'])
        self.assertEqual('MALFORMED_VERIFICATION_RESULT', summary['reason_code'])

    def test_a_patch_on_the_shim_is_what_the_library_reads(self):
        with tempfile.TemporaryDirectory() as tmp:
            packet = pathlib.Path(tmp) / 'packet.json'
            packet.write_text(json.dumps({'required_reviewers': ['security-reviewer']}))
            with mock.patch.object(orchestrate, 'invoke_preflight') as preflight:
                orchestrate.preflight_task_launches({'role': 'builder'}, packet, pathlib.Path(tmp),
                                                    orchestrate.ProviderChoice('codex', None), cli_args(), None)
            launched = [call.args[0] for call in preflight.call_args_list]
            self.assertEqual(2, len(launched))  # the builder and its required reviewer
            self.assertEqual([str(orchestrate.RUNNER)] * 2, [argv[1] for argv in launched])
            self.assertEqual(['codex', 'claude'], [argv[argv.index('--provider') + 1] for argv in launched])
            with mock.patch.object(orchestrate, 'REPO', pathlib.Path(tmp)):
                path = orchestrate.orchestration_manifest_path('F', 'run')
            self.assertEqual(pathlib.Path(tmp) / '.agent-runs' / 'F' / 'orchestrations' / 'run.json', path)

    def test_the_orchestrator_and_the_lifecycle_share_one_blockage_vocabulary(self):
        # Two literal copies (the orchestrator's, now in the library, and harness.py's); nothing else ties them.
        self.assertEqual(set(orchestrate.h.VERIFICATION_BLOCKAGE_OUTCOMES), orchestrate.VERIFICATION_BLOCKAGE_OUTCOMES)

    def test_the_script_entry_point_plans_a_feature_through_the_library(self):
        repository = HERE.parents[1]  # as CI runs it: from the repository root
        run = subprocess.run([sys.executable, str(HERE / 'orchestrate.py'), 'docs/agentic-sdd/examples/INV-LOW-001',
                              '--plan'], capture_output=True, text=True, env=dict(os.environ), cwd=repository,
                             check=False)
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertTrue(run.stdout.startswith('feature=INV-LOW-001 '), run.stdout)


if __name__ == '__main__':
    unittest.main()
