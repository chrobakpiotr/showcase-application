"""Rolling back an unexecuted start: the attempt stays in history, marked rolled back; the next start is a new attempt.

Runs inside Showcase through its shim, on the F1 fixtures (`test_f1_contract_binding`).
"""
import argparse
import contextlib
import io
import json
import unittest
from unittest import mock

import test_f1_contract_binding as f1
import test_f1_resume_recovery as rr

from agent_harness import feature_transitions as ft

harness = f1.harness
vc = f1.vc


class StartRollbackTest(f1.ContractBindingTest):
    def rollback(self, attempt=None, claimed_at=None):
        """`claimed_at` defaults to the running start's (these tests name the attempt; the claim-time identity has
        its own test)."""
        if claimed_at is None:
            claimed_at = self.entry().get('claimed_at', '')
        harness.rollback_unexecuted_start(self.feature, self.doc, 'T-001', 'worker-a', 'batch start aborted',
                                          attempt=attempt, claimed_at=claimed_at)

    def records(self):
        return [(item['attempt'], 'rolled_back' in item) for item in self.entry()['attempt_bindings']]

    def test_s_start_rollback_and_a_new_start(self):
        self.start()
        first = self.entry()['contract_binding']
        self.rollback(attempt=1)
        entry = self.entry()
        self.assertEqual((1, 'pending'), (entry['attempts'], entry['status']))
        self.assertEqual([(1, True)], self.records())
        self.assertEqual(0, ft.attempts_counted(entry))
        self.assertEqual(first, entry['contract_binding'])  # attempt 1's own, not rewritten
        self.assertEqual('batch start aborted', entry['attempt_bindings'][0]['rolled_back']['reason'])
        self.start()  # a new attempt, a new number, a new binding after full validation
        entry = self.entry()
        self.assertEqual((2, 'running'), (entry['attempts'], entry['status']))
        self.assertEqual([(1, True), (2, False)], self.records())
        self.assertEqual(vc.contract_binding(self.feature)[1], entry['contract_binding'])
        self.assertEqual(1, ft.attempts_counted(entry))

    def test_s_a_repeated_rollback_never_touches_the_newer_attempt(self):
        self.start()
        self.rollback(attempt=1)
        self.assertIn('T-001 is not running', self.refused(lambda: self.rollback(attempt=1)))
        self.start()
        before = self.state_bytes()
        err = self.refused(lambda: self.rollback(attempt=1))
        self.assertIn('START_ROLLBACK_REFUSED: attempt 1 of T-001 is not the running attempt (2)', err)
        self.assertEqual(before, self.state_bytes())
        self.assertEqual([(1, True), (2, False)], self.records())

    def test_s_an_attempt_whose_execution_began_is_refused_and_recovered_normally(self):
        self.start()
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_heartbeat(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='worker-a'))
        before = self.state_bytes()
        err = self.refused(lambda: self.rollback(attempt=1))
        self.assertIn('START_ROLLBACK_REFUSED: attempt 1 of T-001 has a heartbeat after its start', err)
        self.assertEqual(before, self.state_bytes())
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_fail(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='worker-a',
                                                reason='execution failed', evidence=None, escalate=False))
        entry = self.entry()
        self.assertEqual((1, 'failed'), (entry['attempts'], entry['status']))
        self.assertEqual([(1, False)], self.records())  # an executed attempt is not marked rolled back
        self.assertEqual(1, ft.attempts_counted(entry))

    def fail(self):
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_fail(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='worker-a',
                                                reason='attempt failed', evidence=None, escalate=False))

    def ready(self):
        return 'T-001' in harness.ready_ids(self.doc, harness.load_state(self.feature, self.doc), self.feature)

    def test_s_a_rolled_back_retry_keeps_its_grant_usable(self):
        for _ in range(3):  # max_rework_attempts 2: three executed attempts exhaust the budget
            self.start()
            self.fail()
        self.assertFalse(self.ready())
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                                                           reason='one more attempt', by='human:operator',
                                                           supersedes=None))
        self.start()  # attempt 4 consumes the grant
        self.rollback(attempt=4)
        self.assertEqual(3, ft.last_executed_attempt(self.entry()))
        self.assertTrue(self.ready())  # the restored grant still names the attempt being retried
        self.start()
        entry = self.entry()
        self.assertEqual((5, 'running'), (entry['attempts'], entry['status']))
        grant = entry['retry_authorizations'][-1]
        self.assertEqual((5, 3), (grant['consumed_attempt'], grant['binding']['expected_attempts']))

    def test_s_a_rolled_back_start_after_a_human_resolution_can_still_be_replanned(self):
        self.start()
        self.fail()
        state = harness.load_state(self.feature, self.doc)
        state['tasks']['T-001']['status'] = 'escalated'
        harness.save_state(self.feature, state)
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_human_resolve(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                                                         decision='Retry once.', decision_file=None,
                                                         by='human:operator'))
        self.start()
        self.rollback(attempt=2)
        self.assertEqual(1, ft.last_executed_attempt(self.entry()))
        harness.validate_latest_human_resolution(self.feature, self.doc, 'T-001', self.entry())  # replan allowed

    def authorize(self):
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_authorize_retry(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                                                           reason='one more attempt', by='human:operator',
                                                           supersedes=None))

    def exhaust(self):
        for _ in range(3):
            self.start()
            self.fail()

    def test_s_claim_after_a_rolled_back_retry_uses_the_grant(self):
        self.exhaust()
        self.authorize()
        self.start()
        self.rollback(attempt=4)
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='worker-a'))
        self.assertEqual((5, 'running'), (self.entry()['attempts'], self.entry()['status']))

    def test_s_a_second_grant_is_refused_while_the_restored_one_is_valid(self):
        self.exhaust()
        self.authorize()
        self.start()
        self.rollback(attempt=4)
        self.assertIn('a valid authorization already exists', self.refused(self.authorize))

    def test_s_a_rolled_back_attempt_leaves_the_budget_unspent(self):
        for _ in range(2):
            self.start()
            self.fail()
        self.start()
        self.rollback(attempt=3)
        self.assertIn('exhausted normal attempts', self.refused(self.authorize))
        self.assertTrue(self.ready())

    def test_s_a_grant_goes_stale_once_its_retry_executed(self):
        self.exhaust()
        self.authorize()
        self.start()
        self.fail()  # attempt 4 executed and failed: its grant is spent
        self.assertFalse(self.ready())
        self.authorize()
        self.assertEqual(4, self.entry()['retry_authorizations'][-1]['binding']['expected_attempts'])
        self.start()
        self.rollback(attempt=5)
        self.assertTrue(self.ready())
        self.start()
        self.fail()  # attempt 6 executed
        self.assertFalse(self.ready())

    def test_s_a_resolution_goes_stale_once_a_later_attempt_executed(self):
        self.start()
        self.fail()
        state = harness.load_state(self.feature, self.doc)
        state['tasks']['T-001']['status'] = 'escalated'
        harness.save_state(self.feature, state)
        with contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_human_resolve(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                                                         decision='Retry once.', decision_file=None,
                                                         by='human:operator'))
        self.start()
        self.rollback(attempt=2)
        self.start()
        self.fail()  # attempt 3 executed
        err = self.refused(lambda: harness.validate_latest_human_resolution(self.feature, self.doc, 'T-001',
                                                                            self.entry()))
        self.assertIn('TASK_REPLAN_NOT_ALLOWED', err)

    def test_s_reopen_counts_only_executed_attempts(self):
        for attempt in (1, 2):
            self.start()
            self.rollback(attempt=attempt)
        self.start()
        self.complete(self.evidence())
        with mock.patch.object(harness, 'prune_task_workspace'), contextlib.redirect_stdout(io.StringIO()):
            harness.cmd_reopen(argparse.Namespace(feature_dir=self.feature, task_id='T-001', reason='rework',
                                                  evidence=None))
        self.assertEqual(1, ft.attempts_counted(self.entry()))
        self.assertNotEqual('escalated', self.entry()['status'])  # one budgeted attempt of three

    def test_s_repeated_unexecuted_starts_never_need_a_grant(self):
        for attempt in range(1, 6):  # more unexecuted starts than the whole budget (3)
            self.start()
            self.rollback(attempt=attempt)
        self.assertTrue(self.ready())
        self.start()  # no retry grant needed
        entry = self.entry()
        self.assertEqual((6, 'running', 1), (entry['attempts'], entry['status'], ft.attempts_counted(entry)))
        self.assertEqual([(n, n < 6) for n in range(1, 7)], self.records())  # history kept, numbers unique
        self.assertNotIn('retry_authorizations', entry)

    def test_s_a_ledger_damaged_by_an_old_rollback_refuses_start_without_writing(self):
        self.start()
        state = harness.load_state(self.feature, self.doc)
        entry = state['tasks']['T-001']
        entry.update({'status': 'pending', 'attempts': 0})  # what a 0.18.0/0.19.0 rollback left
        for key in ('owner', 'claimed_at', 'heartbeat_at', 'lease_expires_at', 'worktree', 'start_origin_status'):
            entry.pop(key, None)
        harness.save_state(self.feature, state)
        before = self.state_bytes()
        self.assertIn('ATTEMPT_LEDGER_DAMAGED: attempt 1 is recorded above the attempt counter 0',
                      self.refused(self.start))
        self.assertEqual(before, self.state_bytes())

    def test_s_a_failed_later_start_in_a_batch_rolls_back_the_attempt_cmd_start_returned(self):
        """The orchestrator's second case: preflight passed, a later start in the batch failed, so the earlier
        unexecuted start is undone by the identity its own `cmd_start` returned (not by reading state afterwards)."""
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            started = harness.cmd_start(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                                                           owner='worker-a'))
        self.assertEqual(1, started['attempt'])
        self.assertEqual(started, json.loads(out.getvalue()))  # the CLI prints the same identity
        self.assertEqual(self.entry()['claimed_at'], started['claimed_at'])
        self.rollback(attempt=started['attempt'], claimed_at=started['claimed_at'])  # a later start failed
        with contextlib.redirect_stdout(io.StringIO()):
            retried = harness.cmd_start(argparse.Namespace(feature_dir=self.feature, task_id='T-001',
                                                           owner='worker-a'))
        self.assertEqual(2, retried['attempt'])
        self.assertEqual((2, 'running', 1), (self.entry()['attempts'], self.entry()['status'],
                                             ft.attempts_counted(self.entry())))
        self.assertIn('is not the running attempt (2)', self.refused(
            lambda: self.rollback(attempt=started['attempt'], claimed_at=started['claimed_at'])))

    def test_s_a_preflight_refusal_creates_no_attempt_to_roll_back(self):
        """The orchestrator's first case: a start refused before its transaction creates no attempt."""
        before = self.state_bytes()
        with mock.patch.object(vc, 'REPO', self.root.parent):  # any refusal before the start's transaction
            self.refused(self.start)
        self.assertEqual(before, self.state_bytes())
        self.assertEqual(0, int(self.entry().get('attempts', 0)))

    def test_s_a_dirty_worktree_is_refused(self):
        self.start()
        (harness.worktree_path('TST-001', 'T-001') / 'a.txt').write_text('work in progress\n')
        before = self.state_bytes()
        self.assertIn('cannot roll back unexecuted start for dirty worktree', self.refused(lambda: self.rollback(attempt=1)))
        self.assertEqual(before, self.state_bytes())


