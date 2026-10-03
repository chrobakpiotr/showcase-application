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
from machine_outcomes import classify_reason, exit_code


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
        code = exit_code(self.outcome)
        if code is not None:
            record['machine_category'] = self.outcome
            record['exit_code'] = code
        # safe_record's narrow schema is the authority; this envelope is
        # validated here for exact keys, nested terminal receipts and secret policy.
        return _safe_execution_record(record, safety=safety)


def _safe_execution_record(record, *, safety):
    allowed_outcomes = {'PASS', 'FAIL', 'ERROR', 'TIMEOUT', 'SKIPPED', 'NOT_RUN',
                        'needs-human', 'verification-blocked', 'verification-owned'}
    allowed_record_keys = {'schema_version', 'family_id', 'profile_hash', 'outcome', 'continuation',
                           'started_at', 'ended_at', 'gates', 'machine_category', 'exit_code'}
    if set(record) - allowed_record_keys or not {'schema_version', 'family_id', 'profile_hash', 'outcome',
            'continuation', 'started_at', 'ended_at', 'gates'} <= set(record) or record['outcome'] not in allowed_outcomes:
        raise ValueError('invalid-execution-record')
    machine = record.get('machine_category')
    if machine is not None and (machine != record['outcome'] or exit_code(machine) != record.get('exit_code')):
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
                 evidence: dict | None = None, attempt_id: str | None = None,
                 failure_grants: dict[str, str] | None = None,
                 authority_context: dict | None = None):
    if authority_context is not None:
        raise StoreError('VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE')
    safety = safety or default_safety()
    store = store or VerificationStore(repository)
    attempt_id = attempt_id or uuid.uuid4().hex
    started_all = time.time()
    gate_results: list[GateExecution] = []
    reusable = dict(evidence or {})
    outcomes = []
    from .candidate import CandidateSealError, seal_candidate
    try:
        candidate_seal = seal_candidate(repository, plan.family.base_sha, {
            'family_id': plan.family.id, 'profile_hash': plan.family.profile_hash,
            'policy_checkpoint': plan.family.policy_checkpoint,
            'origin_policy': plan.family.origin_policy,
        }, trusted_runtime_root=store.root)
    except CandidateSealError as exc:
        blocked = tuple(GateExecution(item.node.id, 'RUN', 'verification-blocked',
            exc.reason, item.fingerprint, item.node.gate.command_hash) for item in plan.decisions)
        return ExecutionResult(plan.family.id, plan.family.profile_hash, 'verification-blocked',
                               blocked, started_all, time.time())
    plan = dataclasses.replace(plan, family=dataclasses.replace(plan.family,
        candidate_identity=candidate_seal.candidate_identity,
        final_changed_surface_id=candidate_seal.changed_surface_id))
    for decision in plan.decisions:
        gate = decision.node.gate
        if any(item in {'FAIL', 'ERROR', 'TIMEOUT'} for item in outcomes):
            gate_results.append(GateExecution(decision.node.id, 'RUN', 'NOT_RUN', 'blocked-by-failure',
                                              decision.fingerprint, gate.command_hash))
            outcomes.append('NOT_RUN')
            continue
        try:
            retry_scope = None
            if isinstance(authority_context, dict):
                obligations = authority_context.get('obligations', [])
                units = authority_context.get('execution_units', [])
                obligation = next((item for item in obligations
                    if item.get('gate_id') == decision.node.id), None)
                unit = next((item for item in units if obligation and
                    obligation['obligation_id'] in item.get('obligation_ids', [])), None)
                if obligation is None or unit is None:
                    raise StoreError('PLAN_BINDING_MISMATCH')
                scope_material = {'plan_id': authority_context['plan_id'],
                    'lifecycle_generation': authority_context['lifecycle_generation'],
                    'task_attempt': authority_context['task_attempt'],
                    'obligation_id': obligation['obligation_id'], 'unit_id': unit['unit_id']}
                retry_scope = {
                    'plan_id': authority_context['plan_id'],
                    'family_id': plan.family.id,
                    'candidate_identity': plan.family.candidate_identity,
                    'final_changed_surface_id': plan.family.final_changed_surface_id,
                    'lifecycle_generation': authority_context['lifecycle_generation'],
                    'task_id': authority_context['task_id'],
                    'task_attempt': authority_context['task_attempt'],
                    'gate_id': decision.node.id,
                    'obligation_id': obligation['obligation_id'],
                    'unit_id': unit['unit_id'],
                    'retry_slot': 'critical-retry-slot-v1:sha256:' + digest(scope_material),
                    'profile_hash': plan.family.profile_hash,
                    'policy_checkpoint': plan.family.policy_checkpoint,
                    'origin_binding': authority_context['origin_binding'],
                    'fence_fingerprint': decision.fingerprint,
                }
            supervisor = VerificationSupervisor(store)
            terminal_evidence = []
            execution_terminals = []
            post_ready = []
            def build_terminal_evidence(command_result, current_ready, post_observation):
                if (current_ready is None or current_ready.action != 'RUN' or
                        post_observation.get('stable') is not True or not post_ready):
                    return None
                if not current_ready.cacheable:
                    return None
                evidence = seal_pass(current_ready, post_ready[0], family=plan.family,
                    evidence_id=f'{decision.node.id}:{attempt_id}', ownership_token=attempt_id,
                    started_at=command_result.started_at, ended_at=command_result.ended_at, artifacts=(),
                    candidate_identity=candidate_seal.candidate_identity,
                    final_changed_surface_id=candidate_seal.changed_surface_id)
                terminal_evidence.append(evidence)
                return evidence_record(evidence, safety=safety)
            def publish_terminal_evidence(_command_result, _current_ready, terminal):
                execution_terminals.append(terminal)
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
                try:
                    after = evaluate_ready_gate(repository, profile, plan.family, decision.node,
                                                evidence=reusable, safety=safety,
                                                trusted_runtime_root=store.root)
                except Exception as exc:
                    return {'stable': False, 'reason': getattr(exc, 'args', ['post-seal-invalid'])[0]}
                post_ready.append(after)
                return {'pre_fingerprint': current_ready.fingerprint,
                        'post_fingerprint': after.fingerprint,
                        'candidate_identity': candidate_seal.candidate_identity,
                        'final_changed_surface_id': candidate_seal.changed_surface_id,
                        'stable': (current_ready.fingerprint == after.fingerprint and
                                  after.action in {'RUN', 'REUSE'})}
            def observe_recovered_after_drain(started):
                # Recovery evidence is applicable only to the exact durable
                # run and gate. A later attempt cannot observe or close it.
                if (started.get('family_id') != plan.family.id or
                        started.get('attempt_id') != attempt_id or
                        started.get('gate_id') != decision.node.id or
                        started.get('candidate_identity') != candidate_seal.candidate_identity or
                        started.get('final_changed_surface_id') != candidate_seal.changed_surface_id or
                        not started.get('input_fingerprint')):
                    return None
                try:
                    after = evaluate_ready_gate(repository, profile, plan.family, decision.node,
                                                evidence=reusable, safety=safety,
                                                trusted_runtime_root=store.root)
                except Exception:
                    return None
                return {'status': 'CAPTURED', 'fingerprint': after.fingerprint,
                        'candidate_identity': candidate_seal.candidate_identity,
                        'final_changed_surface_id': candidate_seal.changed_surface_id,
                        'stable': after.fingerprint == started['input_fingerprint'] and after.action == 'RUN'}
            result, ready = supervisor.execute(
                CommandExecutionBackend(), worktree=pathlib.Path(repository), family_id=plan.family.id,
                attempt_id=attempt_id, gate_id=decision.node.id, command=gate.command,
                cwd=pathlib.Path(repository) / gate.cwd,
                run_dir=store.root / 'runs' / plan.family.id / attempt_id / 'sandbox',
                timeout_seconds=timeout_seconds, sandbox_mode=sandbox_mode,
                preflight=lambda: evaluate_ready_gate(repository, profile, plan.family,
                                                     decision.node, evidence=reusable, safety=safety,
                                                     trusted_runtime_root=store.root),
                terminal_publisher=publish_terminal_evidence, post_observer=observe_after_drain,
                recovery_observer=observe_recovered_after_drain,
                terminal_record_builder=build_terminal_evidence,
                input_fingerprint=decision.fingerprint, profile_hash=plan.family.profile_hash,
                critical=gate.critical,
                retry_policy=gate.retry_policy, retry_controls=gate.retry_controls,
                failure_grant_id=(failure_grants or {}).get(decision.node.id),
                candidate_identity=candidate_seal.candidate_identity,
                final_changed_surface_id=candidate_seal.changed_surface_id,
                plan_id=authority_context.get('plan_id') if isinstance(authority_context, dict) else None,
                retry_scope=retry_scope, policy_checkpoint=plan.family.policy_checkpoint)
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
            category = classify_reason(str(exc))
            outcome = category or 'ERROR'
            gate_results.append(GateExecution(decision.node.id, 'RUN', outcome,
                                              'control-outcome' if category else 'admission-error',
                                              decision.fingerprint, gate.command_hash, error=str(exc)))
            outcomes.append(outcome)
            for rest in plan.decisions[len(gate_results):]:
                gate_results.append(GateExecution(rest.node.id, 'RUN', 'NOT_RUN', 'blocked-by-failure',
                                                  rest.fingerprint, rest.node.gate.command_hash))
                outcomes.append('NOT_RUN')
            break
        except RuntimeError as exc:
            code = str(exc)
            category = classify_reason(code)
            outcome = category or 'ERROR'
            reason = 'control-outcome' if category else ('retry-policy-violation' if code == 'retry-policy-violation' else 'supervisor-error')
            gate_results.append(GateExecution(decision.node.id, 'RUN', outcome, reason,
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
            if not ready.cacheable:
                terminal = execution_terminals[0] if execution_terminals else None
                if (terminal is None or terminal.get('result') != 'PASS' or
                        terminal.get('drainage') != 'DRAINED' or
                        terminal.get('post_observation', {}).get('stable') is not True):
                    outcome = 'ERROR'
                    gate_results.append(GateExecution(decision.node.id, 'RUN', outcome, 'stale-input',
                        ready.fingerprint, gate.command_hash, result.exit_code, error='post-input-changed'))
                    outcomes.append(outcome)
                    break
            elif 'terminal_evidence' not in locals() or not terminal_evidence:
                outcome = 'ERROR'
                gate_results.append(GateExecution(decision.node.id, 'RUN', outcome, 'stale-input',
                    ready.fingerprint, gate.command_hash, result.exit_code, error='post-input-changed'))
                outcomes.append(outcome)
                break
            if ready.cacheable:
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
    elif 'needs-human' in outcomes:
        overall = 'needs-human'
    elif 'verification-blocked' in outcomes:
        overall = 'verification-blocked'
    elif 'verification-owned' in outcomes:
        overall = 'verification-owned'
    elif 'TIMEOUT' in outcomes:
        overall = 'TIMEOUT'
    elif 'ERROR' in outcomes:
        overall = 'ERROR'
    else:
        overall = 'FAIL'
    ended_all = time.time()
    return ExecutionResult(plan.family.id, plan.family.profile_hash, overall, tuple(gate_results),
                           started_all, ended_all)
