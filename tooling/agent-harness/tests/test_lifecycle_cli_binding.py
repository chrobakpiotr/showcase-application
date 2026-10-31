"""AH5-14e-2: Showcase's CLI reaches the library's `lifecycle_cli` through the shim's namespace.

Runs inside Showcase through its shim, on the F1 fixtures (`test_f1_contract_binding`), borrowing their setup without
re-running their tests. Library functions are replaced by spies that call the real ones, so a pass shows that
`parser()` and `doctor` run in the library with the shim's own namespace, that every parsed command dispatches to the
shim's delegate (so Showcase's policy and test patches still apply), and that the shim's `__main__` is the library's
dispatch.
"""
import argparse
import ast
import contextlib
import inspect
import io
import os
import pathlib
import subprocess
import sys
import unittest
from unittest import mock

import test_f1_contract_binding as f1

from agent_harness import lifecycle_cli as cli

harness = f1.harness
MODULE_PATH = pathlib.Path(inspect.getsourcefile(harness))


class LifecycleCliBindingTest(unittest.TestCase):
    setUp_harness = f1.ContractBindingTest.setUp_harness
    tearDown = f1.ContractBindingTest.tearDown
    feature_fixture = f1.ContractBindingTest.feature_fixture
    author_contract = f1.ContractBindingTest.author_contract
    remove_worktrees = f1.ContractBindingTest.remove_worktrees
    setUp = f1.ContractBindingTest.setUp

    def spy(self, name):
        patcher = mock.patch.object(cli, name, side_effect=getattr(cli, name), autospec=True)
        spy = patcher.start()
        self.addCleanup(patcher.stop)
        return spy

    def test_parser_runs_in_the_library_and_dispatches_to_the_shim_s_delegates(self):
        parser = self.spy('parser')
        built = harness.parser()
        self.assertEqual(1, parser.call_count)
        self.assertIs(harness._NS, parser.call_args.kwargs['ns'])
        commands = built._subparsers._group_actions[0].choices
        self.assertEqual(41, len(commands))
        for command, sub in commands.items():
            with self.subTest(command):
                self.assertIs(getattr(harness, 'cmd_' + command.replace('-', '_')), sub.get_default('func'))

    def test_a_parsed_status_command_runs_the_shim_delegate_and_sees_a_test_patch(self):
        args = harness.parser().parse_args(['status', str(self.feature), '--json'])
        self.assertIs(harness.cmd_status, args.func)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            args.func(args)
        self.assertIn('T-001', out.getvalue())
        seen = []
        with mock.patch.object(harness, 'cmd_status', side_effect=seen.append):
            args = harness.parser().parse_args(['status', str(self.feature)])
            args.func(args)
        self.assertEqual([args], seen)  # the handler is read from the shim at `parser()` time, as before the move

    def test_doctor_runs_in_the_library_with_the_shim_namespace_and_showcase_s_checks(self):
        doctor = self.spy('cmd_doctor')
        out = io.StringIO()
        with contextlib.redirect_stdout(out), mock.patch.object(harness, 'repo_root', return_value=self.root):
            try:
                harness.cmd_doctor(argparse.Namespace())
            except SystemExit:
                pass  # the fixture root lacks some required modules; the exit path is the source's, not under test here
        self.assertEqual(1, doctor.call_count)
        self.assertIs(harness._NS, doctor.call_args.kwargs['ns'])
        lines = out.getvalue().splitlines()
        self.assertIn('PASS: git', [line.split(' ->')[0] for line in lines])
        for profile in sorted({p for profiles in harness.RISK_TO_REVIEWERS.values() for p in profiles}):
            self.assertIn(f'profile:{profile}', ' '.join(lines))

    def test_the_shim_keeps_only_delegates_and_the_library_dispatch(self):
        tree = ast.parse(inspect.getsource(harness))
        defs = {n.name: n for n in tree.body if isinstance(n, ast.FunctionDef)}
        for name in ('parser', 'cmd_doctor'):
            with self.subTest(name):
                body = [b for b in defs[name].body
                        if not (isinstance(b, ast.Expr) and isinstance(b.value, ast.Constant))]
                self.assertEqual(1, len(body))
                self.assertEqual(f'_cli.{name}', ast.unparse(body[0].value.func))
                self.assertEqual('_NS', ast.unparse(next(k.value for k in body[0].value.keywords if k.arg == 'ns')))
        guard = [n for n in tree.body if isinstance(n, ast.If) and ast.unparse(n.test) == "__name__ == '__main__'"]
        self.assertEqual(1, len(guard))
        self.assertEqual(['_cli.main(ns=_NS)'], [ast.unparse(n) for n in guard[0].body])
        binds = [ast.unparse(n) for n in tree.body if isinstance(n, ast.Expr) and isinstance(n.value, ast.Call)]
        self.assertIn('_cli.bind(_NS)', binds)

    def test_the_script_entry_point_keeps_help_and_usage_exit_codes(self):
        env = dict(os.environ)  # the interpreter that runs this suite must also resolve the library
        help_run = subprocess.run([sys.executable, str(MODULE_PATH), '--help'], capture_output=True, text=True,
                                  env=env, cwd=self.root, check=False)
        self.assertEqual(0, help_run.returncode)
        with mock.patch.object(sys, 'argv', [MODULE_PATH.name]):  # argparse takes its prog from argv[0]
            expected = harness.parser().format_help()
        self.assertEqual(expected, help_run.stdout)
        for argv in ([], ['no-such-command'], ['start', 'd', 't']):
            with self.subTest(argv):
                run = subprocess.run([sys.executable, str(MODULE_PATH), *argv], capture_output=True, text=True,
                                     env=env, cwd=self.root, check=False)
                self.assertEqual(2, run.returncode)
                self.assertIn('usage:', run.stderr)


if __name__ == '__main__':
    unittest.main()
