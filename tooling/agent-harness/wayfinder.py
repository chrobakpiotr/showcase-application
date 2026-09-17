#!/usr/bin/env python3
"""Wayfinder-style multi-session discovery for large/foggy engineering efforts.

This is a repo-native decision-map layer that sits *before* spec-driven implementation.
It produces decisions, not production code. Once the map converges, `to-spec` collapses
those decisions into a buildable feature spec/plan. After the normal design gate passes,
`to-tasks` creates the executable task DAG consumed by orchestrate.py.
"""
from __future__ import annotations

import argparse
import contextlib
import datetime as dt
import fcntl
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
from typing import Any, Iterator

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import harness as h  # noqa: E402
import runner as r  # noqa: E402
import design as d  # noqa: E402
import telemetry  # noqa: E402
import trust  # noqa: E402
import verification_contract as vc  # noqa: E402

MAP_FILE = 'wayfinder.json'
RESULT_SCHEMA = HERE / 'schemas' / 'wayfinder-result.schema.json'
HANDOFF_SCHEMA = HERE / 'schemas' / 'wayfinder-handoff.schema.json'
TASKS_SCHEMA = HERE / 'schemas' / 'wayfinder-tasks-result.schema.json'
PROTOCOL_VERSION = 2
SUPPORTED_PROTOCOL_VERSIONS = {1, 2}
VALID_LEDGER_TYPES = {'fact', 'decision', 'assumption', 'constraint', 'evidence'}
VALID_LEDGER_STATUSES = {'active', 'superseded', 'rejected'}
TERMINAL_FOG_STATUSES = {'resolved', 'out-of-scope', 'deferred'}
VALID_DECISION_TYPES = {
    'grill', 'research', 'prototype', 'architecture', 'domain', 'contract',
    'security', 'operability', 'performance', 'migration', 'human',
}
VALID_STATUSES = {'open', 'closed', 'needs-human'}


def die(message: str) -> None:
    print(f'ERROR: {message}', file=sys.stderr)
    raise SystemExit(2)


def load_json(path: pathlib.Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding='utf-8'))
    except (FileNotFoundError, json.JSONDecodeError) as exc:
        die(f'{path}: {exc}')
    if not isinstance(value, dict):
        die(f'{path} must contain a JSON object')
    return value


