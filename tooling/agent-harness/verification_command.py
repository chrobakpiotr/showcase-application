"""Neutral command authorization and sandbox invocation bridge.

This module is shared by the legacy runner and the v2 verification executor. It
does not import the runner and deliberately has no shell fallback.
"""
from __future__ import annotations

import pathlib
import shlex
import subprocess
import time
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
        argv = verification_argv(command)
        try:
            resolved_cwd = pathlib.Path(cwd).resolve(strict=True)
        except FileNotFoundError:
            raise CommandRejected('invalid-cwd') from None
        if not resolved_cwd.is_dir():
            raise CommandRejected('invalid-cwd')
        sandbox = verification_sandbox.build_plan(argv, resolved_cwd, run_dir, sandbox_mode)
        env = dict(sandbox.env)
        if environment:
            if any(not isinstance(k, str) or not isinstance(v, str) or k not in env for k, v in environment.items()):
                raise CommandRejected('environment-override-not-allowed')
            env.update(environment)
        # Every invocation is requested verification payload. A sandbox plan or
        # capability-shaped metadata is not qualification evidence; only the
        # complete policy-bound adversarial record can admit the command.
        proof = getattr(sandbox, 'qualification', None)
        protected = bool(proof is not None and proof.authorizes_execution)
        containment = 'strong' if protected else 'none'
        details = getattr(sandbox, 'details', {})
        if not (protected and proof.policy_identity == details.get('policy_fingerprint') and
                proof.backend_identity == details.get('backend_identity') and
                proof.backend_identity.startswith(f'{sandbox.backend}:')):
            raise CommandEnvironmentBlocked('backend-not-v2-qualified')
        proc = subprocess.Popen(sandbox.argv, cwd=resolved_cwd, env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                start_new_session=True)
        try:
            out, err = proc.communicate(timeout=timeout_seconds)
            ended = time.time()
            return CommandResult(tuple(argv), str(resolved_cwd), started, ended,
                                 time.monotonic() - monotonic, proc.returncode,
                                 _decode(out), _decode(err), sandbox_backend=sandbox.backend,
                                 strong_isolation=sandbox.strong_isolation,
                                 protected_paths=protected, descendant_containment=containment)
        except subprocess.TimeoutExpired as exc:
            # Kill the process group so ordinary descendants cannot outlive the
            # timeout. communicate() after kill drains both pipes before return.
            import os, signal
            try:
                os.killpg(proc.pid, signal.SIGKILL)
            except (ProcessLookupError, PermissionError):
                proc.kill()
            tail_out, tail_err = proc.communicate()
            out = _decode(exc.output) + _decode(tail_out)
            err = _decode(exc.stderr) + _decode(tail_err)
            ended = time.time()
            return CommandResult(tuple(argv), str(resolved_cwd), started, ended,
                                 time.monotonic() - monotonic, proc.returncode,
                                 out, err, timed_out=True, sandbox_backend=sandbox.backend,
                                 strong_isolation=sandbox.strong_isolation,
                                 protected_paths=protected, descendant_containment=containment)
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
    except (RuntimeError, ValueError, NotADirectoryError):
        ended = time.time()
        return CommandResult((), str(cwd), started, ended, time.monotonic() - monotonic,
                             None, '', '', error='execution-configuration-error')
