"""Create a job-bound Harness v0.3.0 qualification report from all 26 probes."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import platform
import re
import shutil
import subprocess
import sys
import uuid
from typing import Any

from .b_probes import B1_B10, DockerGradingProbeTarget, run_b1_b10
from .q_lifecycle import Q11_Q16, DockerLifecycleProbeTarget, run_q11_q16
from .q_probes import Q01_Q10, DockerProbeTarget, run_q01_q10


CHECK_IDS = tuple(Q01_Q10) + tuple(Q11_Q16) + tuple(B1_B10)
ROOT = pathlib.Path(__file__).resolve().parents[3]
POLICY_FILES = (
    'docs/specs/SDD-OBS-001/spec.md',
    'docs/reviews/S30-03a-qualification-2026-10-04.json',
    'docs/specs/AH5-04B-QUAL-001/spec.md',
    'tooling/agent-harness/requirements.txt',
)
DEFAULT_WORKLOAD_IMAGE = (
    'python:3.12@sha256:4d1caded1f729ae443eb803f26ffde7b61e696aeaef62f099abb6dd6b14257c7'
)
_ID = re.compile(r'[A-Za-z0-9][A-Za-z0-9._:/@+-]{0,127}')
_DIGEST = re.compile(r'sha256:[0-9a-f]{64}')


def policy_digest(repository: pathlib.Path = ROOT) -> str:
    """Hash the accepted qualification policy inputs, with paths and order bound."""
    digest = hashlib.sha256()
    for relative in POLICY_FILES:
        source = repository / relative
        raw = source.read_bytes()
        digest.update(relative.encode('utf-8') + b'\0')
        digest.update(hashlib.sha256(raw).digest())
    return 'sha256:' + digest.hexdigest()


def assemble_report(records: list[Any], metadata: dict[str, str], evidence_root: pathlib.Path) -> dict[str, Any]:
    """Bind the exact check set to a job and unique, hashed evidence files."""
    seen: dict[str, Any] = {}
    for record in records:
        check_id = _field(record, 'check_id')
        if check_id not in CHECK_IDS or check_id in seen:
            raise ValueError(f'unknown or duplicate qualification check: {check_id}')
        status = _field(record, 'status')
        if status not in {'pass', 'fail', 'not-run'}:
            raise ValueError(f'invalid status for {check_id}: {status!r}')
        seen[check_id] = record
    if set(seen) != set(CHECK_IDS):
        raise ValueError(f'qualification check set mismatch: missing={sorted(set(CHECK_IDS)-set(seen))}')

    required = ('target', 'policy_digest', 'author', 'job_id', 'host', 'kernel', 'engine',
                'workload_image', 'created_at')
    if any(not isinstance(metadata.get(key), str) or not metadata[key] for key in required):
        raise ValueError('qualification metadata is incomplete')
    for key in ('target', 'author', 'job_id', 'host', 'kernel', 'engine'):
        if not _ID.fullmatch(metadata[key]):
            raise ValueError(f'invalid report identifier {key}: {metadata[key]!r}')
    if not _DIGEST.fullmatch(metadata['policy_digest']) or not _DIGEST.fullmatch(metadata['workload_image']):
        raise ValueError('policy_digest and workload_image must be sha256 digests')
    predecessor = metadata.get('docker_desktop_report_sha256')
    if predecessor is not None and not _DIGEST.fullmatch(predecessor):
        raise ValueError('docker_desktop_report_sha256 must be a sha256 digest')

    root = evidence_root.resolve(strict=True)
    checks = []
    owners: dict[str, str] = {}
    digest_owners: dict[str, str] = {}
    for check_id in CHECK_IDS:
        record = seen[check_id]
        source = pathlib.Path(_field(record, 'evidence_path')).resolve(strict=True)
        if not source.is_file() or not source.is_relative_to(root):
            raise ValueError(f'evidence for {check_id} must be a regular file inside {root}')
        relative = source.relative_to(root).as_posix()
        if relative in owners:
            raise ValueError(f'evidence file reused by {check_id} and {owners[relative]}')
        owners[relative] = check_id
        try:
            raw = json.loads(source.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError) as exc:
            raise ValueError(f'evidence for {check_id} is not readable JSON: {exc}') from exc
        if (not isinstance(raw, dict) or raw.get('check_id') != check_id or
                raw.get('status') != _field(record, 'status')):
            raise ValueError(f'evidence file does not identify {check_id}')
        raw['job_id'] = metadata['job_id']
        source.write_text(json.dumps(raw, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        payload = source.read_bytes()
        evidence_digest = 'sha256:' + hashlib.sha256(payload).hexdigest()
        if evidence_digest in digest_owners:
            raise ValueError(f'evidence content reused by {check_id} and {digest_owners[evidence_digest]}')
        digest_owners[evidence_digest] = check_id
        check_status = _field(record, 'status')
        checks.append({
            'id': check_id,
            'result': check_status,
            'evidence': [{
                'path': relative,
                'sha256': evidence_digest,
                'size': len(payload),
            }],
        })

    target_tuple = {
        'job_id': metadata['job_id'], 'host': metadata['host'], 'kernel': metadata['kernel'],
        'engine': metadata['engine'], 'workload_image': metadata['workload_image'],
    }
    if metadata.get('docker_desktop_report_sha256'):
        target_tuple['docker_desktop_report_sha256'] = metadata['docker_desktop_report_sha256']
    return {
        'contract_version': 1,
        'target': metadata['target'],
        'policy_digest': metadata['policy_digest'],
        'author': metadata['author'],
        'tuple': target_tuple,
        'checks': checks,
        'independent_review': None,
        'created_at': metadata['created_at'],
    }


def _field(record: Any, key: str) -> Any:
    if isinstance(record, dict):
        return record.get(key)
    return getattr(record, key)


def capability_report(qualification: dict[str, Any]) -> dict[str, Any]:
    checks_pass = all(check['result'] == 'pass' for check in qualification['checks'])
    review = qualification['independent_review']
    target_online = (qualification['tuple']['engine'] != 'unavailable' and
                     qualification['tuple']['kernel'] != 'unavailable')
    observed = any(check['result'] in {'pass', 'fail'} for check in qualification['checks'])
    discovered = qualification['tuple']['engine'] != 'unavailable'
    supported = target_online and observed
    qualified = supported and checks_pass and review is not None and review.get('verdict') == 'pass'
    return {
        'contract_version': 1,
        'target': qualification['target'],
        'policy_digest': qualification['policy_digest'],
        'discovered': discovered,
        'supported': supported,
        'qualified': qualified,
        # This feature qualifies evidence; it never enables a runtime launch backend.
        'launch_ready': False,
        'capabilities': [],
        'refusal': ('CAPABILITY_UNSUPPORTED' if qualified else
                    'NOT_QUALIFIED' if supported else 'BACKEND_UNAVAILABLE'),
    }


def _job_id(override: str | None) -> str:
    if override:
        return override
    run_id, attempt = os.getenv('GITHUB_RUN_ID'), os.getenv('GITHUB_RUN_ATTEMPT')
    return f'{run_id}-attempt-{attempt}' if run_id and attempt else f'local-{uuid.uuid4().hex}'


def _probe_identity(docker: str, workload_image: str) -> tuple[str, str]:
    try:
        version = subprocess.run([docker, 'version', '--format', '{{.Server.Version}}'],
                                 capture_output=True, text=True, check=False, timeout=30)
        kernel = subprocess.run(
            [docker, 'run', '--rm', '--network=none', workload_image, 'python3', '-c',
             'import platform; print(platform.release())'], capture_output=True, text=True,
            check=False, timeout=30,
        )
    except subprocess.TimeoutExpired:
        return 'unavailable', 'unavailable'
    engine = version.stdout.strip() if version.returncode == 0 else 'unavailable'
    target_kernel = kernel.stdout.strip() if kernel.returncode == 0 else 'unavailable'
    return engine, target_kernel


def _metadata(args: argparse.Namespace) -> dict[str, str]:
    match = re.search(r'@(?P<digest>sha256:[0-9a-f]{64})$', args.workload_image)
    if not match:
        raise ValueError('--workload-image must include an immutable sha256 digest')
    engine, kernel = _probe_identity(args.docker, args.workload_image)
    if args.host:
        host = args.host
    elif os.getenv('GITHUB_ACTIONS') == 'true':
        host = '-'.join(filter(None, (os.getenv('ImageOS'), os.getenv('ImageVersion'),
                                      os.getenv('RUNNER_OS'), os.getenv('RUNNER_ARCH'))))
    else:
        host = f'{platform.system().lower()}-{platform.release().lower()}-{platform.machine().lower()}-docker-desktop'
    predecessor = os.getenv('SHOWCASE_DOCKER_DESKTOP_PREDECESSOR_SHA256')
    if predecessor and not _DIGEST.fullmatch(predecessor):
        raise ValueError('SHOWCASE_DOCKER_DESKTOP_PREDECESSOR_SHA256 must be sha256:<64 hex>')
    metadata = {
        'target': args.target, 'policy_digest': policy_digest(),
        'author': args.author, 'job_id': _job_id(args.job_id),
        'host': host, 'kernel': kernel, 'engine': engine,
        'workload_image': match.group('digest'),
        'created_at': dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00', 'Z'),
    }
    if predecessor:
        metadata['docker_desktop_report_sha256'] = predecessor
    return metadata


def _write_json(path: pathlib.Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')


def run(args: argparse.Namespace) -> int:
    metadata = _metadata(args)
    evidence_root = args.evidence_root.resolve()
    report_path = args.report.resolve()
    capability_path = args.capability_report.resolve()
    if report_path.exists() or capability_path.exists() or (evidence_root.exists() and any(evidence_root.iterdir())):
        raise FileExistsError('refusing to reuse report or evidence from another run; choose fresh paths/job id')
    evidence_root.mkdir(parents=True, exist_ok=True)

    q_target = DockerProbeTarget(args.workload_image, docker=args.docker, timeout_seconds=args.timeout)
    life_target = DockerLifecycleProbeTarget(
        args.workload_image, ROOT / 'tooling/agent-harness/verification_sandbox.py',
        docker=args.docker, timeout_seconds=args.timeout,
    )
    b_target = DockerGradingProbeTarget(args.workload_image, docker=args.docker, timeout_seconds=args.timeout)
    records = []
    records.extend(run_q01_q10(q_target.execute_q01_q10, evidence_root))
    records.extend(run_q11_q16(life_target.execute_q11_q16, evidence_root))
    records.extend(run_b1_b10(b_target.execute_b1_b10, evidence_root))

    report = assemble_report(records, metadata, evidence_root)
    capability = capability_report(report)
    _write_json(report_path, report)
    _write_json(capability_path, capability)

    cli = shutil.which(args.harness_cli)
    if cli is None:
        raise FileNotFoundError(f'Harness v0.3.0 CLI not found: {args.harness_cli}')
    version = subprocess.run([cli, '--version'], capture_output=True, text=True, check=False, timeout=10)
    if version.returncode != 0 or 'agent-harness 0.3.0' not in version.stdout:
        raise RuntimeError(f'expected Harness 0.3.0 CLI, got: {version.stdout.strip()} {version.stderr.strip()}')
    checker = subprocess.run(
        [cli, 'qualification', '--check', str(report_path), '--evidence-root', str(evidence_root),
         '--capability-report', str(capability_path), '--job-id', metadata['job_id']],
        capture_output=True, text=True, check=False,
    )
    _write_json(report_path.parent / 'checker-result.json', {
        'job_id': metadata['job_id'], 'checker_version': version.stdout.strip(),
        'exit_code': checker.returncode,
        'stdout': checker.stdout, 'stderr': checker.stderr,
    })
    if checker.returncode not in {0, 1}:
        print(checker.stdout, file=sys.stdout, end='')
        print(checker.stderr, file=sys.stderr, end='')
        raise RuntimeError(f'Harness qualification checker rejected the report (exit {checker.returncode})')
    print(f"job_id={metadata['job_id']} target={metadata['target']} checker_exit={checker.returncode}")
    print('qualification status=' + ('PASS' if checker.returncode == 0 else 'NOT QUALIFIED'))
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', required=True)
    parser.add_argument('--author', default='showcase-qualification-driver')
    parser.add_argument('--host')
    parser.add_argument('--job-id')
    parser.add_argument('--workload-image', default=DEFAULT_WORKLOAD_IMAGE)
    parser.add_argument('--docker', default='docker')
    parser.add_argument('--harness-cli', default='agent-harness')
    parser.add_argument('--timeout', type=int, default=30)
    parser.add_argument('--evidence-root', type=pathlib.Path, required=True)
    parser.add_argument('--report', type=pathlib.Path, required=True)
    parser.add_argument('--capability-report', type=pathlib.Path, required=True)
    args = parser.parse_args(argv)
    try:
        return run(args)
    except (OSError, ValueError, RuntimeError) as exc:
        parser.error(str(exc))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
