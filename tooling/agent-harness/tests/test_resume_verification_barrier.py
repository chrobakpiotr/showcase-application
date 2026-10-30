"""AH5-14d: `cmd_resume_verification` refuses through the real repository barrier, before any lifecycle change.

Showcase's test `a92eab3c` (its `test_harness.py`), taken verbatim into the shim's tests so it ships with the
candidate: the real `assert_repository_verification_drained` (not patched) sees a live repository verification and
refuses with `VERIFICATION_OWNERSHIP_BLOCKED: verification-owned` (exit 6) before the lifecycle lock is entered; the
state file and every durable file under the fixture root are unchanged. Removing the barrier call is caught: the
lifecycle lock is then entered. It borrows `HarnessTest`'s fixtures without re-running that class.
"""
import argparse
import contextlib
import io
import json
import os
import unittest
from unittest import mock

import test_harness as th

harness = th.harness


class ResumeVerificationBarrierTest(unittest.TestCase):
    setUp = th.HarnessTest.setUp
    tearDown = th.HarnessTest.tearDown
    feature = th.HarnessTest.feature
    passing_builder_result = th.HarnessTest.passing_builder_result

    def test_verification_resume_refuses_live_repository_ownership_without_mutation(self):
        feature = self.feature()
        doc = harness.load_validated(feature)
        state = harness.load_state(feature, doc)
        entry = state['tasks']['T-001']
        entry.update({'status': 'escalated', 'attempts': 2,
                      'control_outcome': 'verification-blocked'})
        result_path = self.passing_builder_result('T-001')
        entry['last_failure_evidence'] = str(result_path)
        state['verification_authority'] = {'accepted_plan_id': 'plan-1', 'generation': 1}
        harness.save_state(feature, state)

        worktree = self.root / 'task-worktree'
        worktree.mkdir()
        store_module = harness._verification_store()
        store = store_module.VerificationStore(feature)
        store.root.mkdir(parents=True, exist_ok=True)
        # Prime the lock file before taking the snapshot; the tested barrier
        # must only inspect the existing repository-verification journal.
        with store_module.repository_lock(store.root):
            pass
        started = store.executions / 'resume-blocker'
        started.mkdir(parents=True)
        (started / 'started.json').write_text(json.dumps({
            'schema_version': 2, 'execution_id': 'resume-blocker',
            'repository_id': store.repository_id, 'backend': 'test',
            'worktree': str(worktree), 'launch_intent_hash': 'a' * 64,
            'started_at': 1.0, 'family_id': 'resume-family',
            'attempt_id': 'resume-attempt', 'gate_id': 'resume-gate',
        }), encoding='utf-8')

        def durable_snapshot():
            entries = []
            for path in self.root.rglob('*'):
                relative = path.relative_to(self.root)
                if '.git' in relative.parts:
                    continue
                name = relative.as_posix()
                if path.is_symlink():
                    entries.append((name, 'symlink', os.readlink(path)))
                elif path.is_dir():
                    entries.append((name, 'directory', None))
                elif path.is_file():
                    entries.append((name, 'file', path.read_bytes()))
            return tuple(sorted(entries))

        before_state = harness.state_path(feature).read_bytes()
        before_durable_data = durable_snapshot()
        accepted = {'plan_id': 'plan-1', 'feature_id': 'TST-001', 'task_id': 'T-001',
                    'task_attempt': 2, 'lifecycle_generation': 1}
        diagnostic = io.StringIO()
        entered_lifecycle_lock = []
        original_locked_state = harness.locked_state

        @contextlib.contextmanager
        def observe_lifecycle_lock(*args, **kwargs):
            entered_lifecycle_lock.append(True)
            with original_locked_state(*args, **kwargs) as current_state:
                yield current_state

        with mock.patch.object(harness, 'worktree_path', return_value=worktree), \
             mock.patch.object(harness, 'resolve_accepted_verification_plan', return_value=accepted), \
             mock.patch.dict(harness.__dict__, {'locked_state': observe_lifecycle_lock}), \
             contextlib.redirect_stderr(diagnostic):
            with self.assertRaises(SystemExit) as rejected:
                harness.cmd_resume_verification(argparse.Namespace(
                    feature_dir=feature, task_id='T-001', reason='precondition repaired',
                    by='operator@example.invalid'))

        self.assertEqual(6, rejected.exception.code)
        self.assertIn('VERIFICATION_OWNERSHIP_BLOCKED: verification-owned', diagnostic.getvalue())
        self.assertEqual([], entered_lifecycle_lock,
                         'the explicit repository barrier must refuse before lifecycle locking')
        self.assertEqual(before_state, harness.state_path(feature).read_bytes())
        self.assertEqual(before_durable_data, durable_snapshot())


if __name__ == '__main__':
    unittest.main()
