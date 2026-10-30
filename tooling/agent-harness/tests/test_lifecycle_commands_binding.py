"""AH5-14e-1: Showcase's commands reach the library's `lifecycle_commands` through the shim's namespace.

Runs inside Showcase through its shim, on the F1 fixtures (`test_f1_contract_binding`), borrowing their setup without
re-running their tests. Library functions are replaced by spies that call the real ones, so a pass shows that a real
command run went through the shim delegate into the library, with the shim's own namespace, while Showcase's policy
bindings (here `reviewers`) are still the ones called.
"""
import argparse
import ast
import contextlib
import inspect
import io
import unittest
from unittest import mock

import test_f1_contract_binding as f1

from agent_harness import feature_access
from agent_harness import lifecycle_commands as lc

harness = f1.harness
MOVED = tuple(n.name for n in ast.parse(inspect.getsource(lc)).body if isinstance(n, ast.FunctionDef) and n.name != 'bind')


class LifecycleCommandsBindingTest(unittest.TestCase):
    setUp_harness = f1.ContractBindingTest.setUp_harness
    tearDown = f1.ContractBindingTest.tearDown
    feature_fixture = f1.ContractBindingTest.feature_fixture
    author_contract = f1.ContractBindingTest.author_contract
    remove_worktrees = f1.ContractBindingTest.remove_worktrees
    setUp = f1.ContractBindingTest.setUp

    def spy(self, name):
        patcher = mock.patch.object(lc, name, side_effect=getattr(lc, name), autospec=True)
        spy = patcher.start()
        self.addCleanup(patcher.stop)
        return spy

    def test_status_runs_through_the_library_with_the_shim_namespace(self):
        status, locked = self.spy('cmd_status'), self.spy('_cmd_status_locked')
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            harness.cmd_status(argparse.Namespace(feature_dir=self.feature, json=False))
        self.assertEqual(1, status.call_count)
        self.assertIs(harness._NS, status.call_args.kwargs['ns'])
        self.assertEqual(1, locked.call_count)  # the library's call of `ns._cmd_status_locked` came back through the shim
        self.assertIs(harness._NS, locked.call_args.kwargs['ns'])
        self.assertIn('T-001', out.getvalue())

    def test_the_reviewers_policy_stays_showcase_s(self):
        library = self.spy('cmd_reviewers')
        with mock.patch.object(harness, 'reviewers', wraps=harness.reviewers) as policy, \
                contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_reviewers(argparse.Namespace(feature_dir=self.feature, task_id='T-001'))
        self.assertEqual(1, library.call_count)
        self.assertGreaterEqual(policy.call_count, 1)  # Showcase's own, read through the live namespace at call time

    def test_the_shim_keeps_only_delegates_one_identity_definition_and_the_shared_transaction(self):
        tree = ast.parse(inspect.getsource(harness))
        defs = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in MOVED]
        self.assertEqual(sorted(MOVED), sorted(n.name for n in defs))  # each once: the unreachable first identity is gone
        for node in defs:
            with self.subTest(node.name):
                body = [b for b in node.body if not (isinstance(b, ast.Expr) and isinstance(b.value, ast.Constant))]
                self.assertEqual(1, len(body))
                self.assertEqual(f'_commands.{node.name}', ast.unparse(body[0].value.func))
                self.assertEqual('_NS', ast.unparse(next(k.value for k in body[0].value.keywords if k.arg == 'ns')))
        self.assertIs(feature_access._authority_transaction, harness._NS._authority_transaction)
        self.assertIs(harness._NS, harness._NS._NS)  # the rebound calls' namespace is the caller's own


if __name__ == '__main__':
    unittest.main()
