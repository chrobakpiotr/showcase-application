"""Q11-Q16 durable identity and execution-unit lifecycle probes."""
from __future__ import annotations

import dataclasses
import importlib.util
import json
import os
import pathlib
import signal
import subprocess
import sys
import tempfile
import time
import uuid
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


_RESTART_CONTROLLER = r'''import importlib.util,json,os,pathlib,sys,time
source,mode,check_id,root_text,record_text,marker_text=sys.argv[1:]
spec=importlib.util.spec_from_file_location('verification_sandbox',source)
vs=importlib.util.module_from_spec(spec);sys.modules['verification_sandbox']=vs;spec.loader.exec_module(vs)
root=pathlib.Path(root_text);record_path=pathlib.Path(record_text);marker=pathlib.Path(marker_text)
backend=vs.PosixProcessGroupBackend()
controller={'pid':os.getpid(),'birth':backend._birth_identity(os.getpid())}
if mode=='start':
    prepared=backend.prepare(root/'units',repository_id='showcase-qualification-probe',policy_identity='q15-q16-process-restart-v1')
    seconds='30' if check_id=='Q15' else '0.2'
    running=backend.launch(prepared,[sys.executable,'-c',f'import time;time.sleep({seconds})'],cwd=root,
                           env={'PATH':os.environ.get('PATH','/usr/bin:/bin')})
    if check_id=='Q16':
        running.process.wait(timeout=5)
        before=backend.reconcile(running.identity)
        if before.status!='DRAINED': raise RuntimeError('initial-drain-not-proven:'+before.status)
    state=backend._read_state(prepared.state_path)
    record={'controller':controller,'identity':running.identity.to_dict(),'state_path':str(prepared.state_path),
            'persisted_state':state['state'],'payload_pid':state.get('pid'),
            'payload_birth_identity':state.get('birth_identity')}
    temporary=record_path.with_suffix('.tmp');temporary.write_text(json.dumps(record,sort_keys=True),encoding='utf-8')
    with temporary.open('rb') as stream: os.fsync(stream.fileno())
    os.replace(temporary,record_path)
    print(json.dumps({'ready':True,'controller':controller}),flush=True)
    time.sleep(60)
elif mode=='recover':
    record=json.loads(record_path.read_text(encoding='utf-8'))
    identity=vs.ExecutionIdentity.from_dict(record['identity'])
    state_path=pathlib.Path(record['state_path'])
    before=backend.reconcile(identity)
    result={'controller':controller,'execution_identity':identity.to_dict(),'recovered_status':before.status,
            'recovered_reason':before.reason_code,'persisted_state':backend._read_state(state_path)['state']}
    if check_id=='Q15' and before.status=='ACTIVE':
        cleanup=backend.cancel_and_drain(identity,3)
        result['cleanup_status']=cleanup.status
    elif check_id=='Q16' and before.status=='DRAINED':
        try:
            backend.launch(vs.PreparedExecution(identity,state_path),
                           [sys.executable,'-c',f'open({str(marker)!r},"w").write("launched")'],
                           cwd=root,env={'PATH':os.environ.get('PATH','/usr/bin:/bin')})
            result['relaunch_status']='UNEXPECTEDLY_STARTED'
        except RuntimeError as exc:
            result['relaunch_status']='RELAUNCH_REJECTED' if str(exc)=='execution-unit-not-prepared' else 'RELAUNCH_ERROR'
            result['relaunch_error']=str(exc)
        result['relaunch_marker_created']=marker.exists()
    print(json.dumps(result,sort_keys=True))
else:
    raise ValueError('unknown-controller-mode')
'''


def _cleanup_restart_probe(root: pathlib.Path, sandbox_source: pathlib.Path) -> None:
    """Drain only execution groups whose durable state belongs to this probe root."""
    module_name = f'verification_sandbox_cleanup_{uuid.uuid4().hex}'
    spec = importlib.util.spec_from_file_location(module_name, sandbox_source)
    if spec is None or spec.loader is None:
        return
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    try:
        spec.loader.exec_module(module)
    finally:
        sys.modules.pop(module_name, None)
    backend = module.PosixProcessGroupBackend()
    for state_path in (root / 'units').glob('*/unit.json'):
        try:
            state = backend._read_state(state_path)
            identity = module.ExecutionIdentity.from_dict(state['identity'])
            if identity.policy_identity != 'q15-q16-process-restart-v1':
                continue
            pid = state.get('pid')
            birth = state.get('birth_identity')
            if state.get('state') in {'ACTIVE', 'CANCELLING'} and isinstance(pid, int) and isinstance(birth, str):
                if backend._birth_identity(pid) == birth:
                    os.killpg(pid, signal.SIGKILL)
            backend.drain(identity, 3)
        except (OSError, ValueError, KeyError, TypeError, AttributeError):
            continue


