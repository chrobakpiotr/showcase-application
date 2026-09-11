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
import pathlib
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

RUNNER = HERE / 'runner.py'
REPO = HERE.parent


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
                main_path = invoke_runner(runner_command(packet, worktree, choice, args, feedback_file=feedback))
            except RuntimeError as exc:
                return TaskOutcome(task_id, 'runner-error', summary=str(exc))

            main = load_result(main_path)
            status = str(main.get('status'))
            if status != 'pass':
                return TaskOutcome(
                    task_id, status, evidence=main_path, summary=str(main.get('summary', '')),
                    rework_tasks=[str(t) for t in main.get('rework_tasks', [])],
                )

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
    ids = h.ready_ids(doc, state)
    idx = h.task_index(doc)
    started: list[tuple[dict[str, Any], pathlib.Path, pathlib.Path, pathlib.Path | None]] = []
    try:
        for task_id in ids:
            feedback = prior_feedback(feature_dir, doc, task_id)
            owner = owner_for(args, task_id)
            h.cmd_start(argparse.Namespace(feature_dir=feature_dir, task_id=task_id, owner=owner))
            task = idx[task_id]
            worktree = h.worktree_path(str(doc.get('feature', feature_dir.name)), task_id)
            packet = feature_dir / 'packets' / f'{task_id}.json'
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
    idx = h.task_index(doc)
    task = idx[outcome.task_id]
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
            candidate = idx.get(task_id)
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

    escalate = outcome.status in {'needs-human', 'runner-error', 'reviewer-error'}
    h.cmd_fail(argparse.Namespace(
        feature_dir=feature_dir, task_id=outcome.task_id, owner=owner,
        reason=outcome.summary or outcome.status, evidence=evidence, escalate=escalate,
    ))


def print_plan(feature_dir: pathlib.Path, doc: dict[str, Any], args: argparse.Namespace) -> None:
    print(
        f'feature={doc["feature"]} max_parallel={doc.get("max_parallel", 4)} '
        f'lease_ttl={h.lease_ttl_seconds(doc)}s heartbeat={h.heartbeat_interval_seconds(doc)}s'
    )
    for task in doc['tasks']:
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
        recovered = h.recover_stale_leases(feature_dir, doc, reason=f'expired lease recovered by orchestration {args.run_id}')
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

        ready = h.ready_ids(doc, state)
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

        for outcome in sorted(outcomes, key=lambda item: item.task_id):
            apply_outcome(feature_dir, doc, outcome, args)
        write_orchestration_manifest(manifest_path, doc, args, rounds=round_no, recovered_stale_leases=recovered_total)

    write_orchestration_manifest(manifest_path, doc, args, status='max-rounds-exceeded', completed_at=telemetry.iso_now())
    die(f'orchestration exceeded --max-rounds={args.max_rounds}')


if __name__ == '__main__':
    main()
