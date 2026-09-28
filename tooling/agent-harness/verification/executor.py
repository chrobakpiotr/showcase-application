"""Canonical authoritative plan executor.

The executor owns gate sequencing and result aggregation. Callers provide a
T-001 Plan and trusted profile snapshot; they cannot supply alternate command
authorization or evidence serialization semantics.
"""
from __future__ import annotations

import dataclasses
import pathlib
import time
import uuid
from dataclasses import dataclass

from .model import Evidence, FileIdentity
from .planner import evaluate_ready_gate, seal_pass
from .serialization import default_safety, digest, evidence_record
from .store import StoreError, VerificationStore
from .supervisor import VerificationSupervisor
from verification_command import CommandExecutionBackend


@dataclass(frozen=True)
class GateExecution:
    gate_id: str
    action: str
    outcome: str
    reason: str
    fingerprint: str | None
    command_hash: str
    exit_code: int | None = None
    started_at: float | None = None
    ended_at: float | None = None
    duration_seconds: float | None = None
    stdout: str = ''
    stderr: str = ''
    evidence: Evidence | None = None
    error: str | None = None


@dataclass(frozen=True)
class ExecutionResult:
    family_id: str
    profile_hash: str
    outcome: str
    gates: tuple[GateExecution, ...]
    started_at: float
    ended_at: float
    continuation: str = 'execute-all-and-aggregate'

    def to_record(self, *, safety=None):
        """The one canonical conversion for structured execution output."""
        safety = safety or default_safety()
        gates = []
        for gate in self.gates:
            row = {
                'gate_id': gate.gate_id, 'action': gate.action, 'outcome': gate.outcome,
                'reason': gate.reason, 'fingerprint': gate.fingerprint,
                'command_hash': gate.command_hash, 'exit_code': gate.exit_code,
                'started_at': gate.started_at, 'ended_at': gate.ended_at,
                'duration_seconds': gate.duration_seconds, 'error': gate.error,
            }
            if gate.evidence is not None:
                row['evidence'] = evidence_record(gate.evidence, safety=safety)
            gates.append(row)
        record = {
            'schema_version': 2, 'family_id': self.family_id,
            'profile_hash': self.profile_hash, 'outcome': self.outcome,
            'continuation': self.continuation, 'started_at': self.started_at,
            'ended_at': self.ended_at, 'gates': gates,
        }
        # safe_record's narrow schema is the authority; this envelope is
        # validated here for exact keys, nested terminal receipts and secret policy.
        return _safe_execution_record(record, safety=safety)


def _safe_execution_record(record, *, safety):
    allowed_outcomes = {'PASS', 'FAIL', 'ERROR', 'TIMEOUT', 'SKIPPED', 'NOT_RUN'}
    if set(record) != {'schema_version', 'family_id', 'profile_hash', 'outcome', 'continuation',
                       'started_at', 'ended_at', 'gates'} or record['outcome'] not in allowed_outcomes:
        raise ValueError('invalid-execution-record')
    if not isinstance(record['gates'], list):
        raise ValueError('invalid-execution-record')
    normalized = []
    ids = set()
    gate_keys = {'gate_id', 'action', 'outcome', 'reason', 'fingerprint', 'command_hash', 'exit_code',
                 'started_at', 'ended_at', 'duration_seconds', 'error', 'evidence'}
    for gate in record['gates']:
        if not isinstance(gate, dict) or set(gate) - gate_keys:
            raise ValueError('invalid-execution-record')
        if gate['gate_id'] in ids or gate['outcome'] not in allowed_outcomes:
            raise ValueError('invalid-execution-record')
        ids.add(gate['gate_id'])
        for key in ('gate_id', 'reason', 'error'):
            value = gate.get(key)
            if value is not None and (not isinstance(value, str) or not safety.safe(value)):
                raise ValueError('unsafe-execution-record')
        if gate.get('evidence') is not None:
            from .serialization import validate_evidence_record
            validate_evidence_record(gate['evidence'], safety=safety)
        normalized.append(dict(gate))
    for key in ('family_id',):
        if not isinstance(record[key], str) or not safety.safe(record[key]):
            raise ValueError('unsafe-execution-record')
    if record['outcome'] not in allowed_outcomes or not safety.safe(record['profile_hash']):
        raise ValueError('unsafe-execution-record')
    return {**record, 'gates': normalized}