def atomic_json(path: pathlib.Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=path.name + '.', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as out:
            json.dump(value, out, indent=2, sort_keys=True)
            out.write('\n')
            out.flush()
            os.fsync(out.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def map_path(map_dir: pathlib.Path) -> pathlib.Path:
    return map_dir / MAP_FILE


def state_repo_root() -> pathlib.Path | None:
    # Prefer the repository that contains this installed harness so read-only status works even if
    # invoked from another cwd. Tests/source-package previews can fall back to the caller's cwd.
    for command in (["git", "-C", str(REPO), "rev-parse", "--show-toplevel"], ["git", "rev-parse", "--show-toplevel"]):
        proc = subprocess.run(command, capture_output=True, text=True, check=False)
        if proc.returncode == 0:
            return pathlib.Path(proc.stdout.strip()).resolve()
    return None


def state_root(epic: str) -> pathlib.Path:
    root = state_repo_root()
    if root is None:
        die('Wayfinder claim mutation requires a Git repository')
    return root / '.agent-state' / 'wayfinder' / epic


@contextlib.contextmanager
def locked_map(map_dir: pathlib.Path) -> Iterator[dict[str, Any]]:
    current = load_map(map_dir)
    root = state_root(str(current['epic']))
    root.mkdir(parents=True, exist_ok=True)
    lock_path = root / 'map.lock'
    with lock_path.open('a+', encoding='utf-8') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        doc = load_map(map_dir)
        yield doc
        atomic_json(map_path(map_dir), doc)
        fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def iso_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def parse_time(value: str | None) -> dt.datetime | None:
    if not value:
        return None
    try:
        parsed = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=dt.timezone.utc)
    except ValueError:
        return None


def load_map(map_dir: pathlib.Path) -> dict[str, Any]:
    return load_json(map_path(map_dir.resolve()))


def decision_index(doc: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {str(item['id']): item for item in doc.get('decisions', []) if isinstance(item, dict) and item.get('id')}


def validate_map(doc: dict[str, Any], map_dir: pathlib.Path | None = None) -> list[str]:
    errors: list[str] = []
    if doc.get('schema_version') not in SUPPORTED_PROTOCOL_VERSIONS:
        errors.append(f'schema_version must be one of {sorted(SUPPORTED_PROTOCOL_VERSIONS)}')
    epic = doc.get('epic')
    if not isinstance(epic, str) or not h.FEATURE_ID_RE.fullmatch(epic):
        errors.append('epic must be a valid feature-style id')
    if map_dir is not None and isinstance(epic, str) and map_dir.name != epic:
        errors.append(f'epic must match directory name: {epic!r} != {map_dir.name!r}')
    destination = doc.get('destination')
    if not isinstance(destination, str) or not destination.strip():
        errors.append('destination must be a non-empty string')
    for field in ('out_of_scope', 'fog'):
        value = doc.get(field, [])
        if not isinstance(value, list) or any(not isinstance(x, dict if field == 'fog' else str) for x in value):
            errors.append(f'{field} has invalid shape')
    fog_ids: set[str] = set()
    for i, fog in enumerate(doc.get('fog', [])):
        if not isinstance(fog, dict):
            continue
        fid = fog.get('id')
        if not isinstance(fid, str) or not fid.startswith('F-'):
            errors.append(f'fog[{i}].id must start with F-')
        elif fid in fog_ids:
            errors.append(f'duplicate fog id: {fid}')
        else:
            fog_ids.add(fid)
        if not isinstance(fog.get('description'), str) or not fog.get('description', '').strip():
            errors.append(f'fog[{i}].description must be non-empty')
        if fog.get('status', 'open') not in {'open', *TERMINAL_FOG_STATUSES}:
            errors.append(f'fog[{i}].status must be open/resolved/out-of-scope/deferred')
    if doc.get('schema_version') == 2:
        ledger = doc.get('ledger', [])
        if not isinstance(ledger, list):
            errors.append('ledger must be an array for schema_version=2')
            ledger = []
        ledger_ids: set[str] = set()
        for i, entry in enumerate(ledger):
            where = f'ledger[{i}]'
            if not isinstance(entry, dict):
                errors.append(f'{where} must be an object'); continue
            kid = entry.get('id')
            if not isinstance(kid, str) or not kid.startswith('K-') or kid in ledger_ids:
                errors.append(f'{where}.id must be a unique K-* id')
            else:
                ledger_ids.add(kid)
            if entry.get('type') not in VALID_LEDGER_TYPES:
                errors.append(f'{where}.type must be one of {sorted(VALID_LEDGER_TYPES)}')
            if entry.get('status', 'active') not in VALID_LEDGER_STATUSES:
                errors.append(f'{where}.status must be one of {sorted(VALID_LEDGER_STATUSES)}')
            if not isinstance(entry.get('statement'), str) or not entry.get('statement', '').strip():
                errors.append(f'{where}.statement must be non-empty')
            if entry.get('status') == 'superseded' and not isinstance(entry.get('superseded_by'), str):
                errors.append(f'{where}.superseded_by required when status=superseded')
        for entry in ledger:
            if isinstance(entry, dict) and entry.get('status') == 'superseded' and entry.get('superseded_by') not in ledger_ids:
                errors.append(f'{entry.get("id")} superseded_by references unknown ledger entry {entry.get("superseded_by")}')

    decisions = doc.get('decisions')
    if not isinstance(decisions, list) or not decisions:
        return errors + ['decisions must be a non-empty array']
    ids: list[str] = []
    for i, item in enumerate(decisions):
        where = f'decisions[{i}]'
        if not isinstance(item, dict):
            errors.append(f'{where} must be an object')
            continue
        did = item.get('id')
        if not isinstance(did, str) or not did.startswith('D-'):
            errors.append(f'{where}.id must start with D-')
            continue
        ids.append(did)
        for field in ('name', 'question'):
            if not isinstance(item.get(field), str) or not item.get(field, '').strip():
                errors.append(f'{where}.{field} must be non-empty')
        if item.get('type') not in VALID_DECISION_TYPES:
            errors.append(f'{where}.type must be one of {sorted(VALID_DECISION_TYPES)}')
        if item.get('status', 'open') not in VALID_STATUSES:
            errors.append(f'{where}.status must be one of {sorted(VALID_STATUSES)}')
        deps = item.get('depends_on', [])
        if not isinstance(deps, list) or any(not isinstance(x, str) for x in deps):
            errors.append(f'{where}.depends_on must be a string array')
        refs = item.get('fog_refs', [])
        if not isinstance(refs, list) or any(not isinstance(x, str) for x in refs):
            errors.append(f'{where}.fog_refs must be a string array')
        elif any(ref not in fog_ids for ref in refs):
            errors.append(f'{where}.fog_refs contains unknown fog id')
        leverage = item.get('leverage', 1)
        if not isinstance(leverage, int) or not 1 <= leverage <= 10:
            errors.append(f'{where}.leverage must be integer 1..10')
        contexts = item.get('context_refs', [])
        if not isinstance(contexts, list) or any(not isinstance(x, str) for x in contexts):
            errors.append(f'{where}.context_refs must be a string array')
    if len(ids) != len(set(ids)):
        errors.append('decision ids must be unique')
    known = set(ids)
    for item in decisions:
        if not isinstance(item, dict) or item.get('id') not in known:
            continue
        for dep in item.get('depends_on', []):
            if dep not in known:
                errors.append(f'{item["id"]} depends on unknown decision {dep}')
            if dep == item['id']:
                errors.append(f'{item["id"]} cannot depend on itself')
    # cycle detection
    idx = decision_index(doc)
    visiting: set[str] = set()
    visited: set[str] = set()
    def visit(did: str) -> None:
        if did in visiting:
            errors.append(f'decision dependency cycle includes {did}')
            return
        if did in visited or did not in idx:
            return
        visiting.add(did)
        for dep in idx[did].get('depends_on', []):
            visit(dep)
        visiting.remove(did)
        visited.add(did)
    for did in idx:
        visit(did)
    ttl = doc.get('claim_ttl_seconds', 1800)
    if not isinstance(ttl, int) or not 60 <= ttl <= 86400:
        errors.append('claim_ttl_seconds must be integer 60..86400')
    return errors


def open_fog(doc: dict[str, Any]) -> list[dict[str, Any]]:
    return [f for f in doc.get('fog', []) if isinstance(f, dict) and f.get('status', 'open') == 'open']


def claim_path(epic: str, decision_id: str) -> pathlib.Path:
    return state_root(epic) / 'claims' / decision_id


def active_claim(doc: dict[str, Any], decision_id: str) -> dict[str, Any] | None:
    # Status/frontier are useful even on an unpacked example outside a Git checkout. In that case
    # there cannot be meaningful live local claims, so treat claim-awareness as unavailable rather
    # than making a read-only preview fail. Mutating claim operations remain Git-repo-only.
    root = state_repo_root()
    if root is None:
        return None
    path = root / '.agent-state' / 'wayfinder' / str(doc['epic']) / 'claims' / decision_id
    payload = path / 'claim.json'
    if not payload.exists():
        return None
    try:
        claim = json.loads(payload.read_text(encoding='utf-8'))
    except json.JSONDecodeError:
        shutil.rmtree(path, ignore_errors=True)
        return None
    expires = parse_time(claim.get('expires_at'))
    if expires is None or expires <= dt.datetime.now(dt.timezone.utc):
        shutil.rmtree(path, ignore_errors=True)
        return None
    return claim if isinstance(claim, dict) else None


def acquire_claim(doc: dict[str, Any], decision_id: str, owner: str) -> None:
    path = claim_path(str(doc['epic']), decision_id)
    path.parent.mkdir(parents=True, exist_ok=True)
    ttl = int(doc.get('claim_ttl_seconds', 1800))
    try:
        path.mkdir()
    except FileExistsError:
        claim = active_claim(doc, decision_id)
        if claim:
            die(f'{decision_id} is already claimed by {claim.get("owner")} until {claim.get("expires_at")}')
        path.mkdir()
    now = dt.datetime.now(dt.timezone.utc)
    atomic_json(path / 'claim.json', {
        'owner': owner,
        'claimed_at': now.isoformat(),
        'expires_at': (now + dt.timedelta(seconds=ttl)).isoformat(),
    })


def release_claim(doc: dict[str, Any], decision_id: str) -> None:
    shutil.rmtree(claim_path(str(doc['epic']), decision_id), ignore_errors=True)


def downstream_count(doc: dict[str, Any], decision_id: str) -> int:
    return sum(decision_id in item.get('depends_on', []) for item in doc.get('decisions', []) if isinstance(item, dict))


def leverage_score(doc: dict[str, Any], item: dict[str, Any]) -> int:
    # Prefer decisions that clear fog or unlock other decisions; creation order is only a final tiebreaker.
    unresolved_fog = {f['id'] for f in open_fog(doc)}
    fog_weight = sum(1 for fid in item.get('fog_refs', []) if fid in unresolved_fog)
    return int(item.get('leverage', 1)) * 10 + fog_weight * 6 + downstream_count(doc, str(item['id'])) * 8


def frontier(doc: dict[str, Any], *, include_claimed: bool = False) -> list[dict[str, Any]]:
    idx = decision_index(doc)
    result: list[dict[str, Any]] = []
    for item in doc.get('decisions', []):
        if not isinstance(item, dict) or item.get('status', 'open') != 'open':
            continue
        if not all(idx.get(dep, {}).get('status') == 'closed' for dep in item.get('depends_on', [])):
            continue
        if not include_claimed and active_claim(doc, str(item['id'])):
            continue
        result.append(item)
    return sorted(result, key=lambda x: (-leverage_score(doc, x), str(x['id'])))


def active_blocking_assumptions(doc: dict[str, Any]) -> list[dict[str, Any]]:
    return [x for x in doc.get('ledger', []) if isinstance(x, dict) and x.get('status', 'active') == 'active' and x.get('type') == 'assumption' and x.get('blocking') is True]


def terminal_reconciliation_errors(doc: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    for decision in doc.get('decisions', []):
        if isinstance(decision, dict) and decision.get('status', 'open') != 'closed':
            errors.append(f"decision {decision.get('id')} is not closed (status={decision.get('status', 'open')}): {decision.get('name')}")
    for fog in doc.get('fog', []):
        if isinstance(fog, dict) and fog.get('status', 'open') == 'open':
            errors.append(f"fog {fog.get('id')} is still open: {fog.get('description')}")
    for entry in active_blocking_assumptions(doc):
        errors.append(f"blocking assumption {entry.get('id')} is still active: {entry.get('statement')}")
    if any(item.get('status') == 'needs-human' for item in doc.get('decisions', []) if isinstance(item, dict)):
        errors.append('one or more decision tickets still need human resolution')
    return errors


def cleared(doc: dict[str, Any]) -> bool:
    return all(item.get('status') == 'closed' for item in doc.get('decisions', []) if isinstance(item, dict)) and not terminal_reconciliation_errors(doc)


def validate_result(result: dict[str, Any], *, allow_empty_decision: bool = False, decision_type: str | None = None) -> None:
    required = {
        'status', 'summary', 'decision', 'rationale', 'evidence', 'new_decisions',
        'fog_resolved', 'fog_added', 'out_of_scope_added', 'spec_inputs',
        'changed_paths', 'commands', 'assumptions', 'residual_risks', 'ledger_entries',
    }
    missing = required - set(result)
    if missing:
        die(f'wayfinder result missing fields: {sorted(missing)}')
    if result['status'] not in {'pass', 'fail', 'needs-human'}:
        die('wayfinder result status must be pass/fail/needs-human')
    if not isinstance(result['summary'], str) or not result['summary'].strip():
        die('wayfinder result summary must be non-empty')
    ledger_entries = result.get('ledger_entries', [])
    if not isinstance(ledger_entries, list):
        die('wayfinder result ledger_entries must be an array')
    for entry in ledger_entries:
        if not isinstance(entry, dict) or entry.get('type') not in VALID_LEDGER_TYPES or not isinstance(entry.get('statement'), str) or not entry.get('statement', '').strip():
            die('wayfinder result contains invalid ledger entry')
    decision_required = decision_type not in {'research', 'prototype'}
    if not allow_empty_decision and result['status'] == 'pass' and decision_required and (not isinstance(result['decision'], str) or not result['decision'].strip()):
        die('passing decision ticket must contain a decision')
    if result['status'] == 'pass' and not decision_required and not (isinstance(result.get('decision'), str) and result.get('decision', '').strip()) and not ledger_entries:
        die('passing research/prototype ticket must produce a fact/evidence/decision ledger entry')
    for field in ('evidence', 'fog_resolved', 'fog_added', 'out_of_scope_added', 'spec_inputs', 'changed_paths', 'commands', 'assumptions', 'residual_risks'):
        if not isinstance(result[field], list) or any(not isinstance(x, str) for x in result[field]):
            die(f'wayfinder result {field} must be a string array')
    if not isinstance(result['new_decisions'], list):
        die('wayfinder result new_decisions must be an array')


def profile_text(worktree: pathlib.Path, profile: str) -> str:
    path = worktree / 'docs' / 'agentic-sdd' / 'agents' / f'{profile}.md'
    if not path.exists():
        die(f'missing wayfinder agent profile: {path}')
    return path.read_text(encoding='utf-8')


def provider_args(args: argparse.Namespace, profile: str, read_only: bool) -> argparse.Namespace:
    return argparse.Namespace(
        print_command=False,
        sandbox='read-only' if read_only else 'workspace-write',
        model=args.model,
        reasoning=args.reasoning,
        profile=profile,
        review_existing=read_only,
        max_turns=args.max_turns,
        max_budget_usd=args.max_budget_usd,
    )


def run_provider_json(*, epic: str, stage: str, prompt: str, worktree: pathlib.Path, profile: str,
                      args: argparse.Namespace, schema: pathlib.Path, read_only: bool) -> tuple[dict[str, Any], pathlib.Path]:
    if 'CONTEXT TRUST BOUNDARY' not in prompt:
        prompt = trust.policy_text() + '\n' + prompt
    p_args = provider_args(args, profile, read_only)
    runtime = REPO / '.agent-runs' / 'wayfinder' / epic / args.run_id / stage
    runtime.mkdir(parents=True, exist_ok=True)
    result_path = runtime / 'result.json'
    before = r.git_snapshot(worktree)
    command = (
        r.codex_command(p_args, prompt, worktree, result_path, schema_path=worktree / schema.relative_to(REPO))
        if args.provider == 'codex'
        else r.claude_command(p_args, prompt, worktree, schema_path=worktree / schema.relative_to(REPO))
    )
    provenance = {
        'schema_version': 1, 'kind': 'wayfinder', 'epic': epic, 'stage': stage,
        'profile': profile, 'provider': args.provider, 'model_requested': args.model,
        'run_id': args.run_id, 'started_at': telemetry.iso_now(),
        'prompt_sha256': hashlib.sha256(prompt.encode()).hexdigest(), 'base_commit': before[0], 'status': 'running',
    }
    telemetry.atomic_write_json(runtime / 'provenance.json', provenance)
    (runtime / 'prompt.txt').write_text(prompt, encoding='utf-8')
    started = time.monotonic()
    try:
        proc = subprocess.run(command, cwd=worktree, text=True, capture_output=True, check=False)
        (runtime / 'stdout.log').write_text(proc.stdout, encoding='utf-8')
        (runtime / 'stderr.log').write_text(proc.stderr, encoding='utf-8')
        if proc.returncode != 0:
            raise RuntimeError(f'{args.provider} exited with {proc.returncode}: {(proc.stderr or proc.stdout)[-2000:]}')
        result = r.extract_claude_result(proc.stdout) if args.provider == 'claude' else load_json(result_path)
        after = r.git_snapshot(worktree)
        if after[0] != before[0]:
            raise RuntimeError('wayfinder agent changed HEAD; commits/rebase/reset are forbidden')
        if after[1] != before[1]:
            raise RuntimeError('wayfinder agent changed git remotes')
        actual = r.git_changed_paths(worktree)
        if read_only and after[2] != before[2]:
            raise RuntimeError(f'read-only wayfinder agent modified worktree: {actual}')
        if 'changed_paths' in result:
            reported = sorted(set(str(x) for x in result.get('changed_paths', [])))
            if reported != actual:
                raise RuntimeError(f'wayfinder changed_paths differs from git diff; reported={reported}, actual={actual}')
        result_path.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        provenance.update({
            'status': result.get('status', 'pass'), 'completed_at': telemetry.iso_now(),
            'duration_ms': round((time.monotonic() - started) * 1000),
            'result_sha256': hashlib.sha256(result_path.read_bytes()).hexdigest(),
        })
        telemetry.atomic_write_json(runtime / 'provenance.json', provenance)
        return result, result_path
    except BaseException as exc:
        provenance.update({
            'status': 'harness-error', 'completed_at': telemetry.iso_now(),
            'duration_ms': round((time.monotonic() - started) * 1000),
            'error': {'type': type(exc).__name__, 'message': str(exc)[:2000]},
        })
        telemetry.atomic_write_json(runtime / 'provenance.json', provenance)
        raise


def map_summary(doc: dict[str, Any]) -> str:
    closed = [d for d in doc.get('decisions', []) if isinstance(d, dict) and d.get('status') == 'closed']
    return '\n'.join(f"- {d['id']} {d['name']}: {d.get('decision_summary', '<closed>')}" for d in closed) or '- none yet'


def prompt_for_decision(doc: dict[str, Any], item: dict[str, Any], worktree: pathlib.Path) -> str:
    fog = '\n'.join(f"- {f['id']}: {f['description']}" for f in open_fog(doc)) or '- none'
    refs = '\n'.join(f'- {x}' for x in item.get('context_refs', [])) or '- inspect repository as needed'
    return f"""You are resolving one decision ticket in a Wayfinder-style discovery map for Showcase Application.

ROLE CONTRACT
-------------
{profile_text(worktree, 'wayfinder-agent')}

{trust.policy_text()}

DESTINATION
-----------
{doc['destination']}

OUT OF SCOPE
------------
{json.dumps(doc.get('out_of_scope', []), indent=2)}

DECISIONS SO FAR
----------------
{map_summary(doc)}

CURRENT FOG
-----------
{fog}

DECISION TICKET
---------------
ID: {item['id']}
Name: {item['name']}
Type: {item['type']}
Question: {item['question']}
Leverage: {item.get('leverage', 1)}
Fog refs: {json.dumps(item.get('fog_refs', []))}
Context refs:
{refs}

RULES
-----
1. Produce knowledge that reduces uncertainty, not a production implementation slice. Architecture/modules/domain/contract/security tickets normally produce a DECISION. Research/prototype tickets may instead produce FACT/EVIDENCE ledger entries without inventing a decision.
2. Inspect current code/ADRs/contracts before inventing architecture.
3. New questions discovered may be emitted as `new_decisions`; only emit questions that are now precise enough to answer. Attach relevant `fog_refs` and any already-closed `depends_on` decisions when they improve map ordering.
4. `fog_added` is for known-unknowns that cannot yet be phrased precisely; `fog_resolved` lists F-* ids actually cleared by this decision.
5. `ledger_entries` are durable typed knowledge: fact | decision | assumption | constraint | evidence. Use `supersedes` to explicitly invalidate older K-* entries. Set blocking=true only for an assumption that must be resolved before map convergence. `spec_inputs` are concise durable facts/constraints that a later to-spec synthesis must preserve.
6. For type=prototype you may modify only this disposable worktree to gather evidence; code is throwaway and must not be proposed for direct promotion.
7. Do not commit, push, merge, rebase, reset HEAD, mutate remotes, open PRs, deploy or mutate trackers.
8. Return ONLY one JSON object conforming to tooling/agent-harness/schemas/wayfinder-result.schema.json.
"""


def next_decision_id(doc: dict[str, Any]) -> str:
    nums: list[int] = []
    for item in doc.get('decisions', []):
        did = str(item.get('id', ''))
        try:
            nums.append(int(did.split('-', 1)[1]))
        except (ValueError, IndexError):
            pass
    return f'D-{(max(nums) if nums else 0) + 1:03d}'


def next_fog_id(doc: dict[str, Any]) -> str:
    nums: list[int] = []
    for item in doc.get('fog', []):
        fid = str(item.get('id', ''))
        try:
            nums.append(int(fid.split('-', 1)[1]))
        except (ValueError, IndexError):
            pass
    return f'F-{(max(nums) if nums else 0) + 1:03d}'


def normalize_new_decision(doc: dict[str, Any], raw: Any, parent: str) -> dict[str, Any] | None:
    if not isinstance(raw, dict):
        return None
    question = raw.get('question')
    if not isinstance(question, str) or not question.strip():
        return None
    dtype = raw.get('type', 'research')
    if dtype not in VALID_DECISION_TYPES:
        dtype = 'research'
    idx = decision_index(doc)
    requested_deps = [x for x in raw.get('depends_on', []) if isinstance(x, str)] if isinstance(raw.get('depends_on', []), list) else []
    deps = [x for x in requested_deps if idx.get(x, {}).get('status') == 'closed']
    if parent in idx and idx[parent].get('status') == 'closed' and parent not in deps:
        deps.append(parent)
    known_open_fog = {f['id'] for f in open_fog(doc)}
    refs = [x for x in raw.get('fog_refs', []) if isinstance(x, str) and x in known_open_fog] if isinstance(raw.get('fog_refs', []), list) else []
    return {
        'id': next_decision_id(doc),
        'name': str(raw.get('name') or question[:80]).strip(),
        'question': question.strip(),
        'type': dtype,
        'status': 'open',
        'depends_on': deps,
        'fog_refs': refs,
        'leverage': max(1, min(10, int(raw.get('leverage', 3)) if isinstance(raw.get('leverage', 3), int) else 3)),
        'context_refs': [str(x) for x in raw.get('context_refs', []) if isinstance(x, str)],
    }


def next_ledger_id(doc: dict[str, Any]) -> str:
    nums: list[int] = []
    for item in doc.get('ledger', []):
        try:
            nums.append(int(str(item.get('id', '')).split('-', 1)[1]))
        except (ValueError, IndexError):
            pass
    return f'K-{(max(nums) if nums else 0) + 1:03d}'


def add_ledger_entry(doc: dict[str, Any], raw: dict[str, Any], source_decision: str) -> str:
    entry = {
        'id': next_ledger_id(doc), 'type': raw.get('type', 'evidence'), 'status': 'active',
        'statement': str(raw.get('statement', '')).strip(), 'source_decision': source_decision,
        'evidence': [str(x) for x in raw.get('evidence', []) if isinstance(x, str)],
        'blocking': bool(raw.get('blocking', False)), 'created_at': iso_now(),
    }
    if entry['type'] not in VALID_LEDGER_TYPES or not entry['statement']:
        die('invalid ledger entry returned by Wayfinder agent')
    supersedes = [str(x) for x in raw.get('supersedes', []) if isinstance(x, str)]
    known = {str(x.get('id')): x for x in doc.get('ledger', []) if isinstance(x, dict)}
    new_id = entry['id']
    for old_id in supersedes:
        old = known.get(old_id)
        if not old or old.get('status', 'active') != 'active':
            die(f'ledger entry attempts to supersede unknown/non-active entry {old_id}')
        old['status'] = 'superseded'; old['superseded_by'] = new_id; old['superseded_at'] = iso_now()
    entry['supersedes'] = supersedes
    doc.setdefault('ledger', []).append(entry)
    return new_id


def apply_result(map_dir: pathlib.Path, decision_id: str, result: dict[str, Any], *, allow_needs_human: bool = False) -> None:
    current = load_map(map_dir)
    known_fog = {f.get('id') for f in current.get('fog', []) if isinstance(f, dict)}
    unknown_fog = sorted(set(result.get('fog_resolved', [])) - known_fog)
    if unknown_fog:
        die(f'wayfinder result references unknown fog ids: {unknown_fog}')
    artifact = map_dir / 'decisions' / f'{decision_id}.json'
    if artifact.exists():
        history = map_dir / 'decisions' / 'history'
        history.mkdir(parents=True, exist_ok=True)
        stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
        shutil.copy2(artifact, history / f'{decision_id}-{stamp}.json')
    with locked_map(map_dir) as doc:
        idx = decision_index(doc)
        item = idx.get(decision_id)
        if not item:
            die(f'decision disappeared while running: {decision_id}')
        allowed_statuses = {'open', 'needs-human'} if allow_needs_human else {'open'}
        if item.get('status') not in allowed_statuses:
            die(f'decision changed concurrently and is not resolvable: {decision_id} status={item.get("status")}')
        if result['status'] == 'pass':
            item['status'] = 'closed'
            item['decision_summary'] = result['decision']
            item['rationale'] = result['rationale']
            item['closed_at'] = iso_now()
            item['spec_inputs'] = result['spec_inputs']
            explicit_entries = list(result.get('ledger_entries', []))
            if isinstance(result.get('decision'), str) and result.get('decision', '').strip() and not any(isinstance(x, dict) and x.get('type') == 'decision' for x in explicit_entries):
                explicit_entries.append({'type': 'decision', 'statement': result['decision'], 'evidence': result.get('evidence', []), 'blocking': False, 'supersedes': []})
            for raw_entry in explicit_entries:
                if isinstance(raw_entry, dict):
                    add_ledger_entry(doc, raw_entry, decision_id)
            resolved = set(result['fog_resolved'])
            for fog in doc.get('fog', []):
                if isinstance(fog, dict) and fog.get('id') in resolved:
                    fog['status'] = 'resolved'
                    fog['resolved_by'] = decision_id
            for description in result['fog_added']:
                fid = next_fog_id(doc)
                doc.setdefault('fog', []).append({'id': fid, 'description': description, 'status': 'open'})
            for text in result['out_of_scope_added']:
                if text not in doc.setdefault('out_of_scope', []):
                    doc['out_of_scope'].append(text)
            for raw in result['new_decisions']:
                new_item = normalize_new_decision(doc, raw, decision_id)
                if new_item:
                    doc['decisions'].append(new_item)
        else:
            item['status'] = 'needs-human'
            item['blocked_summary'] = result['summary']
            item['blocked_at'] = iso_now()
        doc['updated_at'] = iso_now()
    atomic_json(artifact, result)


def repo_base_for_map(map_dir: pathlib.Path, doc: dict[str, Any]) -> str:
    # execution_base can safely create a synthetic local baseline containing protocol + this map.
    return h.execution_base(map_dir, {'feature': doc['epic']}, {})


def resolve_decision(map_dir: pathlib.Path, decision_id: str, args: argparse.Namespace) -> dict[str, Any]:
    doc = load_map(map_dir)
    errors = validate_map(doc, map_dir)
    if errors:
        die('; '.join(errors))
    item = decision_index(doc).get(decision_id)
    if not item:
        die(f'unknown decision {decision_id}')
    if item.get('status') != 'open':
        die(f'{decision_id} is not open')
    ready_ids = {x['id'] for x in frontier(doc, include_claimed=True)}
    if decision_id not in ready_ids:
        die(f'{decision_id} is blocked by unresolved dependencies')
    owner = args.owner or f'{args.provider}-{os.getpid()}'
    # Freeze the immutable baseline before creating local runtime claim state. Claims are coordination metadata,
    # never an input to the decision itself or the synthetic Git baseline.
    base = repo_base_for_map(map_dir, doc)
    acquire_claim(doc, decision_id, owner)
    read_only = item.get('type') != 'prototype'
    wt = d.create_worktree(str(doc['epic']), f'wayfinder-{decision_id}', base)
    try:
        prompt = prompt_for_decision(doc, item, wt)
        result, runtime_result = run_provider_json(
            epic=str(doc['epic']), stage=decision_id, prompt=prompt, worktree=wt,
            profile='wayfinder-agent', args=args, schema=RESULT_SCHEMA, read_only=read_only,
        )
        validate_result(result, decision_type=str(item.get('type')))
        if not read_only:
            patch = subprocess.run(['git', 'diff', '--binary', '--no-ext-diff', 'HEAD'], cwd=wt, capture_output=True, check=True).stdout
            (runtime_result.parent / 'prototype.patch').write_bytes(patch)
        apply_result(map_dir, decision_id, result)
        return result
    finally:
        d.remove_worktree(wt)
        release_claim(doc, decision_id)


def chart_prompt(doc: dict[str, Any], worktree: pathlib.Path) -> str:
    fog = '\n'.join(f"- {f['id']}: {f['description']}" for f in open_fog(doc))
    return f"""You are rechating a Wayfinder decision map whose frontier is empty but fog remains.

ROLE CONTRACT
-------------
{profile_text(worktree, 'wayfinder-agent')}

{trust.policy_text()}

DESTINATION
-----------
{doc['destination']}

DECISIONS SO FAR
----------------
{map_summary(doc)}

REMAINING FOG
-------------
{fog}

Convert only fog that is now precise enough into `new_decisions`. Do not invent implementation tasks. If the remaining fog
cannot yet be turned into a precise decision question, return needs-human and explain the minimum human input required.
Use decision=null. Return ONLY tooling/agent-harness/schemas/wayfinder-result.schema.json.
"""


def chart(map_dir: pathlib.Path, args: argparse.Namespace) -> dict[str, Any]:
    doc = load_map(map_dir)
    if not open_fog(doc):
        return {'status': 'pass', 'summary': 'No fog remains', 'decision': None, 'rationale': '', 'evidence': [], 'new_decisions': [], 'fog_resolved': [], 'fog_added': [], 'out_of_scope_added': [], 'spec_inputs': [], 'changed_paths': [], 'commands': [], 'assumptions': [], 'residual_risks': []}
    base = repo_base_for_map(map_dir, doc)
    wt = d.create_worktree(str(doc['epic']), 'wayfinder-chart', base)
    try:
        result, _ = run_provider_json(
            epic=str(doc['epic']), stage='chart', prompt=chart_prompt(doc, wt), worktree=wt,
            profile='wayfinder-agent', args=args, schema=RESULT_SCHEMA, read_only=True,
        )
        validate_result(result, allow_empty_decision=True)
        if result['status'] != 'pass':
            return result
        with locked_map(map_dir) as current:
            for raw in result['new_decisions']:
                new_item = normalize_new_decision(current, raw, 'D-CHART')
                if new_item:
                    new_item['depends_on'] = []
                    current['decisions'].append(new_item)
            current['updated_at'] = iso_now()
        atomic_json(map_dir / 'decisions' / f'chart-{args.run_id}.json', result)
        return result
    finally:
        d.remove_worktree(wt)


def spec_handoff_prompt(doc: dict[str, Any], worktree: pathlib.Path) -> str:
    decisions = []
    for item in doc.get('decisions', []):
        decisions.append({
            'id': item.get('id'), 'name': item.get('name'), 'decision': item.get('decision_summary'),
            'rationale': item.get('rationale'), 'spec_inputs': item.get('spec_inputs', []),
        })
    active_ledger = [x for x in doc.get('ledger', []) if isinstance(x, dict) and x.get('status', 'active') == 'active']
    fog_disposition = [{k: f.get(k) for k in ('id','description','status','resolved_by','disposition_reason') if k in f} for f in doc.get('fog', []) if isinstance(f, dict)]
    return f"""You are collapsing a CLEARED Wayfinder decision map into durable Agentic SDD artifacts.

ROLE CONTRACT
-------------
{profile_text(worktree, 'wayfinder-synthesizer')}

EPIC: {doc['epic']}
DESTINATION: {doc['destination']}
OUT OF SCOPE: {json.dumps(doc.get('out_of_scope', []), indent=2)}
DECISIONS: {json.dumps(decisions, indent=2)}
ACTIVE DECISION LEDGER: {json.dumps(active_ledger, indent=2)}
FOG TERMINAL DISPOSITION: {json.dumps(fog_disposition, indent=2)}

Detailed decision evidence is available in `docs/wayfinder/{doc['epic']}/decisions/*.json`; inspect it on demand rather than relying only on these summaries.

Produce a complete `spec.md` (WHAT/WHY/contracts/ACs/NFRs/out-of-scope) and `plan.md` (HOW/architecture/data/consistency/failure/
security/observability/migration/rollback) using this repository's vocabulary and current ADRs. Do not reopen settled decisions.
`design_config` must be a valid design.json object; medium/high-risk work should normally require preflight. Do not create tasks.
Return ONLY tooling/agent-harness/schemas/wayfinder-handoff.schema.json.
"""


def tasks_handoff_prompt(feature_dir: pathlib.Path, worktree: pathlib.Path) -> str:
    spec = (feature_dir / 'spec.md').read_text(encoding='utf-8')
    plan = (feature_dir / 'plan.md').read_text(encoding='utf-8')
    gate = (feature_dir / 'design' / 'gate.json').read_text(encoding='utf-8') if (feature_dir / 'design' / 'gate.json').exists() else '{}'
    return f"""You are converting an accepted, design-gated SDD feature into an executable dependency DAG.

ROLE CONTRACT
-------------
{profile_text(worktree, 'wayfinder-synthesizer')}

FEATURE: {feature_dir.name}
SPEC:\n{spec}
PLAN:\n{plan}
DESIGN GATE:\n{gate}
VERIFICATION CONTRACT:\n{(feature_dir / 'verification-contract.json').read_text(encoding='utf-8') if (feature_dir / 'verification-contract.json').exists() else '<missing>'}

Create tracer-bullet implementation tasks with top-level test_policy=risk-driven: each builder should be independently reviewable, fit one context window, declare
precise allowed_paths, test_mode and test_seam, explicit dependencies, relevant risk_tags, acceptance_criteria ids and the smallest deterministic
verification commands. Parallelize only truly independent write surfaces. Include one independent evaluator task covering EVERY
AC-* and VC-* and one final integration task depending on evaluator. Return ONLY tooling/agent-harness/schemas/wayfinder-tasks-result.schema.json.
"""


def handoff_worktree(epic: str, base: str, label: str) -> pathlib.Path:
    return d.create_worktree(epic, label, base)


def cmd_to_spec(map_dir: pathlib.Path, feature_dir: pathlib.Path, args: argparse.Namespace) -> None:
    doc = load_map(map_dir)
    if not cleared(doc):
        die('wayfinder map is not cleared; resolve all decision tickets and fog before to-spec')
    root = h.repo_root()
    expected_parent = (root / 'docs' / 'specs').resolve()
    if feature_dir.parent.resolve() != expected_parent or feature_dir.name != doc.get('epic'):
        die(f'to-spec destination must be docs/specs/{doc.get("epic")} inside the current repository')
    if feature_dir.exists():
        die(f'refusing to overwrite existing feature directory: {feature_dir}')
    base = repo_base_for_map(map_dir, doc)
    wt = handoff_worktree(str(doc['epic']), base, 'wayfinder-to-spec')
    try:
        result, _ = run_provider_json(
            epic=str(doc['epic']), stage='to-spec', prompt=spec_handoff_prompt(doc, wt), worktree=wt,
            profile='wayfinder-synthesizer', args=args, schema=HANDOFF_SCHEMA, read_only=True,
        )
        if result.get('status') != 'pass' or result.get('open_questions'):
            die(f'to-spec did not converge: {result.get("summary")} open_questions={result.get("open_questions")}')
        spec_md = result.get('spec_markdown')
        plan_md = result.get('plan_markdown')
        design_config = result.get('design_config')
        if not isinstance(spec_md, str) or not spec_md.strip() or not isinstance(plan_md, str) or not plan_md.strip() or not isinstance(design_config, dict):
            die('invalid to-spec handoff payload')
        design_config['verification_contract'] = 'required'
        errors = d.validate_config(design_config)
        if errors:
            die('generated design_config is invalid: ' + '; '.join(errors))
        if design_config.get('required_for_orchestration', True) is not True:
            die('Wayfinder to-spec must require the normal design gate before task generation')
        feature_dir.mkdir(parents=True)
        (feature_dir / 'spec.md').write_text(spec_md.rstrip() + '\n', encoding='utf-8')
        (feature_dir / 'plan.md').write_text(plan_md.rstrip() + '\n', encoding='utf-8')
        atomic_json(feature_dir / 'design.json', design_config)
        (feature_dir / 'packets').mkdir()
        (feature_dir / 'evidence').mkdir()
        atomic_json(feature_dir / 'wayfinder-handoff.json', {
            'schema_version': 1, 'epic': doc['epic'], 'map_sha256': hashlib.sha256(map_path(map_dir).read_bytes()).hexdigest(),
            'generated_at': iso_now(), 'run_id': args.run_id,
        })
        print(feature_dir)
    finally:
        d.remove_worktree(wt)


def cmd_to_tasks(feature_dir: pathlib.Path, args: argparse.Namespace) -> None:
    root = h.repo_root()
    if feature_dir.parent.resolve() != (root / 'docs' / 'specs').resolve():
        die('to-tasks requires an active feature under docs/specs/<feature-id>')
    if (feature_dir / 'tasks.json').exists():
        die(f'refusing to overwrite existing tasks.json: {feature_dir / "tasks.json"}')
    for required in ('spec.md', 'plan.md', 'design.json'):
        if not (feature_dir / required).exists():
            die(f'missing {required}; run to-spec/design first')
    gate_errors = h.design_gate_errors(feature_dir, feature_dir / 'spec.md', feature_dir / 'plan.md')
    if gate_errors:
        die('current design gate is required before to-tasks: ' + '; '.join(gate_errors))
    contract_errors = vc.validate_contract(feature_dir, require=True)
    if contract_errors:
        die('current independent verification contract is required before to-tasks: ' + '; '.join(contract_errors))
    doc = {'feature': feature_dir.name}
    base = h.execution_base(feature_dir, doc, {})
    wt = handoff_worktree(feature_dir.name, base, 'wayfinder-to-tasks')
    try:
        result, _ = run_provider_json(
            epic=feature_dir.name, stage='to-tasks', prompt=tasks_handoff_prompt(feature_dir, wt), worktree=wt,
            profile='wayfinder-synthesizer', args=args, schema=TASKS_SCHEMA, read_only=True,
        )
        tasks = result.get('tasks_json')
        if result.get('status') != 'pass' or not isinstance(tasks, dict):
            die(f'to-tasks failed: {result.get("summary")}')
        tasks['feature'] = feature_dir.name
        with tempfile.TemporaryDirectory(dir=h.repo_root()) as tmp:
            check = pathlib.Path(tmp) / feature_dir.name
            check.mkdir()
            shutil.copy2(feature_dir / 'spec.md', check / 'spec.md')
            shutil.copy2(feature_dir / 'plan.md', check / 'plan.md')
            if (feature_dir / 'verification-contract.json').exists():
                shutil.copy2(feature_dir / 'verification-contract.json', check / 'verification-contract.json')
            atomic_json(check / 'tasks.json', tasks)
            errors = h.validate(check)
        if errors:
            die('generated task DAG failed harness validation: ' + '; '.join(errors))
        atomic_json(feature_dir / 'tasks.json', tasks)
        print(feature_dir / 'tasks.json')
    finally:
        d.remove_worktree(wt)


def print_status(doc: dict[str, Any]) -> None:
    idx = decision_index(doc)
    closed_count = sum(x.get('status') == 'closed' for x in idx.values())
    human_count = sum(x.get('status') == 'needs-human' for x in idx.values())
    items = frontier(doc)
    print(f"EPIC {doc['epic']}")
    print(f"DESTINATION {doc['destination']}")
    print(f"DECISIONS closed={closed_count}/{len(idx)} needs-human={human_count}")
    print(f"FOG open={len(open_fog(doc))}")
    ledger = [x for x in doc.get('ledger', []) if isinstance(x, dict)]
    active_knowledge = sum(x.get('status', 'active') == 'active' for x in ledger)
    print(f"LEDGER active={active_knowledge}/{len(ledger)} blocking_assumptions={len(active_blocking_assumptions(doc))}")
    print(f"CLEARED {'yes' if cleared(doc) else 'no'}")
    print('FRONTIER')
    if not items:
        print('  <empty>')
    for item in items:
        claim = active_claim(doc, str(item['id']))
        suffix = f" claimed={claim.get('owner')}" if claim else ''
        print(f"  {item['id']} score={leverage_score(doc, item)} type={item['type']} - {item['name']}{suffix}")


def cmd_manual_resolve(map_dir: pathlib.Path, decision_id: str, decision: str, rationale: str) -> None:
    doc = load_map(map_dir)
    idx = decision_index(doc)
    item = idx.get(decision_id)
    if not item:
        die(f'unknown decision {decision_id}')
    if item.get('status') not in {'open', 'needs-human'}:
        die(f'{decision_id} is already closed')
    if not all(idx.get(dep, {}).get('status') == 'closed' for dep in item.get('depends_on', [])):
        die(f'{decision_id} is still blocked by unresolved dependencies')
    result = {
        'status': 'pass', 'summary': 'Resolved manually by human', 'decision': decision, 'rationale': rationale,
        'evidence': ['human decision'], 'new_decisions': [], 'fog_resolved': [], 'fog_added': [],
        'out_of_scope_added': [], 'spec_inputs': [decision], 'changed_paths': [], 'commands': [],
        'assumptions': [], 'residual_risks': [],
        'ledger_entries': [{'type':'decision','statement':decision,'evidence':['human decision'],'blocking':False,'supersedes':[]}],
    }
    apply_result(map_dir, decision_id, result, allow_needs_human=True)


def cmd_reconcile(map_dir: pathlib.Path) -> None:
    doc = load_map(map_dir)
    errors = terminal_reconciliation_errors(doc)
    if errors:
        print('RECONCILIATION BLOCKED')
        for error in errors:
            print('  - ' + error)
        raise SystemExit(2)
    print('RECONCILIATION PASS')


def cmd_fog_disposition(map_dir: pathlib.Path, fog_id: str, status: str, reason: str) -> None:
    if status not in TERMINAL_FOG_STATUSES:
        die(f'status must be one of {sorted(TERMINAL_FOG_STATUSES)}')
    with locked_map(map_dir) as doc:
        target = next((x for x in doc.get('fog', []) if isinstance(x, dict) and x.get('id') == fog_id), None)
        if not target:
            die(f'unknown fog item {fog_id}')
        target['status'] = status; target['disposition_reason'] = reason; target['disposed_at'] = iso_now(); target['disposed_by'] = 'human'
        doc['updated_at'] = iso_now()


def cmd_supersede(map_dir: pathlib.Path, old_id: str, statement: str, reason: str) -> None:
    with locked_map(map_dir) as doc:
        old = next((x for x in doc.get('ledger', []) if isinstance(x, dict) and x.get('id') == old_id), None)
        if not old or old.get('status', 'active') != 'active':
            die(f'unknown or non-active ledger entry {old_id}')
        new_id = add_ledger_entry(doc, {'type':'decision','statement':statement,'evidence':[reason],'supersedes':[old_id],'blocking':False}, 'human')
        doc['updated_at'] = iso_now()
        print(new_id)


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description='Wayfinder-style decision mapping before SDD specification')
    sub = p.add_subparsers(dest='command', required=True)
    for name in ('validate', 'status', 'frontier'):
        sp = sub.add_parser(name)
        sp.add_argument('map_dir', type=pathlib.Path)
    sp = sub.add_parser('reconcile')
    sp.add_argument('map_dir', type=pathlib.Path)
    sp = sub.add_parser('fog-disposition')
    sp.add_argument('map_dir', type=pathlib.Path); sp.add_argument('fog_id'); sp.add_argument('--status', required=True, choices=sorted(TERMINAL_FOG_STATUSES)); sp.add_argument('--reason', required=True)
    sp = sub.add_parser('supersede')
    sp.add_argument('map_dir', type=pathlib.Path); sp.add_argument('ledger_id'); sp.add_argument('--statement', required=True); sp.add_argument('--reason', required=True)
    sp = sub.add_parser('resolve')
    sp.add_argument('map_dir', type=pathlib.Path)
    sp.add_argument('decision_id')
    add_provider_args(sp)
    sp.add_argument('--owner')
    sp = sub.add_parser('run')
    sp.add_argument('map_dir', type=pathlib.Path)
    add_provider_args(sp)
    sp.add_argument('--owner')
    sp.add_argument('--max-decisions', type=int, default=8)
    sp.add_argument('--no-auto-chart', action='store_true')
    sp = sub.add_parser('chart')
    sp.add_argument('map_dir', type=pathlib.Path)
    add_provider_args(sp)
    sp = sub.add_parser('manual-resolve')
    sp.add_argument('map_dir', type=pathlib.Path)
    sp.add_argument('decision_id')
    sp.add_argument('--decision', required=True)
    sp.add_argument('--rationale', required=True)
    sp = sub.add_parser('to-spec')
    sp.add_argument('map_dir', type=pathlib.Path)
    sp.add_argument('feature_dir', type=pathlib.Path)
    add_provider_args(sp)
    sp = sub.add_parser('to-tasks')
    sp.add_argument('feature_dir', type=pathlib.Path)
    add_provider_args(sp)
    return p


def add_provider_args(p: argparse.ArgumentParser) -> None:
    p.add_argument('--provider', choices=('codex', 'claude'), default='codex')
    p.add_argument('--model')
    p.add_argument('--reasoning', choices=('low', 'medium', 'high', 'xhigh'), default='high')
    p.add_argument('--max-turns', type=int, default=24)
    p.add_argument('--max-budget-usd', type=float)
    p.add_argument('--run-id')


def ensure_provider(args: argparse.Namespace) -> None:
    if not shutil.which(args.provider):
        die(f'{args.provider} CLI is not installed')
    args.run_id = args.run_id or f'wayfinder-{uuid.uuid4().hex[:12]}'


def main() -> None:
    args = parser().parse_args()
    if hasattr(args, 'map_dir'):
        args.map_dir = args.map_dir.resolve()
    if args.command == 'validate':
        doc = load_map(args.map_dir)
        errors = validate_map(doc, args.map_dir)
        if errors:
            for error in errors:
                print(f'FAIL: {error}')
            raise SystemExit(1)
        print('PASS: wayfinder decision map is structurally valid')
        return
    if args.command == 'status':
        doc = load_map(args.map_dir); print_status(doc); return
    if args.command == 'frontier':
        doc = load_map(args.map_dir)
        items = frontier(doc)
        print('\n'.join(f"{x['id']}\t{leverage_score(doc, x)}\t{x['type']}\t{x['name']}" for x in items) or 'NO_FRONTIER')
        return
    if args.command == 'reconcile':
        cmd_reconcile(args.map_dir); return
    if args.command == 'fog-disposition':
        cmd_fog_disposition(args.map_dir, args.fog_id, args.status, args.reason); return
    if args.command == 'supersede':
        cmd_supersede(args.map_dir, args.ledger_id, args.statement, args.reason); return
    if args.command == 'manual-resolve':
        cmd_manual_resolve(args.map_dir, args.decision_id, args.decision, args.rationale); return
    if args.command == 'resolve':
        ensure_provider(args); resolve_decision(args.map_dir, args.decision_id, args); print_status(load_map(args.map_dir)); return
    if args.command == 'chart':
        ensure_provider(args); result = chart(args.map_dir, args); print(json.dumps(result, indent=2)); return
    if args.command == 'run':
        ensure_provider(args)
        for _ in range(max(1, args.max_decisions)):
            doc = load_map(args.map_dir)
            if cleared(doc):
                print('MAP_CLEARED'); return
            items = frontier(doc)
            if not items:
                if open_fog(doc) and not args.no_auto_chart:
                    result = chart(args.map_dir, args)
                    if result.get('status') != 'pass' or not result.get('new_decisions'):
                        print('NEEDS_HUMAN: frontier empty and remaining fog could not be graduated into decisions')
                        raise SystemExit(3)
                    continue
                print_status(doc)
                raise SystemExit(3)
            result = resolve_decision(args.map_dir, str(items[0]['id']), args)
            if result.get('status') != 'pass':
                print_status(load_map(args.map_dir))
                raise SystemExit(3)
        print_status(load_map(args.map_dir))
        return
    if args.command == 'to-spec':
        ensure_provider(args); cmd_to_spec(args.map_dir, args.feature_dir.resolve(), args); return
    if args.command == 'to-tasks':
        ensure_provider(args); cmd_to_tasks(args.feature_dir.resolve(), args); return


if __name__ == '__main__':
    main()
