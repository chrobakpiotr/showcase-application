"""AH5-14d: the lifecycle transitions reach the library's recovery steps through Showcase's namespace.

Runs inside Showcase through its shim, on the F1 fixtures (`test_f1_contract_binding`). Each library function is
replaced by a spy that calls the real one, so a pass shows that the transition's `ns.<name>` call went through the shim
delegate into `agent_harness.lifecycle_recovery`, not into a local copy.
"""
import argparse
import ast
import contextlib
import inspect
import io
from unittest import mock

import test_f1_contract_binding as f1

from agent_harness import lifecycle_recovery as lr

harness = f1.harness
MOVED = ('task_workspace_snapshot', 'restore_task_workspace', 'restore_attempt_authorization',
         'recover_stale_leases', 'cmd_recover_stale', 'cmd_recover_claim',
         'recover_prelaunch_authority', 'cmd_recover_verification', 'cmd_resume_verification',
         'cmd_restore_state', 'cmd_discard_prepared_snapshot')


class RecoveryBindingTest(f1.ContractBindingTest):
    def spy(self, name):
        patcher = mock.patch.object(lr, name, side_effect=getattr(lr, name), autospec=True)
        spy = patcher.start()
        self.addCleanup(patcher.stop)
        return spy

    def reopen(self):
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_reopen(argparse.Namespace(feature_dir=self.feature, task_id='T-001', reason='rework',
                                                  evidence=None))

    def completed(self):
        self.start()
        self.complete(self.evidence())
        self.assertEqual('completed', self.entry()['status'])

    def test_the_start_rollback_restores_the_grant_through_the_library(self):
        spy = self.spy('restore_attempt_authorization')
        self.start()
        harness.rollback_unexecuted_start(self.feature, self.doc, 'T-001', 'worker-a', 'aborted', attempt=1,
                                          claimed_at=self.entry()['claimed_at'])
        self.assertEqual(1, spy.call_count)
        self.assertIs(harness._NS, spy.call_args.kwargs['ns'])

    def test_reopen_snapshots_the_descendants_through_the_library(self):
        self.completed()
        spy = self.spy('task_workspace_snapshot')
        self.reopen()
        self.assertEqual({('TST-001', 'T-002'), ('TST-001', 'T-900')}, {c.args for c in spy.call_args_list})
        self.assertTrue(all(c.kwargs['ns'] is harness._NS for c in spy.call_args_list))
        self.assertEqual('failed', self.entry()['status'])

    def test_a_failed_prune_restores_the_workspace_through_the_library(self):
        self.completed()
        spy = self.spy('restore_task_workspace')
        with mock.patch.object(harness, 'prune_task_workspace', side_effect=RuntimeError('prune failed')), \
                self.assertRaisesRegex(RuntimeError, 'prune failed'):
            self.reopen()
        self.assertEqual(1, spy.call_count)  # the first attempted prune failed: only it is restored
        self.assertIs(harness._NS, spy.call_args.kwargs['ns'])
        self.assertEqual('completed', self.entry()['status'])  # the reopen did not happen

    def test_the_shim_keeps_only_delegates_and_none_recurses(self):
        tree = ast.parse(inspect.getsource(harness))
        defs = {node.name: node for node in tree.body if isinstance(node, ast.FunctionDef)}
        for name in MOVED:
            with self.subTest(name):
                body = [b for b in defs[name].body if not (isinstance(b, ast.Expr) and isinstance(b.value, ast.Constant))]
                if name in ('cmd_restore_state', 'cmd_discard_prepared_snapshot'):
                    self.assertEqual('refuse_unresolved_state_mutation(pathlib.Path(args.feature_dir).resolve())',
                                     ast.unparse(body.pop(0)))
                self.assertEqual(1, len(body))  # no local copy: one delegating return
                call = body[0].value
                self.assertEqual(f'_recovery.{name}', ast.unparse(call.func))
                self.assertEqual('_NS', ast.unparse(next(k.value for k in call.keywords if k.arg == 'ns')))
                library = ast.parse(inspect.getsource(getattr(lr, name)))
                self.assertNotIn(name, {n.attr for n in ast.walk(library) if isinstance(n, ast.Attribute)
                                        and isinstance(n.value, ast.Name) and n.value.id == 'ns'})
