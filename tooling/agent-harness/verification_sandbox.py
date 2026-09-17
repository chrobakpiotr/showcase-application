#!/usr/bin/env python3
"""OS-level isolation wrapper for deterministic verification commands.

The command itself must already have passed runner.py's strict allowlist. This module adds a second
boundary: no network and constrained filesystem writes where the host supports it. `auto` prefers
Codex's local sandbox helper, then native platform tooling. `required` fails closed if no strong
backend exists; `off` is an explicit human opt-out.
"""
from __future__ import annotations

import json
import os
import pathlib
import platform
import shlex
import shutil
import subprocess
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class SandboxPlan:
    argv: list[str]
    env: dict[str, str]
    backend: str
    strong_isolation: bool
    details: dict[str, Any]


def _safe_env(worktree: pathlib.Path, sandbox_home: pathlib.Path) -> dict[str, str]:
    sandbox_home.mkdir(parents=True, exist_ok=True)
    (sandbox_home / 'tmp').mkdir(exist_ok=True)
    passthrough = (
        'PATH', 'LANG', 'LC_ALL', 'LC_CTYPE', 'TZ', 'TERM', 'COLORTERM', 'CI',
        'JAVA_HOME', 'JDK_HOME', 'SDKROOT', 'DEVELOPER_DIR',
        'SYSTEMROOT', 'WINDIR', 'PATHEXT', 'COMSPEC',
    )
    env = {key: os.environ[key] for key in passthrough if key in os.environ}
    env.update({
        'HOME': str(sandbox_home),
        'TMPDIR': str(sandbox_home / 'tmp'),
        'XDG_CACHE_HOME': str(sandbox_home / 'cache'),
        'GRADLE_USER_HOME': str(sandbox_home / 'gradle'),
        'npm_config_cache': str(sandbox_home / 'npm-cache'),
        'PIP_CACHE_DIR': str(sandbox_home / 'pip-cache'),
        'GIT_TERMINAL_PROMPT': '0',
        'CI': env.get('CI', 'true'),
        # Common proxy variables are cleared as defense in depth. Strong backends also isolate network.
        'HTTP_PROXY': '', 'HTTPS_PROXY': '', 'ALL_PROXY': '', 'NO_PROXY': '*',
        'http_proxy': '', 'https_proxy': '', 'all_proxy': '', 'no_proxy': '*',
    })
    # Prevent Gradle from trying to share a daemon across sandboxed invocations.
    env.setdefault('GRADLE_OPTS', '-Dorg.gradle.daemon=false')
    # Optional explicitly prepared read-only Gradle dependency cache. Do not point this at the
    # live ~/.gradle cache: Gradle's shared-cache contract expects a separately seeded directory.
    ro_cache = os.environ.get('AGENTIC_SDD_GRADLE_RO_DEP_CACHE')
    if ro_cache:
        candidate = pathlib.Path(ro_cache).expanduser().resolve()
        if candidate.is_dir() and (candidate / 'modules-2').is_dir():
            env['GRADLE_RO_DEP_CACHE'] = str(candidate)
    return env


def _codex_helper(argv: list[str], worktree: pathlib.Path, env: dict[str, str]) -> SandboxPlan | None:
    codex = shutil.which('codex')
    if not codex:
        return None
    system = platform.system().lower()
    platform_name = 'macos' if system == 'darwin' else 'linux' if system == 'linux' else None
    if not platform_name:
        return None
    return SandboxPlan(
        [codex, 'sandbox', platform_name, '--full-auto', *argv],
        env,
        f'codex-{platform_name}',
        True,
        {'network': 'disabled', 'filesystem': 'workspace-write'},
    )


