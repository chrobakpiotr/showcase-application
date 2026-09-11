#!/usr/bin/env python3
"""Independent acceptance/verification contract authoring for Agentic SDD.

The feature spec states intended behavior. This module creates a second, separately-authored
verification contract using the spec *plus* accepted ADRs, architecture invariants and existing
behavior so the implementation is not evaluated only against criteria written by the same artifact.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import time
import uuid
from typing import Any

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent
SCHEMA = HERE / 'schemas' / 'verification-contract.schema.json'
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import runner as r  # noqa: E402
import telemetry  # noqa: E402
import trust  # noqa: E402

VALID_REQUIREMENT = {'required', 'optional', 'off'}
VALID_ORIGINS = {'spec-derived', 'independent'}
VALID_SOURCE_TYPES = {'spec', 'adr', 'architecture-invariant', 'existing-behavior', 'security', 'performance', 'external-standard', 'human'}


def die(message: str) -> None:
    print(f'ERROR: {message}', file=sys.stderr)
    raise SystemExit(2)


def sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_json(path: pathlib.Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError) as exc:
        die(f'{path}: {exc}')
    if not isinstance(value, dict):
        die(f'{path} must contain a JSON object')
    return value


def repo_root() -> pathlib.Path:
    proc = subprocess.run(['git', '-C', str(REPO), 'rev-parse', '--show-toplevel'], capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        proc = subprocess.run(['git', 'rev-parse', '--show-toplevel'], capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        die('verification-contract generation requires a Git repository')
    return pathlib.Path(proc.stdout.strip()).resolve()


def requirement(feature_dir: pathlib.Path) -> str:
    config = feature_dir / 'design.json'
    if not config.exists():
        return 'optional'
    value = load_json(config).get('verification_contract', 'optional')
    return value if value in VALID_REQUIREMENT else 'invalid'


def input_hashes(feature_dir: pathlib.Path) -> dict[str, str]:
    root = repo_root() if (feature_dir / 'verification-contract.json').exists() else None
    # The installed harness root is reliable for constitution even if called from another cwd.
    constitution = (root or REPO) / 'docs' / 'agentic-sdd' / 'constitution.md'
    gate = feature_dir / 'design' / 'gate.json'
    result = {
        'spec_sha256': sha(feature_dir / 'spec.md'),
        'plan_sha256': sha(feature_dir / 'plan.md'),
        'constitution_sha256': sha(constitution),
    }
    if gate.exists():
        result['design_gate_sha256'] = sha(gate)
    return result


def is_active_feature_dir(feature_dir: pathlib.Path) -> bool:
    resolved = feature_dir.resolve()
    return resolved.parent.name == 'specs' and resolved.parent.parent.name == 'docs'


def validate_contract(feature_dir: pathlib.Path, *, require: bool | None = None) -> list[str]:
    errors: list[str] = []
    required = (requirement(feature_dir) == 'required' and is_active_feature_dir(feature_dir)) if require is None else require
    if requirement(feature_dir) == 'invalid':
        errors.append('design.json.verification_contract must be required/optional/off')
    path = feature_dir / 'verification-contract.json'
    if not path.exists():
        return ['missing required verification-contract.json'] if required else errors
    try:
        doc = json.loads(path.read_text(encoding='utf-8'))
    except json.JSONDecodeError as exc:
        return errors + [f'invalid JSON in verification-contract.json: {exc}']
    if not isinstance(doc, dict):
        return errors + ['verification-contract.json must contain an object']
    if doc.get('schema_version') != 1:
        errors.append('verification-contract.json schema_version must be 1')
    if doc.get('feature') != feature_dir.name:
        errors.append('verification-contract.json feature must match feature directory')
    if doc.get('status') != 'accepted':
        errors.append('verification-contract.json status must be accepted')
    try:
        expected = input_hashes(feature_dir)
    except (OSError, SystemExit):
        expected = {}
    for key, digest in expected.items():
        if doc.get('inputs', {}).get(key) != digest:
            errors.append(f'verification contract is stale: inputs.{key} does not match current artifact')
    criteria = doc.get('criteria')
    if not isinstance(criteria, list) or not criteria:
        errors.append('verification-contract.json criteria must be non-empty')
        criteria = []
    ids: list[str] = []
    independent = 0
    for i, item in enumerate(criteria):
        where = f'criteria[{i}]'
        if not isinstance(item, dict):
            errors.append(f'{where} must be an object'); continue
        cid = item.get('id')
        if not isinstance(cid, str) or not cid.startswith('VC-'):
            errors.append(f'{where}.id must start with VC-')
        else:
            ids.append(cid)
        if not isinstance(item.get('statement'), str) or not item.get('statement', '').strip():
            errors.append(f'{where}.statement must be non-empty')
        if item.get('origin') not in VALID_ORIGINS:
            errors.append(f'{where}.origin must be spec-derived/independent')
        if item.get('origin') == 'independent':
            independent += 1
        if item.get('source_type') not in VALID_SOURCE_TYPES:
            errors.append(f'{where}.source_type is invalid')
        sources = item.get('sources')
        if not isinstance(sources, list) or not sources or any(not isinstance(x, str) or not x.strip() for x in sources):
            errors.append(f'{where}.sources must be a non-empty string array')
        if not isinstance(item.get('verification_hint'), str) or not item.get('verification_hint', '').strip():
            errors.append(f'{where}.verification_hint must be non-empty')
    if len(ids) != len(set(ids)):
        errors.append('verification criterion ids must be unique')
    if required and independent < 1:
        errors.append('required verification contract must contain at least one independent criterion')
    exemptions = doc.get('exemptions', [])
    if not isinstance(exemptions, list):
        errors.append('verification-contract.json exemptions must be an array')
    else:
        xids: set[str] = set()
        for i, item in enumerate(exemptions):
            where = f'exemptions[{i}]'
            if not isinstance(item, dict):
                errors.append(f'{where} must be an object'); continue
            xid = item.get('id')
            if not isinstance(xid, str) or not xid.startswith('VX-') or xid in xids:
                errors.append(f'{where}.id must be a unique VX-* id')
            else:
                xids.add(xid)
            status = item.get('status')
            if status == 'proposed':
                errors.append(f'{where} is still proposed; a human must explicitly accept/reject it')
            if status not in {'accepted', 'rejected', 'proposed'}:
                errors.append(f'{where}.status is invalid')
            if status == 'accepted':
                approver = item.get('approved_by')
                if not isinstance(approver, str) or not (approver == 'human' or approver.startswith('human:')):
                    errors.append(f'{where} accepted exemption requires approved_by=human or human:<name>')
    return errors


def criterion_ids(feature_dir: pathlib.Path) -> set[str]:
    path = feature_dir / 'verification-contract.json'
    if not path.exists():
        return set()
    try:
        doc = json.loads(path.read_text(encoding='utf-8'))
    except json.JSONDecodeError:
        return set()
    return {str(x.get('id')) for x in doc.get('criteria', []) if isinstance(x, dict) and isinstance(x.get('id'), str)}


def prompt(feature_dir: pathlib.Path, worktree: pathlib.Path) -> str:
    root = repo_root()
    rel = feature_dir.resolve().relative_to(root)
    spec = (feature_dir / 'spec.md').read_text(encoding='utf-8')
    plan = (feature_dir / 'plan.md').read_text(encoding='utf-8')
    profile = (worktree / 'docs' / 'agentic-sdd' / 'agents' / 'verification-author.md').read_text(encoding='utf-8')
    return f"""You are independently authoring the verification contract for feature {feature_dir.name}.

