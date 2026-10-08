import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from qualification.q_probes import Q01_Q10, run_q01_q10
from qualification.q_lifecycle import Q11_Q16, DockerLifecycleProbeTarget, run_q11_q16


class QualificationProbeTest(unittest.TestCase):
    def test_q01_q10_ids_and_names_match_the_accepted_qualification_contract(self):
        self.assertEqual(
            [
                ('Q01', 'PERMITTED_WRITE'),
                ('Q02', 'PROTECTED_WRITE_DIRECT'),
                ('Q03', 'PROTECTED_WRITE_CHILD'),
                ('Q04', 'PROTECTED_WRITE_GRANDCHILD'),
                ('Q05', 'CONTAINMENT_CHILD'),
                ('Q06', 'CONTAINMENT_GRANDCHILD'),
                ('Q07', 'CONTAINMENT_PARENT_EXIT'),
                ('Q08', 'CONTAINMENT_NEW_PROCESS_GROUP'),
                ('Q09', 'CONTAINMENT_NEW_SESSION'),
                ('Q10', 'CONTAINMENT_BACKGROUND_SHELL'),
            ],
            list(Q01_Q10.items()),
        )

    def test_probe_failure_does_not_skip_later_checks_and_each_has_unique_evidence(self):
        calls = []

        def execute(check_id, name, evidence_dir):
            calls.append(check_id)
            return {
                'status': 'fail' if check_id == 'Q02' else 'pass',
                'reason_code': 'COUNTEREXAMPLE' if check_id == 'Q02' else 'OBSERVED',
                'stdout': f'{check_id} stdout',
                'stderr': '',
                'details': {'name': name},
            }

        with tempfile.TemporaryDirectory() as tmp:
            result = run_q01_q10(execute, pathlib.Path(tmp))
            self.assertEqual(list(Q01_Q10), calls)
            self.assertEqual(10, len(result))
            self.assertEqual('fail', result[1].status)
            paths = [record.evidence_path for record in result]
            self.assertEqual(10, len(set(paths)))
            for record in result:
                doc = json.loads(pathlib.Path(record.evidence_path).read_text(encoding='utf-8'))
                self.assertEqual(record.check_id, doc['check_id'])
                self.assertEqual(record.status, doc['status'])
                self.assertTrue(pathlib.Path(record.evidence_path).is_file())

    def test_probe_exception_is_not_pass_and_remaining_checks_still_run(self):
        calls = []

        def execute(check_id, _name, _evidence_dir):
            calls.append(check_id)
            if check_id == 'Q03':
                raise OSError('probe unavailable')
            return {'status': 'pass', 'reason_code': 'OBSERVED', 'stdout': '', 'stderr': '', 'details': {}}

        with tempfile.TemporaryDirectory() as tmp:
            result = run_q01_q10(execute, pathlib.Path(tmp))
        self.assertEqual(list(Q01_Q10), calls)
        self.assertEqual('not-run', result[2].status)
        self.assertEqual('PROBE_EXCEPTION', result[2].reason_code)

    def test_probe_rejects_unknown_outcome_as_not_run(self):
        def execute(_check_id, _name, _evidence_dir):
            return {'status': 'unsupported', 'reason_code': 'unknown', 'stdout': '', 'stderr': '', 'details': {}}

        with tempfile.TemporaryDirectory() as tmp:
            result = run_q01_q10(execute, pathlib.Path(tmp))
        self.assertEqual({'not-run'}, {record.status for record in result})


class LifecycleQualificationProbeTest(unittest.TestCase):
    def _fake_docker(self, root):
        state = root / 'docker-state.json'
        executable = root / 'docker'
        executable.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
state_file = pathlib.Path(os.environ["FAKE_DOCKER_STATE"])
state = json.loads(state_file.read_text()) if state_file.exists() else {"containers": {}, "starts": 0}
args = sys.argv[1:]
op = args[0]
def container(value):
    return next((item for item in state["containers"].values()
                 if value in {item["id"], item["name"]}), None)