def _bubblewrap(argv: list[str], worktree: pathlib.Path, sandbox_home: pathlib.Path, env: dict[str, str]) -> SandboxPlan | None:
    bwrap = shutil.which('bwrap')
    if not bwrap or platform.system().lower() != 'linux':
        return None
    cmd = [
        bwrap, '--die-with-parent', '--new-session', '--unshare-all', '--share-user',
        '--ro-bind', '/', '/',
        '--bind', str(worktree), str(worktree),
        '--bind', str(sandbox_home), str(sandbox_home),
        '--proc', '/proc', '--dev', '/dev',
        '--chdir', str(worktree),
    ]
    for key in ('HOME', 'TMPDIR', 'XDG_CACHE_HOME', 'GRADLE_USER_HOME', 'npm_config_cache', 'PIP_CACHE_DIR',
                'GIT_TERMINAL_PROMPT', 'CI', 'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'NO_PROXY',
                'http_proxy', 'https_proxy', 'all_proxy', 'no_proxy', 'GRADLE_OPTS', 'GRADLE_RO_DEP_CACHE'):
        if key in env:
            cmd += ['--setenv', key, env[key]]
    cmd += ['--', *argv]
    return SandboxPlan(cmd, env, 'bubblewrap', True, {'network': 'unshared', 'root': 'read-only', 'worktree': 'writable'})


def _macos_sandbox_exec(argv: list[str], worktree: pathlib.Path, sandbox_home: pathlib.Path, env: dict[str, str]) -> SandboxPlan | None:
    sandbox_exec = shutil.which('sandbox-exec')
    if not sandbox_exec or platform.system().lower() != 'darwin':
        return None
    # Keep the policy deliberately small: read system/toolchain files, write only task workspace and
    # isolated build home, and expose no network. This is stricter than relying on shell allowlists.
    def q(value: pathlib.Path) -> str:
        return json.dumps(str(value))
    profile = sandbox_home / 'verification.sb'
    profile.write_text(
        '(version 1)\n'
        '(deny default)\n'
        '(allow process*)\n'
        '(allow file-read*)\n'
        '(allow sysctl-read)\n'
        '(allow mach-lookup)\n'
        '(allow ipc-posix*)\n'
        '(allow signal)\n'
        f'(allow file-write* (subpath {q(worktree)}) (subpath {q(sandbox_home)}))\n',
        encoding='utf-8',
    )
    return SandboxPlan(
        [sandbox_exec, '-f', str(profile), *argv], env, 'macos-sandbox-exec', True,
        {'network': 'denied-by-default', 'write_roots': [str(worktree), str(sandbox_home)]},
    )


def build_plan(
    argv: list[str], worktree: pathlib.Path, run_dir: pathlib.Path, mode: str = 'auto'
) -> SandboxPlan:
    if mode not in {'auto', 'required', 'off'}:
        raise ValueError(f'unknown verification sandbox mode: {mode}')
    sandbox_home = run_dir / 'verification-home'
    env = _safe_env(worktree, sandbox_home)
    if mode == 'off':
        return SandboxPlan(argv, env, 'off', False, {'explicit_opt_out': True})

    for factory in (
        lambda: _codex_helper(argv, worktree, env),
        lambda: _bubblewrap(argv, worktree, sandbox_home, env),
        lambda: _macos_sandbox_exec(argv, worktree, sandbox_home, env),
    ):
        plan = factory()
        if plan is not None:
            return plan

    if mode == 'required':
        raise RuntimeError(
            'no strong verification sandbox available; install Codex CLI or bubblewrap, '
            'or use macOS sandbox-exec. Use --verification-sandbox off only as an explicit opt-out.'
        )
    return SandboxPlan(
        argv, env, 'allowlist-only', False,
        {'warning': 'OS sandbox unavailable; strict command allowlist and isolated environment only'},
    )


def doctor() -> dict[str, Any]:
    system = platform.system().lower()
    backends = {
        'codex-sandbox': bool(shutil.which('codex')) and system in {'darwin', 'linux'},
        'bubblewrap': bool(shutil.which('bwrap')) and system == 'linux',
        'macos-sandbox-exec': bool(shutil.which('sandbox-exec')) and system == 'darwin',
    }
    return {'platform': system, 'strong_available': any(backends.values()), 'backends': backends}
