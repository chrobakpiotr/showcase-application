import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from verification.store import StoreError, VerificationStore, publish_create_once, resolve_control_root


class StoreTest(unittest.TestCase):
    def test_create_once_is_idempotent_and_collision_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = pathlib.Path(temp) / 'immutable.json'
            first = publish_create_once(path, {'x': 1})
            self.assertEqual(first, publish_create_once(path, {'x': 1}))
            with self.assertRaisesRegex(StoreError, 'immutable-record-collision'):
                publish_create_once(path, {'x': 2})

    def test_unresolved_execution_is_repository_admission_barrier(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'store')
            started = store.executions / 'exec-1' / 'started.json'
            started.parent.mkdir(parents=True)
            started.write_text(json.dumps({'schema_version': 2, 'execution_id': 'exec-1',
                                           'repository_id': store.repository_id, 'backend': 'test'}))
            with self.assertRaisesRegex(StoreError, 'verification-owned'):
                store.admit_repository_verification()

    def test_admission_validates_identity_and_terminal_publication_is_create_once(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'store')
            started = store.executions / 'exec-1' / 'started.json'
            started.parent.mkdir(parents=True)
            started.write_text(json.dumps({'schema_version': 2, 'execution_id': 'other', 'backend': 'b'}))
            (started.parent / 'drained.json').write_text(json.dumps({
                'schema_version': 2, 'execution_id': 'exec-1', 'backend': 'b', 'status': 'drained'}))
            with self.assertRaisesRegex(StoreError, 'invalid-execution-history'):
                store.admit_repository_verification()

    def test_critical_failures_reconstruct_from_immutable_receipts(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'store')
            path = store.runs / 'f' / 'g' / 'terminal.json'
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({'repository_id':'r','profile_hash':'p','gate_id':'g',
                'pre_fingerprint':'f','status':'verification-failed','critical':True,'evidence_id':'e'}))
            self.assertEqual(['e'], [r['evidence_id'] for r in store.critical_failures(
                repository_id='r', profile_hash='p', gate_id='g', fingerprint='f')])

    def test_grant_consumption_is_one_shot_even_for_identical_replay(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'store')
            store.grants.mkdir(parents=True)
            (store.grants / 'grant.json').write_text(json.dumps({'failure_id':'failure','key':{'gate':'g'}}))
            key = {'gate':'g'}
            store.consume_grant('grant', failure_id='failure', key=key)
            with self.assertRaisesRegex(StoreError, 'grant-already-consumed'):
                store.consume_grant('grant', failure_id='failure', key=key)

    def test_malformed_execution_history_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            store = VerificationStore(root, control_root=root / 'store')
            path = store.executions / 'exec-1' / 'started.json'
            path.parent.mkdir(parents=True)
            path.write_text('{')
            with self.assertRaisesRegex(StoreError, 'invalid-execution-history'):
                store.admit_repository_verification()

    def test_primary_repository_authority_resolution(self):
        # The live repository is a linked worktree; both resolve to one primary store.
        root = pathlib.Path(__file__).resolve().parents[3]
        control, repository_id = resolve_control_root(root)
        self.assertEqual('.agent-runs/control/verification-v2', str(control.relative_to(control.parents[2])))
        self.assertEqual(64, len(repository_id))


if __name__ == '__main__':
    unittest.main()
