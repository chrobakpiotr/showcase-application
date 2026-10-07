"""B1-B10 grading-boundary probes; incomplete target controls never pass."""
from __future__ import annotations

import dataclasses
import json
import pathlib
import os
import subprocess
import tempfile
import uuid
from collections.abc import Callable
from typing import Any

from .q_probes import DockerProbeTarget


B1_B10 = {
    'B1': 'HIDDEN_MATERIAL_ABSENT',
    'B2': 'HIDDEN_EXPECTED_VALUES_UNREADABLE',
    'B3': 'FRESH_DESTROYED_SANDBOX',
    'B4': 'FILESYSTEM_AND_MOUNT_BOUNDARIES',
    'B5': 'NO_NETWORK_EGRESS_DNS_OR_METADATA',
    'B6': 'EXACT_ENVIRONMENT_NO_INHERITED_CREDENTIALS',
    'B7': 'NONROOT_NO_CAPS_NO_NEW_PRIVS_SECCOMP',
    'B8': 'RESOURCE_LIMITS_AND_BOUNDED_OUTPUT',
    'B9': 'BOUNDED_ARTIFACT_AFTER_SANDBOX_DESTROYED',
    'B10': 'PER_GRADE_TARGET_AND_LIMIT_PROVENANCE',
}


@dataclasses.dataclass(frozen=True)
class GradingRecord:
    check_id: str
    name: str
    status: str
    reason_code: str
    evidence_path: pathlib.Path


def run_b1_b10(
    execute: Callable[[str, str, pathlib.Path], dict[str, Any]], evidence_root: pathlib.Path,
) -> list[GradingRecord]:
    evidence_root = evidence_root.resolve()
    evidence_root.mkdir(parents=True, exist_ok=True)
    records = []
    for check_id, name in B1_B10.items():
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
        records.append(GradingRecord(check_id, name, status, reason_code, evidence_path))
    return records


