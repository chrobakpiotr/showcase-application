"""Q11-Q16 durable identity and execution-unit lifecycle probes."""
from __future__ import annotations

import dataclasses
import json
import pathlib
import tempfile
from collections.abc import Callable
from typing import Any

from .q_probes import DockerProbeTarget


Q11_Q16 = {
    'Q11': 'EXECUTION_IDENTITY_DURABLE',
    'Q12': 'EXECUTION_IDENTITY_STALE_REJECTED',
    'Q13': 'CANCEL_EXECUTION_UNIT',
    'Q14': 'DRAIN_EXECUTION_UNIT',
    'Q15': 'RESTART_ACTIVE',
    'Q16': 'RESTART_DRAINED',
}


@dataclasses.dataclass(frozen=True)
class LifecycleRecord:
    check_id: str
    name: str
    status: str
    reason_code: str
    evidence_path: pathlib.Path


def run_q11_q16(
    execute: Callable[[str, str, pathlib.Path], dict[str, Any]], evidence_root: pathlib.Path,
) -> list[LifecycleRecord]:
    evidence_root = evidence_root.resolve()
    evidence_root.mkdir(parents=True, exist_ok=True)
    records = []
    for check_id, name in Q11_Q16.items():
        check_dir = evidence_root / check_id
        check_dir.mkdir()
        try:
            observed = execute(check_id, name, check_dir)
            status = observed.get('status')
            if status not in {'pass', 'fail', 'not-run'}:
                status, reason_code = 'not-run', 'INVALID_PROBE_OUTCOME'
            else:
                reason_code = observed.get('reason_code') or 'UNSPECIFIED_PROBE_OUTCOME'
        except Exception as exc:
            observed = {'stdout': '', 'stderr': '', 'details': {'exception': type(exc).__name__}}
            status, reason_code = 'not-run', 'PROBE_EXCEPTION'
        evidence = {
            'check_id': check_id, 'name': name, 'status': status, 'reason_code': reason_code,
            'stdout': observed.get('stdout', ''), 'stderr': observed.get('stderr', ''),
            'details': observed.get('details', {}),
        }
        evidence_path = check_dir / 'probe.json'
        evidence_path.write_text(json.dumps(evidence, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        records.append(LifecycleRecord(check_id, name, status, reason_code, evidence_path))
    return records


class DockerLifecycleProbeTarget(DockerProbeTarget):
    """Run Showcase's existing identity/lifecycle probes inside the pinned Linux target."""

    def __init__(self, workload_image: str, sandbox_source: pathlib.Path, *, docker: str = 'docker',
                 timeout_seconds: int = 30):
        super().__init__(workload_image, docker=docker, timeout_seconds=timeout_seconds)
        self.sandbox_source = sandbox_source.resolve(strict=True)

    def execute_q11_q16(self, check_id: str, name: str, evidence_dir: pathlib.Path) -> dict[str, Any]:
        with tempfile.TemporaryDirectory(prefix=f'{check_id.lower()}-') as scratch:
            scratch_path = pathlib.Path(scratch)
            work = scratch_path / 'work'
            (work / 'writable').mkdir(parents=True)
            (work / 'protected').mkdir()
            work.chmod(0o777)
            (work / 'writable').chmod(0o777)
            (work / 'protected').chmod(0o777)
            source_mount = f'type=bind,src={self.sandbox_source},dst=/verification_sandbox.py,readonly'
            work_mount = f'type=bind,src={work},dst=/work'
            code = (
                "import importlib.util,json,pathlib,sys; "
                "spec=importlib.util.spec_from_file_location('verification_sandbox','/verification_sandbox.py'); "
                "vs=importlib.util.module_from_spec(spec); sys.modules['verification_sandbox']=vs; spec.loader.exec_module(vs); "
                "candidate=vs.BackendCandidate('docker-linux-probe','Docker Engine target','python3','linux'); "
                "writable=('/work/writable',); protected=('/work/protected',); "
                "policy=vs.sandbox_policy_identity(writable,protected); "
                f"result=vs._run_active_qualification_check(candidate,{check_id!r},{name!r},"
                "worktree=pathlib.Path('/work/writable'),writable=writable,protected=protected,"
                "repository_id='showcase-qualification-probe',policy_identity=policy); "
                "print(json.dumps({'status':result[0],'reason_code':result[1],'details':result[2]}))"
            )
            proc = self._run([
                'run', '--rm', *self._base_args(), '--mount', work_mount, '--mount', source_mount,
                self.workload_image, *self._python(code),
            ], timeout=self.timeout_seconds)
        try:
            observation = json.loads(proc.stdout)
        except json.JSONDecodeError:
            observation = None
        if proc.returncode != 0 or not isinstance(observation, dict):
            return {
                'status': 'not-run', 'reason_code': 'LIFECYCLE_PROBE_UNAVAILABLE',
                'stdout': proc.stdout, 'stderr': proc.stderr,
                'details': {'exit_code': proc.returncode},
            }
        status = {'PASS': 'pass', 'FAIL': 'fail'}.get(observation.get('status'), 'not-run')
        return {
            'status': status, 'reason_code': observation.get('reason_code', 'INVALID_PROBE_RESULT'),
            'stdout': proc.stdout, 'stderr': proc.stderr,
            'details': {**observation, 'exit_code': proc.returncode},
        }
