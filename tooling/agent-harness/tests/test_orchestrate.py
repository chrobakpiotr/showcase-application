import argparse
import contextlib
import hashlib
import io
import importlib.util
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

HERE = pathlib.Path(__file__).resolve().parents[1]
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
MODULE_PATH = HERE / 'orchestrate.py'
spec = importlib.util.spec_from_file_location('sdd_orchestrate', MODULE_PATH)
orchestrate = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = orchestrate
spec.loader.exec_module(orchestrate)


class OrchestrateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        self.old_cwd = pathlib.Path.cwd()
        os.chdir(self.root)
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        (self.root / '.gitignore').write_text('.agent-state/\n.agent-runs/\ndocs/specs/*/packets/\n')
        subprocess.run(['git', 'add', '.gitignore'], cwd=self.root, check=True)
        subprocess.run([
            'git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
            'commit', '-q', '-m', 'base'
        ], cwd=self.root, check=True)
        orchestrate.h.STATE_DIR = self.root / '.agent-state'
        roles = self.root / 'docs' / 'agentic-sdd' / 'agents'
        roles.mkdir(parents=True, exist_ok=True)
        for profile in ('builder', 'evaluator', 'integration'):
            (roles / f'{profile}.md').write_text(f'# {profile}\n')

    def tearDown(self):
        os.chdir(self.old_cwd)
        self.tmp.cleanup()

    def feature(self):
        feature = self.root / 'docs' / 'specs' / 'TST-002'
        feature.mkdir(parents=True)
        (feature / 'spec.md').write_text('# TST-002\n- AC-001: built\n- AC-002: evaluated\n')
        (feature / 'plan.md').write_text('# plan\n')
        doc = {
            'feature': 'TST-002', 'max_parallel': 2, 'max_rework_attempts': 2,
            'tasks': [
                {'id': 'T-001', 'title': 'Build', 'objective': 'Build', 'role': 'builder', 'depends_on': [],
                 'allowed_paths': ['modules/domain/**'], 'risk_tags': [], 'acceptance_criteria': ['AC-001'], 'verification': ['true']},
                {'id': 'T-900', 'title': 'Evaluate', 'objective': 'Evaluate', 'role': 'evaluator', 'depends_on': ['T-001'],
                 'allowed_paths': ['tests/**'], 'risk_tags': ['evaluation'], 'acceptance_criteria': ['AC-001', 'AC-002'], 'verification': ['true']},
            ],
        }
        (feature / 'tasks.json').write_text(json.dumps(doc))
        return feature, doc

    def args(self):
        return argparse.Namespace(
            provider='codex', evaluator_provider='claude', review_provider=None,
            model='builder-model', evaluator_model='eval-model', review_model=None,
            reasoning='medium', max_turns=30, max_budget_usd=None, agent_timeout=1800,
            owner_prefix='orch', run_id='testrun123456', verification_timeout=900, verification_sandbox='off'
        )

    def test_runner_command_passes_model_and_agent_timeout(self):
        packet = self.root / 'packet.json'
        worktree = self.root / 'task-worktree'
        packet.write_text('{}')
        worktree.mkdir()
        cmd = orchestrate.runner_command(packet, worktree,
            orchestrate.ProviderChoice('claude', 'review-model'), self.args())
        self.assertEqual('review-model', cmd[cmd.index('--model') + 1])
        self.assertEqual('1800', cmd[cmd.index('--agent-timeout') + 1])

    def test_plan_remains_read_only_and_marks_task_state_unknown_for_unresolved_history(self):
        feature, doc = self.feature()
        output = io.StringIO()
        with mock.patch.object(orchestrate.h, 'unresolved_state_marker', return_value=True), \
                mock.patch.object(orchestrate.h, 'load_state', side_effect=AssertionError('must not read lifecycle state')):
            with contextlib.redirect_stdout(output):
                orchestrate.print_plan(feature, doc, self.args())
        rendered = output.getvalue()
        self.assertIn('lifecycle=recovery-required', rendered)
        self.assertIn('state=unknown', rendered)
        self.assertIn('T-001', rendered)
        self.assertIn('T-900', rendered)
        self.assertFalse(orchestrate.h.state_path(feature).exists())

    def test_preflight_refusal_leaves_the_whole_ready_batch_unstarted(self):
        feature, doc = self.feature()
        state = {
            'tasks': {
                'T-001': {'status': 'pending', 'attempts': 0},
                'T-900': {'status': 'pending', 'attempts': 0},
            }
        }
        before = json.loads(json.dumps(state))
        packet = self.root / 'packet.json'
        packet.write_text(json.dumps({'required_reviewers': []}))
        worktree = self.root / 'task-worktree'
        worktree.mkdir()
        preflighted = []

        with mock.patch.object(orchestrate.h, 'load_state', return_value=state), \
             mock.patch.object(orchestrate.h, 'ready_ids', return_value=['T-001', 'T-900']), \
             mock.patch.object(orchestrate, 'prior_feedback', return_value=None), \
             mock.patch.object(orchestrate.h, 'active_task_contract',
                               side_effect=lambda _feature, _doc, task_id, **_kw:
                               next(task for task in doc['tasks'] if task['id'] == task_id)), \
             mock.patch.object(orchestrate.h, 'write_packet', return_value=packet), \
             mock.patch.object(orchestrate.h, 'worktree_path', return_value=worktree), \
             mock.patch.object(orchestrate.h, 'cmd_worktree_create'), \
             mock.patch.object(orchestrate, 'preflight_task_launches',
                               side_effect=lambda task, *_args, **_kwargs:
                               (preflighted.append(task['id']),
                                (_ for _ in ()).throw(RuntimeError('preflight refused: sandbox unavailable'))
                                if task['id'] == 'T-900' else None)), \
             mock.patch.object(orchestrate.h, 'cmd_start') as start:
            with self.assertRaisesRegex(RuntimeError, 'preflight refused: sandbox unavailable'):
                orchestrate.start_ready_batch(feature, doc, self.args())

        self.assertEqual(['T-001', 'T-900'], preflighted)
        start.assert_not_called()
        self.assertEqual(before, state)

    def test_a_failure_after_a_committed_start_rolls_back_exactly_that_start(self):
        """agent-harness 0.19.1: each committed start is undone by the identity its own cmd_start returned, whether
        a later start in the batch fails or a lookup right after the start does."""
        feature, doc = self.feature()
        packet = self.root / 'packet.json'
        worktree = self.root / 'worktree'
        identity = {'task': 'T-001', 'attempt': 3, 'claimed_at': '2026-10-25T00:00:00+00:00'}
        for failing in ('second start', 'lookup after the first start'):
            with self.subTest(failing=failing):
                def start(args):
                    if args.task_id == 'T-900':
                        raise RuntimeError('second start failed')
                    return identity

                def contract(_feature, _doc, task_id, **_kw):
                    if failing == 'lookup after the first start' and task_id == 'T-001' and start_mock.called:
                        raise RuntimeError('lookup failed')
                    return next(task for task in doc['tasks'] if task['id'] == task_id)
                with mock.patch.object(orchestrate.h, 'ready_ids', return_value=['T-001', 'T-900']), \
                     mock.patch.object(orchestrate, 'prior_feedback', return_value=None), \
                     mock.patch.object(orchestrate, 'prepare_ready_task_for_preflight',
                                       side_effect=lambda _f, _d, task_id, *_a: (contract(None, None, task_id),
                                                                                  worktree, packet, None)), \
                     mock.patch.object(orchestrate.h, 'active_task_contract', side_effect=contract), \
                     mock.patch.object(orchestrate.h, 'worktree_path', return_value=worktree), \
                     mock.patch.object(orchestrate.h, 'resolve_active_packet', return_value={'path': str(packet)}), \
                     mock.patch.object(orchestrate.h, 'cmd_start', side_effect=start) as start_mock, \
                     mock.patch.object(orchestrate.h, 'rollback_unexecuted_start') as rollback:
                    with self.assertRaisesRegex(RuntimeError, 'second start failed|lookup failed'):
                        orchestrate.start_ready_batch(feature, doc, self.args())
                self.assertEqual(1, rollback.call_count)
                args, kwargs = rollback.call_args
                self.assertEqual('T-001', args[2])
                self.assertEqual({'attempt': 3, 'claimed_at': '2026-10-25T00:00:00+00:00'}, kwargs)

    def test_preflight_runner_requires_explicit_success_and_surfaces_refusal_reason(self):
        with mock.patch.object(orchestrate.subprocess, 'run', return_value=subprocess.CompletedProcess(
                [], 0, stdout='preflight: ok\n', stderr='')) as run:
            orchestrate.invoke_preflight(['python', 'runner.py'])
        self.assertEqual(['python', 'runner.py', '--preflight'], run.call_args.args[0])

        for reason in ('no model for codex', 'preflight refused: sandbox unavailable',
                       'preflight refused: evidence root is writable'):
            with self.subTest(reason=reason), mock.patch.object(
                    orchestrate.subprocess, 'run', return_value=subprocess.CompletedProcess(
                        [], 2, stdout='', stderr=f'ERROR: {reason}\n')):
                with self.assertRaisesRegex(RuntimeError, reason.replace(' ', r'\ ')):
                    orchestrate.invoke_preflight(['python', 'runner.py'])

    def test_preflight_covers_builder_and_reviewer_models_with_their_actual_permissions(self):
        feature, doc = self.feature()
        packet = self.root / 'packet.json'
        packet.write_text(json.dumps({'required_reviewers': ['security-reviewer']}))
        worktree = self.root / 'task-worktree'
        worktree.mkdir()
        commands = []
        with mock.patch.object(orchestrate, 'invoke_preflight', side_effect=commands.append):
            orchestrate.preflight_task_launches(
                doc['tasks'][0], packet, worktree,
                orchestrate.choice_for_role('builder', self.args()), self.args(), None,
            )

        self.assertEqual(2, len(commands))
        self.assertEqual('builder-model', commands[0][commands[0].index('--model') + 1])
        self.assertEqual('review-model' if self.args().review_model else 'eval-model',
                         commands[1][commands[1].index('--model') + 1])
        self.assertEqual('security-reviewer', commands[1][commands[1].index('--profile') + 1])
        self.assertIn('--sandbox', commands[1])
        self.assertEqual('read-only', commands[1][commands[1].index('--sandbox') + 1])
        self.assertEqual('1800', commands[0][commands[0].index('--agent-timeout') + 1])

    def test_verification_resume_preflights_only_its_read_only_review_launches(self):
        feature, doc = self.feature()
        packet = self.root / 'packet.json'
        packet.write_text(json.dumps({'required_reviewers': ['security-reviewer']}))
        worktree = self.root / 'task-worktree'
        worktree.mkdir()
        commands = []
        with mock.patch.object(orchestrate, 'invoke_preflight', side_effect=commands.append):
            orchestrate.preflight_task_launches(
                doc['tasks'][0], packet, worktree,
                orchestrate.choice_for_role('builder', self.args()), self.args(), None,
                verification_resume=True,
            )

        self.assertEqual(1, len(commands))
        self.assertEqual('security-reviewer', commands[0][commands[0].index('--profile') + 1])
        self.assertIn('--review-existing', commands[0])

    def _control_binding(self, contents=b'#!/bin/sh\nexit 0\n'):
        executable = self.root / 'bound-control-python'
        executable.write_bytes(contents)
        executable.chmod(0o755)
        return {'command': 'python3', 'path': str(executable.resolve()),
                'sha256': hashlib.sha256(contents).hexdigest()}

    def _accepted_plan(self, plan_id, binding_marker='present', task_attempt=1):
        from verification.profile import load_profile
        profile = load_profile(orchestrate.HERE / 'verification-profiles/showcase.json')
        record = {'plan_id': plan_id, 'task_id': 'T-001', 'task_attempt': task_attempt,
                  'lifecycle_generation': 1, 'profile_id': 'showcase',
                  'profile_hash': profile.content_hash}
        if binding_marker != 'missing':
            record['control_executable_binding'] = self._control_binding()
        return record

    def _launch_started_verification(self, feature, doc, worktree, packet, main_path, plan,
                                     accepted_plan=None):
        state = orchestrate.h.load_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'running', 'attempts': 1,
                                        'owner': orchestrate.owner_for(self.args(), 'T-001')})
        orchestrate.h.save_state(feature, state)
        with mock.patch.object(orchestrate, 'lease_heartbeat',
                               return_value=orchestrate.contextlib.nullcontext()), \
             mock.patch.object(orchestrate.h, 'load_state', return_value=state), \
             mock.patch.object(orchestrate, 'invoke_runner', return_value=main_path), \
             mock.patch.object(orchestrate, 'prepare_task_plan', return_value=plan), \
             mock.patch.object(orchestrate.h, 'resolve_accepted_verification_plan',
                               return_value=accepted_plan or plan), \
             mock.patch.object(orchestrate, 'verification_evidence_summary', return_value={
                 'outcome': 'PASS', 'machine_category': 'pass'}), \
             mock.patch.object(orchestrate.subprocess, 'run', return_value=subprocess.CompletedProcess(
                 [], 0, stdout='{"outcome":"PASS"}\n', stderr='')) as launch:
            outcome = orchestrate.run_started_task(feature, doc, doc['tasks'][0], worktree,
                                                   packet, self.args(), feedback=None)
        return outcome, launch

    def _launch_resumed_verification(self, feature, doc, worktree, packet, plan, *, prior_path,
                                     accepted_plan=None):
        prior_path.parent.mkdir(parents=True, exist_ok=True)
        prior_path.write_text(json.dumps({'task': 'T-001', 'status': 'pass'}))
        prior_digest = hashlib.sha256(prior_path.read_bytes()).hexdigest()
        resume = {'plan_id': plan['plan_id'], 'task_attempt': 1,
                  'prior_result_sha256': prior_digest, 'prior_result_path': str(prior_path),
                  'operator': 'operator@example.invalid'}
        task_state = {'attempts': 1, 'last_failure_evidence': str(prior_path)}
        with mock.patch.object(orchestrate, 'REPO', self.root), \
             mock.patch.object(orchestrate.h, 'resolve_accepted_verification_plan',
                               return_value=accepted_plan or plan), \
             mock.patch.object(orchestrate, 'verification_evidence_summary', return_value={
                 'outcome': 'PASS', 'machine_category': 'pass'}), \
             mock.patch.object(orchestrate.subprocess, 'run', return_value=subprocess.CompletedProcess(
                 [], 0, stdout='{"outcome":"PASS"}\n', stderr='')) as launch:
            outcome = orchestrate.run_resumed_verification(
                feature, doc, doc['tasks'][0], worktree, packet, self.args(), resume, task_state)
        return outcome, launch

    def test_started_and_resumed_verification_use_bound_isolated_interpreter_and_safe_env(self):
        feature, doc = self.feature()
        worktree = self.root / 'task-worktree'
        worktree.mkdir()
        packet = self.root / 'packet.json'
        packet.write_text(json.dumps({'verification': ['true'], 'required_reviewers': []}))
        provenance = self.root / 'provenance.json'
        provenance.write_text(json.dumps({'base_commit': 'a' * 40}))
        main_path = self.root / 'main-result.json'
        main_path.write_text(json.dumps({'task': 'T-001', 'status': 'pass',
                                         'provenance': str(provenance)}))
        cases = []
        for mode in ('started', 'resumed'):
            plan = self._accepted_plan(f'plan-{mode}')
            with self.subTest(mode=mode), \
                 mock.patch.dict(os.environ, {'PYTHONPATH': '/untrusted/python',
                                               'PYTHONSTARTUP': '/untrusted/startup'}):
                if mode == 'started':
                    outcome, launch = self._launch_started_verification(
                        feature, doc, worktree, packet, main_path, plan)
                else:
                    outcome, launch = self._launch_resumed_verification(
                        feature, doc, worktree, packet, plan,
                        prior_path=self.root / '.agent-runs' / 'TST-002' / 'prior.json')
                self.assertEqual('pass', outcome.status, outcome.summary)
                command = launch.call_args.args[0]
                self.assertEqual([
                    plan['control_executable_binding']['path'], '-I',
                    str(orchestrate.HERE / 'verify.py'), 'run', '--mode', 'integration',
                    '--repo', str(worktree), '--plan-id', plan['plan_id'],
                ], command)
                env = launch.call_args.kwargs['env']
                self.assertNotIn('PYTHONPATH', env)
                self.assertNotIn('PYTHONSTARTUP', env)
                cases.append(mode)
        self.assertEqual(['started', 'resumed'], cases)

    def test_started_and_resumed_verification_refuse_missing_or_swapped_interpreter_before_launch(self):
        feature, doc = self.feature()
        worktree = self.root / 'task-worktree'
        worktree.mkdir()
        packet = self.root / 'packet.json'
        packet.write_text(json.dumps({'verification': ['true'], 'required_reviewers': []}))
        provenance = self.root / 'provenance.json'
        provenance.write_text(json.dumps({'base_commit': 'a' * 40}))
        main_path = self.root / 'main-result.json'
        main_path.write_text(json.dumps({'task': 'T-001', 'status': 'pass',
                                         'provenance': str(provenance)}))
        for mode in ('started', 'resumed'):
            for binding_state in ('missing', 'swapped'):
                with self.subTest(mode=mode, binding_state=binding_state):
                    plan = self._accepted_plan(f'plan-{mode}-{binding_state}',
                                               binding_marker=binding_state)
                    if binding_state == 'swapped':
                        plan['control_executable_binding']['path'] = str((self.root / 'swapped-python').resolve())
                        swapped = pathlib.Path(plan['control_executable_binding']['path'])
                        swapped.write_bytes(b'#!/bin/sh\nexit 9\n')
                        swapped.chmod(0o755)
                        # Preserve the pre-swap digest, exactly as a plan made earlier would.
                        plan['control_executable_binding']['sha256'] = hashlib.sha256(
                            b'#!/bin/sh\nexit 0\n').hexdigest()
                    if mode == 'started':
                        with mock.patch.object(orchestrate, 'lease_heartbeat',
                                return_value=orchestrate.contextlib.nullcontext()), \
                             mock.patch.object(orchestrate.h, 'load_state', return_value={
                                 'tasks': {'T-001': {'attempts': 1}}}), \
                             mock.patch.object(orchestrate, 'invoke_runner', return_value=main_path), \
                             mock.patch.object(orchestrate, 'prepare_task_plan', return_value=plan), \
                             mock.patch.object(orchestrate.h, 'resolve_accepted_verification_plan',
                                               return_value=plan), \
                             mock.patch.object(orchestrate.subprocess, 'run') as launch:
                            outcome = orchestrate.run_started_task(feature, doc, doc['tasks'][0],
                                worktree, packet, self.args(), feedback=None)
                    else:
                        prior_path = self.root / '.agent-runs' / 'TST-002' / f'{binding_state}.json'
                        prior_path.parent.mkdir(parents=True, exist_ok=True)
                        prior_path.write_text(json.dumps({'task': 'T-001', 'status': 'pass'}))
                        prior_digest = hashlib.sha256(prior_path.read_bytes()).hexdigest()
                        resume = {'plan_id': plan['plan_id'], 'task_attempt': 1,
                                  'prior_result_sha256': prior_digest,
                                  'prior_result_path': str(prior_path), 'operator': 'operator'}
                        task_state = {'attempts': 1, 'last_failure_evidence': str(prior_path)}
                        with mock.patch.object(orchestrate, 'REPO', self.root), \
                             mock.patch.object(orchestrate.h, 'resolve_accepted_verification_plan',
                                               return_value=plan), \
                             mock.patch.object(orchestrate.subprocess, 'run') as launch:
                            outcome = orchestrate.run_resumed_verification(feature, doc, doc['tasks'][0],
                                worktree, packet, self.args(), resume, task_state)
                    self.assertEqual('verification-blocked', outcome.status)
                    launch.assert_not_called()

    def test_started_and_resumed_verification_refuse_identity_mismatch_before_launch(self):
        feature, doc = self.feature()
        worktree = self.root / 'task-worktree'
        worktree.mkdir()
        packet = self.root / 'packet.json'
        packet.write_text(json.dumps({'verification': ['true'], 'required_reviewers': []}))
        provenance = self.root / 'provenance.json'
        provenance.write_text(json.dumps({'base_commit': 'a' * 40}))
        main_path = self.root / 'main-result.json'
        main_path.write_text(json.dumps({'task': 'T-001', 'status': 'pass',
                                         'provenance': str(provenance)}))

        cases = (
            ('started-task-id', 'started', 'task_id', 'T-900'),
            ('started-exact-record', 'started', 'lifecycle_generation', 2),
            ('resumed-task-id', 'resumed', 'task_id', 'T-900'),
            ('resumed-attempt', 'resumed', 'task_attempt', 2),
        )
        for case, mode, field, value in cases:
            with self.subTest(case=case):
                plan = self._accepted_plan(f'plan-{case}')
                resolved = dict(plan)
                resolved[field] = value
                if mode == 'started':
                    outcome, launch = self._launch_started_verification(
                        feature, doc, worktree, packet, main_path, plan, accepted_plan=resolved)
                else:
                    outcome, launch = self._launch_resumed_verification(
                        feature, doc, worktree, packet, plan,
                        prior_path=self.root / '.agent-runs' / 'TST-002' / f'{case}.json',
                        accepted_plan=resolved)
                self.assertEqual('verification-blocked', outcome.status)
                launch.assert_not_called()

    def test_owner_is_scoped_by_orchestration_run(self):
        self.assertEqual('orch-testrun1-t-001', orchestrate.owner_for(self.args(), 'T-001'))

    def test_provider_routing_separates_evaluator(self):
        args = self.args()
        self.assertEqual('codex', orchestrate.choice_for_role('builder', args).provider)
        self.assertEqual('claude', orchestrate.choice_for_role('evaluator', args).provider)
        self.assertEqual('eval-model', orchestrate.choice_for_role('integration', args).model)

    def test_evaluator_failure_reopens_named_builder_with_feedback(self):
        feature, doc = self.feature()
        state = orchestrate.h.load_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'completed', 'attempts': 1})
        state['tasks']['T-900'].update({'status': 'running', 'attempts': 1, 'owner': 'orch-testrun1-t-900'})
        orchestrate.h.save_state(feature, state)
        evidence = self.root / 'evaluation.json'
        evidence.write_text(json.dumps({
            'status': 'fail', 'summary': 'AC-001 counterexample', 'changed_paths': [], 'commands': ['true'],
            'assumptions': [], 'residual_risks': [], 'rework_tasks': ['T-001']
        }))
        outcome = orchestrate.TaskOutcome(
            'T-900', 'fail', evidence=evidence, summary='AC-001 counterexample', rework_tasks=['T-001']
        )
        orchestrate.apply_outcome(feature, doc, outcome, self.args())
        updated = orchestrate.h.load_state(feature, doc)
        self.assertEqual('failed', updated['tasks']['T-001']['status'])
        self.assertEqual(str(evidence), updated['tasks']['T-001']['rework_evidence'])
        self.assertEqual('pending', updated['tasks']['T-900']['status'])

    def test_needs_human_escalates_immediately(self):
        feature, doc = self.feature()
        state = orchestrate.h.load_state(feature, doc)
        state['tasks']['T-001'].update({'status': 'running', 'attempts': 1, 'owner': 'orch-testrun1-t-001'})
        orchestrate.h.save_state(feature, state)
        evidence = self.root / 'builder.json'
        evidence.write_text(json.dumps({
            'status': 'needs-human', 'summary': 'contract ambiguous', 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': ['ambiguity']
        }))
        outcome = orchestrate.TaskOutcome('T-001', 'needs-human', evidence=evidence, summary='contract ambiguous')
        orchestrate.apply_outcome(feature, doc, outcome, self.args())
        updated = orchestrate.h.load_state(feature, doc)
        self.assertEqual('escalated', updated['tasks']['T-001']['status'])
        self.assertEqual(1, updated['tasks']['T-001']['attempts'])

    def test_verification_blockages_preserve_reason_and_never_start_a_new_builder_attempt(self):
        feature, doc = self.feature()
        for category in ('busy', 'stale-input', 'environment-blocked', 'needs-human',
                         'verification-blocked', 'verification-owned'):
            with self.subTest(category=category):
                state = orchestrate.h.load_state(feature, doc)
                state['tasks']['T-001'].update({
                    'status': 'running', 'attempts': 1, 'owner': 'orch-testrun1-t-001',
                    'control_outcome': None,
                })
                orchestrate.h.save_state(feature, state)
                outcome = orchestrate.TaskOutcome('T-001', category, summary='blocked by verifier')
                orchestrate.apply_outcome(feature, doc, outcome, self.args())

                blocked = orchestrate.h.load_state(feature, doc)['tasks']['T-001']
                self.assertEqual('escalated', blocked['status'])
                self.assertEqual(category, blocked['control_outcome'])
                self.assertEqual(1, blocked['attempts'])

                with mock.patch.object(orchestrate.h, 'cmd_start') as start:
                    started = orchestrate.start_ready_batch(feature, doc, self.args())
                self.assertEqual([], started)
                start.assert_not_called()

                with self.assertRaises(SystemExit):
                    orchestrate.h.cmd_human_resolve(argparse.Namespace(
                        feature_dir=feature, task_id='T-001', decision='retry',
                        decision_file=None, by='operator@example.invalid',
                    ))
                after = orchestrate.h.load_state(feature, doc)['tasks']['T-001']
                self.assertEqual(1, after['attempts'])
                self.assertEqual('escalated', after['status'])
                self.assertEqual(0, after.get('human_resume_grants', 0))

    def test_busy_verification_lock_is_manifested_before_any_outcome_mutation(self):
        feature, doc = self.feature()
        state = orchestrate.h.load_state(feature, doc)
        state['tasks']['T-001'].update({
            'status': 'running', 'attempts': 1, 'owner': 'orch-testrun1-t-001',
        })
        orchestrate.h.save_state(feature, state)
        worktree = self.root / 'worktree'
        subprocess.run(['git', 'worktree', 'add', '-q', '-b', 'task/T-001', str(worktree), 'HEAD'],
                       cwd=self.root, check=True)
        worktree_file = worktree / 'modules' / 'domain' / 'work.txt'
        worktree_file.parent.mkdir(parents=True)
        worktree_file.write_text('uncheckpointed worker bytes')
        head_before = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=worktree, text=True).strip()
        state_before = json.dumps(orchestrate.h.load_state(feature, doc), sort_keys=True)
        manifest = self.root / '.agent-runs' / 'TST-002' / 'orchestrations' / 'busy.json'
        args = self.args()
        outcome = orchestrate.TaskOutcome('T-001', 'busy', summary='verification lock is busy')

        from verification.store import VerificationStore, repository_lock
        store = VerificationStore(feature)
        with repository_lock(store.root):
            with mock.patch('verification.store.repository_lock',
                            side_effect=lambda root: repository_lock(root, timeout=0.01)):
                with self.assertRaises(SystemExit) as busy_probe:
                    orchestrate.h.assert_repository_verification_drained(feature)
            self.assertEqual(3, busy_probe.exception.code)
            with mock.patch.object(orchestrate, 'apply_outcome') as apply:
                with mock.patch.object(orchestrate, 'start_ready_batch') as start:
                    with self.assertRaises(SystemExit) as raised:
                        orchestrate.stop_on_repository_admission_blockage(
                            feature, doc, args, manifest, [outcome], round_no=1, recovered_total=[])

        self.assertEqual(3, raised.exception.code)
        apply.assert_not_called()
        start.assert_not_called()
        self.assertEqual(head_before, subprocess.check_output(
            ['git', 'rev-parse', 'HEAD'], cwd=worktree, text=True).strip())
        self.assertEqual('uncheckpointed worker bytes', worktree_file.read_text())
        self.assertEqual(state_before, json.dumps(orchestrate.h.load_state(feature, doc), sort_keys=True))
        saved_manifest = json.loads(manifest.read_text())
        self.assertEqual('busy', saved_manifest['status'])
        self.assertEqual({'T-001': 'busy'}, saved_manifest['control_outcomes'])

    def test_verification_evidence_binds_profile_and_distinguishes_gate_dispositions(self):
        plan = {
            'plan_id': 'verification-plan-v2:sha256:' + 'a' * 64,
            'family': {'id': 'showcase-t-001-attempt-1'},
            'lifecycle_generation': 3,
            'profile_id': 'showcase',
            'profile_hash': 'b' * 64,
            'policy_checkpoint': 'c' * 64,
            'task_commands': [{'command': 'true', 'cwd': '.'}],
            'obligations': [{'gate_id': 'required-gate'}],
        }
        result = {
            'family_id': 'showcase-t-001-attempt-1',
            'profile_hash': 'b' * 64,
            'continuation': 'execute-all-and-aggregate',
            'outcome': 'verification-failed',
            'reason_code': 'CHECK_COMMAND_FAILED',
            'stdout': 'must not be copied',
            'gates': [
                {'gate_id': 'executed', 'action': 'RUN', 'outcome': 'FAIL',
                 'reason': 'completed', 'fingerprint': 'd' * 64, 'command_hash': 'e' * 64,
                 'stdout': 'secret output'},
                {'gate_id': 'reused', 'action': 'REUSE', 'outcome': 'PASS', 'reason': 'inputs-match'},
                {'gate_id': 'blocked', 'action': 'RUN', 'outcome': 'NOT_RUN',
                 'reason': 'blocked-by-failure'},
            ],
        }

        evidence = orchestrate.verification_evidence_summary(result, plan, 1)

        self.assertEqual('b' * 64, evidence['profile_hash'])
        self.assertEqual('c' * 64, evidence['policy_checkpoint'])
        self.assertEqual('showcase-t-001-attempt-1', evidence['family_id'])
        self.assertEqual('execute-all-and-aggregate', evidence['continuation'])
        self.assertEqual(['RUN', 'REUSE', 'RUN'], [row['action'] for row in evidence['gates']])
        self.assertEqual(['FAIL', 'PASS', 'NOT_RUN'], [row['outcome'] for row in evidence['gates']])
        serialized = json.dumps(evidence)
        self.assertNotIn('stdout', serialized)
        self.assertNotIn('secret output', serialized)
        self.assertEqual('CHECK_COMMAND_FAILED', evidence['reason_code'])

        mismatched = orchestrate.verification_evidence_summary(
            {**result, 'family_id': 'another-family'}, plan, 0)
        self.assertEqual('verification-blocked', mismatched['machine_category'])
        self.assertEqual('PLAN_BINDING_MISMATCH', mismatched['reason_code'])
        self.assertEqual([], mismatched['gates'])

    def test_malformed_verifier_shapes_fail_closed_without_type_errors(self):
        plan = {
            'plan_id': 'verification-plan-v2:sha256:' + 'a' * 64,
            'family': {'id': 'showcase-t-001-attempt-1'},
            'lifecycle_generation': 3,
            'profile_id': 'showcase',
            'profile_hash': 'b' * 64,
            'policy_checkpoint': 'c' * 64,
            'task_commands': [{'command': 'true', 'cwd': '.'}],
            'obligations': [{'gate_id': 'required-gate'}],
        }
        valid = {'family_id': plan['family']['id'], 'profile_hash': plan['profile_hash'],
                 'outcome': 'PASS', 'gates': []}
        malformed = [
            None,
            [],
            valid,
            {**valid, 'outcome': []},
            {**valid, 'outcome': 'weird'},
            {**valid, 'outcome': 'PASS', 'machine_category': 'needs-human', 'exit_code': 4,
             'gates': [{'gate_id': 'required-gate', 'action': 'RUN', 'outcome': 'PASS'}]},
            {**valid, 'outcome': 'PASS', 'gates': [
                {'gate_id': 'required-gate', 'action': 'RUN', 'outcome': 'FAIL'}]},
            {**valid, 'outcome': 'PASS', 'gates': [
                {'gate_id': 'required-gate', 'action': 'RUN', 'outcome': 'NOT_RUN'}]},
            {**valid, 'outcome': 'verification-blocked', 'machine_category': 'verification-blocked',
             'exit_code': 6, 'gates': [{'gate_id': 'required-gate', 'action': 'RUN', 'outcome': 'PASS'}]},
            {**valid, 'outcome': 'needs-human'},
            {**valid, 'machine_category': []},
            {**valid, 'machine_category': 'mystery'},
            {**valid, 'continuation': []},
            {**valid, 'continuation': 'continue-silently'},
            {**valid, 'gates': None},
            {**valid, 'gates': {}},
            {**valid, 'gates': 'not-a-list'},
            {**valid, 'gates': [[]]},
            {**valid, 'gates': [
                {'gate_id': 'required-gate', 'action': 'RUN', 'outcome': 'PASS'}, []]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': [], 'outcome': 'PASS'}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': []}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': 'PASS', 'reason': []}]},
            {**valid, 'gates': [{'gate_id': 'invalid id', 'action': 'RUN', 'outcome': 'PASS'}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'SKIP', 'outcome': 'PASS'}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': 'MAYBE'}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': 'PASS',
                                'fingerprint': 'not-a-hash'}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': 'PASS',
                                'command_hash': 'f' * 63}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': 'PASS',
                                'exit_code': 'zero'}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': 'PASS',
                                'duration_seconds': -1}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': 'PASS',
                                'duration_seconds': 10 ** 1000}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': 'PASS',
                                'started_at': float('inf')}]},
            {**valid, 'gates': [{'gate_id': 'g', 'action': 'RUN', 'outcome': 'PASS',
                                'exit_code': 256}]},
            {**valid, 'exit_code': -1},
            {**valid, 'exit_code': 256},
        ]

        consistent_pass = {
            **valid,
            'gates': [{'gate_id': 'required-gate', 'action': 'RUN', 'outcome': 'PASS'}],
        }
        accepted = orchestrate.verification_evidence_summary(consistent_pass, plan, 0)
        self.assertEqual('PASS', accepted['outcome'])
        self.assertEqual('required-gate', accepted['gates'][0]['gate_id'])

        for result in malformed:
            with self.subTest(result=result):
                summary = orchestrate.verification_evidence_summary(result, plan, 0)
                self.assertEqual('verification-blocked', summary['outcome'])
                self.assertEqual('verification-blocked', summary['machine_category'])
                self.assertEqual('MALFORMED_VERIFICATION_RESULT', summary['reason_code'])
                self.assertEqual([], summary['gates'])

    def test_malformed_verifier_json_becomes_blocked_outcome_without_runner_retry(self):
        feature, doc = self.feature()
        task = doc['tasks'][0]
        state = orchestrate.h.load_state(feature, doc)
        state['tasks'][task['id']].update({
            'status': 'running', 'attempts': 1, 'owner': orchestrate.owner_for(self.args(), task['id']),
        })
        orchestrate.h.save_state(feature, state)
        worktree = self.root / 'task-worktree'
        worktree.mkdir()
        packet = self.root / 'packet.json'
        packet.write_text(json.dumps({'verification': ['true']}))
        provenance = self.root / 'provenance.json'
        provenance.write_text(json.dumps({'base_commit': 'a' * 40}))
        main_result = self.root / 'main-result.json'
        main_result.write_text(json.dumps({'status': 'pass', 'provenance': str(provenance)}))
        plan = {
            'plan_id': 'verification-plan-v2:sha256:' + 'a' * 64,
            'family': {'id': 'showcase-t-001-attempt-1'},
            'lifecycle_generation': 1,
            'profile_id': 'showcase',
            'profile_hash': 'b' * 64,
            'policy_checkpoint': 'c' * 64,
            'task_commands': [{'command': 'true', 'cwd': '.'}],
            'obligations': [{'gate_id': 'required-gate'}],
        }
        plan.update(self._accepted_plan(plan['plan_id']))
        original_run = subprocess.run
        verification_outputs = [
            '[]\n',
            json.dumps({
                'family_id': plan['family']['id'], 'profile_hash': plan['profile_hash'],
                'outcome': 'PASS', 'gates': [
                    {'gate_id': 'required-gate', 'action': 'RUN', 'outcome': 'FAIL'}],
            }) + '\n',
        ]
        for verification_output in verification_outputs:
            with self.subTest(verification_output=verification_output):
                main_result.write_text(json.dumps({'status': 'pass', 'provenance': str(provenance)}))

                def run_verifier_only(cmd, *call_args, **call_kwargs):
                    if isinstance(cmd, list) and str(orchestrate.HERE / 'verify.py') in cmd:
                        return subprocess.CompletedProcess(
                            cmd, 0, stdout=verification_output, stderr='')
                    return original_run(cmd, *call_args, **call_kwargs)

                with mock.patch.object(orchestrate, 'lease_heartbeat',
                                       return_value=orchestrate.contextlib.nullcontext()), \
                        mock.patch.object(orchestrate.h, 'load_state', return_value={
                            'tasks': {'T-001': {'attempts': 1}}}), \
                        mock.patch.object(orchestrate, 'invoke_runner', return_value=main_result) as invoke, \
                        mock.patch.object(orchestrate, 'prepare_task_plan', return_value=plan), \
                        mock.patch.object(orchestrate.h, 'resolve_accepted_verification_plan',
                                          return_value=plan), \
                        mock.patch.object(orchestrate.subprocess, 'run', side_effect=run_verifier_only):
                    outcome = orchestrate.run_started_task(
                        feature, doc, task, worktree, packet, self.args(), feedback=None)

                self.assertEqual('verification-blocked', outcome.status)
                self.assertEqual('MALFORMED_VERIFICATION_RESULT', outcome.summary)
                evidence = json.loads(main_result.read_text())
                self.assertEqual('verification-blocked', evidence['verification']['outcome'])
                self.assertNotIn('verification_authority', evidence)
                invoke.assert_called_once()

    def test_verification_resume_reuses_prior_builder_result_without_provider_launch(self):
        feature, doc = self.feature()
        allowed = self.root / '.agent-runs' / 'TST-002'
        allowed.mkdir(parents=True)
        main_path = allowed / 'prior-result.json'
        main_path.write_text(json.dumps({'task': 'T-001', 'status': 'pass', 'summary': 'builder passed'}))
        worktree = self.root / 'task-worktree'
        worktree.mkdir()
        packet = self.root / 'packet.json'
        packet.write_text(json.dumps({'required_reviewers': []}))
        accepted = self._accepted_plan('plan-1', task_attempt=2)
        task_state = {'attempts': 2, 'last_failure_evidence': str(main_path)}
        original_bytes = main_path.read_bytes()
        resume = {'plan_id': 'plan-1', 'task_attempt': 2,
                  'prior_result_sha256': hashlib.sha256(original_bytes).hexdigest(),
                  'prior_result_path': str(main_path),
                  'operator': 'operator@example.invalid'}
        process = mock.Mock(returncode=0, stdout='{}', stderr='')
        with mock.patch.object(orchestrate, 'REPO', self.root), \
             mock.patch.object(orchestrate.h, 'resolve_accepted_verification_plan', return_value=accepted), \
             mock.patch.object(orchestrate.subprocess, 'run', return_value=process) as verify, \
             mock.patch.object(orchestrate, 'verification_evidence_summary', return_value={
                 'outcome': 'PASS', 'machine_category': 'pass'}), \
             mock.patch.object(orchestrate, 'invoke_runner') as provider:
            outcome = orchestrate.run_resumed_verification(
                feature, doc, doc['tasks'][0], worktree, packet, self.args(),
                resume, task_state)
        self.assertEqual('pass', outcome.status)
        self.assertNotEqual(main_path.resolve(), outcome.evidence)
        self.assertEqual(original_bytes, main_path.read_bytes())
        verify.assert_called_once()
        self.assertIn('--plan-id', verify.call_args.args[0])
        resumed = json.loads(outcome.evidence.read_text())
        self.assertEqual('plan-1', resumed['verification_authority']['plan_id'])
        self.assertEqual(str(main_path.resolve()), resumed['verification_resume']['prior_result_path'])
        self.assertEqual(resume['prior_result_sha256'], resumed['verification_resume']['prior_result_sha256'])
        self.assertEqual('operator@example.invalid', resumed['verification_resume']['operator'])
        provider.assert_not_called()

    def test_verification_resume_rejects_changed_prior_builder_result_before_running_verifier(self):
        feature, doc = self.feature()
        allowed = self.root / '.agent-runs' / 'TST-002'
        allowed.mkdir(parents=True)
        main_path = allowed / 'prior-result.json'
        main_path.write_text(json.dumps({'task': 'T-001', 'status': 'pass'}))
        resume = {'plan_id': 'plan-1', 'task_attempt': 2,
                  'prior_result_sha256': '0' * 64, 'prior_result_path': str(main_path),
                  'operator': 'operator'}
        task_state = {'attempts': 2, 'last_failure_evidence': str(main_path)}
        with mock.patch.object(orchestrate, 'REPO', self.root), \
             mock.patch.object(orchestrate, 'invoke_runner') as provider, \
             mock.patch.object(orchestrate.subprocess, 'run') as verify:
            outcome = orchestrate.run_resumed_verification(
                feature, doc, doc['tasks'][0], self.root / 'worktree', self.root / 'packet',
                self.args(), resume, task_state)
        self.assertEqual('verification-blocked', outcome.status)
        self.assertIsNone(outcome.evidence)
        verify.assert_not_called()
        provider.assert_not_called()

    def test_started_verification_resume_routes_around_provider_runner(self):
        feature, doc = self.feature()
        task = doc['tasks'][0]
        worktree = self.root / 'task-worktree'
        packet = self.root / 'packet.json'
        resume = {'plan_id': 'plan-1', 'task_attempt': 2}
        state = {'tasks': {'T-001': {'verification_resume_active': resume, 'attempts': 2}}}
        expected = orchestrate.TaskOutcome('T-001', 'verification-blocked', summary='precondition pending')
        with mock.patch.object(orchestrate, 'lease_heartbeat',
                               return_value=orchestrate.contextlib.nullcontext()), \
             mock.patch.object(orchestrate.h, 'load_state', return_value=state), \
             mock.patch.object(orchestrate, 'run_resumed_verification', return_value=expected) as resume_run, \
             mock.patch.object(orchestrate, 'invoke_runner') as provider:
            outcome = orchestrate.run_started_task(
                feature, doc, task, worktree, packet, self.args(), feedback=None)
        self.assertIs(expected, outcome)
        resume_run.assert_called_once_with(feature, doc, task, worktree, packet,
                                           self.args(), resume, state['tasks']['T-001'])
        provider.assert_not_called()


    def test_human_resolution_makes_escalated_task_resumable_with_feedback(self):
        feature, doc = self.feature()
        state = orchestrate.h.load_state(feature, doc)
        state['tasks']['T-001'].update({
            'status': 'escalated', 'attempts': 3, 'last_failure': 'contract ambiguous',
            'last_failure_evidence': str(self.root / '.agent-runs' / 'result.json'),
        })
        orchestrate.h.save_state(feature, state)

        orchestrate.h.cmd_human_resolve(argparse.Namespace(
            feature_dir=feature, task_id='T-001', decision='Keep the existing API contract and retry.',
            decision_file=None, by='architect@example.invalid',
        ))

        updated = orchestrate.h.load_state(feature, doc)
        self.assertEqual('failed', updated['tasks']['T-001']['status'])
        self.assertIn('T-001', orchestrate.h.ready_ids(doc, updated))
        feedback = orchestrate.prior_feedback(feature, doc, 'T-001')
        self.assertIsNotNone(feedback)
        self.assertTrue(feedback.exists())
        self.assertIn('Keep the existing API contract and retry.', feedback.read_text())



if __name__ == '__main__':
    unittest.main()
