import json
import copy
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from qualification.b_probes import B1_B10, DockerGradingProbeTarget, run_b1_b10


class QualificationGradingProbeTest(unittest.TestCase):
    def _b8_result(self, cgroup, *, memory_returncode=137, oom_killed=True):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)

        def completed(returncode=0, stdout='', stderr=''):
            proc = subprocess.CompletedProcess([], returncode, stdout, stderr)
            proc.captured_output_bytes = len(stdout.encode()) + len(stderr.encode())
            proc.output_truncated = False
            return proc

        def run_python(code, **_kwargs):
            if '/sys/fs/cgroup' in code:
                return completed(stdout=json.dumps(cgroup))
            if 'time.sleep(5)' in code:
                from qualification.b_probes import DockerCommandTimeout
                raise DockerCommandTimeout(
                    ['docker', 'run'], 2, output='', stderr='', container_id='wall-id',
                    container_state={'status': 'exited', 'exit_code': 137}, kill_exit_code=0,
                )
            if 'bytearray' in code:
                return completed(memory_returncode, stderr='memory probe terminated')
            if 'subprocess.Popen' in code:
                return completed(stdout=json.dumps({'pid_limit_observed': True, 'children': 63, 'errno': 11}))
            if "print('X'*1100000)" in code:
                proc = completed(stdout='x' * 1_048_576)
                proc.captured_output_bytes = 1_048_576
                proc.output_truncated = True
                return proc
            raise AssertionError(code)

        with mock.patch.object(target, '_run_python', side_effect=run_python), \
             mock.patch.object(target, '_disk_limit_probe', return_value={'limit_observed': True}), \
             mock.patch.object(target, '_container_state_by_name', return_value={
                 'container_id': 'memory-id', 'status': 'exited', 'exit_code': memory_returncode,
                 'oom_killed': oom_killed,
             }), mock.patch.object(target, '_remove_container_by_id', return_value=True):
            return target.execute_b1_b10('B8', 'RESOURCE_LIMITS_AND_BOUNDED_OUTPUT', pathlib.Path('/tmp'))

    def test_b8_disk_probe_shares_exactly_256_mib_between_workspace_and_tmp(self):
        observed = {}

        def run_docker(args, *, timeout=None, **_kwargs):
            observed['args'] = args
            observed['timeout'] = timeout
            return subprocess.CompletedProcess(
                args, 0, json.dumps({
                    'workspace_bytes': 251658240, 'tmp_bytes': 16777216,
                    'total_bytes': 268435456, 'limit_observed': True,
                    'overflow_errno': 'ENOSPC', 'dev_shm_write_blocked': True,
                }), ''
            )

        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        with mock.patch.object(target, '_run', side_effect=run_docker):
            result = target._disk_limit_probe()

        self.assertTrue(result['limit_observed'], result)
        self.assertEqual(268435456, result['total_bytes'])
        self.assertTrue(any('--tmpfs=/workspace:rw,noexec,nosuid,size=251658240,mode=1777' == arg
                            for arg in observed['args']))
        self.assertTrue(any('--tmpfs=/tmp:rw,noexec,nosuid,size=16m' == arg
                            for arg in observed['args']))
        self.assertIn("/dev/shm/shm-probe", observed['args'][-1])
        self.assertTrue(result['overflow_errno'] == 'ENOSPC')
        self.assertTrue(result['dev_shm_write_blocked'])

    def test_docker_timeout_kills_container_by_id_and_records_state(self):
        from qualification.b_probes import DockerCommandTimeout

        calls = []

        class TimedOutProcess:
            def __init__(self):
                self._pipe_writers = []
                for stream_name in ('stdout', 'stderr'):
                    read_fd, write_fd = os.pipe()
                    setattr(self, stream_name, os.fdopen(read_fd, 'rb', buffering=0))
                    self._pipe_writers.append(write_fd)
                self.waits = 0
                self.killed = False
                self.returncode = None

            def poll(self):
                return self.returncode

            def wait(self, timeout=None):
                self.waits += 1
                self.returncode = -9
                for descriptor in self._pipe_writers:
                    os.close(descriptor)
                self._pipe_writers.clear()
                return self.returncode

            def kill(self):
                self.killed = True

        def docker_command(args, **_kwargs):
            calls.append(args)
            if args[1:3] == ['inspect', '--format={{.Id}}']:
                return subprocess.CompletedProcess(args, 0, 'container-id\n', '')
            if args[1:3] == ['kill', 'container-id']:
                return subprocess.CompletedProcess(args, 0, 'container-id\n', '')
            if args[1:3] == ['inspect', '--format={{.Id}}|{{.State.Status}}|{{.State.ExitCode}}']:
                return subprocess.CompletedProcess(args, 0, 'container-id|exited|137\n', '')
            if args[1:3] == ['rm', 'container-id']:
                return subprocess.CompletedProcess(args, 0, 'container-id\n', '')
            raise AssertionError(args)

        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        with mock.patch('qualification.b_probes.subprocess.Popen', return_value=TimedOutProcess()), \
             mock.patch('qualification.b_probes.subprocess.run', side_effect=docker_command):
            with self.assertRaises(DockerCommandTimeout) as raised:
                target._run(['run', '--name', 'wall-probe', '--rm', 'image', 'python3', '-c', 'pass'], timeout=0.05)

        self.assertEqual('container-id', raised.exception.container_id)
        self.assertEqual({'status': 'exited', 'exit_code': 137}, raised.exception.container_state)
        self.assertEqual(['docker', 'inspect', '--format={{.Id}}', 'wall-probe'], calls[0])
        self.assertIn(['docker', 'kill', 'container-id'], calls)
        self.assertIn(['docker', 'inspect', '--format={{.Id}}|{{.State.Status}}|{{.State.ExitCode}}', 'container-id'], calls)

    def test_docker_command_output_capture_is_capped_across_stdout_and_stderr(self):
        target = DockerGradingProbeTarget(
            'python:3.12-slim@sha256:' + 'a' * 64,
            docker=sys.executable,
        )
        result = target._run([
            '-c', 'import sys; sys.stdout.write("x" * 600000); sys.stderr.write("y" * 600000)'
        ])

        self.assertEqual(0, result.returncode)
        self.assertLessEqual(len(result.stdout.encode()) + len(result.stderr.encode()), 1_048_576)
        self.assertTrue(result.output_truncated)

    def test_docker_timeout_kills_descendants_holding_output_pipes(self):
        target = DockerGradingProbeTarget(
            'python:3.12-slim@sha256:' + 'a' * 64, docker=sys.executable,
        )
        import time
        with tempfile.TemporaryDirectory() as tmp:
            child_pid_file = pathlib.Path(tmp) / 'escaped-child.pid'
            script = (
                'import os,pathlib,time; child=os.fork(); '
                f'\nif child == 0:\n os.setsid(); pathlib.Path({str(child_pid_file)!r}).write_text(str(os.getpid())); time.sleep(30); os._exit(0)\n'
                'time.sleep(30)\n'
            )
            started = time.monotonic()
            try:
                with self.assertRaises(subprocess.TimeoutExpired):
                    target._run(['-c', script], timeout=0.25)
                self.assertLess(time.monotonic() - started, 2)
            finally:
                if child_pid_file.is_file():
                    try:
                        os.kill(int(child_pid_file.read_text()), 9)
                    except ProcessLookupError:
                        pass

    def test_mount_policy_rejects_ancestor_credentials_and_unexpected_host_mounts(self):
        from qualification.b_probes import inspect_mount_policy

        mountinfo = '\n'.join((
            '1 0 0:1 / / rw - overlay overlay rw',
            '2 1 0:2 / /proc rw - proc proc rw',
            '3 1 0:3 / /root rw - ext4 /dev/sda1 rw',
            '4 1 0:4 / /host-secrets rw - overlay overlay rw',
        ))
        result = inspect_mount_policy(
            mountinfo, expected_external_targets={'/workspace', '/inputs'}, expected_tmpfs={})
        self.assertIn('/root', result['protected_overlaps'])
        self.assertIn('/root', result['unexpected_external_mounts'])
        self.assertIn('/host-secrets', result['unexpected_external_mounts'])

    def test_mount_policy_accepts_only_expected_external_mounts(self):
        from qualification.b_probes import inspect_mount_policy

        mountinfo = '\n'.join((
            '1 0 0:1 / / rw - overlay overlay rw',
            '2 1 0:2 / /proc rw - proc proc rw',
            '3 1 0:3 / /workspace rw - ext4 /dev/sda1 rw',
            '4 1 0:4 / /inputs ro - ext4 /dev/sda1 ro',
        ))
        result = inspect_mount_policy(
            mountinfo, expected_external_targets={'/workspace', '/inputs'}, expected_tmpfs={})
        self.assertTrue(result['mountinfo_valid'])
        self.assertTrue(result['exact_external_allowlist'])
        self.assertEqual([], result['protected_overlaps'])
        self.assertEqual([], result['unexpected_external_mounts'])

    def test_udp_send_without_response_is_not_misreported_as_blocked(self):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        result = subprocess.CompletedProcess(
            ['docker', 'run'], 1,
            json.dumps({'egress_tcp': 'blocked', 'metadata_tcp': 'blocked',
                        'egress_udp': 'send-accepted', 'metadata_udp': 'blocked:ENETUNREACH',
                        'dns': 'temporary-name-resolution-failure', 'interfaces': ['lo'],
                        'active_non_loopback_interfaces': [], 'non_loopback_routes': [],
                        'route_observation_complete': True}), '',
        )
        def run_probe(code, **_kwargs):
            compile(code, '<B5-probe>', 'exec')
            return result

        with mock.patch.object(target, '_run_python', side_effect=run_probe):
            observed = target.execute_b1_b10(
                'B5', 'NO_NETWORK_EGRESS_DNS_OR_METADATA', pathlib.Path('/tmp'))
        self.assertEqual('fail', observed['status'], observed)

    def test_b5_accepts_only_network_unreachable_with_isolated_namespace(self):
        from qualification.b_probes import network_isolation_observed

        observed = {
            'interfaces': ['lo', 'ip6tnl0'], 'active_non_loopback_interfaces': [],
            'non_loopback_routes': [], 'route_observation_complete': True,
            'egress_tcp': 'blocked:ENETUNREACH', 'metadata_tcp': 'blocked:EHOSTUNREACH',
            'egress_udp': 'blocked:ENETUNREACH', 'metadata_udp': 'blocked:EHOSTUNREACH',
            'dns': 'temporary-name-resolution-failure',
        }
        self.assertTrue(network_isolation_observed(observed))
        for key, value in (
            ('egress_tcp', 'blocked:ETIMEDOUT'),
            ('egress_udp', 'blocked:EMFILE'),
            ('dns', 'dns-error:-2'),
            ('non_loopback_routes', ['eth0']),
            ('active_non_loopback_interfaces', ['eth0']),
            ('interfaces', ['eth0', 'lo']),
            ('route_observation_complete', False),
        ):
            changed = dict(observed)
            changed[key] = value
            if key == 'interfaces':
                changed['active_non_loopback_interfaces'] = ['eth0']
            with self.subTest(key=key, value=value):
                self.assertFalse(network_isolation_observed(changed))

    def test_b6_rejects_ancestor_mount_exposing_credential_paths(self):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        stdout = json.dumps({
            'inherited_env': {'SHOWCASE_HOST_CANARY': False, 'AWS_ACCESS_KEY_ID': False,
                              'ANTHROPIC_API_KEY': False},
            'host_file_canary_absent': True, 'host_env_canary_absent': True,
            'mountinfo': '\n'.join((
                '1 0 0:1 / / rw - overlay overlay rw',
                '2 1 0:2 / /proc rw - proc proc rw',
                '3 1 0:3 / /root rw - ext4 /dev/sda1 rw',
            )),
        })
        with mock.patch.object(target, '_run_python', return_value=subprocess.CompletedProcess([], 0, stdout, '')), \
             mock.patch.object(target, '_container_mounts_by_name', return_value={
                 'binds': [{'Type': 'bind', 'Source': '/host/root', 'Destination': '/root', 'RW': True}],
                 'tmpfs': {'/tmp': 'rw,noexec,nosuid,size=16m',
                           '/dev/shm': 'ro,noexec,nosuid,size=1m'}}), \
             mock.patch.object(target, '_remove_container'):
            observed = target.execute_b1_b10(
                'B6', 'EXACT_ENVIRONMENT_NO_INHERITED_CREDENTIALS', pathlib.Path('/tmp'))
        self.assertEqual('fail', observed['status'], observed)
        self.assertIn('/root', observed['details']['protected_overlaps'])

    def test_b6_rejects_daemon_reported_unexpected_mount(self):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        result = subprocess.CompletedProcess([], 0, json.dumps({
            'inherited_env': {'SHOWCASE_HOST_CANARY': False, 'AWS_ACCESS_KEY_ID': False,
                              'ANTHROPIC_API_KEY': False},
            'host_file_canary_absent': True, 'host_env_canary_absent': True,
            'mountinfo': '1 0 0:1 / / rw - overlay overlay rw\n2 1 0:2 / /proc rw - proc proc rw',
        }), '')
        with mock.patch.object(target, '_run_python', return_value=result), \
             mock.patch.object(target, '_container_mounts_by_name', return_value={
                 'binds': [{'Type': 'bind', 'Source': '/host/secret', 'Destination': '/host-secrets', 'RW': True}],
                 'tmpfs': {'/tmp': 'rw,noexec,nosuid,size=16m',
                           '/dev/shm': 'ro,noexec,nosuid,size=1m'}}), \
             mock.patch.object(target, '_remove_container'):
            observed = target.execute_b1_b10(
                'B6', 'EXACT_ENVIRONMENT_NO_INHERITED_CREDENTIALS', pathlib.Path('/tmp'))
        self.assertEqual('fail', observed['status'], observed)
        self.assertFalse(observed['details']['daemon_mounts_match'])

    def test_b8_makes_dev_shm_read_only_and_requires_write_to_fail(self):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        self.assertIn('--tmpfs=/dev/shm:ro,noexec,nosuid,size=1m', target._base_args())

    def test_b8_requires_the_exact_cgroup_limits(self):
        expected = {
            'cpu.max': '100000 100000', 'memory.max': '536870912',
            'memory.swap.max': '0', 'pids.max': '64',
        }
        result = self._b8_result(expected)
        self.assertEqual('pass', result['status'], result)
        for changed in (
            {**expected, 'cpu.max': 'max 100000'},
            {**expected, 'memory.max': 'max'},
            {**expected, 'memory.swap.max': '536870912'},
            {**expected, 'pids.max': 'max'},
        ):
            with self.subTest(changed=changed):
                self.assertEqual('fail', self._b8_result(changed)['status'])

    def test_b8_does_not_call_an_unrelated_nonzero_memory_exit_an_oom(self):
        result = self._b8_result({
            'cpu.max': '100000 100000', 'memory.max': '536870912',
            'memory.swap.max': '0', 'pids.max': '64',
        }, memory_returncode=23, oom_killed=False)

        self.assertEqual('fail', result['status'], result)
        self.assertFalse(result['details']['memory']['limit_observed'])

    def test_b4_workspace_is_writable_for_configured_uid_under_restrictive_umask(self):
        observed_modes = []
        observed = {}
        mountinfo = '\n'.join((
            '1 0 0:1 / / rw - overlay overlay rw',
            '2 1 0:2 / /proc rw - proc proc rw',
            '3 1 0:3 / /dev rw - tmpfs tmpfs rw',
            '4 1 0:4 / /dev/pts rw - devpts devpts rw',
            '5 1 0:5 / /dev/shm ro - tmpfs shm ro',
            '6 1 0:6 / /sys rw - sysfs sysfs rw',
            '7 1 0:7 / /sys/fs/cgroup rw - cgroup2 cgroup rw',
            '8 1 0:8 / /etc/hosts ro - ext4 /dev/sda1 ro',
            '9 1 0:9 / /etc/hostname ro - ext4 /dev/sda1 ro',
            '10 1 0:10 / /etc/resolv.conf ro - ext4 /dev/sda1 ro',
            '11 1 0:11 / /tmp rw - tmpfs tmpfs rw',
            '12 1 0:12 / /workspace rw - ext4 /dev/sda1 rw',
            '13 1 0:13 / /inputs ro - ext4 /dev/sda1 ro',
        ))

        def run_probe(_code, *, mounts=(), **_kwargs):
            compile(_code, '<B4-probe>', 'exec')
            observed['mounts'] = mounts
            workspace_mount = next(mount for mount in mounts if 'dst=/workspace' in mount)
            workspace = pathlib.Path(workspace_mount.split('src=', 1)[1].split(',', 1)[0])
            mode = workspace.stat().st_mode & 0o777
            observed_modes.append(mode)
            workspace_rw = bool(mode & 0o002)
            result = {
                'uid': 65532, 'workspace_rw': workspace_rw, 'input_ro': True,
                'root_ro': True, 'tmpfs': True, 'host_paths_mounted': [], 'git_mounted': False,
                'mountinfo': mountinfo,
            }
            return subprocess.CompletedProcess([], 0 if workspace_rw else 1, json.dumps(result), '')

        def inspect_mounts(_name):
            found = []
            for mount in observed['mounts']:
                values = dict(part.split('=', 1) for part in mount.split(',') if '=' in part)
                found.append({'Type': 'bind', 'Source': values['src'], 'Destination': values['dst'],
                              'RW': 'readonly' not in mount})
            found.extend((
                {'Type': 'tmpfs', 'Source': '', 'Destination': '/tmp', 'RW': True},
                {'Type': 'tmpfs', 'Source': '', 'Destination': '/dev/shm', 'RW': None},
            ))
            return {'binds': [item for item in found if item['Type'] == 'bind'],
                    'tmpfs': {'/tmp': 'rw,noexec,nosuid,size=16m',
                              '/dev/shm': 'ro,noexec,nosuid,size=1m'}}

        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        old_umask = os.umask(0o077)
        try:
            with tempfile.TemporaryDirectory() as tmp, \
                 mock.patch.object(target, '_run_python', side_effect=run_probe), \
                 mock.patch.object(target, '_container_mounts_by_name', side_effect=inspect_mounts), \
                 mock.patch.object(target, '_remove_container'):
                result = target.execute_b1_b10('B4', 'FILESYSTEM_AND_MOUNT_BOUNDARIES', pathlib.Path(tmp))
        finally:
            os.umask(old_umask)

        self.assertEqual([0o777], observed_modes)
        self.assertEqual('pass', result['status'], result)

    def test_b1_b10_ids_and_names_are_exact(self):
        self.assertEqual([
            ('B1', 'HIDDEN_MATERIAL_ABSENT'),
            ('B2', 'HIDDEN_EXPECTED_VALUES_UNREADABLE'),
            ('B3', 'FRESH_DESTROYED_SANDBOX'),
            ('B4', 'FILESYSTEM_AND_MOUNT_BOUNDARIES'),
            ('B5', 'NO_NETWORK_EGRESS_DNS_OR_METADATA'),
            ('B6', 'EXACT_ENVIRONMENT_NO_INHERITED_CREDENTIALS'),
            ('B7', 'NONROOT_NO_CAPS_NO_NEW_PRIVS_SECCOMP'),
            ('B8', 'RESOURCE_LIMITS_AND_BOUNDED_OUTPUT'),
            ('B9', 'BOUNDED_ARTIFACT_AFTER_SANDBOX_DESTROYED'),
            ('B10', 'PER_GRADE_TARGET_AND_LIMIT_PROVENANCE'),
        ], list(B1_B10.items()))

    def test_b9_reads_artifact_only_after_container_removal_is_verified(self):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        events = []
        proc = subprocess.CompletedProcess(['docker', 'run'], 0, '', '')

        def launch(_code, *, mounts, **_kwargs):
            source = pathlib.Path(mounts[0].split('src=', 1)[1].split(',', 1)[0])
            (source / 'artifact.bin').write_bytes(b'grade-result')
            return proc

        def control(args):
            events.append(tuple(args))
            return subprocess.CompletedProcess(args, 0, '', '')

        original_read = os.read

        def read_artifact(fd, size):
            if size <= 1_048_577:
                events.append(('read-artifact',))
            return original_read(fd, size)

        with mock.patch.object(target, '_run_python', side_effect=launch), \
             mock.patch.object(target, '_container_state_by_name', return_value={
                 'container_id': 'immutable-container-id', 'status': 'exited', 'exit_code': 0,
                 'oom_killed': False,
             }), mock.patch.object(target, '_control', side_effect=control), \
             mock.patch('qualification.b_probes.os.read', side_effect=read_artifact):
            result = target.execute_b1_b10(
                'B9', 'BOUNDED_ARTIFACT_AFTER_SANDBOX_DESTROYED', pathlib.Path('/tmp'))

        self.assertEqual('pass', result['status'], result)
        self.assertTrue(result['details']['artifact_matches_written_bytes'])
        self.assertEqual(12, result['details']['artifact_bytes'])
        self.assertEqual('22088ecf0cae358847d59ab20eef014d79fb25a2a0d530d7b6863be48cdea6ab',
                         result['details']['artifact_sha256'])
        self.assertIn(('rm', 'immutable-container-id'), events)
        self.assertIn(('ps', '-a', '--no-trunc', '--filter', 'id=immutable-container-id',
                       '--format={{.ID}}'), events)
        remove_event = ('rm', 'immutable-container-id')
        inventory_event = ('ps', '-a', '--no-trunc', '--filter', 'id=immutable-container-id',
                           '--format={{.ID}}')
        self.assertLess(events.index(remove_event), events.index(inventory_event))
        self.assertLess(events.index(inventory_event), events.index(('read-artifact',)))

    def test_b9_does_not_read_artifact_when_removal_fails(self):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        proc = subprocess.CompletedProcess(['docker', 'run'], 0, '', '')

        def launch(_code, *, mounts, **_kwargs):
            source = pathlib.Path(mounts[0].split('src=', 1)[1].split(',', 1)[0])
            (source / 'artifact.bin').write_bytes(b'grade-result')
            return proc

        def control(args):
            if args[0] == 'rm':
                return subprocess.CompletedProcess(args, 1, '', 'remove denied')
            return subprocess.CompletedProcess(args, 0, '', '')

        with mock.patch.object(target, '_run_python', side_effect=launch), \
             mock.patch.object(target, '_container_state_by_name', return_value={
                 'container_id': 'immutable-container-id', 'status': 'exited', 'exit_code': 0,
                 'oom_killed': False,
             }), mock.patch.object(target, '_control', side_effect=control), \
             mock.patch('qualification.b_probes.os.read', side_effect=AssertionError('read before destroy')):
            result = target.execute_b1_b10(
                'B9', 'BOUNDED_ARTIFACT_AFTER_SANDBOX_DESTROYED', pathlib.Path('/tmp'))

        self.assertEqual('fail', result['status'])
        self.assertFalse(result['details']['container_destroyed_before_result_read'])

    def test_b9_rejects_empty_or_altered_artifact_bytes(self):
        for artifact_bytes in (b'', b'altered-result'):
            with self.subTest(artifact_bytes=artifact_bytes):
                target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
                proc = subprocess.CompletedProcess(['docker', 'run'], 0, '', '')

                def launch(_code, *, mounts, **_kwargs):
                    source = pathlib.Path(mounts[0].split('src=', 1)[1].split(',', 1)[0])
                    (source / 'artifact.bin').write_bytes(artifact_bytes)
                    return proc

                def control(args):
                    return subprocess.CompletedProcess(args, 0, '', '')

                with mock.patch.object(target, '_run_python', side_effect=launch), \
                     mock.patch.object(target, '_container_state_by_name', return_value={
                         'container_id': 'immutable-container-id', 'status': 'exited', 'exit_code': 0,
                         'oom_killed': False,
                     }), mock.patch.object(target, '_control', side_effect=control):
                    result = target.execute_b1_b10(
                        'B9', 'BOUNDED_ARTIFACT_AFTER_SANDBOX_DESTROYED', pathlib.Path('/tmp'))

                self.assertEqual('fail', result['status'], result)
                self.assertTrue(result['details']['container_destroyed_before_result_read'])
                self.assertFalse(result['details']['artifact_matches_written_bytes'])

    def test_b9_rejects_symlinked_artifact_without_reading_host_target(self):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        proc = subprocess.CompletedProcess(['docker', 'run'], 0, '', '')

        with tempfile.TemporaryDirectory() as outside:
            sentinel = pathlib.Path(outside) / 'host-secret-sentinel'
            sentinel.write_bytes(b'host-secret-must-not-be-read')

            def launch(_code, *, mounts, **_kwargs):
                source = pathlib.Path(mounts[0].split('src=', 1)[1].split(',', 1)[0])
                (source / 'artifact.bin').symlink_to(sentinel)
                return proc

            def control(args):
                return subprocess.CompletedProcess(args, 0, '', '')

            with mock.patch.object(target, '_run_python', side_effect=launch), \
                 mock.patch.object(target, '_container_state_by_name', return_value={
                     'container_id': 'immutable-container-id', 'status': 'exited', 'exit_code': 0,
                     'oom_killed': False,
                 }), mock.patch.object(target, '_control', side_effect=control), \
                 mock.patch('qualification.b_probes.os.read', side_effect=AssertionError('symlink target read')) as read:
                result = target.execute_b1_b10(
                    'B9', 'BOUNDED_ARTIFACT_AFTER_SANDBOX_DESTROYED', pathlib.Path('/tmp'))

            self.assertEqual('fail', result['status'], result)
            self.assertTrue(result['details']['container_destroyed_before_result_read'])
            self.assertFalse(result['details']['artifact_read'])
            read.assert_not_called()
            self.assertEqual(b'host-secret-must-not-be-read', sentinel.read_bytes())

    def test_b9_rejects_fifo_without_blocking_or_reading(self):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        proc = subprocess.CompletedProcess(['docker', 'run'], 0, '', '')
        original_open = os.open

        def launch(_code, *, mounts, **_kwargs):
            source = pathlib.Path(mounts[0].split('src=', 1)[1].split(',', 1)[0])
            os.mkfifo(source / 'artifact.bin')
            return proc

        def open_nonblocking_artifact(path, flags, *args, **kwargs):
            if path == 'artifact.bin':
                self.assertTrue(flags & os.O_NONBLOCK)
            return original_open(path, flags, *args, **kwargs)

        def control(args):
            return subprocess.CompletedProcess(args, 0, '', '')

        with mock.patch.object(target, '_run_python', side_effect=launch), \
             mock.patch.object(target, '_container_state_by_name', return_value={
                 'container_id': 'immutable-container-id', 'status': 'exited', 'exit_code': 0,
                 'oom_killed': False,
             }), mock.patch.object(target, '_control', side_effect=control), \
             mock.patch('qualification.b_probes.os.open', side_effect=open_nonblocking_artifact), \
             mock.patch('qualification.b_probes.os.read', side_effect=AssertionError('FIFO read')) as read:
            result = target.execute_b1_b10(
                'B9', 'BOUNDED_ARTIFACT_AFTER_SANDBOX_DESTROYED', pathlib.Path('/tmp'))

        self.assertEqual('fail', result['status'], result)
        self.assertTrue(result['details']['container_destroyed_before_result_read'])
        self.assertFalse(result['details']['artifact_read'])
        read.assert_not_called()

    def test_b9_does_not_read_artifact_when_inventory_still_contains_container(self):
        target = DockerGradingProbeTarget('python:3.12-slim@sha256:' + 'a' * 64)
        proc = subprocess.CompletedProcess(['docker', 'run'], 0, '', '')

        def launch(_code, *, mounts, **_kwargs):
            source = pathlib.Path(mounts[0].split('src=', 1)[1].split(',', 1)[0])
            (source / 'artifact.bin').write_bytes(b'grade-result')
            return proc

        def control(args):
            stdout = 'immutable-container-id\n' if args[0] == 'ps' else ''
            return subprocess.CompletedProcess(args, 0, stdout, '')

        with mock.patch.object(target, '_run_python', side_effect=launch), \
             mock.patch.object(target, '_container_state_by_name', return_value={
                 'container_id': 'immutable-container-id', 'status': 'exited', 'exit_code': 0,
                 'oom_killed': False,
             }), mock.patch.object(target, '_control', side_effect=control), \
             mock.patch('qualification.b_probes.os.read', side_effect=AssertionError('read before destroy')):
            result = target.execute_b1_b10(
                'B9', 'BOUNDED_ARTIFACT_AFTER_SANDBOX_DESTROYED', pathlib.Path('/tmp'))

        self.assertEqual('fail', result['status'])
        self.assertFalse(result['details']['container_destroyed_before_result_read'])

    def test_b10_emits_and_validates_a_candidate_result_from_observed_launch(self):
        from agent_harness import contract

        target = DockerGradingProbeTarget(
            'python:3.12-slim@sha256:' + 'a' * 64, target_id='showcase-test-target')
        cgroup = {
            'cpu.max': '100000 100000', 'memory.max': '536870912',
            'memory.swap.max': '0', 'pids.max': '64',
        }
        proc = subprocess.CompletedProcess(['docker', 'run'], 0, json.dumps(cgroup), '')
        proc.captured_output_bytes = len(proc.stdout)
        proc.output_truncated = False
        state = {'container_id': 'container-id', 'status': 'exited', 'exit_code': 0, 'oom_killed': False}
        with mock.patch.object(target, '_run', return_value=proc) as launch, \
             mock.patch.object(target, '_container_state_by_name', return_value=state), \
             mock.patch.object(target, '_control', return_value=subprocess.CompletedProcess([], 0, '', '')):
            result = target.execute_b1_b10(
                'B10', 'PER_GRADE_TARGET_AND_LIMIT_PROVENANCE', pathlib.Path('/tmp'))

        request = result['details']['request']
        candidate = result['details']['result']
        self.assertEqual('pass', result['status'], result)
        self.assertEqual('unqualified', candidate['isolation_level'])
        self.assertEqual({'id': 'showcase-test-target', 'qualification_digest': None,
                          'image_digest': 'sha256:' + 'a' * 64}, candidate['target'])
        self.assertEqual({**request['limits'], 'timeout_seconds': request['timeout_seconds']},
                         candidate['limits']['applied'])
        self.assertEqual('completed', candidate['outcome'])
        self.assertEqual(0, candidate['exit_code'])
        contract.validate_target(candidate['target'], qualified=False)
        contract.validate_result(candidate, request)
        self.assertTrue(any('size=251658240' in arg for arg in launch.call_args.args[0]))
        self.assertTrue(any('--tmpfs=/tmp:rw,noexec,nosuid,size=16m' == arg
                            for arg in launch.call_args.args[0]))

    def test_b10_unknown_terminal_has_no_candidate_target_and_fails_check(self):
        from agent_harness import contract
        from qualification.b_probes import DockerCommandTimeout

        target = DockerGradingProbeTarget(
            'python:3.12-slim@sha256:' + 'a' * 64, target_id='showcase-test-target')
        timeout = DockerCommandTimeout(
            ['docker', 'run'], 1, output='', stderr='', container_id=None,
            container_state=None, kill_exit_code=1,
        )
        with mock.patch.object(target, '_run', side_effect=timeout):
            result = target.execute_b1_b10(
                'B10', 'PER_GRADE_TARGET_AND_LIMIT_PROVENANCE', pathlib.Path('/tmp'))

        self.assertEqual('fail', result['status'])
        candidate = result['details']['result']
        request = result['details']['request']
        self.assertEqual('unknown', candidate['outcome'])
        self.assertIsNone(candidate['target'])
        self.assertIsNone(candidate['limits'])
        contract.validate_result(candidate, request)

    def test_b10_unknown_without_observed_container_state_has_no_candidate_target(self):
        from agent_harness import contract

        target = DockerGradingProbeTarget(
            'python:3.12-slim@sha256:' + 'a' * 64, target_id='showcase-test-target')
        proc = subprocess.CompletedProcess(['docker', 'run'], 0, '{}', '')
        proc.captured_output_bytes = 2
        proc.output_truncated = False
        with mock.patch.object(target, '_run', return_value=proc), \
             mock.patch.object(target, '_container_state_by_name', return_value=None), \
             mock.patch.object(target, '_control', return_value=subprocess.CompletedProcess([], 0, '', '')):
            result = target.execute_b1_b10(
                'B10', 'PER_GRADE_TARGET_AND_LIMIT_PROVENANCE', pathlib.Path('/tmp'))

        candidate = result['details']['result']
        self.assertEqual('fail', result['status'])
        self.assertEqual('unknown', candidate['outcome'])
        self.assertIsNone(candidate['target'])
        self.assertIsNone(candidate['limits'])
        contract.validate_result(candidate, result['details']['request'])

    def test_b10_contract_rejects_non_candidate_target_and_mismatched_limits(self):
        from agent_harness import contract

        target = DockerGradingProbeTarget(
            'python:3.12-slim@sha256:' + 'a' * 64, target_id='showcase-test-target')
        proc = subprocess.CompletedProcess(['docker', 'run'], 0, json.dumps({
            'cpu.max': '100000 100000', 'memory.max': '536870912',
            'memory.swap.max': '0', 'pids.max': '64',
        }), '')
        proc.captured_output_bytes = len(proc.stdout)
        proc.output_truncated = False
        state = {'container_id': 'container-id', 'status': 'exited', 'exit_code': 0, 'oom_killed': False}
        with mock.patch.object(target, '_run', return_value=proc), \
             mock.patch.object(target, '_container_state_by_name', return_value=state), \
             mock.patch.object(target, '_control', return_value=subprocess.CompletedProcess([], 0, '', '')):
            observed = target.execute_b1_b10(
                'B10', 'PER_GRADE_TARGET_AND_LIMIT_PROVENANCE', pathlib.Path('/tmp'))

        request = observed['details']['request']
        result = observed['details']['result']
        for mutation in (
            lambda value: value.update(isolation_level='qualified'),
            lambda value: value['target'].update(qualification_digest='sha256:' + 'b' * 64),
            lambda value: value['limits']['applied'].update(pids=63),
        ):
            invalid = copy.deepcopy(result)
            mutation(invalid)
            with self.subTest(invalid=invalid), self.assertRaises(contract.ContractError):
                contract.validate_result(invalid, request)

    def test_lifecycle_subprocess_tracebacks_are_redacted_before_evidence(self):
        from qualification.q_lifecycle import _safe_controller_stderr

        secret_traceback = 'RuntimeError: token=super-secret-' + ('x' * 1000)
        safe = _safe_controller_stderr(secret_traceback)
        self.assertLessEqual(len(safe), 160)
        self.assertNotIn('super-secret', safe)
        self.assertEqual('[controller stderr redacted]', safe)

    def test_failure_and_exception_do_not_skip_checks_and_evidence_is_per_check(self):
        calls = []

        def execute(check_id, name, evidence_dir):
            calls.append(check_id)
            if check_id == 'B2':
                return {'status': 'fail', 'reason_code': 'COUNTEREXAMPLE'}
            if check_id == 'B3':
                raise OSError('probe unavailable')
            return {'status': 'pass', 'reason_code': 'OBSERVED', 'details': {'name': name}}

        with tempfile.TemporaryDirectory() as tmp:
            records = run_b1_b10(execute, pathlib.Path(tmp))
            self.assertEqual(list(B1_B10), calls)
            self.assertEqual(10, len(records))
            self.assertEqual('fail', records[1].status)
            self.assertEqual('not-run', records[2].status)
            self.assertEqual('PROBE_EXCEPTION', records[2].reason_code)
            self.assertEqual(10, len({record.evidence_path for record in records}))
            for record in records:
                evidence = json.loads(record.evidence_path.read_text(encoding='utf-8'))
                self.assertEqual(record.check_id, evidence['check_id'])
                self.assertEqual(record.status, evidence['status'])
                self.assertTrue(record.evidence_path.is_file())

    def test_invalid_outcome_is_not_a_pass(self):
        records = run_b1_b10(
            lambda *_: {'status': 'unsupported', 'reason_code': 'UNKNOWN'},
            pathlib.Path(tempfile.mkdtemp()),
        )
        self.assertEqual({'not-run'}, {record.status for record in records})
        self.assertEqual({'INVALID_PROBE_OUTCOME'}, {record.reason_code for record in records})


if __name__ == '__main__':
    unittest.main()
