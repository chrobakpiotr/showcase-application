"""Adoption regression: run the copied candidate shim with Showcase's real registry and fixtures."""
import argparse
import contextlib
import io
import json
import os
import subprocess
import unittest
from unittest import mock

import test_harness as th

harness = th.harness


class UnknownStateMarkerTest(unittest.TestCase):
    setUp_harness = th.HarnessTest.setUp
    tearDown = th.HarnessTest.tearDown
    feature = th.HarnessTest.feature
    state_files = th.HarnessTest._runtime_state_files

    def setUp(self):
        self.setUp_harness()
        self.assertTrue(hasattr(harness, 'unresolved_state_marker'), 'adopted shim dropped the marker policy')
        patch = mock.patch.object(harness, 'REPO', self.root)
        patch.start()
        self.addCleanup(patch.stop)
        registry_dir = self.root.resolve() / 'protected-registry'
        registry_dir.mkdir(mode=0o700)
        registry_dir.chmod(0o700)
        self.registry = registry_dir / 'unknown-state.json'
        patch = mock.patch.object(harness.unknown_state_registry, '_REGISTRY_PATH_OVERRIDE', self.registry)
        patch.start()
        self.addCleanup(patch.stop)
        seed = self.feature(feature_id='DEMO-002')
        doc = harness.load_validated(seed)
        harness.save_state(seed, harness.initial_state(seed, doc))

    def refs(self):
        return subprocess.check_output(['git', 'for-each-ref', '--format=%(refname) %(objectname)',
                                        'refs/agent-state', 'refs/agent-state-snapshots',
                                        'refs/agent-state-prepared'], cwd=self.root)

    def write_registry(self, kind):
        if kind == 'absent':
            self.registry.unlink(missing_ok=True)
            return
        common = harness.state_snapshots.common_dir(self.root)
        document = {'schema_version': 1, 'repository_sha256': harness.sha256_bytes(os.fsencode(str(common))),
                    'features': sorted(harness.unknown_state_registry.UNKNOWN_STATE_FEATURES)}
        self.registry.write_text('{invalid' if kind == 'garbled' else json.dumps(document))
        self.registry.chmod(0o600)

    def test_adopted_shim_refuses_missing_history_without_changing_state_or_refs(self):
        for feature_id in ('SDD-001', 'AH5-04B-QUAL-001'):
            feature = self.feature(feature_id=feature_id)
            for registry in ('valid', 'absent', 'garbled'):
                self.write_registry(registry)
                self.assertEqual('consistent', harness.state_snapshots.current_repository_state_status(
                    self.root, harness.runtime_state_dir(feature)))
                before = self.state_files(), self.refs()
                commands = [
                    ['claim', str(feature), 'T-001', '--owner', 'worker'],
                    ['start', str(feature), 'T-001', '--owner', 'worker'],
                    ['recover-stale', str(feature)],
                    ['reopen', str(feature), 'T-001', '--reason', 'test'],
                    ['authorize-retry', str(feature), 'T-001', '--reason', 'test'],
                    ['restore-state', str(feature), '--expected-snapshot-commit', '0' * 40, '--operator', 'test'],
                    ['discard-prepared-snapshot', str(feature), '--expected-prepared-commit', '0' * 40,
                     '--operator', 'test'],
                ]
                for argv in commands:
                    with self.subTest(feature=feature_id, registry=registry, command=argv[0]):
                        args = harness.parser().parse_args(argv)
                        err = io.StringIO()
                        with contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()), \
                                self.assertRaises(SystemExit) as refused:
                            args.func(args)
                        self.assertEqual(2, refused.exception.code)
                        expected = ('STATE_RECOVERY_REGISTRY_INVALID' if registry == 'garbled'
                                    else 'STATE_RECOVERY_REQUIRED')
                        self.assertIn(expected, err.getvalue())
                        self.assertFalse(harness.state_path(feature).exists())
                        self.assertEqual(before, (self.state_files(), self.refs()))
                if registry != 'garbled':
                    out = io.StringIO()
                    with contextlib.redirect_stdout(out):
                        harness.cmd_status(argparse.Namespace(feature_dir=feature, json=True))
                    status = json.loads(out.getvalue())
                    self.assertEqual('recovery-required', status['lifecycle_status'])
                    self.assertEqual('unresolved-state-marker', status['recovery_reason'])
                    self.assertEqual(before, (self.state_files(), self.refs()))

    def test_restore_and_discard_refuse_before_entering_library_recovery(self):
        feature = self.feature(feature_id='SDD-001')
        for name in ('cmd_restore_state', 'cmd_discard_prepared_snapshot'):
            with self.subTest(command=name), mock.patch.object(harness._recovery, name) as delegate:
                with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                    getattr(harness, name)(argparse.Namespace(feature_dir=feature))
                delegate.assert_not_called()
