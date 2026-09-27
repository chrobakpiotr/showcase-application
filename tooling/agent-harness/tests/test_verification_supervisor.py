import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from verification.store import StoreError, VerificationStore
from verification.supervisor import VerificationSupervisor
import verification_command


class SupervisorTest(unittest.TestCase):
    def test_unqualified_backend_refuses_child_and_closes_refusal_journal(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            result, _ = VerificationSupervisor(store).execute(
                verification_command.run_command, worktree=root, family_id='family', attempt_id='attempt',
                gate_id='gate', command='python3 -V', cwd=root, run_dir=root / 'run',
                timeout_seconds=2, sandbox_mode='off')
            self.assertEqual('backend-not-v2-qualified', result.error)
            execution = next(store.executions.iterdir())
            self.assertTrue((execution / 'started.json').exists())
            self.assertTrue((execution / 'drained.json').exists())
            store.admit_repository_verification()

    def test_unresolved_or_corrupt_journal_blocks_other_families(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'control')
            journal = store.executions / 'x'
            journal.mkdir(parents=True)
            (journal / 'started.json').write_text('{"schema_version":2,"backend":"unknown"}')
            with self.assertRaises(StoreError):
                store.admit_repository_verification()


if __name__ == '__main__':
    unittest.main()
