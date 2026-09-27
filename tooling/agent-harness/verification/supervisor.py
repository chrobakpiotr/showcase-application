"""Trusted lifecycle journal for verification child executions."""
from __future__ import annotations

import hashlib
import pathlib
import time
import uuid

from .store import StoreError, VerificationStore, publish_create_once, repository_lock


class SupervisorError(RuntimeError):
    """Execution lifecycle could not be established safely."""


class VerificationSupervisor:
    def __init__(self, store: VerificationStore):
        self.store = store

    def execute(self, command_runner, *, worktree: pathlib.Path, family_id: str,
                attempt_id: str, gate_id: str, command: str, cwd: pathlib.Path,
                run_dir: pathlib.Path, timeout_seconds: float, sandbox_mode: str,
                environment=None, preflight=None):
        execution_id = uuid.uuid4().hex
        journal = self.store.executions / execution_id
        with repository_lock(self.store.root):
            self.store.admit_repository_verification()
            ready = preflight() if preflight else None
            if ready is not None and ready.action == 'REUSE':
                return None, ready
            started = {
                'schema_version': 2, 'execution_id': execution_id,
                'repository_id': self.store.repository_id,
                'worktree': str(worktree.resolve()), 'family_id': family_id,
                'attempt_id': attempt_id, 'gate_id': gate_id,
                'backend': 'unqualified', 'started_at': time.time(),
                'launch_intent_hash': hashlib.sha256(command.encode('utf-8')).hexdigest(),
            }
            publish_create_once(journal / 'started.json', started)
            result = command_runner(command, cwd=cwd, run_dir=run_dir,
                                    timeout_seconds=timeout_seconds,
                                    sandbox_mode=sandbox_mode, environment=environment,
                                    required_capabilities=True)
            if result.error == 'backend-not-v2-qualified':
                # No process has launched. This execution journal can be closed
                # because the bridge explicitly refused before spawn.
                publish_create_once(journal / 'drained.json', {
                    'schema_version': 2, 'execution_id': execution_id,
                    'backend': started['backend'], 'status': 'drained',
                    'reason': 'refused-before-launch', 'ended_at': time.time(),
                })
            elif result.sandbox_backend and result.protected_paths and result.descendant_containment == 'strong':
                # This branch is presently unreachable because the bridge does
                # not trust capability flags. Keep the journal unresolved until
                # a qualified backend has a pre-launch identity handshake.
                raise SupervisorError('backend-identity-not-predeclared')
            elif result.error is not None and result.error != 'backend-not-v2-qualified':
                # The bridge's configuration/launch failure occurred before
                # a command process was returned. Conservatively retain an
                # unresolved journal: later admission must not guess drainage.
                raise SupervisorError('verification-owned')
            else:
                # Unknown lifecycle cannot be declared drained. This becomes a
                # repository-wide admission barrier for the next operation.
                raise SupervisorError('verification-owned')
            return result, ready