if op == "run":
    name = args[args.index("--name") + 1]
    cid = "sha256:" + name[-12:]
    mount = args[args.index("--mount") + 1]
    source = next(part[4:] for part in mount.split(",") if part.startswith("src="))
    marker = pathlib.Path(source) / "child.pid"
    marker.write_text("4321\\n")
    state["containers"][cid] = {"id": cid, "name": name, "status": "running", "pid": 4321}
    print(cid)
elif op == "inspect":
    item = container(args[-1])
    if item is None: sys.exit(1)
    fmt = args[args.index("--format") + 1]
    if fmt == "{{.State.Status}}": print(item["status"])
    else: print(item["status"] + " " + str(item["pid"]))
elif op == "top":
    item = container(args[1])
    if item is None or item["status"] != "running": sys.exit(1)
    print("PID PPID PGID SID COMMAND\\n4321 1 4321 4321 python3\\n4322 4321 4321 4321 python3")
elif op == "stop":
    item = container(args[-1])
    if item is None: sys.exit(1)
    item["status"], item["pid"] = "exited", 0
elif op == "start":
    state["starts"] += 1
    item = container(args[-1])
    if item is None: sys.exit(1)
    item["status"], item["pid"] = "running", 4321
elif op == "rm":
    item = container(args[-1])
    if item: state["containers"].pop(item["id"])
else:
    sys.exit(2)
