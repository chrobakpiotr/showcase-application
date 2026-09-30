"""Exact script permission derives from real current accepted plan references."""
import argparse
import contextlib
import io
import json
import pathlib
import subprocess
import sys
import unittest
from unittest import mock

HARNESS = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HARNESS))
import harness as control
import test_harness as fixtures
import verification_command as bridge
from verification import authority
from verification.profile import load_profile

SCRIPTS = {
    'domain-mutation-threshold': './tooling/scripts/verify-domain-pitest.sh',
    'critical-postgres-regression': './tooling/scripts/verify-critical-postgres-tests.sh',
    'critical-rabbitmq-regression': './tooling/scripts/verify-critical-rabbitmq-tests.sh',
}


class ScriptCommandsTest(unittest.TestCase):
    def setUp(self):
        self.addCleanup(setattr, fixtures.harness, 'STATE_DIR', fixtures.harness.STATE_DIR)
        self.addCleanup(setattr, control, 'STATE_DIR', control.STATE_DIR)
        self.fixture = fixtures.HarnessTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.tearDown)
        self.root = self.fixture.root
        control.STATE_DIR = self.root / '.agent-state'
        self.feature = self.fixture.feature()
        task_path = self.feature / 'tasks.json'
        task_doc = json.loads(task_path.read_text())
        task_doc['tasks'][0]['verification'] = list(SCRIPTS.values())
        task_path.write_text(json.dumps(task_doc))
        doc = fixtures.harness.load_validated(self.feature)
        self.doc = doc
        fixtures.harness.write_packet(doc, doc['tasks'][0], self.feature)
        with contextlib.redirect_stdout(io.StringIO()):
            fixtures.harness.cmd_claim(argparse.Namespace(feature_dir=self.feature,
                task_id='T-001', owner='worker'))
        base = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        state = fixtures.harness.load_state(self.feature, doc)
        state['tasks']['T-001']['checkpoint_commit'] = base
        fixtures.harness.save_state(self.feature, state)
        for command in SCRIPTS.values():
            destination = self.root / command[2:]
            destination.parent.mkdir(parents=True, exist_ok=True)
            source = HARNESS.parents[1] / command[2:]
            destination.write_bytes(source.read_bytes())
            destination.chmod(0o755)
        self.record = authority.prepare_task_plan(self.root, self.feature, 'T-001', 1,
                                                 base, list(SCRIPTS.values()))
        self.profile = load_profile(HARNESS / 'verification-profiles/showcase.json')

    def reference(self, gate_id, *, occurrence=False):
        obligation = next(o for o in self.record['obligations']
                          if o['profile_gate_id'] == gate_id and o['occurrence'] is occurrence)
        unit = next(u for u in self.record['execution_units']
                    if obligation['obligation_id'] in u['obligation_ids'])
        return {'plan_id': self.record['plan_id'], 'obligation_id': obligation['obligation_id'],
                'unit_id': unit['unit_id']}

    def parse(self, command, context, *, cwd=None, worktree=None):
        return bridge.verification_argv(command, script_context=context,
            worktree=worktree or self.root, cwd=cwd or self.root)

    def test_all_real_profile_commands_use_shared_parser(self):
        for gate in self.profile.gates:
            with self.subTest(gate=gate.id):
                if gate.id in SCRIPTS:
                    self.assertEqual([gate.command], self.parse(gate.command, self.reference(gate.id)))
                    self.assertEqual([gate.command], self.parse(gate.command,
                        self.reference(gate.id, occurrence=True)))
                else:
                    self.assertTrue(bridge.verification_argv(gate.command))

    def test_missing_or_fabricated_context_and_altered_commands_reject(self):
        gate_id, script = next(iter(SCRIPTS.items()))
        reference = self.reference(gate_id)
        for context in (None, {'gate_id': gate_id}, {**reference, 'unit_id': 'unknown'},
                        {**reference, 'obligation_id': 'unknown'},
                        {**reference, 'plan_id': 'verification-plan-v2:sha256:' + 'a' * 64},
                        {**reference, 'gate_id': gate_id}):
            with self.subTest(context=context), self.assertRaises(bridge.CommandRejected):
                self.parse(script, context)
        for command in (script + ' --anything', 'sh ' + script, 'bash -c ' + script,
                        script.replace('./', '../', 1), str(self.root / script[2:]),
                        SCRIPTS['critical-postgres-regression']):
            with self.subTest(command=command), self.assertRaises(bridge.CommandRejected):
                self.parse(command, reference)
        with self.assertRaises(bridge.CommandRejected):
            self.parse(script, reference, cwd=self.root / 'tooling')
        with self.assertRaises(bridge.CommandRejected):
            self.parse(script, reference, worktree=self.root / 'tooling')

    def test_candidate_drift_and_superseded_authority_reject(self):
        gate_id, script = next(iter(SCRIPTS.items()))
        reference = self.reference(gate_id)
        path = self.root / script[2:]
        original = path.read_bytes()
        path.write_bytes(original + b'\n# changed\n')
        with self.assertRaises(bridge.CommandRejected):
            self.parse(script, reference)
        path.write_bytes(original)
        state = fixtures.harness.load_state(self.feature, self.doc)
        state.pop('verification_authority')
        fixtures.harness.save_state(self.feature, state)
        with self.assertRaises(bridge.CommandRejected):
            self.parse(script, reference)

    def test_script_and_ancestor_symlinks_invalidate_accepted_permission(self):
        for directory in (False, True):
            with self.subTest(directory=directory):
                gate_id, script = next(iter(SCRIPTS.items()))
                path = self.root / script[2:]
                target = path.parent if directory else path
                moved = target.with_name(target.name + '-real')
                target.rename(moved)
                target.symlink_to(moved, target_is_directory=directory)
                state = fixtures.harness.load_state(self.feature, self.doc)
                from verification.store import StoreError
                with self.assertRaisesRegex(StoreError, 'CANDIDATE_SEALING_UNSAFE_OBJECT'):
                    authority.prepare_task_plan(self.root, self.feature, 'T-001', 1,
                        state['tasks']['T-001']['checkpoint_commit'], list(SCRIPTS.values()))
                with self.assertRaises(bridge.CommandRejected):
                    self.parse(script, self.reference(gate_id))
                target.unlink()
                moved.rename(target)

    def test_parser_permission_cannot_enable_direct_script_launch(self):
        gate_id, script = next(iter(SCRIPTS.items()))
        reference = self.reference(gate_id)
        with mock.patch.object(bridge.verification_sandbox, 'build_plan') as sandbox:
            with self.assertRaisesRegex(bridge.CommandEnvironmentBlocked,
                                       'VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE'):
                bridge.CommandExecutionBackend().prepare(worktree=self.root, cwd=self.root,
                    run_dir=self.root / '.agent-runs' / 'probe', repository_id='fixture',
                    command=script, sandbox_mode='required', command_context=reference)
            sandbox.assert_not_called()


if __name__ == '__main__':
    unittest.main()
