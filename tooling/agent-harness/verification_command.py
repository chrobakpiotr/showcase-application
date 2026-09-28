"""Neutral command authorization and sandbox invocation bridge.

This module is shared by the legacy runner and the v2 verification executor. It
does not import the runner and deliberately has no shell fallback.
"""
from __future__ import annotations

import pathlib
import shlex
import subprocess
import time
import hashlib
import uuid
from dataclasses import dataclass
from typing import Mapping

import verification_sandbox


class CommandRejected(ValueError):
    """Command does not satisfy the repository verification allowlist."""


class CommandEnvironmentBlocked(RuntimeError):
    """Execution cannot meet the required sandbox contract."""


SHELL_META = {'|', '||', '&&', ';', '>', '>>', '<', '<<', '&'}


def verification_argv(command: str) -> list[str]:
    if not isinstance(command, str) or not command.strip():
        raise CommandRejected('invalid-command')
    try:
        argv = shlex.split(command, posix=True)
    except ValueError:
        raise CommandRejected('invalid-command') from None
    if (not argv or any(token in SHELL_META or '$(' in token or '`' in token or '${' in token
                        for token in argv)):
        raise CommandRejected('unsupported-command-composition')
    executable = argv[0]
    allowed = executable in {'./gradlew', 'python3', 'python', 'npm', 'npx', 'true'}
    if executable == 'docker':
        allowed = len(argv) >= 2 and (argv[1] == 'build' or argv[1:3] == ['compose', 'config'])
    elif executable == 'helm':
        allowed = len(argv) >= 2 and argv[1] in {'lint', 'template'}
    elif executable == 'terraform':
        allowed = len(argv) >= 2 and argv[1] in {'fmt', 'validate'}
    elif executable == 'git':
        allowed = len(argv) >= 2 and argv[1] in {'diff', 'status'}
    if not allowed:
        raise CommandRejected('command-not-allowlisted')
    return argv


@dataclass(frozen=True)
class CommandResult:
    argv: tuple[str, ...]
    cwd: str
    started_at: float
    ended_at: float
    duration_seconds: float
    exit_code: int | None
    stdout: str
    stderr: str
    timed_out: bool = False
    error: str | None = None
    sandbox_backend: str | None = None
    strong_isolation: bool = False
    protected_paths: bool = False
    descendant_containment: str = 'none'


@dataclass(frozen=True)
class PreparedCommand:
    identity: object
    backend_identity: str
    policy_identity: str
    qualification_fingerprint: str
    backend_prepared: object
    sandbox_plan: object
    argv: tuple[str, ...]


class CommandExecutionBackend:
    """Phase-B qualified command facade consumed by the durable supervisor."""
    def __init__(self):
        self.process_backend = verification_sandbox.PosixProcessGroupBackend()
        self._running = {}

    def prepare(self, *, worktree, cwd, run_dir, repository_id, command, sandbox_mode,
                environment=None, control_root=None, **_kwargs):
        argv = verification_argv(command)
        plan = verification_sandbox.build_plan(argv, pathlib.Path(cwd), pathlib.Path(run_dir), sandbox_mode)
        proof = plan.qualification
        if not (isinstance(proof, verification_sandbox.BackendQualification) and
                verification_sandbox.qualifies_v2(plan) and
                proof.policy_identity == plan.details.get('policy_fingerprint') and
                proof.backend_identity.startswith(plan.backend + ':')):
            raise CommandEnvironmentBlocked('backend-not-v2-qualified')
        execution_id = _kwargs.get('execution_id') or uuid.uuid4().hex
        unit_root = pathlib.Path(control_root or run_dir) / 'backend-units'
        unit_dir = unit_root / execution_id
        state_path = unit_dir / 'unit.json'
        if state_path.exists():
            state = self.process_backend._read_state(state_path)
            identity = verification_sandbox.ExecutionIdentity.from_dict(state['identity'])
            prior_started = pathlib.Path(control_root or run_dir) / 'executions' / execution_id / 'started.json'
            reusable_terminal = prior_started.exists() and state.get('state') in {'DRAINED', 'LAUNCH_FAILED'}
            if (identity.execution_id != execution_id or identity.repository_id != repository_id or
                    identity.policy_identity != proof.policy_identity or
                    identity.backend_identity != proof.backend_identity or
                    (state.get('state') != 'PREPARED' and not reusable_terminal)):
                raise RuntimeError('execution-identity-conflict')
            prepared = verification_sandbox.PreparedExecution(identity, state_path)
        else:
            prepared = self.process_backend.prepare(unit_root, repository_id=repository_id,
                policy_identity=proof.policy_identity, execution_id=execution_id,
                backend_identity=proof.backend_identity)
        return PreparedCommand(prepared.identity, proof.backend_identity, proof.policy_identity,
                               proof.fingerprint, prepared, plan, tuple(argv))

    def launch(self, prepared: PreparedCommand, *, command, cwd, timeout_seconds, environment=None):
        started = time.time()
        monotonic = time.monotonic()
        env = dict(prepared.sandbox_plan.env)
        if environment:
            if any(not isinstance(key, str) or not isinstance(value, str) or key not in env
                   for key, value in environment.items()):
                raise CommandRejected('environment-override-not-allowed')
            env.update(environment)
        running = self.process_backend.launch(prepared.backend_prepared, list(prepared.sandbox_plan.argv),
                                              cwd=pathlib.Path(cwd), env=env)
        self._running[prepared.identity.execution_id] = running.process
        try:
            stdout, stderr = running.process.communicate(timeout=timeout_seconds)
            self._running.pop(prepared.identity.execution_id, None)
            return CommandResult(prepared.argv, str(pathlib.Path(cwd).resolve()), started, time.time(),
                time.monotonic() - monotonic, running.process.returncode, _decode(stdout), _decode(stderr),
                sandbox_backend=prepared.sandbox_plan.backend, strong_isolation=prepared.sandbox_plan.strong_isolation,
                protected_paths=True, descendant_containment='strong')
        except subprocess.TimeoutExpired as exc:
            # The supervisor must persist CANCELLING before the control-plane
            # cancellation request, so return control with the unit still owned.
            return CommandResult(prepared.argv, str(pathlib.Path(cwd).resolve()), started, time.time(),
                time.monotonic() - monotonic, running.process.returncode,
                _decode(exc.output), _decode(exc.stderr), timed_out=True,
                sandbox_backend=prepared.sandbox_plan.backend, strong_isolation=prepared.sandbox_plan.strong_isolation,
                protected_paths=True, descendant_containment='strong')

    def inspect(self, identity):
        return self.process_backend.inspect(_execution_identity(identity))

    def cancel_and_drain(self, identity, timeout):
        execution_identity = _execution_identity(identity)
        result = self.process_backend.cancel_and_drain(execution_identity, timeout)
        process = self._running.get(execution_identity.execution_id)
        if result.status == 'DRAINED' and process is not None:
            try:
                process.communicate(timeout=2)
            except subprocess.TimeoutExpired:
                # The process-unit backend already proved all members gone;
                # a handle timeout cannot justify parent-only termination.
                return type(result)('UNCERTAIN', 'DRAINED_HANDLE_STILL_OPEN',
                                    'drainage-proved-but-parent-handle-did-not-close')
            self._running.pop(execution_identity.execution_id, None)
        return result


