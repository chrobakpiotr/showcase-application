import pathlib
import subprocess
import tempfile
import unittest
from unittest import mock

import operational_state as ops
import repository_state_lock as locks


class OperationalStateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = pathlib.Path(self.tmp.name) / 'repo'
        self.repo.mkdir()
        subprocess.run(['git', 'init', '-q', str(self.repo)], check=True)

    def test_operational_writes_preserve_legacy_snapshot_and_lock_inode(self):
        old = self.repo / '.agent-state/wayfinder/EPIC-001/map.lock'
        old.parent.mkdir(parents=True)
        old.write_bytes(b'')
        before = old.stat()
        with locks.lifecycle_lock(self.repo):
            ops.refuse_legacy_claims(self.repo, 'EPIC-001')
            new = ops.ensure_directory(self.repo, 'wayfinder', 'EPIC-001')
            (new / 'map.lock').write_bytes(b'')
            cp = ops.ensure_directory(self.repo, 'control-plane')
            (cp / 'example.json').write_text('{}')
        self.assertFalse(new.is_relative_to(self.repo / '.agent-state'))
        self.assertEqual(before.st_ino, old.stat().st_ino)
        self.assertEqual(before.st_mode, old.stat().st_mode)
        self.assertEqual(b'', old.read_bytes())
        self.assertEqual([old], [p for p in (self.repo / '.agent-state').rglob('*') if p.is_file()])

    def test_legacy_claim_even_expired_is_not_silently_abandoned(self):
        old = self.repo / '.agent-state/wayfinder/EPIC-001/claims/D-001'
        old.mkdir(parents=True)
        with locks.lifecycle_lock(self.repo):
            with self.assertRaisesRegex(RuntimeError, 'REQUIRE_MIGRATION'):
                ops.refuse_legacy_claims(self.repo, 'EPIC-001')
        self.assertTrue(old.exists())
        self.assertFalse(ops.root(self.repo).exists())

    def test_symlink_and_unlocked_creation_refuse(self):
        with self.assertRaisesRegex(RuntimeError, 'LOCK_REQUIRED'):
            ops.ensure_directory(self.repo, 'control-plane')
        outside = self.repo / 'outside'
        outside.mkdir()
        ops.root(self.repo).symlink_to(outside, target_is_directory=True)
        with locks.lifecycle_lock(self.repo):
            with self.assertRaisesRegex(RuntimeError, 'UNSAFE_DIRECTORY'):
                ops.ensure_directory(self.repo, 'control-plane')
        self.assertEqual([], list(outside.iterdir()))

    def test_invalid_identifier_refuses(self):
        for value in ('..', '../escape', '/absolute', ''):
            with self.assertRaisesRegex(RuntimeError, 'INVALID_ID'):
                ops.directory(self.repo, 'wayfinder', value)

    def test_real_writers_leave_snapshot_tree_unchanged(self):
        from test_wayfinder import wayfinder, base_map
        from test_control_plane import cp
        old = self.repo / '.agent-state/wayfinder/EPIC-001/map.lock'
        old.parent.mkdir(parents=True)
        old.write_bytes(b'')
        before = old.stat()
        with mock.patch.object(wayfinder, 'state_repo_root', return_value=self.repo), mock.patch.object(cp, 'REPO', self.repo):
            doc = base_map()
            wayfinder.acquire_claim(doc, 'D-001', 'fixture-owner')
            self.assertEqual('fixture-owner', wayfinder.active_claim(doc, 'D-001')['owner'])
            wayfinder.release_claim(doc, 'D-001')
            self.assertIsNone(wayfinder.active_claim(doc, 'D-001'))
            saved = cp.save_snapshot({'source': 'github', 'key': 'GH-1'})
            self.assertTrue(saved.is_relative_to(ops.root(self.repo)))
        self.assertEqual(before.st_ino, old.stat().st_ino)
        self.assertEqual(before.st_mode, old.stat().st_mode)
        self.assertEqual(b'', old.read_bytes())
        self.assertEqual([old], [p for p in (self.repo / '.agent-state').rglob('*') if p.is_file()])

    def test_linked_worktree_shares_operational_root(self):
        subprocess.run(['git', '-C', str(self.repo), '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '--allow-empty', '-qm', 'fixture'], check=True)
        linked = pathlib.Path(self.tmp.name) / 'linked'
        subprocess.run(['git', '-C', str(self.repo), 'worktree', 'add', '--detach', str(linked)], check=True, capture_output=True)
        self.assertEqual(ops.root(self.repo).resolve(), ops.root(linked).resolve())
        old = self.repo / '.agent-state/wayfinder/EPIC-001/claims/D-001'
        old.mkdir(parents=True)
        with locks.lifecycle_lock(linked):
            with self.assertRaisesRegex(RuntimeError, 'PRIMARY_CHECKOUT_REQUIRED'):
                ops.refuse_legacy_claims(linked, 'EPIC-001')
            with self.assertRaisesRegex(RuntimeError, 'PRIMARY_CHECKOUT_REQUIRED'):
                ops.ensure_directory(linked, 'control-plane')
        self.assertFalse(ops.root(self.repo).exists())