ROLE CONTRACT
-------------
{profile}

{trust.policy_text()}

FEATURE ARTIFACTS
-----------------
SPEC (trusted intent, but NOT the only source of truth):
{spec}

PLAN (trusted design decision, but verify it against repository invariants):
{plan}

The same artifacts are available under `{rel}` in this isolated read-only worktree. Inspect AGENTS.md, the constitution,
relevant ADRs, existing tests/contracts and current behavior. Derive criteria that can falsify the implementation even when
spec.md forgot a safety/property requirement. At least one criterion MUST have origin=independent for medium/high-risk work.
Do not invent product behavior. Independent criteria should protect already-accepted architecture, compatibility, security,
operability or existing behavior. Exemptions must remain status=proposed unless a pre-existing human approval is explicitly
present in trusted repository artifacts.

Return ONLY JSON conforming to agent-harness/schemas/verification-contract.schema.json.
"""


def provider_args(args: argparse.Namespace) -> argparse.Namespace:
    return argparse.Namespace(
        print_command=False, sandbox='read-only', model=args.model, reasoning=args.reasoning,
        profile='verification-author', review_existing=True, max_turns=args.max_turns,
        max_budget_usd=args.max_budget_usd,
    )


def generate(feature_dir: pathlib.Path, args: argparse.Namespace) -> pathlib.Path:
    import harness as h  # lazy: avoids circular import when harness validates contracts

    feature_dir = feature_dir.resolve()
    for name in ('spec.md', 'plan.md'):
        if not (feature_dir / name).exists():
            die(f'missing {name}')
    design_errors = h.design_gate_errors(feature_dir, feature_dir / 'spec.md', feature_dir / 'plan.md')
    if design_errors:
        die('verification contract must be authored after a current design gate: ' + '; '.join(design_errors))
    existing = feature_dir / 'verification-contract.json'
    if existing.exists() and not args.force:
        die(f'refusing to overwrite existing contract without --force: {existing}')
    base = h.execution_base(feature_dir, {'feature': feature_dir.name}, {})
    root = h.repo_root()
    wt_root = root.parent / f'{root.name}-agent-worktrees' / feature_dir.name
    wt_root.mkdir(parents=True, exist_ok=True)
    wt = wt_root / f'_verification-{uuid.uuid4().hex[:8]}'
    subprocess.run(['git', 'worktree', 'add', '--detach', str(wt), base], cwd=root, check=True, capture_output=True)
    run_id = args.run_id or uuid.uuid4().hex
    runtime = root / '.agent-runs' / 'verification-contract' / feature_dir.name / run_id
    runtime.mkdir(parents=True, exist_ok=True)
    result_path = runtime / 'result.json'
    text = prompt(feature_dir, wt)
    p_args = provider_args(args)
    cmd = r.codex_command(p_args, text, wt, result_path, schema_path=wt / SCHEMA.relative_to(REPO)) if args.provider == 'codex' else r.claude_command(p_args, text, wt, schema_path=wt / SCHEMA.relative_to(REPO))
    (runtime / 'prompt.txt').write_text(text, encoding='utf-8')
    started = time.monotonic()
    provenance = {'schema_version': 1, 'kind': 'verification-contract', 'feature': feature_dir.name, 'provider': args.provider, 'run_id': run_id, 'started_at': telemetry.iso_now(), 'status': 'running'}
    telemetry.atomic_write_json(runtime / 'provenance.json', provenance)
    try:
        proc = subprocess.run(cmd, cwd=wt, text=True, capture_output=True, check=False)
        (runtime / 'stdout.log').write_text(proc.stdout, encoding='utf-8')
        (runtime / 'stderr.log').write_text(proc.stderr, encoding='utf-8')
        if proc.returncode != 0:
            die(f'{args.provider} exited with {proc.returncode}: {(proc.stderr or proc.stdout)[-2000:]}')
        raw = r.extract_claude_result(proc.stdout) if args.provider == 'claude' else load_json(result_path)
        if raw.get('status') != 'pass':
            die(f'verification author did not pass: {raw.get("summary")}')
        if not isinstance(raw.get('criteria'), list) or not raw['criteria']:
            die('verification author returned no criteria')
        contract = {
            'schema_version': 1,
            'feature': feature_dir.name,
            'status': 'accepted',
            'authored_by': {'profile': 'verification-author', 'provider': args.provider, 'run_id': run_id},
            'generated_at': telemetry.iso_now(),
            'inputs': input_hashes(feature_dir),
            'summary': raw.get('summary'),
            'criteria': raw.get('criteria'),
            'exemptions': raw.get('exemptions', []),
            'assumptions': raw.get('assumptions', []),
        }
        existing.parent.mkdir(parents=True, exist_ok=True)
        existing.write_text(json.dumps(contract, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        errors = validate_contract(feature_dir, require=requirement(feature_dir) == 'required')
        if errors:
            existing.unlink(missing_ok=True)
            die('generated verification contract is invalid: ' + '; '.join(errors))
        provenance.update({'status': 'pass', 'completed_at': telemetry.iso_now(), 'duration_ms': round((time.monotonic()-started)*1000), 'contract_sha256': sha(existing)})
        telemetry.atomic_write_json(runtime / 'provenance.json', provenance)
        return existing
    finally:
        subprocess.run(['git', 'worktree', 'remove', '--force', str(wt)], cwd=root, check=False, capture_output=True)
        shutil.rmtree(wt, ignore_errors=True)


def main() -> None:
    p = argparse.ArgumentParser(description='Independent verification-contract gate')
    sub = p.add_subparsers(dest='command', required=True)
    v = sub.add_parser('validate'); v.add_argument('feature_dir', type=pathlib.Path)
    s = sub.add_parser('show'); s.add_argument('feature_dir', type=pathlib.Path)
    g = sub.add_parser('generate'); g.add_argument('feature_dir', type=pathlib.Path); g.add_argument('--provider', choices=('codex','claude'), required=True); g.add_argument('--model'); g.add_argument('--reasoning', choices=('low','medium','high','xhigh')); g.add_argument('--max-turns', type=int, default=20); g.add_argument('--max-budget-usd', type=float); g.add_argument('--run-id'); g.add_argument('--force', action='store_true')
    args = p.parse_args()
    if args.command == 'validate':
        errors = validate_contract(args.feature_dir.resolve())
        if errors:
            for error in errors: print('ERROR:', error, file=sys.stderr)
            raise SystemExit(2)
        print(f'OK {args.feature_dir}')
    elif args.command == 'show':
        print((args.feature_dir / 'verification-contract.json').read_text(encoding='utf-8'), end='')
    else:
        print(generate(args.feature_dir, args))


if __name__ == '__main__':
    main()
