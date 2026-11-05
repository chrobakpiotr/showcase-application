import contextlib
import io
import json
import os
import sys
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

    def test_legacy_claim_under_another_epic_also_refuses(self):
        other = self.repo / '.agent-state/wayfinder/EPIC-001/claims/D-001'
        other.mkdir(parents=True)
        with locks.lifecycle_lock(self.repo):
            with self.assertRaisesRegex(RuntimeError, 'REQUIRE_MIGRATION: EPIC-001'):
                ops.refuse_legacy_claims(self.repo, 'EPIC-002')
        self.assertTrue(other.exists())
        self.assertFalse(ops.root(self.repo).exists())

    def test_legacy_claims_symlink_in_other_epic_is_unsafe(self):
        (self.repo / '.agent-state/wayfinder/EPIC-001').mkdir(parents=True)
        outside = self.repo / 'outside'
        outside.mkdir()
        (self.repo / '.agent-state/wayfinder/EPIC-001/claims').symlink_to(outside, target_is_directory=True)
        with locks.lifecycle_lock(self.repo):
            with self.assertRaisesRegex(RuntimeError, 'LEGACY_CLAIMS_UNSAFE'):
                ops.refuse_legacy_claims(self.repo, 'EPIC-002')

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
        from test_decision_map import decision_map, base_map
        from test_control_plane import cp
        old = self.repo / '.agent-state/wayfinder/EPIC-001/map.lock'
        old.parent.mkdir(parents=True)
        old.write_bytes(b'')
        before = old.stat()
        with mock.patch.object(decision_map, 'state_repo_root', return_value=self.repo), mock.patch.object(cp, 'REPO', self.repo):
            doc = base_map()
            decision_map.acquire_claim(doc, 'D-001', 'fixture-owner')
            self.assertEqual('fixture-owner', decision_map.active_claim(doc, 'D-001')['owner'])
            decision_map.release_claim(doc, 'D-001')
            self.assertIsNone(decision_map.active_claim(doc, 'D-001'))
            saved = cp.save_snapshot({'source': 'github', 'key': 'GH-1'})
            self.assertTrue(saved.is_relative_to(ops.root(self.repo)))
        self.assertEqual(before.st_ino, old.stat().st_ino)
        self.assertEqual(before.st_mode, old.stat().st_mode)
        self.assertEqual(b'', old.read_bytes())
        self.assertEqual([old], [p for p in (self.repo / '.agent-state').rglob('*') if p.is_file()])

    def test_state_under_the_old_directory_is_used_in_place_and_both_are_refused(self):
        from test_decision_map import decision_map
        with mock.patch.object(decision_map, 'state_repo_root', return_value=self.repo):
            new = ops.directory(self.repo, 'decision-maps', 'EPIC-001')
            self.assertEqual(new, decision_map.state_root('EPIC-001'))
            old = ops.directory(self.repo, 'wayfinder', 'EPIC-001')
            old.mkdir(parents=True)
            self.assertEqual(old, decision_map.state_root('EPIC-001'))
            with decision_map.locked_claim_state('EPIC-001'):
                self.assertTrue((old / 'map.lock').is_file())
            new.mkdir(parents=True)
            with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()) as err:
                decision_map.state_root('EPIC-001')
            self.assertIn('DECISION_MAP_STATE_AMBIGUOUS', err.getvalue())

    def test_migrate_and_downgrade_through_the_real_locks(self):
        from test_decision_map import decision_map, base_map
        old_dir = self.repo / 'docs/wayfinder/EPIC-001'
        old_dir.mkdir(parents=True)
        doc = base_map()
        (old_dir / 'wayfinder.json').write_text(json.dumps(doc), encoding='utf-8')
        with mock.patch.object(decision_map, 'state_repo_root', return_value=self.repo), \
                contextlib.redirect_stdout(io.StringIO()):
            with decision_map.locked_claim_state('EPIC-001'):
                pass
            decision_map.cmd_migrate(old_dir, False)
            new_dir = self.repo / 'docs/decision-maps/EPIC-001'
            self.assertTrue((new_dir / 'decision-map.json').is_file())
            self.assertEqual('EPIC-001', decision_map.load_map(new_dir)['epic'])
            self.assertTrue((ops.directory(self.repo, 'decision-maps', 'EPIC-001') / 'map.lock').is_file())
            decision_map.cmd_migrate(new_dir, True)
            self.assertTrue((old_dir / 'wayfinder.json').is_file())
            self.assertEqual('EPIC-001', decision_map.load_map(old_dir)['epic'])

    def test_the_old_script_name_forwards_with_one_deprecation_line(self):
        here = pathlib.Path(ops.__file__).resolve().parent
        example = here.parents[1] / 'docs/agentic-sdd/examples/SHIP-PLATFORM-001'
        env = {**os.environ, 'PYTHONPATH': os.pathsep.join(filter(None, [str(here), os.environ.get('PYTHONPATH')]))}
        old = subprocess.run([sys.executable, str(here / 'wayfinder.py'), 'validate', str(example)],
                             capture_output=True, text=True, env=env)
        new = subprocess.run([sys.executable, str(here / 'decision_map.py'), 'validate', str(example)],
                             capture_output=True, text=True, env=env)
        self.assertEqual((new.returncode, new.stdout), (old.returncode, old.stdout))
        self.assertEqual(1, old.stderr.count('DEPRECATED'), old.stderr)
        self.assertEqual(0, new.stderr.count('DEPRECATED'))

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
