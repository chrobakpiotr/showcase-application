import argparse
import contextlib
import io
import json
import pathlib
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from verification.admission import AdmissionConflict, RepositoryAdmission
from verification.admission import process_start_token


class RepositoryAdmissionTest(unittest.TestCase):
    def test_launch_capability_is_single_use(self):
        from verification.admission import issue_launch_capability, validate_launch_capability
        capability = issue_launch_capability('consumption-1', 'reservation-1')
        self.assertTrue(validate_launch_capability(
            capability, consumption_id='consumption-1', reservation_id='reservation-1'))
        self.assertFalse(validate_launch_capability(
            capability, consumption_id='consumption-1', reservation_id='reservation-1'))

    def test_verification_reservation_binds_pid_and_process_start_token(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            active = admission.reserve_verification('exec-1', {})['active']
            self.assertEqual(os.getpid(), active['context']['owner_pid'])
            self.assertEqual(process_start_token(os.getpid()), active['context']['owner_start_time'])

    def test_recovery_accepts_pid_reuse_only_when_start_token_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            admission.reserve_verification('exec-1', {})
            with mock.patch('verification.admission.process_start_token',
                            return_value='different-process-instance'):
                self.assertTrue(admission.release_unstarted_if_owner_dead('exec-1'))
            self.assertIsNone(admission.active())

    def test_recovery_does_not_release_when_process_instance_is_still_alive_or_unknown(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            admission.reserve_verification('exec-1', {})
            with mock.patch('verification.admission.process_start_token',
                            return_value=process_start_token(os.getpid())):
                self.assertFalse(admission.release_unstarted_if_owner_dead('exec-1'))
            self.assertEqual('exec-1', admission.active()['id'])

    def test_verifier_reservation_and_mutation_claim_are_mutually_exclusive(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            admission.reserve_verification('exec-1', {'plan_id': 'plan-1'})
            with self.assertRaisesRegex(AdmissionConflict, 'verification-owned'):
                admission.reserve_mutation('mutation-1', {'operation': 'claim'})
            admission.release('exec-1')
            admission.reserve_mutation('mutation-1', {'operation': 'claim'})
            with self.assertRaisesRegex(AdmissionConflict, 'mutation-owned'):
                admission.reserve_verification('exec-2', {'plan_id': 'plan-2'})

    def test_crash_left_reservation_survives_restart_and_requires_exact_release(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            RepositoryAdmission(root, 'repo-1').reserve_verification('exec-1', {'plan_id': 'plan-1'})
            restarted = RepositoryAdmission(root, 'repo-1')
            with self.assertRaisesRegex(AdmissionConflict, 'verification-owned'):
                restarted.reserve_verification('exec-2', {'plan_id': 'plan-2'})
            with self.assertRaisesRegex(AdmissionConflict, 'reservation-mismatch'):
                restarted.release('exec-2')
            restarted.release('exec-1')

    def test_verification_waits_for_atomic_lifecycle_mutation_then_reserves(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            mutation_entered = threading.Event()
            allow_mutation_commit = threading.Event()
            verification_reserved = threading.Event()
            failures = []

            def mutate():
                try:
                    with admission.mutation():
                        mutation_entered.set()
                        self.assertTrue(allow_mutation_commit.wait(2))
                except BaseException as exc:
                    failures.append(exc)

            def verify():
                try:
                    self.assertTrue(mutation_entered.wait(2))
                    admission.reserve_verification('exec-1', {'plan_id': 'plan-1'})
                    verification_reserved.set()
                except BaseException as exc:
                    failures.append(exc)

            mutation_thread = threading.Thread(target=mutate)
            verification_thread = threading.Thread(target=verify)
            mutation_thread.start()
            verification_thread.start()
            self.assertTrue(mutation_entered.wait(2))
            self.assertFalse(verification_reserved.wait(0.05))
            allow_mutation_commit.set()
            mutation_thread.join(2)
            verification_thread.join(2)
            self.assertFalse(failures)
            self.assertTrue(verification_reserved.is_set())
            self.assertEqual('exec-1', admission.active()['id'])

    def test_corrupt_or_symlinked_admission_authority_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            admission = RepositoryAdmission(root, 'repo-1')
            admission.path.write_text('{broken')
            with self.assertRaisesRegex(AdmissionConflict, 'admission-authority-invalid'):
                admission.reserve_verification('exec-1', {})
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            outside = root / 'outside.json'
            outside.write_text('{"schema_version":1,"repository_id":"repo-1","generation":0,"active":null}')
            admission = RepositoryAdmission(root, 'repo-1')
            admission.path.symlink_to(outside)
            with self.assertRaisesRegex(AdmissionConflict, 'admission-authority-invalid'):
                admission.reserve_mutation('mutation-1', {})

    def test_verification_authority_validation_and_reservation_share_one_guard(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            with self.assertRaisesRegex(ValueError, 'stale-plan'):
                admission.reserve_verification('exec-1', {'plan_id': 'plan-1'},
                                               validate=lambda: (_ for _ in ()).throw(ValueError('stale-plan')))
            self.assertIsNone(admission.active())
            checked = []
            admission.reserve_verification('exec-1', {'plan_id': 'plan-1'},
                                           validate=lambda: checked.append('validated'))
            self.assertEqual(['validated'], checked)
            self.assertEqual('exec-1', admission.active()['id'])

    def test_only_matching_verifier_may_advance_its_claim_under_lifecycle_lock(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            admission.reserve_verification('exec-1', {'plan_id': 'plan-1'})
            with admission.mutation(allow_verification_id='exec-1'):
                pass
            with self.assertRaisesRegex(AdmissionConflict, 'verification-owned'):
                with admission.mutation(allow_verification_id='exec-2'):
                    self.fail('a different verifier advanced the active claim')


class PlanExecutionAdmissionTest(unittest.TestCase):
    def setUp(self):
        import harness as control_harness
        import test_harness as fixtures
        self.addCleanup(setattr, control_harness, 'STATE_DIR', control_harness.STATE_DIR)
        self.fixture = fixtures.HarnessTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.tearDown)
        control_harness.STATE_DIR = self.fixture.root / '.agent-state'
        self.feature = self.fixture.feature()
        self.doc = control_harness.load_validated(self.feature)
        control_harness.write_packet(self.doc, control_harness.task_index(self.doc)['T-001'], self.feature)
        with contextlib.redirect_stdout(io.StringIO()):
            control_harness.cmd_claim(argparse.Namespace(feature_dir=self.feature, task_id='T-001', owner='worker'))
        checkpoint = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.fixture.root, text=True).strip()
        from verification import authority
        self.plan = authority.prepare_task_plan(self.fixture.root, self.feature, 'T-001', 1,
            checkpoint, self.doc['tasks'][0]['verification'])

    def test_supervisor_consumes_exact_lifecycle_reservation_before_launch(self):
        import harness as control_harness
        from verification.store import StoreError, VerificationStore
        from verification.supervisor import VerificationSupervisor
        from verification.authority import resolve_accepted
        from test_verification_supervisor import LifecycleBackend

        accepted = resolve_accepted(self.fixture.root, self.plan['plan_id'])
        obligation = accepted['obligations'][0]
        unit = next(item for item in accepted['execution_units']
                    if obligation['obligation_id'] in item['obligation_ids'])
        store = VerificationStore(self.fixture.root)
        backend = LifecycleBackend(store, self.fixture.root.parent /
            f'controlled-unit-{self.fixture.root.name}')

        def authorize(repository_admission_id, _execution_id):
            doc = control_harness.load_validated(self.feature)
            current = control_harness.load_state(self.feature, doc)
            reserved = control_harness.admit_verification_execution(
                self.fixture.root, accepted['plan_id'], unit['unit_id'],
                expected_generation=accepted['lifecycle_generation'],
                expected_state_revision=current['state_revision'],
                repository_admission_id=repository_admission_id)
            consumed = control_harness.consume_verification_launch(
                self.fixture.root, accepted['plan_id'], unit['unit_id'],
                reservation_id=reserved['reservation']['reservation_id'],
                expected_generation=accepted['lifecycle_generation'],
                expected_state_revision=reserved['state_revision'],
                repository_admission_id=repository_admission_id)
            return {'reservation_id': reserved['reservation']['reservation_id'],
                'consumption_id': consumed['consumption']['consumption_id'],
                'admission_id': reserved['admission']['admission_id'],
                'admission_sha256': reserved['admission_sha256'],
                'reservation_transition_id': reserved['reservation']['transition_id'],
                'consumption_transition_id': consumed['consumption']['transition_id'],
                'plan_acceptance_transition_id': reserved['plan_acceptance_transition_id'],
                'plan_id': accepted['plan_id'], 'unit_id': unit['unit_id'],
                'obligation_ids': list(unit['obligation_ids']),
                'lifecycle_generation': accepted['lifecycle_generation'],
                'capability': consumed['capability']}

        VerificationSupervisor(store).execute(backend,
            worktree=self.fixture.root, family_id=accepted['family']['id'],
            attempt_id='admission-integration-test', gate_id=obligation['gate_id'],
            command=obligation['command'], cwd=self.fixture.root / obligation['cwd'],
            run_dir=store.root / 'controlled-run', timeout_seconds=2,
            sandbox_mode='required', profile_hash=accepted['profile_hash'],
            policy_checkpoint=accepted['policy_checkpoint'],
            candidate_identity=accepted['candidate_identity'],
            final_changed_surface_id=accepted['final_changed_surface_id'],
            plan_id=accepted['plan_id'], launch_authorizer=authorize)

        state = control_harness.load_state(self.feature, self.doc)
        authority = state['verification_authority']
        self.assertEqual('execution_terminal', authority['launch_reservations'][unit['unit_id']]['status'])
        self.assertEqual('execution_terminal', authority['launch_consumptions'][unit['unit_id']]['status'])
        marker = json.loads(next(store.executions.glob('*/launching.json')).read_text())
        terminal = json.loads(next(store.executions.glob('*/terminal.json')).read_text())
        revision_after_terminal = state['state_revision']
        replayed = control_harness.terminalize_verification_execution(
            self.fixture.root, terminal, repository_admission_id=terminal['execution_id'])
        self.assertEqual(terminal['receipt_hash'], replayed['terminal_receipt_hash'])
        self.assertEqual(revision_after_terminal,
                         control_harness.load_state(self.feature, self.doc)['state_revision'])
        self.assertEqual(marker['consumption_id'], terminal['launch_consumption_id'])
        self.assertEqual(unit['obligation_ids'], marker['obligation_ids'])
        forged = {key: value for key, value in terminal.items() if key != 'receipt_hash'}
        forged['launch_consumption_id'] = 'launch-consumption-v1:sha256:' + 'd' * 64
        with self.assertRaisesRegex(StoreError, 'invalid-execution-terminal'):
            store.publish_execution_terminal(forged)
        self.assertIn('launch', backend.events)

    def test_consumed_authority_without_launch_marker_recovers_as_safe_abort(self):
        import harness as control_harness
        from verification.authority import resolve_accepted
        from verification.store import StoreError, VerificationStore
        from verification.supervisor import VerificationSupervisor
        from test_verification_supervisor import LifecycleBackend

        accepted = resolve_accepted(self.fixture.root, self.plan['plan_id'])
        obligation = accepted['obligations'][0]
        unit = next(item for item in accepted['execution_units']
                    if obligation['obligation_id'] in item['obligation_ids'])
        store = VerificationStore(self.fixture.root)
        backend = LifecycleBackend(store, self.fixture.root.parent / 'safe-abort-unit')

        def authorize(repository_admission_id, _execution_id):
            current = control_harness.load_state(self.feature, self.doc)
            reserved = control_harness.admit_verification_execution(
                self.fixture.root, accepted['plan_id'], unit['unit_id'],
                expected_generation=accepted['lifecycle_generation'],
                expected_state_revision=current['state_revision'],
                repository_admission_id=repository_admission_id)
            consumed = control_harness.consume_verification_launch(
                self.fixture.root, accepted['plan_id'], unit['unit_id'],
                reservation_id=reserved['reservation']['reservation_id'],
                expected_generation=accepted['lifecycle_generation'],
                expected_state_revision=reserved['state_revision'],
                repository_admission_id=repository_admission_id)
            return {'reservation_id': reserved['reservation']['reservation_id'],
                'consumption_id': consumed['consumption']['consumption_id'],
                'admission_id': reserved['admission']['admission_id'],
                'admission_sha256': reserved['admission_sha256'],
                'reservation_transition_id': reserved['reservation']['transition_id'],
                'consumption_transition_id': consumed['consumption']['transition_id'],
                'plan_acceptance_transition_id': reserved['plan_acceptance_transition_id'],
                'plan_id': accepted['plan_id'], 'unit_id': unit['unit_id'],
                'obligation_ids': list(unit['obligation_ids']),
                'lifecycle_generation': accepted['lifecycle_generation'],
                'capability': consumed['capability']}

        supervisor = VerificationSupervisor(store)
        supervisor.inject_crash_at = 'after-launch-authority'
        with self.assertRaisesRegex(RuntimeError, 'injected-crash'):
            supervisor.execute(backend, worktree=self.fixture.root,
                family_id=accepted['family']['id'], attempt_id='safe-abort-attempt',
                gate_id=obligation['gate_id'], command=obligation['command'],
                cwd=self.fixture.root / obligation['cwd'], run_dir=store.root / 'safe-abort-run',
                timeout_seconds=2, sandbox_mode='required',
                profile_hash=accepted['profile_hash'], policy_checkpoint=accepted['policy_checkpoint'],
                candidate_identity=accepted['candidate_identity'],
                final_changed_surface_id=accepted['final_changed_surface_id'],
                plan_id=accepted['plan_id'], launch_authorizer=authorize)
        state = control_harness.load_state(self.feature, self.doc)
        self.assertEqual('launch_consumed', state['verification_authority']['launch_consumptions'][unit['unit_id']]['status'])
        recovery = VerificationSupervisor(VerificationStore(self.fixture.root))
        with mock.patch.object(recovery, '_terminalize_lifecycle_authority'):
            recovered = recovery.recover()
        self.assertEqual('ABORTED_PREPARED', recovered[0].state.value, recovered[0].reason_code)
        terminal = recovered[0].terminal
        self.assertEqual('ABORTED', terminal['result'])
        self.assertEqual(0, terminal['harness_invocation_upper_bound'])
        journal = store.executions / terminal['execution_id']
        self.assertTrue((journal / 'launch-authority.json').is_file())
        self.assertFalse((journal / 'launching.json').exists())
        self.assertNotIn('launch', backend.events)
        # The immutable receipt and absent launch marker are necessary but not
        # sufficient: only the supervisor's independently checked recovery
        # path may authorize the lifecycle safe-abort CAS.
        with self.assertRaisesRegex(StoreError, 'SAFE_PRELAUNCH_ABORT_PROOF_REQUIRED'):
            control_harness.terminalize_verification_execution(
                self.fixture.root, terminal, repository_admission_id=terminal['execution_id'])
        # Simulate a crash before durable drainage after terminal publication;
        # recovery must recreate the trusted proof from the journal state.
        (journal / 'drained.json').unlink()
        recovered = VerificationSupervisor(VerificationStore(self.fixture.root)).recover()
        self.assertEqual('TERMINAL_RECONSTRUCTED', recovered[0].reason_code)
        state = control_harness.load_state(self.feature, self.doc)
        authority_state = state['verification_authority']
        self.assertEqual('safe_prelaunch_abort', authority_state['launch_reservations'][unit['unit_id']]['status'])
        self.assertEqual('safe_prelaunch_abort', authority_state['launch_consumptions'][unit['unit_id']]['status'])
        revision = state['state_revision']
        control_harness.terminalize_verification_execution(
            self.fixture.root, terminal, repository_admission_id=terminal['execution_id'])
        self.assertEqual(revision, control_harness.load_state(self.feature, self.doc)['state_revision'])

    @unittest.skipUnless(hasattr(os, 'fork'), 'requires POSIX process termination semantics')
    def test_sigkill_after_consumption_before_authority_journal_is_recoverable(self):
        import signal
        import harness as control_harness
        from verification.authority import resolve_accepted
        from verification.store import VerificationStore
        from verification.supervisor import VerificationSupervisor
        from test_verification_supervisor import LifecycleBackend

        accepted = resolve_accepted(self.fixture.root, self.plan['plan_id'])
        obligation = accepted['obligations'][0]
        unit = next(item for item in accepted['execution_units']
                    if obligation['obligation_id'] in item['obligation_ids'])
        store = VerificationStore(self.fixture.root)
        child = os.fork()
        if child == 0:
            try:
                backend = LifecycleBackend(store, self.fixture.root.parent / 'sigkill-before-authority')
                def authorize(repository_admission_id, _execution_id):
                    current = control_harness.load_state(self.feature, self.doc)
                    reserved = control_harness.admit_verification_execution(
                        self.fixture.root, accepted['plan_id'], unit['unit_id'],
                        expected_generation=accepted['lifecycle_generation'],
                        expected_state_revision=current['state_revision'],
                        repository_admission_id=repository_admission_id)
                    control_harness.consume_verification_launch(
                        self.fixture.root, accepted['plan_id'], unit['unit_id'],
                        reservation_id=reserved['reservation']['reservation_id'],
                        expected_generation=accepted['lifecycle_generation'],
                        expected_state_revision=reserved['state_revision'],
                        repository_admission_id=repository_admission_id)
                    os.kill(os.getpid(), signal.SIGKILL)
                VerificationSupervisor(store).execute(backend,
                    worktree=self.fixture.root, family_id=accepted['family']['id'],
                    attempt_id='sigkill-before-authority', gate_id=obligation['gate_id'],
                    command=obligation['command'], cwd=self.fixture.root / obligation['cwd'],
                    run_dir=store.root / 'sigkill-run', timeout_seconds=2, sandbox_mode='required',
                    profile_hash=accepted['profile_hash'], policy_checkpoint=accepted['policy_checkpoint'],
                    candidate_identity=accepted['candidate_identity'],
                    final_changed_surface_id=accepted['final_changed_surface_id'],
                    plan_id=accepted['plan_id'], launch_authorizer=authorize)
            finally:
                os._exit(91)
        _pid, status = os.waitpid(child, 0)
        self.assertTrue(os.WIFSIGNALED(status))
        self.assertEqual(signal.SIGKILL, os.WTERMSIG(status))
        recovered = VerificationSupervisor(VerificationStore(self.fixture.root)).recover()
        self.assertEqual(1, len(recovered))
        self.assertEqual('ABORTED_PREPARED', recovered[0].state.value, recovered[0].reason_code)
        self.assertEqual(0, recovered[0].terminal['harness_invocation_upper_bound'])
        state = control_harness.load_state(self.feature, self.doc)
        reservation = state['verification_authority']['launch_reservations'][unit['unit_id']]
        self.assertEqual('safe_prelaunch_abort', reservation['status'])
        self.assertEqual('safe_prelaunch_abort',
                         state['verification_authority']['launch_consumptions'][unit['unit_id']]['status'])

    def test_admission_record_binds_exact_unit_members_and_origin(self):
        from verification.admission import build_plan_admission_record, validate_plan_admission_record
        from verification.store import VerificationStore
        unit = self.plan['execution_units'][0]
        record = build_plan_admission_record(self.plan, unit['unit_id'],
            repository_id=VerificationStore(self.fixture.root).repository_id,
            plan_acceptance_transition_id='37e1896c-ef43-4b13-964c-0d9f401b3258',
            admission_transition_id='e59b590b-42d9-45cd-91e2-8aeed0833f7a')
        self.assertEqual(unit['obligation_ids'], record['obligation_ids'])
        self.assertEqual(unit['required_origin'], record['execution_origin'])
        self.assertEqual(self.plan['plan_id'], record['plan_id'])
        self.assertEqual(self.plan['lifecycle_generation'], record['lifecycle_generation'])
        self.assertIs(validate_plan_admission_record(record, self.plan, unit), record)

    def test_task_admission_cannot_be_promoted_or_add_an_obligation(self):
        import copy
        from verification.admission import build_plan_admission_record, validate_plan_admission_record
        from verification.store import StoreError, VerificationStore
        unit = next(item for item in self.plan['execution_units'] if item['required_origin'] == 'task')
        record = build_plan_admission_record(self.plan, unit['unit_id'],
            repository_id=VerificationStore(self.fixture.root).repository_id,
            plan_acceptance_transition_id='37e1896c-ef43-4b13-964c-0d9f401b3258',
            admission_transition_id='e59b590b-42d9-45cd-91e2-8aeed0833f7a')
        promoted = copy.deepcopy(record); promoted['execution_origin'] = 'independent'
        with self.assertRaisesRegex(StoreError, 'invalid-execution-admission'):
            validate_plan_admission_record(promoted, self.plan, unit)
        expanded = copy.deepcopy(record); expanded['obligation_ids'].append(self.plan['obligations'][-1]['obligation_id'])
        with self.assertRaisesRegex(StoreError, 'invalid-execution-admission'):
            validate_plan_admission_record(expanded, self.plan, unit)

    def test_plan_admission_and_launch_reservation_commit_in_one_lifecycle_cas(self):
        import harness as control_harness
        from verification.store import VerificationStore
        from verification.admission import (RepositoryAdmission, validate_launch_capability)
        from verification.store import StoreError
        from verification.authority import resolve_accepted

        accepted = resolve_accepted(self.fixture.root, self.plan['plan_id'])
        units = accepted['execution_units']
        feature_state = control_harness.load_state(self.feature, self.doc)
        state_revision = feature_state['state_revision']
        bound = feature_state['verification_authority']
        store = VerificationStore(self.fixture.root)
        admissions = {}
        reservations = {}
        for unit in units:
            result = control_harness.admit_verification_execution(
                self.fixture.root, self.plan['plan_id'], unit['unit_id'],
                expected_generation=self.plan['lifecycle_generation'],
                expected_state_revision=state_revision)
            state_revision = result['state_revision']
            admissions[unit['unit_id']] = result['admission']
            reservations[unit['unit_id']] = result['reservation']
            admission = store.load_admission_record(result['admission']['admission_id'],
                expected_hash=result['admission_sha256'])
            reservation = result['reservation']
            self.assertEqual(admission['unit_id'], reservation['unit_id'])
            self.assertEqual(admission['obligation_ids'], reservation['obligation_ids'])
        final_state = control_harness.load_state(self.feature, self.doc)
        bound = final_state['verification_authority']
        self.assertEqual(set(admissions), set(bound['admissions']))
        self.assertEqual(set(reservations), set(bound['launch_reservations']))
        self.assertEqual(final_state['state_revision'], state_revision)
        self.assertEqual(final_state['state_revision'], 1 + len(units))
        transition = final_state['lifecycle_transitions'][-1]
        self.assertEqual('admission-reserved', transition['operation'])
        self.assertEqual(final_state['state_revision'], transition['after_state_revision'])

        repository_admission = RepositoryAdmission.for_repository(self.fixture.root)
        execution_id = 'controlled-execution-' + '1' * 16
        repository_admission.reserve_verification(execution_id, {'plan_id': self.plan['plan_id']})
        try:
            consumed = {}
            for unit in units:
                result = control_harness.consume_verification_launch(
                    self.fixture.root, self.plan['plan_id'], unit['unit_id'],
                    reservation_id=reservations[unit['unit_id']]['reservation_id'],
                    expected_generation=self.plan['lifecycle_generation'],
                    expected_state_revision=state_revision,
                    repository_admission_id=execution_id)
                state_revision = result['state_revision']
                consumed[unit['unit_id']] = result
                stored_consumption = store.load_launch_consumption_record(
                    result['consumption']['consumption_id'],
                    admission=admissions[unit['unit_id']],
                    reservation=reservations[unit['unit_id']],
                    expected_hash=result['consumption_sha256'])
                self.assertEqual(result['consumption'], stored_consumption)
                self.assertTrue(validate_launch_capability(result['capability'],
                    consumption_id=result['consumption']['consumption_id'],
                    reservation_id=reservations[unit['unit_id']]['reservation_id']))
                self.assertFalse(validate_launch_capability(result['capability'],
                    consumption_id=result['consumption']['consumption_id'],
                    reservation_id='another-reservation'))
                with self.assertRaisesRegex(StoreError, 'VERIFICATION_LAUNCH_ALREADY_CONSUMED'):
                    control_harness.consume_verification_launch(
                        self.fixture.root, self.plan['plan_id'], unit['unit_id'],
                        reservation_id=reservations[unit['unit_id']]['reservation_id'],
                        expected_generation=self.plan['lifecycle_generation'],
                        expected_state_revision=state_revision,
                        repository_admission_id=execution_id)
            final_state = control_harness.load_state(self.feature, self.doc)
            claim = final_state['verification_authority']['launch_consumptions']
            self.assertEqual(set(consumed), set(claim))
            self.assertEqual('launch-consumed', final_state['lifecycle_transitions'][-1]['operation'])
        finally:
            repository_admission.release(execution_id)


if __name__ == '__main__':
    unittest.main()