def run_process_restart_probe(check_id: str, work_root: pathlib.Path,
                              sandbox_source: pathlib.Path) -> dict[str, Any]:
    """Exercise Q15/Q16 across two OS processes and preserve the observed boundary."""
    if check_id not in {'Q15', 'Q16'}:
        return {'status': 'not-run', 'reason_code': 'NOT_A_RESTART_CHECK', 'details': {}}
    root = pathlib.Path(work_root).resolve() / f'{check_id.lower()}-{uuid.uuid4().hex}'
    root.mkdir(parents=True)
    sandbox_source = pathlib.Path(sandbox_source).resolve(strict=True)
    controller_path = root / 'controller.py'
    controller_path.write_text(_RESTART_CONTROLLER, encoding='utf-8')
    record_path, marker = root / 'execution.json', root / 'relaunch-marker'
    common = [str(controller_path), str(sandbox_source)]
    controller_a = subprocess.Popen(
        [sys.executable, *common, 'start', check_id, str(root), str(record_path), str(marker)],
        cwd=root, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    controller_b = None
    try:
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline and not record_path.exists() and controller_a.poll() is None:
            time.sleep(.02)
        if not record_path.is_file():
            stdout, stderr = controller_a.communicate(timeout=2)
            return {'status': 'not-run', 'reason_code': 'CONTROLLER_A_DID_NOT_PERSIST_STATE',
                    'stdout': stdout, 'stderr': stderr,
                    'details': {'controller_a_pid': controller_a.pid, 'exit_code': controller_a.returncode}}
        first = json.loads(record_path.read_text(encoding='utf-8'))
        controller_a.kill()
        stdout_a, stderr_a = controller_a.communicate(timeout=3)
        controller_b = subprocess.run(
            [sys.executable, *common, 'recover', check_id, str(root), str(record_path), str(marker)],
            cwd=root, capture_output=True, text=True, timeout=8, check=False,
        )
        try:
            second = json.loads(controller_b.stdout)
        except json.JSONDecodeError:
            second = {}
        a_identity = first.get('controller', {})
        b_identity = second.get('controller', {})
        same_execution = first.get('identity') == second.get('execution_identity')
        distinct_processes = (a_identity.get('pid') != b_identity.get('pid') and
                              a_identity.get('birth') != b_identity.get('birth'))
        exited = controller_a.returncode == -signal.SIGKILL
        if check_id == 'Q15':
            passed = (controller_b.returncode == 0 and exited and distinct_processes and same_execution and
                      first.get('persisted_state') == 'ACTIVE' and second.get('recovered_status') == 'ACTIVE' and
                      second.get('cleanup_status') == 'DRAINED')
        else:
            passed = (controller_b.returncode == 0 and exited and distinct_processes and same_execution and
                      first.get('persisted_state') == 'DRAINED' and second.get('recovered_status') == 'DRAINED' and
                      second.get('relaunch_status') == 'RELAUNCH_REJECTED' and
                      second.get('relaunch_marker_created') is False)
        return {
            'status': 'pass' if passed else 'fail',
            'reason_code': 'CONTROLLER_PROCESS_RESTART_RECOVERED' if passed else 'CONTROLLER_PROCESS_RESTART_UNPROVEN',
            'stdout': controller_b.stdout,
            'stderr': '\n'.join(filter(None, (stderr_a, controller_b.stderr))),
            'details': {
                'controller_a': {**a_identity, 'exit_code': controller_a.returncode,
                                 'stdout': stdout_a.strip(), 'persisted_state': first.get('persisted_state')},
                'controller_b': {**b_identity, **{key: value for key, value in second.items() if key != 'controller'},
                                 'exit_code': controller_b.returncode},
                'execution_identity': first.get('identity'),
                'payload_pid': first.get('payload_pid'),
                'payload_birth_identity': first.get('payload_birth_identity'),
                'relaunch_marker_created': marker.exists(),
            },
        }
    except (OSError, subprocess.SubprocessError, ValueError, KeyError) as exc:
        return {'status': 'not-run', 'reason_code': 'CONTROLLER_PROCESS_RESTART_PROBE_ERROR',
                'stderr': f'{type(exc).__name__}: {exc}',
                'details': {'controller_a_pid': controller_a.pid,
                            'controller_b_pid': controller_b.pid if controller_b else None}}
    finally:
        if controller_a.poll() is None:
            controller_a.kill()
            controller_a.wait(timeout=3)
        _cleanup_restart_probe(root, sandbox_source)


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
        self.qualification_source = pathlib.Path(__file__).resolve().parent

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
            if check_id in {'Q15', 'Q16'}:
                qualification_mount = f'type=bind,src={self.qualification_source},dst=/opt/qualification,readonly'
                code = (
                    "import json,pathlib,sys;sys.path.insert(0,'/opt'); "
                    "from qualification.q_lifecycle import run_process_restart_probe; "
                    f"result=run_process_restart_probe({check_id!r},pathlib.Path('/work'),"
                    "pathlib.Path('/verification_sandbox.py')); print(json.dumps(result))"
                )
                proc = self._run([
                    'run', '--rm', *self._base_args(), '--mount', work_mount, '--mount', source_mount,
                    '--mount', qualification_mount, self.workload_image, *self._python(code),
                ], timeout=self.timeout_seconds)
                try:
                    observation = json.loads(proc.stdout)
                except json.JSONDecodeError:
                    observation = None
                if proc.returncode != 0 or not isinstance(observation, dict):
                    return {'status': 'not-run', 'reason_code': 'RESTART_PROBE_UNAVAILABLE',
                            'stdout': proc.stdout, 'stderr': proc.stderr,
                            'details': {'exit_code': proc.returncode}}
                return {**observation, 'stdout': proc.stdout, 'stderr': proc.stderr}
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