class DockerGradingProbeTarget(DockerProbeTarget):
    """Measure grading-relevant target controls without enabling a grading backend."""

    def _run(self, args: list[str], *, timeout: int | None = None, env: dict[str, str] | None = None):
        import subprocess
        return subprocess.run([self.docker, *args], capture_output=True, text=True,
                              timeout=timeout or self.timeout_seconds, check=False, env=env)

    def _run_python(self, code: str, *, name: str | None = None, mounts: tuple[str, ...] = (),
                    timeout: int | None = None, argv_extra: tuple[str, ...] = (),
                    docker_env: dict[str, str] | None = None):
        docker_args = ['run', '--rm']
        if name:
            docker_args += ['--name', name]
        docker_args += [*self._base_args()]
        for mount in mounts:
            docker_args += ['--mount', mount]
        docker_args += [self.workload_image, *self._python(code), *argv_extra]
        return self._run(docker_args, timeout=timeout, env=docker_env)

    def execute_b1_b10(self, check_id: str, name: str, evidence_dir: pathlib.Path) -> dict[str, Any]:
        if check_id == 'B1':
            with tempfile.TemporaryDirectory(prefix='hidden-bundle-') as bundle:
                canary = f'hidden-canary-{uuid.uuid4().hex}'
                (pathlib.Path(bundle) / canary).write_text(canary, encoding='ascii')
                code = (
                    "import os,sys; token=sys.argv[1]; found=[]\n"
                    "forbidden={'/proc','/sys','/dev'}\n"
                    "for root,dirs,files in os.walk('/',topdown=True,onerror=lambda _:None):\n"
                    " dirs[:]=[d for d in dirs if os.path.join(root,d) not in forbidden]\n"
                    " for n in files+dirs:\n"
                    "  p=os.path.join(root,n)\n"
                    "  if token in n: found.append(p)\n"
                    "  try:\n   if os.path.isfile(p) and os.path.getsize(p)<=1048576 and token in open(p,errors='ignore').read(): found.append(p)\n"
                    "  except (OSError,PermissionError): pass\n"
                    "visible=bool(found) or any(token in v for v in os.environ.values())\n"
                    "print('hidden-canary-visible' if visible else 'hidden-canary-absent')\n"
                    "raise SystemExit(visible)\n"
                )
                proc = self._run_python(code, argv_extra=(canary,))
            passed = proc.returncode == 0 and 'hidden-canary-absent' in proc.stdout
            return self._observed(proc, passed, 'HIDDEN_MATERIAL_ABSENT',
                                  {'hidden_bundle_created_outside_target': True, 'host_bundle_mounted': False})

        if check_id == 'B2':
            with tempfile.TemporaryDirectory(prefix='hidden-expected-') as secret_root:
                secret = pathlib.Path(secret_root) / f'expected-{uuid.uuid4().hex}.txt'
                secret.write_text(uuid.uuid4().hex, encoding='ascii')
                hidden_path = f'/host-hidden/{secret.name}'
                code = (
                    "import pathlib,sys; p=pathlib.Path(sys.argv[1]); visible=p.exists(); "
                    "print('expected-visible' if visible else 'expected-unreadable'); raise SystemExit(visible)"
                )
                proc = self._run_python(code, argv_extra=(hidden_path,), mounts=(), timeout=self.timeout_seconds)
                passed = proc.returncode == 0 and 'expected-unreadable' in proc.stdout
                return self._observed(proc, passed, 'HIDDEN_EXPECTED_VALUE_UNREADABLE',
                                      {'host_expected_value_created': True, 'host_path_was_mounted': False,
                                       'tested_container_path': hidden_path})

        if check_id == 'B3':
            marker = uuid.uuid4().hex
            first = self._run_python(f"from pathlib import Path; Path('/tmp/marker').write_text({marker!r})")
            second = self._run_python("from pathlib import Path; print('marker-absent' if not Path('/tmp/marker').exists() else 'marker-present')")
            passed = first.returncode == 0 and second.returncode == 0 and 'marker-absent' in second.stdout
            return {
                'status': 'pass' if passed else 'fail', 'reason_code': 'SANDBOX_STATE_NOT_PERSISTED' if passed else 'SANDBOX_STATE_PERSISTED',
                'stdout': first.stdout + second.stdout, 'stderr': first.stderr + second.stderr,
                'details': {'first_exit': first.returncode, 'second_exit': second.returncode,
                            'second_sandbox_marker_absent': 'marker-absent' in second.stdout},
            }

        if check_id == 'B4':
            with tempfile.TemporaryDirectory(prefix='grading-mounts-') as mounts_root:
                root = pathlib.Path(mounts_root)
                workspace, inputs = root / 'workspace', root / 'inputs'
                workspace.mkdir(mode=0o777); inputs.mkdir(mode=0o777)
                (inputs / 'fixture.txt').write_text('read-only input', encoding='ascii')
                workspace_mount = f'type=bind,src={workspace},dst=/workspace'
                inputs_mount = f'type=bind,src={inputs},dst=/inputs,readonly'
                code = (
                    "import json,os,pathlib\n"
                    "lines=pathlib.Path('/proc/self/mountinfo').read_text().splitlines()\n"
                    "mounts={line.split()[4]:line for line in lines if len(line.split())>5}\n"
                    "def can_write(p):\n try:\n  pathlib.Path(p).write_text('probe'); return True\n except OSError: return False\n"
                    "data={'uid':os.getuid(),'workspace_rw':can_write('/workspace/probe'),"
                    "'input_ro':not can_write('/inputs/fixture.txt'),'root_ro':not can_write('/qual-probe'),"
                    "'tmpfs': 'tmpfs' in mounts.get('/tmp',''),"
                    "'host_paths_mounted':[p for p in ('/root/.ssh','/root/.gnupg','/var/run/docker.sock','/run/host-services/ssh-auth.sock') if p in mounts],"
                    "'git_mounted':any('/.git' in p for p in mounts)}; print(json.dumps(data)); "
                    "raise SystemExit(not(data['uid']!=0 and data['workspace_rw'] and data['input_ro'] and data['root_ro'] and data['tmpfs'] and not data['host_paths_mounted'] and not data['git_mounted']))"
                )
                proc = self._run_python(code, mounts=(workspace_mount, inputs_mount))
            try:
                data = json.loads(proc.stdout)
            except json.JSONDecodeError:
                data = {}
            passed = proc.returncode == 0 and data.get('uid', 0) != 0 and data.get('workspace_rw') and data.get('input_ro') and data.get('root_ro') and data.get('tmpfs') and not data.get('host_paths_mounted') and not data.get('git_mounted')
            return self._observed(proc, passed, 'FILESYSTEM_BOUNDARIES_OBSERVED', data)

        if check_id == 'B5':
            code = (
                "import json,socket; result={}; "
                "forbidden=[('egress_tcp','1.1.1.1',443),('metadata_tcp','169.254.169.254',80)]; "
                "\nfor name,host,port in forbidden:\n try:\n  s=socket.create_connection((host,port),timeout=.5); s.close(); result[name]='connected'\n except OSError: result[name]='blocked'\n"
                "for name,host in [('egress_udp','1.1.1.1'),('metadata_udp','169.254.169.254')]:\n"
                " try:\n  s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); s.settimeout(.5); s.sendto(b'probe',(host,53)); s.recvfrom(32); result[name]='response'\n"
                " except OSError: result[name]='blocked'\n"
                "try: socket.getaddrinfo('example.com',443); result['dns']='resolved'\nexcept OSError: result['dns']='blocked'\n"
                "print(json.dumps(result)); raise SystemExit(any(x!='blocked' for x in result.values()))"
            )
            proc = self._run_python(code)
            try:
                data = json.loads(proc.stdout)
            except json.JSONDecodeError:
                data = {}
            passed = proc.returncode == 0 and data and all(v == 'blocked' for v in data.values())
            return self._observed(proc, passed, 'EGRESS_DNS_METADATA_BLOCKED', data)

        if check_id == 'B6':
            canary = f'host-env-canary-{uuid.uuid4().hex}'
            with tempfile.TemporaryDirectory(prefix='host-credentials-') as host_creds:
                (pathlib.Path(host_creds) / 'canary').write_text(canary, encoding='ascii')
                hidden_file_path = '/host-credentials/canary'
                files = ('/root/.ssh', '/root/.aws', '/root/.docker/config.json',
                         '/root/.gnupg', '/run/host-services/ssh-auth.sock', hidden_file_path)
                code = (
                    "import json,os,pathlib,sys; names=('SHOWCASE_HOST_CANARY','AWS_ACCESS_KEY_ID','ANTHROPIC_API_KEY'); "
                    "lines=pathlib.Path('/proc/self/mountinfo').read_text().splitlines(); mounts={x.split()[4] for x in lines if len(x.split())>5}; "
                    "files=tuple(sys.argv[1:-2]); env={n:(n in os.environ) for n in names}; "
                    "mounted=[p for p in files if p in mounts or any(m.startswith(p.rstrip('/')+'/') for m in mounts)]; "
                    "file_canary=pathlib.Path(sys.argv[-2]).exists(); value_canary=sys.argv[-1] in os.environ.values(); "
                    "data={'inherited_env':env,'mounted_credential_paths':mounted,'host_file_canary_absent':not file_canary,'host_env_canary_absent':not value_canary}; "
                    "print(json.dumps(data)); raise SystemExit(any(env.values()) or bool(mounted) or file_canary or value_canary)"
                )
                proc = self._run_python(code, argv_extra=(*files, hidden_file_path, canary),
                                        docker_env={**os.environ, 'SHOWCASE_HOST_CANARY': canary})
            try:
                data = json.loads(proc.stdout)
            except json.JSONDecodeError:
                data = {}
            passed = proc.returncode == 0 and data and not any(data.get('inherited_env', {}).values()) and not data.get('mounted_credential_paths') and data.get('host_file_canary_absent') and data.get('host_env_canary_absent')
            return self._observed(proc, passed, 'HOST_ENV_AND_CREDENTIALS_ABSENT', data)

        if check_id == 'B7':
            code = (
                "import ctypes,json,os,pathlib,socket\n"
                "text=pathlib.Path('/proc/self/status').read_text()\n"
                "get=lambda n: next((x.split()[1] for x in text.splitlines() if x.startswith(n+':')),None)\n"
                "status={'uid':os.getuid(),'capeff':get('CapEff'),'no_new_privs':get('NoNewPrivs'),'seccomp':get('Seccomp')}\n"
                "try:\n socket.socket(socket.AF_INET,socket.SOCK_RAW,socket.IPPROTO_ICMP); status['raw_socket']='allowed'\n"
                "except OSError:\n status['raw_socket']='blocked'\n"
                "try:\n os.setuid(0); status['setuid']='allowed'\n"
                "except OSError:\n status['setuid']='blocked'\n"
                "libc=ctypes.CDLL(None,use_errno=True)\n"
                "rc=libc.mount(b'none',b'/tmp',b'tmpfs',0,None)\n"
                "status['mount']='allowed' if rc==0 else 'blocked'\n"
                "print(json.dumps(status))\n"
                "raise SystemExit(status['uid']==0 or status['capeff']!='0000000000000000' or status['no_new_privs']!='1' or status['seccomp']!='2' or status['raw_socket']!='blocked' or status['setuid']!='blocked' or status['mount']!='blocked')\n"
            )
            proc = self._run_python(code)
            try:
                data = json.loads(proc.stdout)
            except json.JSONDecodeError:
                data = {}
            passed = proc.returncode == 0 and data
            return self._observed(proc, passed, 'PRIVILEGES_RESTRICTED', data)

        if check_id == 'B8':
            # Exercise each limit independently. Configuration alone cannot pass this check;
            # disk quota and the caller's output cap are not supplied by this Docker runner.
            checks: dict[str, Any] = {}
            limits = self._run_python(
                "import pathlib,json; p=pathlib.Path('/sys/fs/cgroup'); "
                "print(json.dumps({n:(p/n).read_text().strip() if (p/n).exists() else None for n in ('cpu.max','memory.max','memory.swap.max','pids.max')}))"
            )
            checks['configured'] = {'exit_code': limits.returncode, 'cgroup': limits.stdout.strip()}
            wall_name = f'showcase-b8-wall-{uuid.uuid4().hex[:10]}'
            try:
                self._run_python('import time; time.sleep(5)', name=wall_name, timeout=2)
                checks['wall'] = {'status': 'not-killed-by-deadline'}
            except subprocess.TimeoutExpired:
                checks['wall'] = {'status': 'client-timeout', 'container_name': wall_name}
            inspect = self._run(['inspect', wall_name])
            if inspect.returncode == 0:
                self._run(['rm', '-f', wall_name])
            checks['wall']['container_gone_after_timeout'] = self._run(['inspect', wall_name]).returncode != 0

            memory = self._run_python(
                "x=bytearray(600*1024*1024); print('memory-allocation-completed')", timeout=15
            )
            checks['memory'] = {'exit_code': memory.returncode,
                                'limit_observed': memory.returncode != 0,
                                'stderr_tail': memory.stderr[-300:]}
            pids = self._run_python(
                "import subprocess,sys; ps=[]\n"
                "try:\n\n while True: ps.append(subprocess.Popen([sys.executable,'-c','import time; time.sleep(20)']))\n"
                "except OSError as e:\n print('pid-limit-observed',len(ps),type(e).__name__)\n"
                "finally:\n\n [p.kill() for p in ps]\n [p.wait() for p in ps]\n",
                timeout=15,
            )
            checks['pids'] = {'exit_code': pids.returncode, 'evidence': pids.stdout[-300:],
                              'limit_named': 'pid-limit-observed' in pids.stdout}

            with tempfile.TemporaryDirectory(prefix='b8-disk-') as disk_root:
                pathlib.Path(disk_root).chmod(0o777)
                mount = f'type=bind,src={disk_root},dst=/workspace'
                disk = self._run_python(
                    "from pathlib import Path; p=Path('/workspace/disk-probe'); f=p.open('wb'); f.truncate(268435457); f.close(); print('disk-over-limit-completed')",
                    mounts=(mount,), timeout=20,
                )
                disk_bytes = (pathlib.Path(disk_root) / 'disk-probe').stat().st_size if (pathlib.Path(disk_root) / 'disk-probe').exists() else 0
                checks['disk'] = {'exit_code': disk.returncode, 'bytes_written': disk_bytes,
                                  'limit_observed': disk_bytes <= 268435456}

            output = self._run_python("print('X'*1100000)", timeout=15)
            output_bytes = len(output.stdout.encode('utf-8'))
            checks['output'] = {'captured_bytes': output_bytes, 'required_max_bytes': 1048576,
                                'limit_observed': output_bytes <= 1048576}
            passed = (limits.returncode == 0 and checks['wall']['container_gone_after_timeout'] and
                      checks['memory']['limit_observed'] and checks['pids']['limit_named'] and
                      checks['disk']['limit_observed'] and checks['output']['limit_observed'])
            reason = 'ALL_RESOURCE_LIMITS_ENFORCED' if passed else 'RESOURCE_LIMIT_COUNTEREXAMPLE'
            return {'status': 'pass' if passed else 'fail', 'reason_code': reason,
                    'stdout': json.dumps({k: v for k, v in checks.items() if k != 'output'}, sort_keys=True) +
                              f"\noutput_captured_bytes={output_bytes}\n",
                    'stderr': output.stderr[-500:], 'details': checks}

        if check_id == 'B9':
            with tempfile.TemporaryDirectory(prefix='bounded-output-') as tmp:
                output_root = pathlib.Path(tmp); output_root.chmod(0o777)
                output = output_root / 'artifact.bin'
                mount = f'type=bind,src={output_root},dst=/output'
                code = "from pathlib import Path; Path('/output/artifact.bin').write_bytes(b'grade-result')"
                container_name = f'showcase-b9-{uuid.uuid4().hex[:12]}'
                proc = self._run_python(code, name=container_name, mounts=(mount,))
                exists = output.is_file()
                inspect = self._run(['inspect', container_name])
                gone = inspect.returncode != 0
                if not gone:
                    self._run(['rm', '-f', container_name])
                payload = output.read_bytes() if exists else b''
                passed = proc.returncode == 0 and exists and len(payload) <= 1_048_576 and gone
                return {'status': 'pass' if passed else 'fail',
                        'reason_code': 'BOUNDED_ARTIFACT_AFTER_DESTROY' if passed else 'ARTIFACT_OR_DESTROY_BOUNDARY_FAILED',
                        'stdout': proc.stdout, 'stderr': proc.stderr,
                        'details': {'artifact_bytes': len(payload), 'artifact_sha256': __import__('hashlib').sha256(payload).hexdigest(),
                                    'container_gone_before_result_read': gone}}

        if check_id == 'B10':
            return {
                'status': 'not-run', 'reason_code': 'CONTRACT_V1_HAS_NO_PER_GRADE_TARGET_DIGEST_FIELDS',
                'stdout': '', 'stderr': '',
                'details': {'required_fields': ['target_id', 'qualification_report_digest', 'workload_image_digest',
                                                'applied_limits', 'limit_fired', 'exit_code'],
                            'limitation': 'No v2 grade result is accepted or exposed by this Showcase checkout.'},
            }
        return {'status': 'not-run', 'reason_code': 'UNKNOWN_CHECK', 'stdout': '', 'stderr': '', 'details': {}}

    @staticmethod
    def _observed(proc, passed: bool, reason: str, details: dict[str, Any] | None = None) -> dict[str, Any]:
        return {'status': 'pass' if passed else 'fail', 'reason_code': reason,
                'stdout': proc.stdout, 'stderr': proc.stderr,
                'details': {'exit_code': proc.returncode, **(details or {})}}