state_file.write_text(json.dumps(state))
''', encoding='utf-8')
        executable.chmod(0o755)
        return executable, state

    def test_q11_q16_probe_runner_is_job_bound_complete_and_redacts_exceptions(self):
        calls = []

        def execute(check_id, name, evidence_dir, job_id):
            calls.append((check_id, name, job_id))
            if check_id == 'Q12':
                raise RuntimeError('Authorization: do-not-record-this-secret')
            return {'status': 'pass', 'reason_code': 'OBSERVED', 'stdout': 'x' * 9000,
                    'stderr': '', 'details': {'controller_pid': 123}}

        with tempfile.TemporaryDirectory() as tmp:
            result = run_q11_q16(execute, pathlib.Path(tmp), job_id='run-157-attempt-2')
            self.assertEqual(list(Q11_Q16), [item.check_id for item in result])
            self.assertEqual(6, len({item.evidence_path for item in result}))
            self.assertEqual(6, len(calls))
            for item in result:
                record = json.loads(item.evidence_path.read_text(encoding='utf-8'))
                self.assertEqual('run-157-attempt-2', record['job_id'])
                self.assertEqual(item.check_id, record['check_id'])
                self.assertLessEqual(len(record['stdout']), 4096)
            q12 = result[1].evidence_path.read_text(encoding='utf-8')
            self.assertNotIn('do-not-record-this-secret', q12)
            self.assertEqual('PROBE_EXCEPTION', result[1].reason_code)

    def test_docker_q11_q16_use_durable_identity_fresh_controllers_and_terminal_fencing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            docker, state = self._fake_docker(root)
            os.environ['FAKE_DOCKER_STATE'] = str(state)
            try:
                target = DockerLifecycleProbeTarget(
                    'example.invalid/workload@sha256:' + 'a' * 64,
                    docker=str(docker), timeout_seconds=10,
                )
                records = run_q11_q16(target.execute_q11_q16, root / 'evidence', job_id='job-q')
            finally:
                os.environ.pop('FAKE_DOCKER_STATE', None)

            self.assertEqual(list(Q11_Q16), [record.check_id for record in records])
            self.assertEqual({'pass'}, {record.status for record in records},
                             {record.check_id: json.loads(record.evidence_path.read_text()) for record in records})
            evidence = {record.check_id: json.loads(record.evidence_path.read_text()) for record in records}
            self.assertEqual('job-q', evidence['Q11']['job_id'])
            for check_id in Q11_Q16:
                identity = json.loads((root / 'evidence' / check_id / 'execution-identity.json').read_text())
                self.assertEqual('drained', identity['state'])
                self.assertEqual('sha256:' + 'a' * 64, identity['image_digest'])
            self.assertTrue(evidence['Q11']['details']['identity_fsynced'])
            self.assertNotEqual(evidence['Q11']['details']['controller_pids'][0],
                                evidence['Q11']['details']['controller_pids'][1])
            self.assertTrue(evidence['Q12']['details']['stale_identity_rejected'])
            self.assertEqual(1, evidence['Q12']['details']['supplied_generation'])
            self.assertEqual(2, evidence['Q12']['details']['persisted_generation'])
            self.assertTrue(evidence['Q13']['details']['cancelled_and_drained'])
            self.assertTrue(evidence['Q14']['details']['process_table_empty_after_drain'])
            self.assertNotEqual(evidence['Q15']['details']['controller_pids'][0],
                                evidence['Q15']['details']['controller_pids'][1])
            self.assertTrue(evidence['Q15']['details']['same_active_generation_recovered'])
            self.assertTrue(evidence['Q15']['details']['controller_killed'])
            self.assertTrue(evidence['Q16']['details']['terminal_restart_rejected'])
            self.assertTrue(evidence['Q16']['details']['relaunch_attempted'])
            self.assertFalse(evidence['Q16']['details']['launch_admitted'])
            self.assertTrue(evidence['Q16']['details']['controller_killed'])
            self.assertTrue(evidence['Q16']['details']['terminal_record_fsynced'])
            self.assertEqual(3, len(set(evidence['Q16']['details']['controller_pids'])))
            final_state = json.loads(state.read_text())
            self.assertEqual(0, final_state['starts'], 'terminal recovery invoked docker start')
            self.assertNotIn('restart_requests', final_state)

    def test_terminal_relaunch_admission_requires_a_durable_drain_tombstone(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            docker, state = self._fake_docker(root)
            os.environ['FAKE_DOCKER_STATE'] = str(state)
            check_dir = root / 'Q16'
            check_dir.mkdir()
            target = DockerLifecycleProbeTarget(
                'example.invalid/workload@sha256:' + 'a' * 64,
                docker=str(docker), timeout_seconds=10,
            )
            name = 'showcase-qual-q16-no-tombstone'
            try:
                started = target._controller('start', check_dir, 'job-q', name)
                self.assertEqual('pass', started['status'])
                attempted = target._controller('relaunch', check_dir, 'job-q', name,
                                               generation=started['details']['generation'])
                self.assertTrue(attempted['details']['relaunch_attempted'])
                self.assertFalse(attempted['details']['terminal_restart_rejected'])
                self.assertEqual('fail', attempted['status'])
            finally:
                target._cleanup(name)
                os.environ.pop('FAKE_DOCKER_STATE', None)

    def test_q15_fails_if_controller_exits_before_kill_is_applied(self):
        class ExitsBeforeKillTarget(DockerLifecycleProbeTarget):
            def _spawn_held_controller(self, command):
                command = [item for item in command if item != '--hold']
                result = subprocess.run(command, capture_output=True, text=True,
                                        timeout=self.timeout_seconds + 10, check=False)
                observed = json.loads(result.stdout)

                class ExitedController:
                    def __init__(self, pid, returncode, stdout, stderr):
                        self.pid, self.returncode = pid, returncode
                        self.stdout, self.stderr = stdout, stderr

                    def poll(self):
                        return self.returncode

                    def kill(self):
                        # The child already exited; no signal can be delivered.
                        return None

                    def communicate(self, timeout=None):
                        return self.stdout, self.stderr

                return ExitedController(observed['details']['controller_pid'], result.returncode,
                                        result.stdout, result.stderr)

        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            docker, state = self._fake_docker(root)
            os.environ['FAKE_DOCKER_STATE'] = str(state)
            try:
                (root / 'Q15').mkdir()
                target = ExitsBeforeKillTarget(
                    'example.invalid/workload@sha256:' + 'a' * 64,
                    docker=str(docker), timeout_seconds=10,
                )
                result = target.execute_q11_q16('Q15', 'RESTART_ACTIVE', root / 'Q15', 'job-q')
            finally:
                os.environ.pop('FAKE_DOCKER_STATE', None)

            self.assertEqual('fail', result['status'])
            self.assertFalse(result['details']['controller_killed'])


if __name__ == '__main__':
    unittest.main()
