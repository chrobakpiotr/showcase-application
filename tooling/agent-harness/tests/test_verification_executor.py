import dataclasses
import pathlib
import sys
import subprocess
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from verification.executor import ExecutionResult, GateExecution, execute_plan
from verification.model import Family
from verification.planner import build_plan
from verification.profile import load_profile
from verification.store import VerificationStore


class ExecutorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        subprocess.run(['git', 'config', 'user.email', 'test@example.invalid'], cwd=self.root, check=True)
        subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=self.root, check=True)
        (self.root / 'seed').write_text('seed')
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', 'commit', '-qm', 'seed'], cwd=self.root, check=True)
        self.base = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.root, text=True).strip()
        self.profile = load_profile({'schema_version': 1, 'gates': [{
            'id': 'unit', 'command': 'python3 -V', 'inputs': [], 'mandatory': True,
            'cacheable': False, 'sandbox': 'off',
        }]})
        self.family = Family('family-1', self.base, 'integration', self.profile.content_hash, 'b' * 40)

    def test_environment_requirement_is_blocked_and_identity_is_preserved(self):
        plan = build_plan(self.root, self.profile, self.family)
        store = VerificationStore(self.root, control_root=self.root / 'control')
        result = execute_plan(self.root, self.profile, plan, store=store, sandbox_mode='off')
        self.assertEqual('verification-blocked', result.outcome)
        self.assertEqual(self.profile.content_hash, result.profile_hash)
        self.assertEqual('unit', result.gates[0].gate_id)
        self.assertEqual('verification-blocked', result.gates[0].outcome)
        record = result.to_record()
        self.assertEqual('family-1', record['family_id'])
        self.assertEqual(self.profile.content_hash, record['profile_hash'])

    def test_execution_record_is_deterministic_for_same_input(self):
        first = ExecutionResult('family', 'a' * 64, 'PASS', (
            GateExecution('one', 'RUN', 'PASS', 'completed', 'b' * 64, 'c' * 64, 0),
        ), 1.0, 2.0)
        second = dataclasses.replace(first)
        self.assertEqual(first.to_record(), second.to_record())

    def test_failure_skips_remaining_required_gates_explicitly(self):
        p = load_profile({'schema_version': 1, 'gates': [
            {'id': 'one', 'command': 'python3 -c "raise SystemExit(4)"', 'inputs': [], 'mandatory': True, 'cacheable': False},
            {'id': 'two', 'command': 'python3 -V', 'inputs': [], 'mandatory': True, 'cacheable': False},
        ]})
        family = dataclasses.replace(self.family, profile_hash=p.content_hash)
        plan = build_plan(self.root, p, family)
        store = VerificationStore(self.root, control_root=self.root / 'control')
        result = execute_plan(self.root, p, plan, store=store, sandbox_mode='off')
        self.assertEqual('verification-blocked', result.outcome)  # v2 isolation fails closed before process launch
        self.assertEqual(['verification-blocked', 'NOT_RUN'], [g.outcome for g in result.gates])
        self.assertEqual('blocked-by-failure', result.gates[1].reason)


if __name__ == '__main__':
    unittest.main()
