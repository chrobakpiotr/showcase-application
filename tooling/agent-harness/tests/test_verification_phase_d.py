"""Phase-D failure-fence and one-shot retry contract tests.

These deliberately use isolated control roots. The fixture publishes a real
Phase-C execution terminal before asking the store to derive failure truth.
"""
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from verification.serialization import canonical
from verification.store import StoreError, VerificationStore, publish_create_once
from verification.supervisor import SupervisorState, VerificationSupervisor


class FailureGrantTest(unittest.TestCase):
    context = {
        'repository_id': 'repo-id', 'profile_hash': 'a' * 64,
        'gate_id': 'critical-gate', 'fingerprint': 'b' * 64,
        'policy_identity': 'policy-v1', 'backend_identity': 'test',
    }

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.store = VerificationStore(self.root, control_root=self.root / 'control')
        self.context = {**type(self).context, 'repository_id': self.store.repository_id}

    def publish_execution(self, execution_id='execution-1', *, result='FAIL', critical=True,
                          fingerprint=None, profile_hash=None, gate_id=None, policy='policy-v1',
                          predecessor_failure_id=None, consumption_id=None, retry_proof=None):
        identity = {'scheme': 'test-unit', 'locator': execution_id, 'backend_identity': 'test',
                    'policy_identity': policy}
        started = {
            'schema_version': 2, 'execution_id': execution_id,
            'repository_id': self.store.repository_id, 'backend': 'test',
            'backend_identity': 'test', 'policy_identity': policy,
            'worktree': str(self.root), 'launch_intent_hash': 'c' * 64,
            'started_at': 1.0, 'family_id': 'family', 'attempt_id': 'attempt',
            'gate_id': gate_id or self.context['gate_id'],
            'input_fingerprint': fingerprint or self.context['fingerprint'],
            'profile_hash': profile_hash or self.context['profile_hash'],
            'critical': critical, 'execution_identity': identity,
            'predecessor_failure_id': predecessor_failure_id, 'consumption_id': consumption_id,
            'retry_proof': retry_proof,
        }
        started_hash = publish_create_once(self.store.executions / execution_id / 'started.json', started)
        receipt = {
            'schema_version': 1, 'execution_id': execution_id,
            'repository_id': self.store.repository_id, 'started_hash': started_hash,
            'execution_identity': identity, 'backend_identity': 'test',
            'policy_identity': policy, 'command_identity': 'c' * 64,
            'exit_code': 1 if result == 'FAIL' else 0, 'timed_out': False,
            'cancelled': False, 'drainage': 'DRAINED', 'output_observation': 'CAPTURED',
            'stdout_hash': hashlib.sha256(b'').hexdigest(),
            'stderr_hash': hashlib.sha256(b'').hexdigest(), 'post_observation': {},
            'result': result, 'ended_at': 2.0,
        }
        self.store.publish_execution_terminal(receipt)
        return next(item for item in self.store.reconstruct_execution_terminals(rebuild=False)
                    if item['execution_id'] == execution_id)

    def failure(self, execution_id='execution-1', **kwargs):
        terminal = self.publish_execution(execution_id, **kwargs)
        return self.store.publish_critical_failure(terminal['execution_id'])

    def grant(self, failure_id, grant_id='grant-1', context=None):
        return self.store.issue_failure_grant(grant_id, failure_id=failure_id,
            context=context or self.context, issuer='test-operator', reason='isolated-retry-test')

    def fresh_process(self, source, *args):
        code = ("import pathlib,sys; sys.path.insert(0,sys.argv[1]); " + source)
        return subprocess.run([sys.executable, '-c', code,
            str(pathlib.Path(__file__).resolve().parents[1]), str(self.root),
            str(self.store.root), *args], check=True, text=True, capture_output=True).stdout.strip()

    def test_pd1_critical_fail_creates_durable_failure_receipt(self):
        failure = self.failure()
        self.assertTrue((self.store.root / 'failures' / f"{failure['failure_id']}.json").is_file())
        self.assertEqual(failure, self.store.publish_critical_failure(failure['execution_id']))

    def test_pd2_failure_reconstructs_without_projection_in_fresh_store(self):
        failure = self.failure()
        projection = self.store.root / 'state' / 'failure-index.json'
        projection.unlink(missing_ok=True)
        source = ("from verification.store import VerificationStore; "
            "s=VerificationStore(sys.argv[2],control_root=pathlib.Path(sys.argv[3])); "
            "c={'repository_id':s.repository_id,'profile_hash':" + repr(self.context['profile_hash']) +
            ",'gate_id':'critical-gate','fingerprint':" + repr(self.context['fingerprint']) +
            "}; print(s.current_failure(c)['failure_id'])")
        output = self.fresh_process(source)
        self.assertEqual(failure['failure_id'], output)
        self.assertFalse(projection.exists())

    def test_pd3_fence_blocks_relaunch_without_grant(self):
        self.failure()
        with self.assertRaisesRegex(StoreError, 'FAILURE_GRANT_REQUIRED'):
            self.store.consume_failure_grant(None, failure_id=None, context=self.context,
                                             execution_id='retry-1')

    def test_pd4_exact_grant_permits_one_retry(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        receipt = self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
            context=self.context, execution_id='retry-1')
        self.assertEqual('grant-1', receipt['grant_id'])

    def test_pd5_grant_consumption_is_before_launch_authority(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        consumed = self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
            context=self.context, execution_id='retry-1')
        self.assertTrue((self.store.consumptions / 'grant-1.json').is_file())
        self.assertEqual('retry-1', consumed['execution_id'])

    def test_pd6_crash_after_consume_burns_grant_in_fresh_store(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
            context=self.context, execution_id='retry-1')
        output = self.fresh_process("from verification.store import VerificationStore; "
            "s=VerificationStore(sys.argv[2],control_root=pathlib.Path(sys.argv[3])); "
            "print(s.grant_consumed('grant-1'))")
        self.assertEqual('True', output)
        with self.assertRaisesRegex(StoreError, 'FAILURE_GRANT_CONSUMED'):
            VerificationStore(self.root, control_root=self.root / 'control').consume_failure_grant(
                'grant-1', failure_id=failure['failure_id'],
                context=self.context, execution_id='retry-2')

    def test_pd7_concurrent_consume_has_one_winner(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        outcomes = []
        barrier = threading.Barrier(2)
        def consume(index):
            store = VerificationStore(self.root, control_root=self.root / 'control')
            barrier.wait()
            try:
                store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
                    context=self.context, execution_id=f'retry-{index}')
                outcomes.append('WIN')
            except StoreError:
                outcomes.append('LOSE')
        workers = [threading.Thread(target=consume, args=(i,)) for i in range(2)]
        for worker in workers: worker.start()
        for worker in workers: worker.join(5)
        self.assertCountEqual(['WIN', 'LOSE'], outcomes)

    def test_pd8_replay_cannot_reuse_grant(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
            context=self.context, execution_id='retry-1')
        with self.assertRaisesRegex(StoreError, 'FAILURE_GRANT_CONSUMED'):
            self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
                context=self.context, execution_id='retry-1')

    def test_pd9_f1_grant_cannot_authorize_f2(self):
        first = self.failure('execution-1')
        self.grant(first['failure_id'])
        second = self.failure('execution-2')
        with self.assertRaises(StoreError):
            self.store.consume_failure_grant('grant-1', failure_id=second['failure_id'],
                context=self.context, execution_id='retry-2')

    def test_pd10_wrong_repository_is_rejected(self):
        failure = self.failure()
        wrong = {**self.context, 'repository_id': 'other-repository'}
        with self.assertRaises(StoreError):
            self.grant(failure['failure_id'], context=wrong)
            self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
                context=self.context, execution_id='retry-1')

    def test_pd11_wrong_task_or_gate_is_rejected(self):
        failure = self.failure()
        wrong = {**self.context, 'gate_id': 'other-gate'}
        with self.assertRaises(StoreError):
            self.grant(failure['failure_id'], context=wrong)
            self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
                context=self.context, execution_id='retry-1')

    def test_pd12_wrong_input_fingerprint_is_rejected(self):
        failure = self.failure()
        with self.assertRaises(StoreError):
            self.grant(failure['failure_id'], context={**self.context, 'fingerprint': 'd' * 64})
            self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
                context=self.context, execution_id='retry-1')

    def test_pd13_wrong_policy_is_rejected(self):
        failure = self.failure()
        for key, value in (('policy_identity', 'new-policy'), ('backend_identity', 'new-backend')):
            with self.subTest(key=key), self.assertRaises(StoreError):
                self.grant(failure['failure_id'], context={**self.context, key: value})

    def test_pd14_consumed_state_reconstructs_without_projection(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
            context=self.context, execution_id='retry-1')
        projection = self.store.root / 'state' / 'grant-index.json'
        projection.unlink(missing_ok=True)
        output = self.fresh_process("from verification.store import VerificationStore; "
            "s=VerificationStore(sys.argv[2],control_root=pathlib.Path(sys.argv[3])); "
            "print(s.grant_consumed('grant-1'))")
        self.assertEqual('True', output)

    def test_pd15_retry_success_preserves_failure_history(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        consumed = self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
            context=self.context, execution_id='retry-1')
        self.publish_execution('retry-1', result='PASS', critical=False,
            predecessor_failure_id=failure['failure_id'], consumption_id=consumed['consumption_id'],
            retry_proof={'failure_id': failure['failure_id'], 'grant_id': 'grant-1',
                         'consumption_id': consumed['consumption_id'],
                         'grant_hash': consumed['grant_hash']})
        self.store.publish_failure_resolution(failure['failure_id'], 'retry-1', result='PASS')
        self.assertEqual(failure, self.store.failure_receipt(failure['failure_id']))
        self.assertIsNone(self.store.current_failure(self.context))
        self.grant(failure['failure_id'], grant_id='grant-2')
        with self.assertRaisesRegex(StoreError, 'FAILURE_GRANT_CONSUMED'):
            self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
                context=self.context, execution_id='retry-2')
        second_use = self.store.consume_failure_grant('grant-2', failure_id=failure['failure_id'],
            context=self.context, execution_id='retry-2')
        self.publish_execution('retry-2', result='PASS', critical=False,
            predecessor_failure_id=failure['failure_id'], consumption_id=second_use['consumption_id'],
            retry_proof={'failure_id': failure['failure_id'], 'grant_id': 'grant-2',
                'consumption_id': second_use['consumption_id'], 'grant_hash': second_use['grant_hash']})
        self.store.publish_failure_resolution(failure['failure_id'], 'retry-2', result='PASS')
        self.assertEqual(2, len(list(self.store.resolutions.glob('*.json'))))

    def test_pd16_retry_critical_fail_creates_f2(self):
        first = self.failure()
        self.grant(first['failure_id'])
        consumed = self.store.consume_failure_grant('grant-1', failure_id=first['failure_id'],
            context=self.context, execution_id='retry-1')
        second_terminal = self.publish_execution('retry-1', result='FAIL',
            predecessor_failure_id=first['failure_id'], consumption_id=consumed['consumption_id'],
            retry_proof={'failure_id': first['failure_id'], 'grant_id': 'grant-1',
                'consumption_id': consumed['consumption_id'], 'grant_hash': consumed['grant_hash']})
        second = self.store.publish_critical_failure(second_terminal['execution_id'])
        self.assertNotEqual(first['failure_id'], second['failure_id'])
        self.assertEqual(second['failure_id'], self.store.current_failure(self.context)['failure_id'])

    def test_pd17_f2_requires_new_grant(self):
        first = self.failure()
        self.grant(first['failure_id'])
        consumed = self.store.consume_failure_grant('grant-1', failure_id=first['failure_id'],
            context=self.context, execution_id='retry-1')
        second_terminal = self.publish_execution('retry-1', result='FAIL',
            predecessor_failure_id=first['failure_id'], consumption_id=consumed['consumption_id'],
            retry_proof={'failure_id': first['failure_id'], 'grant_id': 'grant-1',
                'consumption_id': consumed['consumption_id'], 'grant_hash': consumed['grant_hash']})
        second = self.store.publish_critical_failure(second_terminal['execution_id'])
        with self.assertRaisesRegex(StoreError, 'FAILURE_GRANT_REQUIRED'):
            self.store.consume_failure_grant(None, failure_id=second['failure_id'],
                context=self.context, execution_id='retry-2')

    def test_pd18_noncritical_failure_does_not_create_fence(self):
        self.publish_execution(critical=False)
        self.assertIsNone(self.store.current_failure(self.context))

    def test_pd19_missing_retry_proof_blocks_launch(self):
        failure = self.failure()
        self.publish_execution('execution-2', result='PASS', critical=False)
        with self.assertRaisesRegex(StoreError, 'RETRY_PROOF_MISSING'):
            self.store.validate_retry_proof('execution-2')

    def test_pd20_started_retry_without_consumption_proof_fails_closed(self):
        failure = self.failure()
        self.publish_execution('retry-1', result='PASS')
        with self.assertRaisesRegex(StoreError, 'RETRY_PROOF_MISSING'):
            self.store.validate_retry_proof('retry-1', required_failure_id=failure['failure_id'])
        started = {'schema_version': 2, 'execution_id': 'retry-incomplete',
            'repository_id': self.store.repository_id, 'backend': 'test',
            'worktree': str(self.root), 'launch_intent_hash': 'd' * 64, 'started_at': 3.0,
            'family_id': 'family-2', 'attempt_id': 'attempt-2', 'gate_id': 'critical-gate',
            'input_fingerprint': self.context['fingerprint'],
            'profile_hash': self.context['profile_hash'], 'policy_identity': self.context['policy_identity'],
            'backend_identity': self.context['backend_identity'], 'critical': True,
            'predecessor_failure_id': failure['failure_id'], 'consumption_id': 'missing-consumption',
            'retry_proof': None, 'execution_identity': {'locator': 'prepared'}}
        publish_create_once(self.store.executions / 'retry-incomplete' / 'started.json', started)
        recovery = VerificationSupervisor(self.store).recover()
        incomplete = next(item for item in recovery if item.execution_id == 'retry-incomplete')
        self.assertEqual(SupervisorState.UNCERTAIN, incomplete.state)
        self.assertEqual('RETRY_PROOF_MISSING', incomplete.reason_code)
        with self.assertRaisesRegex(StoreError, 'verification-owned'):
            self.store.admit_repository_verification()

    def test_pd21_failure_projection_rebuild(self):
        failure = self.failure()
        projection = self.store.root / 'state' / 'failure-index.json'
        projection.unlink(missing_ok=True)
        self.store.rebuild_failure_projections()
        self.assertTrue(projection.is_file())
        projected = json.loads(projection.read_text())
        self.assertEqual([failure['failure_id']], projected['active_fences'])
        self.assertEqual(failure['failure_id'], self.store.current_failure(self.context)['failure_id'])

    def test_pd22_grant_projection_rebuild(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        self.store.rebuild_failure_projections()
        self.assertTrue((self.store.root / 'state' / 'grant-index.json').is_file())

    def test_pd23_fresh_process_retry_recovery(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
            context=self.context, execution_id='retry-1')
        output = self.fresh_process("from verification.store import VerificationStore; "
            "s=VerificationStore(sys.argv[2],control_root=pathlib.Path(sys.argv[3])); "
            "print(s.retry_state(sys.argv[4]))", failure['failure_id'])
        self.assertEqual('CONSUMED', output)

    def test_pd24_no_duplicate_retry_payload_opportunity(self):
        failure = self.failure()
        self.grant(failure['failure_id'])
        self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
            context=self.context, execution_id='retry-1')
        with self.assertRaisesRegex(StoreError, 'FAILURE_GRANT_CONSUMED'):
            self.store.consume_failure_grant('grant-1', failure_id=failure['failure_id'],
                context=self.context, execution_id='retry-2')
        self.assertTrue((self.store.consumptions / 'grant-1.json').is_file())


if __name__ == '__main__':
    unittest.main()
