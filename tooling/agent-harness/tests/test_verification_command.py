import pathlib
import sys
import tempfile
import unittest
from types import SimpleNamespace
import subprocess
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import verification_command as command


class VerificationCommandTest(unittest.TestCase):
    def git_root(self, root):
        subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
        subprocess.run(['git', 'config', 'user.email', 'test@example.invalid'], cwd=root, check=True)
        subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
        (root / 'seed').write_text('seed')
        subprocess.run(['git', 'add', 'seed'], cwd=root, check=True)
        subprocess.run(['git', 'commit', '-qm', 'seed'], cwd=root, check=True)

    def test_argv_allowlist_and_no_shell_fallback(self):
        self.assertEqual(['python3', '-c', 'print(1)'], command.verification_argv("python3 -c 'print(1)'"))
        for value in ('python3 -c x | cat', 'python3 $(whoami)', 'sh -c true', ''):
            with self.subTest(value=value), self.assertRaises(command.CommandRejected):
                command.verification_argv(value)

    def test_launch_rechecks_the_admitted_executable_digest_before_spawn(self):
        with tempfile.TemporaryDirectory(dir=pathlib.Path.home()) as temporary:
            root = pathlib.Path(temporary).resolve()
            executable = root / 'approved-tool'
            executable.write_text('#!/bin/sh\nexit 0\n')
            executable.chmod(0o700)
            binding = command.verification_sandbox.resolve_executable(
                'approved-tool', toolchain_directories=(str(root),), tmpdir='/not-a-temp-root')
            executable.write_text('#!/bin/sh\nexit 9\n')
            plan = command.verification_sandbox.SandboxPlan(
                [binding['path']], {}, 'off', False, {}, executable_bindings=(binding,))
            prepared = command.PreparedCommand(SimpleNamespace(execution_id='unit'), 'backend',
                'policy', 'qualification', None, plan, (binding['path'],))
            backend = command.CommandExecutionBackend()
            with mock.patch.object(backend.process_backend, 'launch') as launch, \
                 self.assertRaises(command.verification_sandbox.ExecutableResolutionError):
                backend.launch(prepared, command='approved-tool', cwd=root, timeout_seconds=1)
            launch.assert_not_called()

    def test_resolved_plan_binding_is_forwarded_and_swap_fails_before_payload(self):
        import dataclasses
        with tempfile.TemporaryDirectory() as temp, tempfile.TemporaryDirectory(dir=pathlib.Path.home()) as tool_dir:
            root = pathlib.Path(temp).resolve()
            toolchain = pathlib.Path(tool_dir).resolve()
            executable = toolchain / 'python3'
            executable.write_text('#!/bin/sh\nexit 0\n')
            executable.chmod(0o700)
            self.git_root(root)
            resolved_binding = {'command': 'python3', 'path': str(executable),
                                'sha256': command.verification_sandbox._sha256_file(executable)}
            backend = command.CommandExecutionBackend()
            real_build_plan = command.verification_sandbox.build_plan
            real_resolve = command.verification_sandbox.resolve_executable

            def resolve(name, **kwargs):
                if name == 'python3':
                    return resolved_binding
                return real_resolve(name, **kwargs)

            with mock.patch.object(command.verification_sandbox, 'resolve_executable',
                                   side_effect=resolve):
                binding = command.executable_binding_for_command(
                    'python3 -V', worktree=root, cwd=root,
                    toolchain_directories=(str(toolchain),))
            self.assertEqual(resolved_binding, binding)

            def qualified_plan(argv, worktree, run_dir, mode, **kwargs):
                plan = real_build_plan(argv, worktree, run_dir, mode, **kwargs)
                self.assertEqual(binding, plan.executable_bindings[0])
                checks = tuple(command.verification_sandbox.QualificationCheck(
                    check_id, name, 'PASS', 'TEST_ONLY', 'test-evidence')
                    for check_id, name in command.verification_sandbox.QUALIFICATION_CHECKS)
                details = {**plan.details, 'policy_fingerprint': 'test-policy'}
                proof = command.verification_sandbox.BackendQualification(
                    'QUALIFIED', f'{plan.backend}:test', details['policy_fingerprint'],
                    'test-qualification', (), checks, ())
                return dataclasses.replace(plan, details=details, qualification=proof)

            class SwapAfterPlanBackend(command.CommandExecutionBackend):
                requires_detached_supervisor = False

                def prepare(self, **kwargs):
                    prepared = super().prepare(**kwargs)
                    executable.write_text('#!/bin/sh\nexit 1\n')
                    return prepared

            from verification.store import VerificationStore
            from verification.supervisor import SupervisorError, VerificationSupervisor
            store = VerificationStore(root, control_root=root / 'control')
            backend = SwapAfterPlanBackend()
            with mock.patch('agent_harness.sandbox.resolve_executable',
                            side_effect=resolve), \
                 mock.patch.object(command.verification_sandbox, 'build_plan',
                                   side_effect=qualified_plan), \
                 mock.patch.object(backend.process_backend, 'launch') as launch, \
                 self.assertRaisesRegex(SupervisorError, 'executable-binding-changed'):
                VerificationSupervisor(store).execute(backend, worktree=root,
                    family_id='resolved-family', attempt_id='resolved-attempt', gate_id='python3',
                    command='python3 -V', cwd=root, run_dir=root / 'run', timeout_seconds=1,
                    sandbox_mode='off', toolchain_directories=(str(toolchain),),
                    plan_id='verification-plan-v2:sha256:' + 'b' * 64,
                    expected_executable_binding=binding)
            launch.assert_not_called()

    def test_post_timeout_handle_drain_is_bounded_and_uncertain_not_success(self):
        identity = command.verification_sandbox.ExecutionIdentity(
            'execution', 'backend', '/unit', 'repository', 'policy', 'birth')
        process = mock.Mock()
        process.communicate.side_effect = subprocess.TimeoutExpired('payload', 2)
        backend = command.CommandExecutionBackend()
        backend._running[identity.execution_id] = process
        with mock.patch.object(backend.process_backend, 'cancel_and_drain',
                               return_value=command.verification_sandbox.Reconciliation(
                                   'DRAINED', 'CANCEL_DRAINED', 'unit-stopped')) as drain:
            result = backend.cancel_and_drain(identity, 2)
        self.assertEqual('UNCERTAIN', result.status)
        self.assertEqual('DRAINED_HANDLE_STILL_OPEN', result.reason_code)
        drain.assert_called_once_with(identity, 2)
        process.communicate.assert_called_once_with(timeout=2)

    def test_R15_unqualified_backend_never_executes_payload_even_when_sandbox_is_off(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            sentinel = root / 'sentinel'
            with mock.patch.object(command.CommandExecutionBackend, 'launch') as launch:
                result = command.run_command(
                    f"python3 -c 'open(\\\"{sentinel}\\\", \\\"w\\\").write(\\\"ran\\\")'",
                    cwd=root, run_dir=root / 'run', timeout_seconds=5, sandbox_mode='off',
                    required_capabilities=False)
            self.assertEqual('backend-not-v2-qualified', result.error)
            self.assertFalse(sentinel.exists())
            launch.assert_not_called()

    def test_docker_discovery_alone_cannot_launch_in_auto_or_required_mode(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            docker = root / 'docker'
            docker.write_text('#!/bin/sh\nexit 0\n')
            docker.chmod(0o755)
            sentinel = root / 'sentinel'
            real_resolve = command.verification_sandbox.resolve_executable
            def resolve(name, **kwargs):
                if name in {'codex', 'bwrap', 'sandbox-exec'}:
                    raise command.verification_sandbox.ExecutableResolutionError('trusted-executable-not-found')
                if name == 'docker':
                    return {'command': 'docker', 'path': str(docker),
                            'sha256': command.verification_sandbox._sha256_file(docker)}
                return real_resolve(name, **kwargs)
            with mock.patch.object(command.verification_sandbox.platform, 'system',
                                   return_value='Darwin'), \
                 mock.patch.object(command.verification_sandbox.shutil, 'which',
                                   side_effect=AssertionError('backend lookup bypassed shared resolver')), \
                 mock.patch('agent_harness.sandbox.resolve_executable', side_effect=resolve):
                status = command.verification_sandbox.doctor()
                self.assertTrue(status['backend_status']['docker-container']['discovered'])
                self.assertFalse(status['backend_status']['docker-container']['qualified'])
                self.assertFalse(status['backend_status']['docker-container']['launch_ready'])
                with mock.patch.object(command.CommandExecutionBackend, 'launch') as launch:
                    automatic = command.run_command(
                        f"python3 -c 'open(\\\"{sentinel}\\\", \\\"w\\\").write(\\\"ran\\\")'",
                        cwd=root, run_dir=root / 'run-auto', timeout_seconds=5,
                        sandbox_mode='auto')
                    required = command.run_command(
                        f"python3 -c 'open(\\\"{sentinel}\\\", \\\"w\\\").write(\\\"ran\\\")'",
                        cwd=root, run_dir=root / 'run-required', timeout_seconds=5,
                        sandbox_mode='required')
            self.assertEqual('backend-not-v2-qualified', automatic.error)
            self.assertEqual('VERIFICATION_SANDBOX_UNAVAILABLE', required.error)
            self.assertFalse(sentinel.exists())
            launch.assert_not_called()

    def test_R15_backend_discovery_or_prepare_failure_never_reaches_payload(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            with mock.patch.object(command.verification_sandbox, 'build_plan',
                                   side_effect=RuntimeError('backend-prepare-failed')), \
                 mock.patch.object(command.CommandExecutionBackend, 'launch') as launch:
                result = command.run_command('python3 -V', cwd=root, run_dir=root / 'run',
                                             timeout_seconds=1, required_capabilities=False)
            self.assertEqual('backend-prepare-failed', result.error)
            launch.assert_not_called()

    def test_unqualified_backend_rejects_long_running_payload_before_spawn(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            result = command.run_command(
                "python3 -c 'import time; print(\"ready\",flush=True); time.sleep(10)'",
                cwd=root, run_dir=root / 'run', timeout_seconds=0.1, sandbox_mode='off')
            self.assertEqual('backend-not-v2-qualified', result.error)
            self.assertFalse(result.timed_out)

    def test_invalid_cwd_and_missing_executable_are_errors(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            invalid_cwd = command.run_command('python3 -V', cwd=root / 'missing', run_dir=root / 'a',
                                              timeout_seconds=1, sandbox_mode='off')
            self.assertEqual('invalid-cwd', invalid_cwd.error)
            # Backend qualification precedes executable launch.
            missing = command.run_command('./gradlew test', cwd=root, run_dir=root / 'b',
                                          timeout_seconds=1, sandbox_mode='off')
            self.assertEqual('worktree-gradle-wrapper-invalid', missing.error)

    def test_v2_capability_requirement_fails_before_spawn(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            self.git_root(root)
            result = command.run_command('python3 -V', cwd=root, run_dir=root / 'run',
                                         timeout_seconds=1, sandbox_mode='off',
                                         required_capabilities=True)
            self.assertEqual('backend-not-v2-qualified', result.error)
            self.assertIsNone(result.exit_code)

    def test_claimed_backend_flags_do_not_authorize_spawn(self):
        with tempfile.TemporaryDirectory() as temp, \
             mock.patch.object(command.verification_sandbox, 'build_plan') as build:
            root = pathlib.Path(temp)
            self.git_root(root)
            plan_type = command.verification_sandbox.SandboxPlan
            build.return_value = plan_type(['python3','-V'], {'PATH':'/usr/bin'}, 'claimed', True,
                                           {'protected_paths':True,'descendant_containment':'strong'})
            result = command.run_command('python3 -V', cwd=root, run_dir=root/'run', timeout_seconds=1,
                                         sandbox_mode='auto', required_capabilities=True)
            self.assertEqual('backend-not-v2-qualified', result.error)


if __name__ == '__main__':
    unittest.main()