class ResumeStartRollbackTest(rr.ResumeRecoveryTest):
    """A verification resume continues the same attempt number, so a start is identified by number and claim time."""

    def resume_start(self):
        state = harness.load_state(self.feature, self.doc)
        accepted = {'plan_id': 'plan-1', 'feature_id': 'TST-001', 'task_id': 'T-001', 'task_attempt': 1,
                    'lifecycle_generation': state.get('feature_generation', 1)}
        with mock.patch.object(harness, 'resolve_accepted_verification_plan', return_value=accepted), \
                contextlib.redirect_stdout(io.StringIO()):
            return harness.cmd_start(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='worker-a'))

    def undo(self, started):
        harness.rollback_unexecuted_start(self.feature, self.doc, 'T-001', 'worker-a', 'batch start aborted',
                                          attempt=started['attempt'], claimed_at=started['claimed_at'])

    def test_s_a_repeated_rollback_never_undoes_a_newer_resume_start(self):
        self.pending_resume()
        first = self.resume_start()
        self.assertEqual((1, 'running'), (first['attempt'], self.entry()['status']))
        self.assertIn('verification_resume_active', self.entry())
        self.undo(first)
        self.assertEqual(('failed', 1), (self.entry()['status'], self.entry()['attempts']))
        self.assertIn('verification_resume_pending', self.entry())
        second = self.resume_start()
        self.assertEqual(first['attempt'], second['attempt'])  # same number: a resume continues the attempt
        self.assertNotEqual(first['claimed_at'], second['claimed_at'])
        before = self.state_bytes()
        err = self.refused(lambda: self.undo(first))
        self.assertIn('is not the running start of T-001', err)
        self.assertEqual(before, self.state_bytes())
        self.undo(second)  # the newer start's own identity still works
        self.assertEqual('failed', self.entry()['status'])


def load_tests(loader, tests, pattern):
    """Only this module's own tests; the F1 tests run in `test_f1_contract_binding`."""
    return loader.loadTestsFromNames([f'{__name__}.{case.__name__}.{name}'
                                      for case in (StartRollbackTest, ResumeStartRollbackTest)
                                      for name in loader.getTestCaseNames(case) if name.startswith('test_s_')])


if __name__ == '__main__':
    unittest.main()
