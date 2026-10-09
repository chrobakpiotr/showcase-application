#!/usr/bin/env python3
"""Parallel outer-loop orchestrator for the repo-native SDD protocol.

This process owns task leasing/heartbeats, crash recovery, worktree lifecycle, provider routing,
specialist review, bounded rework and completion checkpoints. Coding-agent processes stay inside
runner.py and are never allowed to push or mutate repository remotes.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import contextlib
import datetime as dt
import json
import math
import pathlib
import re
import subprocess
import sys
import threading
import uuid
from dataclasses import dataclass
from typing import Any, Iterator

HERE = pathlib.Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import harness as h  # noqa: E402
import telemetry  # noqa: E402
from machine_outcomes import exit_code  # noqa: E402

VERIFICATION_BLOCKAGE_OUTCOMES = {
    'busy', 'stale-input', 'environment-blocked', 'needs-human',
    'verification-blocked', 'verification-owned', 'abandoned',
}
VERIFICATION_OUTCOMES = VERIFICATION_BLOCKAGE_OUTCOMES | {
    'pass', 'verification-failed', 'invalid-policy', 'invalid-cache',
    'retry-policy-violation', 'harness-error',
}
MAX_SAFE_JSON_INTEGER = (1 << 53) - 1
MAX_PROCESS_EXIT_CODE = 255
from verification.authority import prepare_task_plan  # noqa: E402

RUNNER = HERE / 'runner.py'
REPO = HERE.parents[1]


@dataclass(frozen=True)
class ProviderChoice:
    provider: str
    model: str | None


@dataclass
class TaskOutcome:
    task_id: str
    status: str
    evidence: pathlib.Path | None = None
    summary: str = ''
    rework_tasks: list[str] | None = None


def die(message: str) -> None:
    print(f'ERROR: {message}', file=sys.stderr)
    raise SystemExit(2)


def load_result(path: pathlib.Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding='utf-8'))
    except (FileNotFoundError, json.JSONDecodeError) as exc:
        die(f'invalid runner evidence {path}: {exc}')
    if not isinstance(value, dict):
        die(f'runner evidence must be an object: {path}')
    return value


def choice_for_role(role: str, args: argparse.Namespace) -> ProviderChoice:
    if role in {'evaluator', 'integration'}:
        return ProviderChoice(args.evaluator_provider or args.provider, args.evaluator_model or args.model)
    return ProviderChoice(args.provider, args.model)


def review_choice(args: argparse.Namespace) -> ProviderChoice:
    return ProviderChoice(
        args.review_provider or args.evaluator_provider or args.provider,
        args.review_model or args.evaluator_model or args.model,
    )


def owner_for(args: argparse.Namespace, task_id: str) -> str:
    return f'{args.owner_prefix}-{args.run_id[:8]}-{task_id.lower()}'


def runner_command(
    packet: pathlib.Path,
    worktree: pathlib.Path,
    choice: ProviderChoice,
    args: argparse.Namespace,
    *,
    profile: str | None = None,
    review_existing: bool = False,
    feedback_file: pathlib.Path | None = None,
    defer_verification: bool = False,
) -> list[str]:
    cmd = [
        sys.executable,
        str(RUNNER),
        str(packet),
        '--provider', choice.provider,
        '--worktree', str(worktree),
        '--max-turns', str(args.max_turns),
        '--verification-timeout', str(args.verification_timeout),
        '--verification-sandbox', args.verification_sandbox,
        '--orchestration-id', args.run_id,
    ]
    if choice.model:
        cmd += ['--model', choice.model]
    if args.reasoning and choice.provider == 'codex':
        cmd += ['--reasoning', args.reasoning]
    if args.max_budget_usd is not None and choice.provider == 'claude':
        cmd += ['--max-budget-usd', str(args.max_budget_usd)]
    if profile:
        cmd += ['--profile', profile]
    if review_existing:
        cmd += ['--review-existing']
    elif profile:
        cmd += ['--sandbox', 'read-only']
    if profile and profile.endswith('-reviewer'):
        cmd += ['--skip-verification']
    if feedback_file and feedback_file.exists():
        cmd += ['--feedback-file', str(feedback_file)]
    if defer_verification:
        cmd += ['--skip-verification']
    return cmd


def invoke_runner(cmd: list[str]) -> pathlib.Path:
    proc = subprocess.run(cmd, text=True, capture_output=True, check=False)
    if proc.returncode != 0:
        detail = proc.stderr.strip() or proc.stdout.strip() or f'exit={proc.returncode}'
        raise RuntimeError(detail[-4000:])
    lines = [line.strip() for line in proc.stdout.splitlines() if line.strip()]
    if not lines:
        raise RuntimeError('runner returned no evidence path')
    path = pathlib.Path(lines[-1]).resolve()
    if not path.exists():
        raise RuntimeError(f'runner evidence path does not exist: {path}')
    return path


@contextlib.contextmanager
def lease_heartbeat(
    feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str, owner: str
) -> Iterator[None]:
    """Refresh a running task lease while provider/reviewer subprocesses are active."""
    stop = threading.Event()
    errors: list[str] = []
    interval = h.heartbeat_interval_seconds(doc)

    def beat() -> None:
        while not stop.wait(interval):
            try:
                h.heartbeat(feature_dir, doc, task_id, owner)
            except BaseException as exc:  # capture; main worker converts it to infrastructure error
                errors.append(str(exc))
                stop.set()
                return

    # Refresh immediately so a resumed/slow-to-start provider gets a full lease window.
    h.heartbeat(feature_dir, doc, task_id, owner)
    thread = threading.Thread(target=beat, name=f'lease-heartbeat-{task_id}', daemon=True)
    thread.start()
    try:
        yield
    finally:
        stop.set()
        thread.join(timeout=max(2, min(interval, 10)))
        if errors:
            raise RuntimeError(f'lease heartbeat failed for {task_id}: {errors[-1]}')


def prior_feedback(feature_dir: pathlib.Path, doc: dict[str, Any], task_id: str) -> pathlib.Path | None:
    state = h.load_state(feature_dir, doc)
    entry = state['tasks'][task_id]
    raw = entry.get('rework_evidence') or entry.get('last_failure_evidence')
    if not raw:
        return None
    path = pathlib.Path(str(raw))
    return path if path.exists() else None


def aggregate_success(main_path: pathlib.Path, reviews: list[tuple[str, pathlib.Path]]) -> pathlib.Path:
    main = load_result(main_path)
    aggregate = dict(main)
    aggregate['status'] = 'pass'
    aggregate['specialist_reviews'] = []
    for profile, path in reviews:
        result = load_result(path)
        aggregate['specialist_reviews'].append({
            'profile': profile,
            'status': result.get('status'),
            'summary': result.get('summary'),
            'evidence': str(path),
            'findings': result.get('findings', []),
            'provenance': result.get('provenance'),
        })
    out = main_path.parent / 'aggregate-result.json'
    out.write_text(json.dumps(aggregate, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return out


def verification_evidence_summary(result: dict[str, Any], plan_record: dict[str, Any],
                                  process_exit_code: int) -> dict[str, Any]:
    """Persist only allowlisted verification decisions, never raw command output."""
    summary: dict[str, Any] = {
        'plan_id': plan_record['plan_id'],
        'family_id': plan_record['family']['id'],
        'lifecycle_generation': plan_record['lifecycle_generation'],
        'profile_id': plan_record['profile_id'],
        'profile_hash': plan_record['profile_hash'],
        'policy_checkpoint': plan_record['policy_checkpoint'],
        'outcome': 'harness-error',
        'process_exit_code': process_exit_code,
        'gates': [],
    }
    def malformed() -> dict[str, Any]:
        summary.update({
            'outcome': 'verification-blocked',
            'machine_category': 'verification-blocked',
            'reason_code': 'MALFORMED_VERIFICATION_RESULT',
            'gates': [],
        })
        summary.pop('continuation', None)
        return summary

    if not isinstance(result, dict):
        return malformed()
    raw_outcome = result.get('outcome')
    raw_category = result.get('machine_category')
    raw_continuation = result.get('continuation')
    raw_reason_code = result.get('reason_code')
    raw_exit_code = result.get('exit_code')
    gates = result.get('gates')
    allowed_outcomes = VERIFICATION_OUTCOMES | {'PASS', 'FAIL', 'ERROR', 'TIMEOUT'}
    if (not isinstance(raw_outcome, str) or
            raw_outcome not in allowed_outcomes or
            raw_category is not None and (not isinstance(raw_category, str) or
                                          raw_category not in allowed_outcomes) or
            raw_continuation is not None and (not isinstance(raw_continuation, str) or
                raw_continuation not in {'execute-all-and-aggregate', 'continue-from-first-non-green'}) or
            raw_reason_code is not None and not isinstance(raw_reason_code, str) or
            raw_exit_code is not None and (type(raw_exit_code) is not int or
                                           not 0 <= raw_exit_code <= MAX_PROCESS_EXIT_CODE) or
            not isinstance(gates, list)):
        return malformed()
    if type(process_exit_code) is not int or not 0 <= process_exit_code <= MAX_PROCESS_EXIT_CODE:
        return malformed()
    expected_outcome_exit = exit_code(raw_outcome)
    if expected_outcome_exit is not None:
        if (raw_category != raw_outcome or raw_exit_code != expected_outcome_exit or
                process_exit_code != expected_outcome_exit):
            return malformed()
    elif raw_category is not None or raw_exit_code is not None:
        return malformed()
    if raw_outcome == 'PASS':
        if process_exit_code != 0 or raw_category is not None or raw_exit_code is not None:
            return malformed()
        obligations = plan_record.get('obligations')
        if (not isinstance(obligations, list) or not obligations or
                any(not isinstance(item, dict) for item in gates)):
            return malformed()
        expected_gate_ids = [item.get('gate_id') if isinstance(item, dict) else None
                             for item in obligations]
        actual_gate_ids = [item.get('gate_id') for item in gates if isinstance(item, dict)]
        if (any(not isinstance(gate_id, str) for gate_id in expected_gate_ids) or
                actual_gate_ids != expected_gate_ids or
                any(item.get('outcome') != 'PASS' for item in gates)):
            return malformed()
    allowed_gate_outcomes = {'PASS', 'FAIL', 'ERROR', 'TIMEOUT', 'NOT_RUN',
                             'needs-human', 'verification-blocked', 'verification-owned'}
    for gate in gates:
        if not isinstance(gate, dict):
            return malformed()
        gate_id, action, gate_outcome = gate.get('gate_id'), gate.get('action'), gate.get('outcome')
        if (not isinstance(gate_id, str) or not re.fullmatch(r'[A-Za-z0-9._-]{1,128}', gate_id) or
                not isinstance(action, str) or action not in {'RUN', 'REUSE'}):
            return malformed()
        if not isinstance(gate_outcome, str) or gate_outcome not in allowed_gate_outcomes:
            return malformed()
        reason = gate.get('reason')
        if reason is not None and (not isinstance(reason, str) or
                                   not re.fullmatch(r'[A-Za-z0-9_.:-]{1,128}', reason)):
            return malformed()
        for key in ('fingerprint', 'command_hash'):
            value = gate.get(key)
            if value is not None and (not isinstance(value, str) or
                                      not re.fullmatch(r'[0-9a-f]{64}', value)):
                return malformed()
        for key in ('exit_code', 'started_at', 'ended_at', 'duration_seconds'):
            value = gate.get(key)
            if value is None:
                continue
            if key == 'exit_code':
                if type(value) is not int or not 0 <= value <= MAX_PROCESS_EXIT_CODE:
                    return malformed()
            else:
                if type(value) is int:
                    if abs(value) > MAX_SAFE_JSON_INTEGER:
                        return malformed()
                elif type(value) is float:
                    if not math.isfinite(value) or abs(value) > MAX_SAFE_JSON_INTEGER:
                        return malformed()
                else:
                    return malformed()
                if value < 0:
                    return malformed()
    summary['outcome'] = (raw_outcome if raw_outcome in
        VERIFICATION_OUTCOMES | {'PASS', 'FAIL', 'ERROR', 'TIMEOUT'} else 'harness-error')
    if result.get('family_id') != plan_record['family']['id'] or result.get('profile_hash') != plan_record['profile_hash']:
        summary['outcome'] = 'verification-blocked'
        summary['machine_category'] = 'verification-blocked'
        summary['reason_code'] = 'PLAN_BINDING_MISMATCH'
        return summary
    continuation = result.get('continuation')
    if continuation in {'execute-all-and-aggregate', 'continue-from-first-non-green'}:
        summary['continuation'] = continuation
    category = result.get('machine_category')
    if category in VERIFICATION_OUTCOMES:
        summary['machine_category'] = category
    reason_code = result.get('reason_code')
    if isinstance(reason_code, str) and re.fullmatch(r'[A-Z0-9_.:-]{1,128}', reason_code):
        summary['reason_code'] = reason_code
    for gate in gates:
        gate_id = gate.get('gate_id')
        action = gate.get('action')
        outcome = gate.get('outcome')
        item = {'gate_id': gate_id, 'action': action, 'outcome': outcome}
        reason = gate.get('reason')
        if isinstance(reason, str) and re.fullmatch(r'[A-Za-z0-9_.:-]{1,128}', reason):
            item['reason'] = reason
        for key in ('fingerprint', 'command_hash'):
            value = gate.get(key)
            if isinstance(value, str) and re.fullmatch(r'[0-9a-f]{64}', value):
                item[key] = value
        for key in ('exit_code', 'started_at', 'ended_at', 'duration_seconds'):
            value = gate.get(key)
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                item[key] = value
        summary['gates'].append(item)
    return summary


def run_started_task(
    feature_dir: pathlib.Path,
    doc: dict[str, Any],
    task: dict[str, Any],
    worktree: pathlib.Path,
    packet: pathlib.Path,
    args: argparse.Namespace,
    feedback: pathlib.Path | None,
) -> TaskOutcome:
    task_id = task['id']
    owner = owner_for(args, task_id)
    choice = choice_for_role(task['role'], args)
    try:
        with lease_heartbeat(feature_dir, doc, task_id, owner):
            try:
                main_path = invoke_runner(runner_command(packet, worktree, choice, args,
                    feedback_file=feedback, defer_verification=True))
            except RuntimeError as exc:
                return TaskOutcome(task_id, 'runner-error', summary=str(exc))

            main = load_result(main_path)
            status = str(main.get('status'))
            if status != 'pass':
                return TaskOutcome(
                    task_id, status, evidence=main_path, summary=str(main.get('summary', '')),
                    rework_tasks=[str(t) for t in main.get('rework_tasks', [])],
                )

            # The provider has finished; trusted orchestration now seals the
            # candidate, creates the immutable plan and accepts it through the
            # feature lifecycle CAS. Runner/provider output never creates this
            # authority.
            packet_doc = load_result(packet)
            lifecycle = h.load_state(feature_dir, doc)
            task_attempt = lifecycle.get('tasks', {}).get(task_id, {}).get('attempts')
            provenance_path = pathlib.Path(str(main.get('provenance', '')))
            try:
                provenance = json.loads(provenance_path.read_text(encoding='utf-8'))
                base_sha = provenance.get('base_commit')
                if not isinstance(base_sha, str) or len(base_sha) != 40:
                    raise ValueError('BASE_COMMIT_UNAVAILABLE')
                plan_record = prepare_task_plan(worktree, feature_dir, task_id,
                    task_attempt, base_sha, packet_doc.get('verification', []))
                verify_proc = subprocess.run([sys.executable, str(HERE / 'verify.py'), 'run',
                    '--mode', 'integration', '--repo', str(worktree),
                    '--plan-id', plan_record['plan_id']], cwd=worktree, text=True,
                    capture_output=True, timeout=args.verification_timeout, check=False)
                verification_result = json.loads(verify_proc.stdout)
            except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError, SystemExit) as exc:
                main['verification'] = {
                    'outcome': 'verification-blocked',
                    'reason_code': 'ACCEPTED_PLAN_UNAVAILABLE',
                }
                main_path.write_text(json.dumps(main, indent=2, sort_keys=True) + '\n', encoding='utf-8')
                return TaskOutcome(task_id, 'verification-blocked', main_path,
                                   f'accepted verification authority unavailable: {type(exc).__name__}')
            verification_summary = verification_evidence_summary(
                verification_result, plan_record, verify_proc.returncode)
            main['verification'] = verification_summary
            main_path.write_text(json.dumps(main, indent=2, sort_keys=True) + '\n', encoding='utf-8')
            if main['verification'].get('reason_code') == 'PLAN_BINDING_MISMATCH':
                return TaskOutcome(task_id, 'verification-blocked', main_path, 'PLAN_BINDING_MISMATCH')
            verification_outcome = verification_summary['outcome']
            if verify_proc.returncode != 0 or verification_outcome != 'PASS':
                category = verification_summary.get('machine_category', verification_outcome)
                if category in {'FAIL', 'ERROR', 'TIMEOUT', 'PASS', 'harness-error'}:
                    category = 'verification-failed'
                if category not in VERIFICATION_OUTCOMES:
                    category = 'verification-failed'
                return TaskOutcome(task_id, category, main_path,
                    str(verification_summary.get('reason_code', verification_outcome or category)))
            main['verification_authority'] = {
                'plan_id': plan_record['plan_id'],
                'lifecycle_generation': plan_record['lifecycle_generation'],
                'task_id': task_id, 'task_attempt': task_attempt,
                'status': 'PASS',
            }
            main_path.write_text(json.dumps(main, indent=2, sort_keys=True) + '\n', encoding='utf-8')

            reviews: list[tuple[str, pathlib.Path]] = []
            if task['role'] == 'builder':
                packet_doc = load_result(packet)
                profiles = [str(p) for p in packet_doc.get('required_reviewers', [])]
                for profile in profiles:
                    dirty = bool(h.changed_paths(worktree))
                    try:
                        path = invoke_runner(
                            runner_command(
                                packet, worktree, review_choice(args), args,
                                profile=profile, review_existing=dirty,
                            )
                        )
                    except RuntimeError as exc:
                        return TaskOutcome(task_id, 'reviewer-error', main_path, f'{profile}: {exc}')
                    review = load_result(path)
                    reviews.append((profile, path))
                    if review.get('status') != 'pass':
                        return TaskOutcome(
                            task_id, str(review.get('status')), evidence=path,
                            summary=f'{profile}: {review.get("summary", "review failed")}',
                        )

            evidence = aggregate_success(main_path, reviews) if reviews else main_path
            return TaskOutcome(task_id, 'pass', evidence=evidence, summary=str(main.get('summary', '')))
    except RuntimeError as exc:
        return TaskOutcome(task_id, 'runner-error', summary=str(exc))


def start_ready_batch(
    feature_dir: pathlib.Path, doc: dict[str, Any], args: argparse.Namespace
) -> list[tuple[dict[str, Any], pathlib.Path, pathlib.Path, pathlib.Path | None]]:
    state = h.load_state(feature_dir, doc)
    ids = h.ready_ids(doc, state, feature_dir)
    started: list[tuple[dict[str, Any], pathlib.Path, pathlib.Path, pathlib.Path | None]] = []
    try:
        for task_id in ids:
            feedback = prior_feedback(feature_dir, doc, task_id)
            owner = owner_for(args, task_id)
            h.cmd_start(argparse.Namespace(feature_dir=feature_dir, task_id=task_id, owner=owner))
            task = h.active_task_contract(feature_dir, doc, task_id)
            worktree = h.worktree_path(str(doc.get('feature', feature_dir.name)), task_id)
            packet = pathlib.Path(h.resolve_active_packet(feature_dir, doc, task_id)['path'])
            started.append((task, worktree, packet, feedback))
    except BaseException as exc:
        for task, worktree, packet, feedback in reversed(started):
            owner = owner_for(args, task['id'])
            try:
                h.rollback_unexecuted_start(feature_dir, doc, task['id'], owner, f'batch start aborted: {exc}')
            except BaseException:
                pass
        raise
    return started


def apply_outcome(feature_dir: pathlib.Path, doc: dict[str, Any], outcome: TaskOutcome, args: argparse.Namespace) -> None:
    task = h.active_task_contract(feature_dir, doc, outcome.task_id)
    owner = owner_for(args, outcome.task_id)
    evidence = str(outcome.evidence) if outcome.evidence else None

    if outcome.status == 'pass':
        assert outcome.evidence is not None
        h.cmd_complete(argparse.Namespace(
            feature_dir=feature_dir, task_id=outcome.task_id, owner=owner, evidence=str(outcome.evidence)
        ))
        return

    if task['role'] == 'evaluator' and outcome.status == 'fail':
        current = h.load_state(feature_dir, doc)
        candidates = []
        for task_id in outcome.rework_tasks or []:
            candidate = h.active_task_contract(feature_dir, doc, task_id)
            if candidate and candidate.get('role') == 'builder' and current['tasks'][task_id].get('status') == 'completed':
                candidates.append(task_id)
        if not candidates:
            h.cmd_fail(argparse.Namespace(
                feature_dir=feature_dir, task_id=outcome.task_id, owner=owner,
                reason=outcome.summary or 'evaluator rejected implementation without safe rework target',
                evidence=evidence, escalate=True,
            ))
            return
        h.cmd_fail(argparse.Namespace(
            feature_dir=feature_dir, task_id=outcome.task_id, owner=owner,
            reason=outcome.summary or 'acceptance evaluation failed', evidence=evidence, escalate=False,
        ))
        for task_id in candidates:
            h.cmd_reopen(argparse.Namespace(
                feature_dir=feature_dir, task_id=task_id,
                reason=outcome.summary or f'evaluator requested rework from {outcome.task_id}', evidence=evidence,
            ))
        return

    control = outcome.status if outcome.status in VERIFICATION_BLOCKAGE_OUTCOMES else None
    escalate = (outcome.status in VERIFICATION_BLOCKAGE_OUTCOMES or
                outcome.status in {'runner-error', 'reviewer-error'})
    h.cmd_fail(argparse.Namespace(
        feature_dir=feature_dir, task_id=outcome.task_id, owner=owner,
        reason=outcome.summary or outcome.status, evidence=evidence, escalate=escalate,
    ), control_outcome=control)


def stop_on_repository_admission_blockage(
    feature_dir: pathlib.Path,
    doc: dict[str, Any],
    args: argparse.Namespace,
    manifest_path: pathlib.Path,
    outcomes: list[TaskOutcome],
    round_no: int,
    recovered_total: list[str],
) -> None:
    """Record repository admission failures before any lifecycle/worktree mutation.

    In particular, applying a worker's ``busy`` result through ``cmd_fail`` would
    reacquire the same verification lock and exit before recording the outcome.
    A repository-wide blocker also prevents checkpointing successful siblings.
    """
    blocked = [outcome for outcome in outcomes
               if outcome.status in {'busy', 'verification-owned'}]
    if not blocked:
        return
    category = ('verification-owned' if any(item.status == 'verification-owned' for item in blocked)
                else 'busy')
    state = h.load_state(feature_dir, doc)
    write_orchestration_manifest(
        manifest_path, doc, args, rounds=round_no,
        recovered_stale_leases=recovered_total,
        task_statuses={tid: entry['status'] for tid, entry in state['tasks'].items()},
        status=category,
        control_outcomes={item.task_id: item.status for item in blocked},
        completed_at=telemetry.iso_now(),
    )
    code = exit_code(category)
    raise SystemExit(code if code is not None else 3)


def print_plan(feature_dir: pathlib.Path, doc: dict[str, Any], args: argparse.Namespace) -> None:
    state = h.load_state(feature_dir, doc)
    print(
        f'feature={doc["feature"]} max_parallel={doc.get("max_parallel", 4)} '
        f'lease_ttl={h.lease_ttl_seconds(doc)}s heartbeat={h.heartbeat_interval_seconds(doc)}s'
    )
    for planned in doc['tasks']:
        # A completed task packet is immutable historical evidence.  A later
        # feature replan must not make it a prerequisite for routing current
        # work; use the validated current DAG contract for completed rows.
        task = (planned if state.get('tasks', {}).get(planned['id'], {}).get('status') == 'completed'
                else h.active_task_contract(feature_dir, doc, planned['id']))
        choice = choice_for_role(task['role'], args)
        reviews = h.reviewers(task) if task['role'] == 'builder' else []
        deps = ','.join(task.get('depends_on', [])) or '-'
        reviewer_text = ','.join(reviews) or '-'
        print(
            f'{task["id"]:7} role={task["role"]:11} provider={choice.provider:6} '
            f'deps={deps:24} reviewers={reviewer_text}'
        )


def orchestration_manifest_path(feature: str, run_id: str) -> pathlib.Path:
    path = REPO / '.agent-runs' / feature / 'orchestrations'
    path.mkdir(parents=True, exist_ok=True)
    return path / f'{run_id}.json'


def write_orchestration_manifest(path: pathlib.Path, doc: dict[str, Any], args: argparse.Namespace, **updates: Any) -> None:
    if path.exists():
        try:
            manifest = json.loads(path.read_text(encoding='utf-8'))
        except json.JSONDecodeError:
            manifest = {}
    else:
        manifest = {
            'schema_version': 1, 'run_id': args.run_id, 'feature': doc['feature'],
            'started_at': telemetry.iso_now(), 'provider': args.provider,
            'evaluator_provider': args.evaluator_provider or args.provider,
            'review_provider': args.review_provider or args.evaluator_provider or args.provider,
            'verification_sandbox': args.verification_sandbox,
        }
    manifest.update(updates)
    telemetry.atomic_write_json(path, manifest)


def main() -> None:
    p = argparse.ArgumentParser(description='Execute a complete SDD task DAG with bounded parallel agent workers')
    p.add_argument('feature_dir', type=pathlib.Path)
    p.add_argument('--provider', choices=('codex', 'claude'), default='codex')
    p.add_argument('--evaluator-provider', choices=('codex', 'claude'))
    p.add_argument('--review-provider', choices=('codex', 'claude'))
    p.add_argument('--model')
    p.add_argument('--evaluator-model')
    p.add_argument('--review-model')
    p.add_argument('--reasoning', choices=('low', 'medium', 'high', 'xhigh'), default='medium')
    p.add_argument('--max-turns', type=int, default=30)
    p.add_argument('--max-budget-usd', type=float)
    p.add_argument('--verification-timeout', type=int, default=900, help='Per-command timeout for outer deterministic verification')
    p.add_argument('--verification-sandbox', choices=('auto', 'required', 'off'), default='auto')
    p.add_argument('--owner-prefix', default='orchestrator')
    p.add_argument('--run-id', help='Stable correlation id; generated automatically when omitted')
    p.add_argument('--plan', action='store_true', help='Show provider/task routing without changing state')
    p.add_argument('--max-rounds', type=int, default=100, help='Hard guard against orchestration bugs')
    args = p.parse_args()
    args.run_id = args.run_id or uuid.uuid4().hex[:12]

    feature_dir = args.feature_dir.resolve()
    doc = h.load_validated(feature_dir)
    if args.plan:
        print_plan(feature_dir, doc, args)
        return
    if args.max_rounds < 1:
        die('--max-rounds must be positive')
    if args.verification_timeout < 1:
        die('--verification-timeout must be positive')

    manifest_path = orchestration_manifest_path(doc['feature'], args.run_id)
    write_orchestration_manifest(manifest_path, doc, args, status='running', rounds=0, recovered_stale_leases=[])
    recovered_total: list[str] = []

    for round_no in range(1, args.max_rounds + 1):
        try:
            recovered = h.recover_stale_leases(
                feature_dir, doc, reason=f'expired lease recovered by orchestration {args.run_id}')
        except SystemExit as exc:
            code = exc.code if isinstance(exc.code, int) else 2
            category = {3: 'busy', 5: 'verification-blocked', 6: 'verification-owned'}.get(code)
            if category is None:
                raise
            write_orchestration_manifest(manifest_path, doc, args, status=category,
                                         completed_at=telemetry.iso_now())
            raise
        for task_id in recovered:
            if task_id not in recovered_total:
                recovered_total.append(task_id)
        if recovered:
            telemetry.reconcile_running(
                REPO, doc['feature'], args.run_id, set(recovered),
                reason='task lease expired and was recovered by the orchestrator',
            )
        state = h.load_state(feature_dir, doc)
        statuses = {tid: entry['status'] for tid, entry in state['tasks'].items()}
        write_orchestration_manifest(
            manifest_path, doc, args, rounds=round_no - 1, recovered_stale_leases=recovered_total,
            task_statuses=statuses,
        )
        escalated = [tid for tid, status in statuses.items() if status == 'escalated']
        if escalated:
            state = h.load_state(feature_dir, doc)
            control = [(tid, state['tasks'][tid].get('control_outcome')) for tid in escalated
                       if state['tasks'][tid].get('control_outcome')]
            if control:
                precedence = {
                    'needs-human': 0, 'verification-blocked': 1, 'verification-owned': 2,
                    'environment-blocked': 3, 'stale-input': 4, 'busy': 5, 'abandoned': 6,
                }
                category = min(control, key=lambda item: (precedence[item[1]], item[0]))[1]
                write_orchestration_manifest(manifest_path, doc, args, status=category,
                    control_outcomes={tid: value for tid, value in control},
                    completed_at=telemetry.iso_now())
                code = exit_code(category)
                if code is None:
                    code = 3 if category in {'environment-blocked', 'stale-input', 'busy', 'abandoned'} else 2
                raise SystemExit(code)
            write_orchestration_manifest(manifest_path, doc, args, status='needs-human', completed_at=telemetry.iso_now())
            die(f'human decision required; escalated tasks: {escalated}')
        if all(status == 'completed' for status in statuses.values()):
            telemetry.reconcile_running(
                REPO, doc['feature'], args.run_id,
                reason='DAG reached terminal completion after this invocation lost ownership',
            )
            usage = telemetry.summarize(REPO, doc['feature'], args.run_id)
            write_orchestration_manifest(
                manifest_path, doc, args, status='pass', completed_at=telemetry.iso_now(), rounds=round_no - 1,
                recovered_stale_leases=recovered_total, task_statuses=statuses, usage=usage,
            )
            print(f'PASS: {doc["feature"]} DAG completed in {round_no - 1} orchestration rounds run_id={args.run_id}')
            return

        ready = h.ready_ids(doc, state, feature_dir)
        if not ready:
            running = [tid for tid, status in statuses.items() if status == 'running']
            if running:
                write_orchestration_manifest(manifest_path, doc, args, status='blocked-by-live-lease', task_statuses=statuses)
                die(f'no task is ready because live leases are still running: {running}; resume after completion or lease expiry')
            write_orchestration_manifest(manifest_path, doc, args, status='blocked', task_statuses=statuses)
            die(f'DAG is not complete but no task is ready: {statuses}')
        print(f'ROUND {round_no}: ready={ready} run_id={args.run_id}')
        started = start_ready_batch(feature_dir, doc, args)

        outcomes: list[TaskOutcome] = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(started)) as pool:
            future_map = {
                pool.submit(run_started_task, feature_dir, doc, task, worktree, packet, args, feedback): task['id']
                for task, worktree, packet, feedback in started
            }
            for future in concurrent.futures.as_completed(future_map):
                task_id = future_map[future]
                try:
                    outcome = future.result()
                except Exception as exc:
                    outcome = TaskOutcome(task_id, 'runner-error', summary=f'unhandled worker error: {exc}')
                outcomes.append(outcome)
                print(f'  {task_id}: {outcome.status} {outcome.summary}'.rstrip())

        # Any repository admission blocker must be recorded before applying
        # outcomes: even a busy cmd_fail would reacquire the busy verification
        # lock, and sibling outcomes could checkpoint worktrees under that lock.
        stop_on_repository_admission_blockage(
            feature_dir, doc, args, manifest_path, outcomes, round_no, recovered_total)

        for outcome in sorted(outcomes, key=lambda item: item.task_id):
            apply_outcome(feature_dir, doc, outcome, args)
        write_orchestration_manifest(manifest_path, doc, args, rounds=round_no, recovered_stale_leases=recovered_total)

    write_orchestration_manifest(manifest_path, doc, args, status='max-rounds-exceeded', completed_at=telemetry.iso_now())
    die(f'orchestration exceeded --max-rounds={args.max_rounds}')


if __name__ == '__main__':
    main()