def execute_plan(repository: pathlib.Path, profile, plan, *, store: VerificationStore | None = None,
                 timeout_seconds: float = 900, sandbox_mode: str = 'auto', safety=None,
                 evidence: dict | None = None, attempt_id: str | None = None):
    safety = safety or default_safety()
    store = store or VerificationStore(repository)
    attempt_id = attempt_id or uuid.uuid4().hex
    started_all = time.time()
    gate_results: list[GateExecution] = []
    reusable = dict(evidence or {})
    outcomes = []
    for decision in plan.decisions:
        gate = decision.node.gate
        if any(item in {'FAIL', 'ERROR', 'TIMEOUT'} for item in outcomes):
            gate_results.append(GateExecution(decision.node.id, 'RUN', 'NOT_RUN', 'blocked-by-failure',
                                              decision.fingerprint, gate.command_hash))
            outcomes.append('NOT_RUN')
            continue
        if decision.action == 'REUSE':
            gate_results.append(GateExecution(decision.node.id, 'REUSE', 'PASS', decision.reason,
                                              decision.fingerprint, gate.command_hash))
            outcomes.append('PASS')
            continue
        try:
            supervisor = VerificationSupervisor(store)
            terminal_evidence = []
            post_ready = []
            def build_terminal_evidence(command_result, current_ready, post_observation):
                if (current_ready is None or current_ready.action != 'RUN' or
                        post_observation.get('stable') is not True or not post_ready):
                    return None
                evidence = seal_pass(current_ready, post_ready[0], family=plan.family,
                    evidence_id=f'{decision.node.id}:{attempt_id}', ownership_token=attempt_id,
                    started_at=command_result.started_at, ended_at=command_result.ended_at, artifacts=())
                terminal_evidence.append(evidence)
                return evidence_record(evidence, safety=safety)
            def publish_terminal_evidence(_command_result, _current_ready, terminal):
                record = terminal.get('verification_evidence')
                if record is None:
                    return None
                store.publish_evidence_record(record)
                store.rebuild_projection(record)
                if terminal_evidence:
                    evidence = terminal_evidence[0]
                else:
                    evidence = Evidence(**{**record,
                        'artifacts': tuple(FileIdentity(**item) for item in record['artifacts']),
                        'dependencies': tuple((item['gate_id'], item['evidence_id'], item['fingerprint'])
                                              for item in record['dependencies'])})
                    terminal_evidence.append(evidence)
                return evidence
            def observe_after_drain(_command_result, current_ready):
                if current_ready is None:
                    return {'stable': False, 'reason': 'ready-gate-missing'}
                after = evaluate_ready_gate(repository, profile, plan.family, decision.node,
                                            evidence=reusable, safety=safety)
                post_ready.append(after)
                return {'pre_fingerprint': current_ready.fingerprint,
                        'post_fingerprint': after.fingerprint,
                        'stable': current_ready.fingerprint == after.fingerprint and after.action == 'RUN'}
            def observe_recovered_after_drain(started):
                # Recovery evidence is applicable only to the exact durable
                # run and gate. A later attempt cannot observe or close it.
                if (started.get('family_id') != plan.family.id or
                        started.get('attempt_id') != attempt_id or
                        started.get('gate_id') != decision.node.id or
                        not started.get('input_fingerprint')):
                    return None
                after = evaluate_ready_gate(repository, profile, plan.family, decision.node,
                                            evidence=reusable, safety=safety)
                return {'status': 'CAPTURED', 'fingerprint': after.fingerprint,
                        'stable': after.fingerprint == started['input_fingerprint'] and after.action == 'RUN'}
            result, ready = supervisor.execute(
                CommandExecutionBackend(), worktree=pathlib.Path(repository), family_id=plan.family.id,
                attempt_id=attempt_id, gate_id=decision.node.id, command=gate.command,
                cwd=pathlib.Path(repository) / gate.cwd,
                run_dir=store.root / 'runs' / plan.family.id / attempt_id / 'sandbox',
                timeout_seconds=timeout_seconds, sandbox_mode=sandbox_mode,
                preflight=lambda: evaluate_ready_gate(repository, profile, plan.family,
                                                     decision.node, evidence=reusable, safety=safety),
                terminal_publisher=publish_terminal_evidence, post_observer=observe_after_drain,
                recovery_observer=observe_recovered_after_drain,
                terminal_record_builder=build_terminal_evidence,
                input_fingerprint=decision.fingerprint)
            if ready is None:
                raise RuntimeError('ready-gate-not-evaluated')
            if ready.action == 'REUSE':
                gate_results.append(GateExecution(decision.node.id, 'REUSE', 'PASS', ready.reason,
                                                  ready.fingerprint, gate.command_hash))
                outcomes.append('PASS')
                continue
            if result is None:
                raise RuntimeError('execution-result-missing')
        except StoreError as exc:
            outcome = 'ERROR'
            gate_results.append(GateExecution(decision.node.id, 'RUN', outcome, 'admission-error',
                                              decision.fingerprint, gate.command_hash, error=str(exc)))
            outcomes.append(outcome)
            for rest in plan.decisions[len(gate_results):]:
                gate_results.append(GateExecution(rest.node.id, 'RUN', 'NOT_RUN', 'blocked-by-failure',
                                                  rest.fingerprint, rest.node.gate.command_hash))
                outcomes.append('NOT_RUN')
            break
        except RuntimeError as exc:
            code = str(exc)
            outcome = 'ERROR'
            gate_results.append(GateExecution(decision.node.id, 'RUN', outcome, 'supervisor-error',
                                              decision.fingerprint, gate.command_hash, error=code))
            outcomes.append(outcome)
            for rest in plan.decisions[len(gate_results):]:
                gate_results.append(GateExecution(rest.node.id, 'RUN', 'NOT_RUN', 'blocked-by-failure',
                                                  rest.fingerprint, rest.node.gate.command_hash))
                outcomes.append('NOT_RUN')
            break
        if result.error:
            outcome = 'ERROR'
        elif result.timed_out:
            outcome = 'TIMEOUT'
        elif result.exit_code == 0:
            outcome = 'PASS'
        else:
            outcome = 'FAIL'
        exec_evidence = None
        if outcome == 'PASS':
            if 'terminal_evidence' not in locals() or not terminal_evidence:
                outcome = 'ERROR'
                gate_results.append(GateExecution(decision.node.id, 'RUN', outcome, 'stale-input',
                    ready.fingerprint, gate.command_hash, result.exit_code, error='post-input-changed'))
                outcomes.append(outcome)
                break
            exec_evidence = terminal_evidence[0]
            reusable[decision.node.id] = exec_evidence
        gate_results.append(GateExecution(decision.node.id, 'RUN', outcome,
                                          'completed' if outcome in {'PASS', 'FAIL'} else outcome.lower(),
                                          ready.fingerprint, gate.command_hash,
                                          result.exit_code, result.started_at, result.ended_at,
                                          result.duration_seconds, result.stdout, result.stderr,
                                          exec_evidence, result.error))
        outcomes.append(outcome)
        if outcome in {'ERROR', 'TIMEOUT', 'FAIL'}:
            # Current SDD contract says remaining unexecuted required gates are
            # explicitly blocked after the first required gate failure.
            for rest in plan.decisions[len(gate_results):]:
                gate_results.append(GateExecution(rest.node.id, 'RUN', 'NOT_RUN', 'blocked-by-failure',
                                                  rest.fingerprint, rest.node.gate.command_hash))
                outcomes.append('NOT_RUN')
            break
    if not outcomes or all(value == 'PASS' for value in outcomes):
        overall = 'PASS'
    elif 'TIMEOUT' in outcomes:
        overall = 'TIMEOUT'
    elif 'ERROR' in outcomes:
        overall = 'ERROR'
    else:
        overall = 'FAIL'
    ended_all = time.time()
    return ExecutionResult(plan.family.id, plan.family.profile_hash, overall, tuple(gate_results),
                           started_all, ended_all)