def _execution_identity(value):
    if isinstance(value, verification_sandbox.ExecutionIdentity):
        return value
    return verification_sandbox.ExecutionIdentity.from_dict(value)


def _decode(value):
    if value is None:
        return ''
    if isinstance(value, bytes):
        return value.decode('utf-8', errors='replace')
    return str(value)


def run_command(command: str, *, cwd: pathlib.Path, run_dir: pathlib.Path,
                timeout_seconds: float, sandbox_mode: str = 'auto',
                environment: Mapping[str, str] | None = None,
                required_capabilities: bool = True) -> CommandResult:
    """Run one allowlisted command, capturing diagnostics and classifying failure.

    Shell syntax is never interpreted. `environment`, when given, may only
    override variables already admitted by the sandbox environment.
    """
    started = time.time()
    monotonic = time.monotonic()
    try:
        verification_argv(command)
        try:
            resolved_cwd = pathlib.Path(cwd).resolve(strict=True)
        except (FileNotFoundError, OSError):
            raise CommandRejected('invalid-cwd') from None
        if not resolved_cwd.is_dir():
            raise CommandRejected('invalid-cwd')
        from verification.store import VerificationStore
        from verification.supervisor import VerificationSupervisor, SupervisorError
        store = VerificationStore(resolved_cwd)
        result, _ = VerificationSupervisor(store).execute(CommandExecutionBackend(),
            worktree=resolved_cwd, family_id='direct-command', attempt_id=uuid.uuid4().hex,
            gate_id='direct-' + hashlib.sha256(command.encode()).hexdigest()[:24], command=command,
            cwd=resolved_cwd, run_dir=run_dir, timeout_seconds=timeout_seconds,
            sandbox_mode=sandbox_mode, environment=environment)
        return result
    except CommandEnvironmentBlocked as exc:
        ended = time.time()
        return CommandResult((), str(cwd), started, ended, time.monotonic() - monotonic,
                             None, '', '', error=str(exc))
    except CommandRejected as exc:
        ended = time.time()
        return CommandResult((), str(cwd), started, ended, time.monotonic() - monotonic,
                             None, '', '', error=str(exc))
    except subprocess.TimeoutExpired:
        ended = time.time()
        return CommandResult((), str(cwd), started, ended, time.monotonic() - monotonic,
                             None, '', '', timed_out=True, error='timeout')
    except OSError as exc:
        ended = time.time()
        category = 'missing-executable' if isinstance(exc, FileNotFoundError) else 'permission-or-process-error'
        return CommandResult((), str(cwd), started, ended, time.monotonic() - monotonic,
                             None, '', '', error=category)
    except SupervisorError as exc:
        ended = time.time()
        return CommandResult((), str(cwd), started, ended, time.monotonic() - monotonic,
                             None, '', '', error=str(exc))
    except (RuntimeError, ValueError, NotADirectoryError) as exc:
        ended = time.time()
        return CommandResult((), str(cwd), started, ended, time.monotonic() - monotonic,
                             None, '', '', error='execution-configuration-error')
