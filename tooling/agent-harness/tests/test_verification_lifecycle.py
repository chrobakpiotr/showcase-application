"""End-to-end checks for plan admission, launch and lifecycle terminalization."""
from __future__ import annotations

import argparse
import contextlib
import io
import pathlib
import subprocess
import sys
import unittest
from unittest import mock

TESTS = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(TESTS))
sys.path.insert(0, str(TESTS.parent))

import test_harness
import test_telemetry
import test_verification_supervisor
import harness as control_harness
from verification.authority import prepare_task_plan, resolve_execution
from verification.executor import execute_plan
from verification.store import StoreError, VerificationStore
from verification.supervisor import VerificationSupervisor


class VerificationLifecycleTest(unittest.TestCase):
    def setUp(self):
        self.original_cwd = pathlib.Path.cwd()
        self.original_state_dir = control_harness.STATE_DIR
        self.fixture = test_harness.HarnessTest()
        self.fixture.setUp()
        self.original_fixture_state_dir = test_harness.harness.STATE_DIR
        control_harness.STATE_DIR = self.fixture.root / '.agent-state'
        test_harness.harness.STATE_DIR = control_harness.STATE_DIR

        # The task fixture's accepted verification command resolves through a
        # committed executable just as the production resolver requires.
        gradlew = self.fixture.root / 'gradlew'
        gradlew.write_text('#!/bin/sh\nexit 0\n', encoding='utf-8')
        gradlew.chmod(0o700)
        subprocess.run(['git', 'add', 'gradlew'], cwd=self.fixture.root, check=True)
        subprocess.run(['git', 'commit', '-qm', 'add bound test executable'],
                       cwd=self.fixture.root, check=True)
        self.feature = self.fixture.feature()
        self.doc = control_harness.load_validated(self.feature)
        control_harness.write_packet(
            self.doc, control_harness.task_index(self.doc)['T-001'], self.feature)
        with contextlib.redirect_stdout(io.StringIO()):
            control_harness.cmd_claim(argparse.Namespace(
                feature_dir=self.feature, task_id='T-001', owner='lifecycle-test'))
        checkpoint = subprocess.check_output(
            ['git', 'rev-parse', 'HEAD'], cwd=self.fixture.root, text=True).strip()
        self.accepted = prepare_task_plan(
            self.fixture.root, self.feature, 'T-001', 1, checkpoint,
            self.doc['tasks'][0]['verification'])
        self.addCleanup(self._cleanup)

    def _cleanup(self):
        control_harness.STATE_DIR = self.original_state_dir
        test_harness.harness.STATE_DIR = self.original_fixture_state_dir
        self.fixture.tearDown()
        if pathlib.Path.cwd() != self.original_cwd:
            import os
            os.chdir(self.original_cwd)

    def test_executor_uses_real_admission_consumption_and_terminal_cas(self):
        accepted, profile, plan, units = resolve_execution(
            self.fixture.root, self.accepted['plan_id'])
        self.assertEqual(self.accepted, accepted)
        self.assertTrue(units)

        store = VerificationStore(self.fixture.root)
        backend_root = self.fixture.root.parent / 'lifecycle-controlled-backend'
        backend = test_verification_supervisor.LifecycleBackend(store, backend_root)
        with mock.patch('verification.executor.CommandExecutionBackend', return_value=backend):
            result = execute_plan(self.fixture.root, profile, plan, store=store,
                attempt_id='lifecycle-end-to-end', sandbox_mode='required',
                authority_context=accepted)

        self.assertEqual('PASS', result.outcome, result.to_record())
        self.assertEqual(len(plan.decisions), backend.events.count('launch'))
        current = control_harness.load_state(self.feature, self.doc)
        authority = current['verification_authority']
        self.assertEqual(len(units), len(authority['admissions']))
        self.assertEqual(len(units), len(authority['launch_consumptions']))
        self.assertTrue(all(item['status'] == 'execution_terminal'
                            for item in authority['launch_reservations'].values()))
        self.assertTrue(all(item['status'] == 'execution_terminal'
                            for item in authority['launch_consumptions'].values()))
        terminals = VerificationStore(self.fixture.root).reconstruct_execution_terminals(
            rebuild=False)
        self.assertEqual(len(units), len(terminals))
        self.assertTrue(all(item.get('launch_consumption_id') for item in terminals))

    def _reserve_first_unit(self):
        unit = self.accepted['execution_units'][0]
        state = control_harness.load_state(self.feature, self.doc)
        admitted = control_harness.admit_verification_execution(
            self.fixture.root, self.accepted['plan_id'], unit['unit_id'],
            expected_generation=state.get('feature_generation', 1),
            expected_state_revision=state.get('state_revision', 0))
        return unit, admitted

    def _args_for_lifecycle_command(self, command, live_state):
        if command == 'replan-task':
            active = control_harness.resolve_active_packet(self.feature, self.doc, 'T-001')
            proposal = self.feature / 'proposed-replan.json'
            proposed = dict(control_harness.active_task_contract(
                self.feature, self.doc, 'T-001'), objective='Replanned while verification is active')
            proposal.write_text(__import__('json').dumps(proposed), encoding='utf-8')
            return control_harness.cmd_replan_task, argparse.Namespace(
                feature_dir=self.feature, task_id='T-001', expected_status='running',
                expected_attempts=1, expected_active_revision=active['revision_id'],
                expected_contract_sha256=active['contract_sha256'],
                expected_feature_generation=1, proposed_task_file=str(proposal),
                reason='barrier regression test', by='lifecycle-test', checkpoint=None)
        if command == 'reconcile-feature':
            (self.feature / 'plan.md').write_text('# changed after accepted plan\n', encoding='utf-8')
            return control_harness.cmd_reconcile_feature, argparse.Namespace(
                feature_dir=self.feature, expected_generation=1,
                reason='barrier regression test', by='lifecycle-test')
        state = control_harness.load_state(self.feature, self.doc)
        state['tasks']['T-001']['status'] = 'completed'
        control_harness.save_state(self.feature, state)
        return control_harness.cmd_reopen, argparse.Namespace(
            feature_dir=self.feature, task_id='T-001', reason='barrier regression test', evidence=None)

    def test_live_reservation_blocks_every_lifecycle_replan_entrypoint(self):
        # Use actual admitted plans and persisted .agent-state. Later reservation
        # phases are durable authority variants after the real reservation CAS.
        unit, _admitted = self._reserve_first_unit()
        for live_state in ('launch_reserved', 'launch_consumed', 'execution_started'):
            with self.subTest(live_state=live_state):
                state = control_harness.load_state(self.feature, self.doc)
                authority = state['verification_authority']
                reservations = dict(authority['launch_reservations'])
                reservation = reservations[unit['unit_id']]
                reservations[unit['unit_id']] = {**reservation, 'status': live_state}
                state['verification_authority'] = dict(authority, launch_reservations=reservations)
                control_harness.save_state(self.feature, state)
                for command in ('replan-task', 'reopen', 'reconcile-feature'):
                    plan_path = self.feature / 'plan.md'
                    original_plan = plan_path.read_bytes()
                    operation, args = self._args_for_lifecycle_command(command, live_state)
                    diagnostic = io.StringIO()
                    try:
                        with self.subTest(command=command):
                            try:
                                with contextlib.redirect_stderr(diagnostic):
                                    operation(args)
                            except RuntimeError as exc:
                                self.assertEqual('verification-owned', str(exc))
                            except SystemExit as exc:
                                self.assertEqual(6, exc.code)
                                self.assertIn('VERIFICATION_OWNERSHIP_BLOCKED: verification-owned',
                                              diagnostic.getvalue())
                            else:
                                self.fail(f'{command} proceeded during {live_state}')
                    finally:
                        plan_path.write_bytes(original_plan)
                    if command == 'reopen':
                        state = control_harness.load_state(self.feature, self.doc)
                        state['tasks']['T-001']['status'] = 'running'
                        control_harness.save_state(self.feature, state)

    def test_resolver_refuses_plan_not_named_by_current_authority(self):
        state = control_harness.load_state(self.feature, self.doc)
        authority = dict(state['verification_authority'])
        authority['accepted_plan_id'] = 'superseded-plan'
        state['verification_authority'] = authority
        control_harness.save_state(self.feature, state)

        with self.assertRaisesRegex(StoreError, 'ACCEPTED_PLAN_UNAVAILABLE'):
            control_harness.resolve_accepted_verification_plan(
                self.fixture.root, self.accepted['plan_id'])

    def test_admission_refuses_plan_superseded_by_winning_replan(self):
        active = control_harness.resolve_active_packet(self.feature, self.doc, 'T-001')
        proposal = self.feature / 'winning-replan.json'
        proposal.write_text(__import__('json').dumps(dict(
            control_harness.active_task_contract(self.feature, self.doc, 'T-001'),
            objective='Winning lifecycle replan')), encoding='utf-8')
        with contextlib.redirect_stdout(io.StringIO()):
            control_harness.cmd_replan_task(argparse.Namespace(
                feature_dir=self.feature, task_id='T-001', expected_status='running',
                expected_attempts=1, expected_active_revision=active['revision_id'],
                expected_contract_sha256=active['contract_sha256'],
                expected_feature_generation=1, proposed_task_file=str(proposal),
                reason='admission race regression test', by='lifecycle-test', checkpoint=None))
        unit = self.accepted['execution_units'][0]
        state = control_harness.load_state(self.feature, self.doc)
        with self.assertRaisesRegex(StoreError, 'ACCEPTED_PLAN_UNAVAILABLE'):
            control_harness.admit_verification_execution(
                self.fixture.root, self.accepted['plan_id'], unit['unit_id'],
                expected_generation=state.get('feature_generation', 1),
                expected_state_revision=state.get('state_revision', 0))

    def test_admission_refuses_task_attempt_mismatch_with_stable_code(self):
        unit = self.accepted['execution_units'][0]
        state = control_harness.load_state(self.feature, self.doc)
        state['tasks']['T-001']['attempts'] = 2
        control_harness.save_state(self.feature, state)
        state = control_harness.load_state(self.feature, self.doc)

        with mock.patch.object(control_harness, 'resolve_accepted_verification_plan',
                               return_value=self.accepted):
            with self.assertRaisesRegex(StoreError, '^VERIFICATION_ATTEMPT_BINDING_MISMATCH$'):
                control_harness.admit_verification_execution(
                    self.fixture.root, self.accepted['plan_id'], unit['unit_id'],
                    expected_generation=state.get('feature_generation', 1),
                    expected_state_revision=state.get('state_revision', 0))

    def test_consumption_refuses_task_attempt_mismatch_with_stable_code(self):
        unit, admitted = self._reserve_first_unit()
        state = control_harness.load_state(self.feature, self.doc)
        state['tasks']['T-001']['attempts'] = 2
        control_harness.save_state(self.feature, state)
        state = control_harness.load_state(self.feature, self.doc)

        with mock.patch.object(control_harness, 'resolve_accepted_verification_plan',
                               return_value=self.accepted):
            with self.assertRaisesRegex(StoreError, '^VERIFICATION_ATTEMPT_BINDING_MISMATCH$'):
                control_harness.consume_verification_launch(
                    self.fixture.root, self.accepted['plan_id'], unit['unit_id'],
                    reservation_id=admitted['reservation']['reservation_id'],
                    expected_generation=state.get('feature_generation', 1),
                    expected_state_revision=state.get('state_revision', 0),
                    repository_admission_id='attempt-binding-regression-test')

    def test_second_consumption_after_lost_reply_never_issues_another_capability(self):
        _accepted, profile, plan, units = resolve_execution(
            self.fixture.root, self.accepted['plan_id'])
        gate = plan.decisions[0]
        obligation = next(item for item in self.accepted['obligations']
                          if item.get('gate_id') == gate.node.id)
        unit = next(item for item in units if obligation['obligation_id'] in item['obligation_ids'])
        store = VerificationStore(self.fixture.root)
        backend = test_verification_supervisor.LifecycleBackend(
            store, self.fixture.root.parent / 'lost-consume-reply-backend')
        refusal_codes = []

        def authorize(metadata):
            current = control_harness.load_state(self.feature, self.doc)
            admitted = control_harness.admit_verification_execution(
                self.fixture.root, self.accepted['plan_id'], unit['unit_id'],
                expected_generation=1,
                expected_state_revision=current.get('state_revision', 0),
                repository_admission_id=metadata['repository_admission_id'])
            consumed = control_harness.consume_verification_launch(
                self.fixture.root, self.accepted['plan_id'], unit['unit_id'],
                reservation_id=admitted['reservation']['reservation_id'],
                expected_generation=1, expected_state_revision=admitted['state_revision'],
                repository_admission_id=metadata['repository_admission_id'])

            # Model a lost response: the first durable CAS succeeded, but the
            # caller has to resolve the uncertain result by retrying from state.
            for retry_revision in (admitted['state_revision'], consumed['state_revision']):
                try:
                    control_harness.consume_verification_launch(
                        self.fixture.root, self.accepted['plan_id'], unit['unit_id'],
                        reservation_id=admitted['reservation']['reservation_id'],
                        expected_generation=1, expected_state_revision=retry_revision,
                        repository_admission_id=metadata['repository_admission_id'])
                except RuntimeError as exc:
                    code = str(exc)
                    self.assertIn(code, {'VERIFICATION_EXECUTION_PLAN_STALE',
                                         'VERIFICATION_EXECUTION_PLAN_CONFLICT'})
                    refusal_codes.append(code)
                else:
                    raise AssertionError('repeat consumption issued a second launch capability')

            return {
                'reservation_id': admitted['reservation']['reservation_id'],
                'consumption_id': consumed['consumption']['consumption_id'],
                'capability': consumed['capability'], 'plan_id': self.accepted['plan_id'],
                'unit_id': unit['unit_id'], 'obligation_ids': list(unit['obligation_ids']),
                'admission_id': admitted['admission']['admission_id'],
                'admission_sha256': admitted['admission_sha256'],
                'reservation_transition_id': admitted['reservation']['transition_id'],
                'consumption_transition_id': consumed['consumption']['transition_id'],
                'plan_acceptance_transition_id': admitted['plan_acceptance_transition_id'],
                'lifecycle_generation': 1,
            }

        result, _ready = VerificationSupervisor(store).execute(
            backend, worktree=self.fixture.root, family_id=plan.family.id,
            attempt_id='lost-consume-reply', gate_id=gate.node.id,
            command=gate.node.gate.command, cwd=self.fixture.root,
            run_dir=self.fixture.root / '.agent-runs' / 'lost-consume-reply',
            timeout_seconds=10, sandbox_mode='required', plan_id=self.accepted['plan_id'],
            candidate_identity=plan.family.candidate_identity,
            final_changed_surface_id=plan.family.final_changed_surface_id,
            profile_hash=profile.content_hash,
            policy_checkpoint=plan.family.policy_checkpoint,
            launch_authorizer=authorize)

        self.assertEqual(0, result.exit_code)
        self.assertEqual(['VERIFICATION_EXECUTION_PLAN_STALE',
                          'VERIFICATION_EXECUTION_PLAN_CONFLICT'], refusal_codes)
        state = control_harness.load_state(self.feature, self.doc)
        authority = state['verification_authority']
        self.assertEqual(1, len(authority['launch_consumptions']))
        self.assertEqual(1, len(list(store.launch_consumptions.glob('*.json'))))
        self.assertEqual(1, backend.events.count('launch'))

    def test_signed_manual_registration_reaches_coverage_verification_without_stubs(self):
        fixture = test_telemetry.TelemetryTest()._signed_manual_fixture()
        previous_state_dir = control_harness.STATE_DIR
        control_harness.STATE_DIR = fixture['root'] / '.agent-state'
        try:
            checkpoint = fixture['requested_checkpoint']
            telemetry = test_telemetry.telemetry
            original_generation = control_harness.load_state(
                fixture['feature_dir'], control_harness.load_validated(fixture['feature_dir'])
            ).get('feature_generation', 1)
            observation_path = telemetry.record_manual(
                repo=fixture['root'], feature='TST-MANUAL', role='evaluator',
                provider='manual', checkpoint=checkpoint, verdict='PASS',
                report=fixture['report_path'].relative_to(fixture['root'].resolve()).as_posix(),
                attestation=fixture['attestation_path'].relative_to(fixture['root'].resolve()).as_posix(),
            )
            observation = __import__('json').loads(observation_path.read_text(encoding='utf-8'))
            state = control_harness.load_state(
                fixture['feature_dir'], control_harness.load_validated(fixture['feature_dir'])
            )
            self.assertEqual(original_generation, state.get('feature_generation', 1))
            ledger = state.get('manual_observation_ledger', [])
            self.assertEqual(1, sum(item.get('observation_id') == observation['observation_id']
                                    for item in ledger))
            proof = telemetry.verify_manual_observation_for_coverage(fixture['root'], observation)
            self.assertEqual(observation['attestation_sha256'], proof['attestation_sha256'])
            self.assertEqual(observation['report_sha256'], proof['report_sha256'])
        finally:
            control_harness.STATE_DIR = previous_state_dir
            fixture['temporary'].cleanup()


if __name__ == '__main__':
    unittest.main()
