import argparse
import datetime as dt
import fcntl
import importlib.util
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'wayfinder.py'
spec = importlib.util.spec_from_file_location('sdd_wayfinder', MODULE_PATH)
wayfinder = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = wayfinder
spec.loader.exec_module(wayfinder)


def base_map():
    return {
        'schema_version': 2,
        'epic': 'EPIC-001',
        'destination': 'Reach a clear implementation-ready design.',
        'claim_ttl_seconds': 1800,
        'out_of_scope': [],
        'ledger': [],
        'fog': [
            {'id': 'F-001', 'description': 'Unknown carrier semantics', 'status': 'open'},
            {'id': 'F-002', 'description': 'Unknown migration path', 'status': 'open'},
        ],
        'decisions': [
            {'id': 'D-001', 'name': 'Root', 'question': 'What is truth?', 'type': 'architecture', 'status': 'open', 'depends_on': [], 'fog_refs': ['F-001'], 'leverage': 10, 'context_refs': []},
            {'id': 'D-002', 'name': 'Leaf', 'question': 'How migrate?', 'type': 'migration', 'status': 'open', 'depends_on': ['D-001'], 'fog_refs': ['F-002'], 'leverage': 5, 'context_refs': []},
        ],
    }


class WayfinderTest(unittest.TestCase):
    def _assert_operation_waits_for_wayfinder_lock(self, root, operation, state_assertion):
        lock_path = root / '.agent-state' / 'wayfinder' / 'EPIC-001' / 'map.lock'
        lock_path.parent.mkdir(parents=True, exist_ok=True)
        lock_holder = subprocess.Popen(
            [sys.executable, '-c', (
                'import fcntl, pathlib, sys; '
                'path = pathlib.Path(sys.argv[1]); '
                'f = path.open("a+"); fcntl.flock(f.fileno(), fcntl.LOCK_EX); '
                'print("LOCKED", flush=True); sys.stdin.readline(); '
                'fcntl.flock(f.fileno(), fcntl.LOCK_UN)'
            ), str(lock_path)],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        try:
            self.assertEqual('LOCKED', lock_holder.stdout.readline().strip())
            started = threading.Event()
            finished = threading.Event()
            errors = []

            def invoke():
                started.set()
                try:
                    operation()
                except BaseException as exc:
                    errors.append(exc)
                finally:
                    finished.set()

            worker = threading.Thread(target=invoke)
            worker.start()
            self.assertTrue(started.wait(2))
            self.assertFalse(finished.wait(0.15), 'operation bypassed the held Wayfinder map lock')
            state_assertion()
            lock_holder.stdin.write('\n')
            lock_holder.stdin.flush()
            self.assertTrue(finished.wait(3), 'operation did not continue after releasing map lock')
            worker.join(timeout=1)
            self.assertEqual([], errors)
        finally:
            if lock_holder.poll() is None:
                lock_holder.stdin.write('\n')
                lock_holder.stdin.flush()
                lock_holder.wait(timeout=3)
            if lock_holder.stdout:
                lock_holder.stdout.close()
            if lock_holder.stderr:
                lock_holder.stderr.close()
            if lock_holder.stdin:
                lock_holder.stdin.close()

    def test_valid_map(self):
        self.assertEqual([], wayfinder.validate_map(base_map()))

    def test_frontier_respects_dependencies(self):
        doc = base_map()
        self.assertEqual(['D-001'], [x['id'] for x in wayfinder.frontier(doc, include_claimed=True)])
        doc['decisions'][0]['status'] = 'closed'
        self.assertEqual(['D-002'], [x['id'] for x in wayfinder.frontier(doc, include_claimed=True)])

    def test_frontier_orders_by_leverage_not_creation(self):
        doc = base_map()
        doc['decisions'].append({'id': 'D-003', 'name': 'Higher leverage', 'question': 'Q?', 'type': 'research', 'status': 'open', 'depends_on': [], 'fog_refs': ['F-001', 'F-002'], 'leverage': 9, 'context_refs': []})
        doc['decisions'][0]['leverage'] = 1
        self.assertEqual('D-003', wayfinder.frontier(doc, include_claimed=True)[0]['id'])

    def test_cleared_requires_closed_decisions_and_no_fog(self):
        doc = base_map()
        self.assertFalse(wayfinder.cleared(doc))
        for item in doc['decisions']:
            item['status'] = 'closed'
        for fog in doc['fog']:
            fog['status'] = 'resolved'
        self.assertTrue(wayfinder.cleared(doc))

    def test_apply_result_closes_decision_resolves_fog_and_adds_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            map_dir = pathlib.Path(tmp) / 'EPIC-001'
            map_dir.mkdir()
            (map_dir / 'wayfinder.json').write_text(json.dumps(base_map()))
            # state_root requires a git repo cwd
            repo = pathlib.Path(tmp) / 'repo'; repo.mkdir()
            subprocess.run(['git', 'init', '-q'], cwd=repo, check=True)
            old = os.getcwd(); os.chdir(repo)
            try:
                result = {
                    'status': 'pass', 'summary': 'decided', 'decision': 'Internal canonical state wins', 'rationale': 'R',
                    'evidence': ['ADR'], 'new_decisions': [{'name': 'New', 'question': 'How reconcile?', 'type': 'research', 'leverage': 4, 'context_refs': []}],
                    'fog_resolved': ['F-001'], 'fog_added': ['Unknown replay window'], 'out_of_scope_added': ['No production carrier credentials'],
                    'spec_inputs': ['Canonical state is authoritative'], 'changed_paths': [], 'commands': [], 'assumptions': [], 'residual_risks': [], 'ledger_entries': []
                }
                wayfinder.apply_result(map_dir, 'D-001', result)
                updated = json.loads((map_dir / 'wayfinder.json').read_text())
                self.assertEqual('closed', updated['decisions'][0]['status'])
                self.assertEqual('resolved', updated['fog'][0]['status'])
                self.assertTrue(any(x['name'] == 'New' for x in updated['decisions']))
                self.assertTrue(any(x['description'] == 'Unknown replay window' for x in updated['fog']))
                self.assertTrue((map_dir / 'decisions' / 'D-001.json').exists())
            finally:
                os.chdir(old)

    def test_claim_excludes_live_decision_and_expired_claim_recovers(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            prior_repo = wayfinder.REPO; wayfinder.REPO = root
            old = os.getcwd(); os.chdir(root)
            try:
                doc = base_map()
                wayfinder.acquire_claim(doc, 'D-001', 'worker-a')
                self.assertEqual([], [x for x in wayfinder.frontier(doc) if x['id'] == 'D-001'])
                cp = wayfinder.claim_path(doc['epic'], 'D-001') / 'claim.json'
                claim = json.loads(cp.read_text())
                claim['expires_at'] = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(seconds=1)).isoformat()
                cp.write_text(json.dumps(claim))
                self.assertIsNone(wayfinder.active_claim(doc, 'D-001'))
                self.assertEqual(['D-001'], [x['id'] for x in wayfinder.frontier(doc, include_claimed=True)])
            finally:
                os.chdir(old); wayfinder.REPO = prior_repo

    def test_expired_claim_cleanup_through_active_claim_frontier_and_status_uses_map_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            prior_repo = wayfinder.REPO; wayfinder.REPO = root
            old = os.getcwd(); os.chdir(root)
            try:
                doc = base_map()
                claim_dir = wayfinder.claim_path(doc['epic'], 'D-001')
                claim_dir.mkdir(parents=True)
                expired = dt.datetime.now(dt.timezone.utc) - dt.timedelta(seconds=1)
                (claim_dir / 'claim.json').write_text(json.dumps({'owner': 'old', 'expires_at': expired.isoformat()}))
                for name, operation in (
                    ('active_claim', lambda: wayfinder.active_claim(doc, 'D-001')),
                    ('frontier', lambda: wayfinder.frontier(doc)),
                    ('print_status', lambda: wayfinder.print_status(doc)),
                ):
                    with self.subTest(caller=name):
                        if not claim_dir.exists():
                            claim_dir.mkdir(parents=True)
                            (claim_dir / 'claim.json').write_text(
                                json.dumps({'owner': 'old', 'expires_at': expired.isoformat()}))
                        self._assert_operation_waits_for_wayfinder_lock(
                            root,
                            operation,
                            lambda: self.assertTrue(claim_dir.exists(), 'stale claim was removed under another writer lock'),
                        )
                        self.assertFalse(claim_dir.exists(), f'{name} did not clean the expired claim')
            finally:
                os.chdir(old); wayfinder.REPO = prior_repo

    def test_claim_creation_and_release_use_map_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            prior_repo = wayfinder.REPO; wayfinder.REPO = root
            old = os.getcwd(); os.chdir(root)
            try:
                doc = base_map()
                claim_dir = wayfinder.claim_path(doc['epic'], 'D-001')
                self._assert_operation_waits_for_wayfinder_lock(
                    root,
                    lambda: wayfinder.acquire_claim(doc, 'D-001', 'worker-a'),
                    lambda: self.assertFalse(claim_dir.exists(), 'claim appeared while another writer held map lock'),
                )
                self.assertTrue((claim_dir / 'claim.json').is_file())
                self._assert_operation_waits_for_wayfinder_lock(
                    root,
                    lambda: wayfinder.release_claim(doc, 'D-001'),
                    lambda: self.assertTrue(claim_dir.exists(), 'claim was removed while another writer held map lock'),
                )
                self.assertFalse(claim_dir.exists())
            finally:
                os.chdir(old); wayfinder.REPO = prior_repo

    def test_waiting_for_map_lock_releases_lifecycle_lock_first(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            prior_repo = wayfinder.REPO; wayfinder.REPO = root
            old = os.getcwd(); os.chdir(root)
            try:
                doc = base_map()
                lock_path = wayfinder.state_root(doc['epic']) / 'map.lock'
                lock_path.parent.mkdir(parents=True, exist_ok=True)
                lock_path.touch(mode=0o644)
                lock_holder = subprocess.Popen(
                    [sys.executable, '-c', (
                        'import fcntl, pathlib, sys; f=pathlib.Path(sys.argv[1]).open("r+"); '
                        'fcntl.flock(f.fileno(), fcntl.LOCK_EX); print("LOCKED", flush=True); '
                        'sys.stdin.readline(); fcntl.flock(f.fileno(), fcntl.LOCK_UN)'
                    ), str(lock_path)],
                    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                )
                try:
                    self.assertEqual('LOCKED', lock_holder.stdout.readline().strip())
                    started = threading.Event()
                    finished = threading.Event()
                    worker = threading.Thread(target=lambda: (started.set(), wayfinder.active_claim(doc, 'D-001'), finished.set()))
                    worker.start()
                    self.assertTrue(started.wait(2))
                    self.assertFalse(finished.wait(0.15), 'reader did not wait for map.lock')

                    acquired_lifecycle = threading.Event()
                    contender = threading.Thread(target=lambda: self._acquire_and_release_lifecycle(root, acquired_lifecycle))
                    contender.start()
                    self.assertTrue(acquired_lifecycle.wait(1),
                                    'operation waited for map.lock while still holding the lifecycle lock')
                    lock_holder.stdin.write('\n'); lock_holder.stdin.flush()
                    self.assertTrue(finished.wait(3))
                    worker.join(timeout=1); contender.join(timeout=1)
                finally:
                    if lock_holder.poll() is None:
                        lock_holder.stdin.write('\n'); lock_holder.stdin.flush(); lock_holder.wait(timeout=3)
                    if lock_holder.stdout: lock_holder.stdout.close()
                    if lock_holder.stderr: lock_holder.stderr.close()
                    if lock_holder.stdin: lock_holder.stdin.close()
            finally:
                os.chdir(old); wayfinder.REPO = prior_repo

    @staticmethod
    def _acquire_and_release_lifecycle(root, acquired):
        import repository_state_lock
        with repository_state_lock.lifecycle_lock(root):
            acquired.set()

    def test_map_writer_holds_lifecycle_across_atomic_replace_and_lock_inode_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            prior_repo = wayfinder.REPO; wayfinder.REPO = root
            old = os.getcwd(); os.chdir(root)
            try:
                map_dir = root / 'docs' / 'wayfinder' / 'EPIC-001'; map_dir.mkdir(parents=True)
                (map_dir / 'wayfinder.json').write_text(json.dumps(base_map()))
                doc = wayfinder.load_map(map_dir)
                lock_path = wayfinder._ensure_map_lock(wayfinder.state_root(doc['epic']))
                identity_before = (lock_path.stat().st_dev, lock_path.stat().st_ino)
                original = lock_path.read_bytes()
                lifecycle_path = wayfinder.h.state_snapshots.lifecycle_lock_path(root)
                with lifecycle_path.open('a+') as lifecycle:
                    fcntl.flock(lifecycle.fileno(), fcntl.LOCK_EX)
                    started = threading.Event(); finished = threading.Event(); errors = []
                    def update_map():
                        started.set()
                        try:
                            wayfinder.cmd_manual_resolve(map_dir, 'D-001', 'Selected', 'Test fixture')
                        except BaseException as exc:
                            errors.append(exc)
                        finally:
                            finished.set()
                    worker = threading.Thread(target=update_map); worker.start()
                    self.assertTrue(started.wait(2))
                    self.assertFalse(finished.wait(0.15), 'map writer did not wait for lifecycle lock')
                    self.assertEqual(base_map(), json.loads((map_dir / 'wayfinder.json').read_text()))
                    fcntl.flock(lifecycle.fileno(), fcntl.LOCK_UN)
                    self.assertTrue(finished.wait(3)); worker.join(timeout=1)
                    self.assertEqual([], errors)
                self.assertEqual('closed', wayfinder.load_map(map_dir)['decisions'][0]['status'])
                self.assertEqual(identity_before, (lock_path.stat().st_dev, lock_path.stat().st_ino))
                self.assertEqual(original, lock_path.read_bytes())
            finally:
                os.chdir(old); wayfinder.REPO = prior_repo

    def test_map_lock_symlink_is_refused_without_following_or_replacing_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            prior_repo = wayfinder.REPO; wayfinder.REPO = root
            old = os.getcwd(); os.chdir(root)
            try:
                doc = base_map()
                lock_path = wayfinder.state_root(doc['epic']) / 'map.lock'
                lock_path.parent.mkdir(parents=True)
                target = pathlib.Path(tmp) / 'target'; target.write_text('leave unchanged')
                lock_path.symlink_to(target)
                with self.assertRaises(SystemExit):
                    wayfinder.active_claim(doc, 'D-001')
                self.assertEqual('leave unchanged', target.read_text())
                self.assertTrue(lock_path.is_symlink())
            finally:
                os.chdir(old); wayfinder.REPO = prior_repo

    def test_manual_resolve_can_close_needs_human_ticket(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            map_dir = root / 'EPIC-001'; map_dir.mkdir()
            doc = base_map(); doc['decisions'][0]['status'] = 'needs-human'
            (map_dir / 'wayfinder.json').write_text(json.dumps(doc))
            old = os.getcwd(); os.chdir(root)
            try:
                wayfinder.cmd_manual_resolve(map_dir, 'D-001', 'Human choice', 'Business constraint')
                updated = json.loads((map_dir / 'wayfinder.json').read_text())
                self.assertEqual('closed', updated['decisions'][0]['status'])
            finally:
                os.chdir(old)

    def test_full_resolve_with_fake_codex_closes_ticket_and_cleans_worktree(self):
        source_root = pathlib.Path(__file__).resolve().parents[3]
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            shutil.copytree(source_root / 'tooling' / 'agent-harness', root / 'tooling' / 'agent-harness')
            (root / 'docs' / 'agentic-sdd').mkdir(parents=True)
            shutil.copytree(source_root / 'docs' / 'agentic-sdd' / 'agents', root / 'docs' / 'agentic-sdd' / 'agents')
            (root / 'docs' / 'agentic-sdd' / 'constitution.md').write_text('# constitution\n')
            (root / 'AGENTS.md').write_text('# map\n'); (root / 'CLAUDE.md').write_text('# claude\n')
            (root / 'gradlew').write_text('#!/bin/sh\nexit 0\n'); (root / 'gradlew').chmod(0o755)
            map_dir = root / 'docs' / 'wayfinder' / 'EPIC-001'; map_dir.mkdir(parents=True)
            doc = base_map(); doc['fog'][1]['status'] = 'resolved'
            (map_dir / 'wayfinder.json').write_text(json.dumps(doc))
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.invalid'], cwd=root, check=True)
            subprocess.run(['git', 'add', '.'], cwd=root, check=True); subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            fake_bin = pathlib.Path(tmp) / 'bin'; fake_bin.mkdir(); fake = fake_bin / 'codex'
            fake.write_text("""#!/usr/bin/env python3
import json, pathlib, sys
args=sys.argv[1:]
out=pathlib.Path(args[args.index('--output-last-message')+1])
result={'status':'pass','summary':'resolved','decision':'Use canonical internal shipment state','rationale':'stable provider boundary','evidence':['ADR 0035'],'new_decisions':[],'fog_resolved':['F-001'],'fog_added':[],'out_of_scope_added':[],'spec_inputs':['Canonical state owns normalized status'],'changed_paths':[],'commands':[],'assumptions':[],'residual_risks':[],'ledger_entries':[]}
out.parent.mkdir(parents=True, exist_ok=True); out.write_text(json.dumps(result))
print(json.dumps({'type':'result','usage':{'input_tokens':1,'output_tokens':1}}))
"""); fake.chmod(0o755)
            env=os.environ.copy(); env['PATH']=str(fake_bin)+os.pathsep+env.get('PATH','')
            proc=subprocess.run(['python3','tooling/agent-harness/wayfinder.py','resolve','docs/wayfinder/EPIC-001','D-001','--provider','codex','--run-id','wf-test'], cwd=root, env=env, text=True, capture_output=True)
            self.assertEqual(0, proc.returncode, msg=proc.stdout+'\n'+proc.stderr)
            updated=json.loads((map_dir/'wayfinder.json').read_text())
            self.assertEqual('closed', updated['decisions'][0]['status'])
            worktrees=subprocess.run(['git','worktree','list','--porcelain'], cwd=root, text=True, capture_output=True, check=True).stdout
            self.assertNotIn('_design', worktrees)

    def test_full_handoff_wayfinder_to_spec_design_to_tasks_with_fake_codex(self):
        source_root = pathlib.Path(__file__).resolve().parents[3]
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp) / 'repo'; root.mkdir()
            shutil.copytree(source_root / 'tooling' / 'agent-harness', root / 'tooling' / 'agent-harness')
            (root / 'docs' / 'agentic-sdd').mkdir(parents=True)
            shutil.copytree(source_root / 'docs' / 'agentic-sdd' / 'agents', root / 'docs' / 'agentic-sdd' / 'agents')
            (root / 'docs' / 'agentic-sdd' / 'constitution.md').write_text('# constitution\n')
            (root / 'AGENTS.md').write_text('# map\n'); (root / 'CLAUDE.md').write_text('# claude\n')
            (root / '.gitignore').write_text('.agent-state/\n.agent-runs/\n__pycache__/\n*.pyc\n')
            (root / 'gradlew').write_text('#!/bin/sh\nexit 0\n'); (root / 'gradlew').chmod(0o755)
            map_dir = root / 'docs' / 'wayfinder' / 'WF-001'; map_dir.mkdir(parents=True)
            (map_dir / 'wayfinder.json').write_text(json.dumps({
                'schema_version': 2, 'epic': 'WF-001', 'destination': 'Produce buildable spec', 'claim_ttl_seconds': 1800,
                'out_of_scope': [], 'ledger': [], 'fog': [{'id': 'F-001', 'description': 'truth unknown', 'status': 'open'}],
                'decisions': [{'id': 'D-001', 'name': 'Truth', 'question': 'What owns truth?', 'type': 'architecture',
                               'status': 'open', 'depends_on': [], 'fog_refs': ['F-001'], 'leverage': 10, 'context_refs': []}],
            }))
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.invalid'], cwd=root, check=True)
            subprocess.run(['git', 'add', '.'], cwd=root, check=True); subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            fake_bin = pathlib.Path(tmp) / 'bin'; fake_bin.mkdir(); fake = fake_bin / 'codex'
            fake.write_text("""#!/usr/bin/env python3
import json, pathlib, sys
args=sys.argv[1:]
out=pathlib.Path(args[args.index('--output-last-message')+1])
schema=pathlib.Path(args[args.index('--output-schema')+1]).name
if schema=='wayfinder-result.schema.json':
 result={'status':'pass','summary':'resolved','decision':'Internal state is canonical','rationale':'stable boundary','evidence':['repo'],'new_decisions':[],'fog_resolved':['F-001'],'fog_added':[],'out_of_scope_added':[],'spec_inputs':['internal state canonical'],'changed_paths':[],'commands':[],'assumptions':[],'residual_risks':[],'ledger_entries':[]}
elif schema=='wayfinder-handoff.schema.json':
 result={'status':'pass','summary':'handoff','spec_markdown':'# WF-001\\nRisk: high\\n\\n## Acceptance criteria\\n- AC-001: works\\n','plan_markdown':'# WF-001 plan\\nStatus: DRAFT\\n','design_config':{'required_for_orchestration':True,'grill':'auto','prototype':'auto','architecture_grill':'auto','prototype_max_parallel':2,'prototype_questions':[],'verification_contract':'required'},'open_questions':[],'assumptions':[]}
elif schema=='design-result.schema.json':
 result={'status':'pass','summary':'design pass','blocking_questions':[],'non_blocking_risks':[],'prototype_recommendations':[],'findings':['ok'],'changed_paths':[],'commands':[],'assumptions':[],'residual_risks':[],'ledger_entries':[]}
elif schema=='verification-contract.schema.json':
 result={'status':'pass','summary':'independent contract','criteria':[{'id':'VC-001','statement':'preserve architecture invariant','origin':'independent','source_type':'architecture-invariant','sources':['constitution'],'verification_hint':'architecture test'}],'exemptions':[],'assumptions':[]}
elif schema=='wayfinder-tasks-result.schema.json':
 result={'status':'pass','summary':'tasks','assumptions':[],'tasks_json':{'feature':'WF-001','test_policy':'risk-driven','max_parallel':2,'max_rework_attempts':1,'lease_ttl_seconds':1800,'heartbeat_interval_seconds':60,'tasks':[{'id':'T-001','title':'Build','objective':'Build it','role':'builder','depends_on':[],'allowed_paths':['modules/domain/wf/**'],'risk_tags':['domain'],'acceptance_criteria':['AC-001'],'verification':['./gradlew test'],'test_mode':'existing-suite','test_seam':'domain behavior via existing tests'},{'id':'T-900','title':'Evaluate','objective':'Falsify it','role':'evaluator','depends_on':['T-001'],'allowed_paths':['docs/specs/WF-001/evidence/**'],'risk_tags':['evaluation'],'acceptance_criteria':['AC-001','VC-001'],'verification':['./gradlew test']},{'id':'T-990','title':'Integrate','objective':'Prepare human result','role':'integration','depends_on':['T-900'],'allowed_paths':['docs/specs/WF-001/evidence/**'],'risk_tags':['integration'],'acceptance_criteria':['AC-001'],'verification':['./gradlew test']}]}}
else: raise SystemExit('unexpected schema '+schema)
out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(result)); print(json.dumps({'type':'result','usage':{'input_tokens':1,'output_tokens':1}}))
"""); fake.chmod(0o755)
            env = os.environ.copy(); env['PATH'] = str(fake_bin) + os.pathsep + env.get('PATH', '')
            commands = [
                ['python3', 'tooling/agent-harness/wayfinder.py', 'run', 'docs/wayfinder/WF-001', '--provider', 'codex', '--run-id', 'wf-map'],
                ['python3', 'tooling/agent-harness/wayfinder.py', 'to-spec', 'docs/wayfinder/WF-001', 'docs/specs/WF-001', '--provider', 'codex', '--run-id', 'wf-spec'],
                ['python3', 'tooling/agent-harness/design.py', 'docs/specs/WF-001', '--provider', 'codex', '--run-id', 'wf-design'],
                ['python3', 'tooling/agent-harness/verification_contract.py', 'generate', 'docs/specs/WF-001', '--provider', 'codex', '--run-id', 'wf-vc'],
                ['python3', 'tooling/agent-harness/wayfinder.py', 'to-tasks', 'docs/specs/WF-001', '--provider', 'codex', '--run-id', 'wf-tasks'],
                ['python3', 'tooling/agent-harness/harness.py', 'validate', 'docs/specs/WF-001'],
            ]
            for command in commands:
                proc = subprocess.run(command, cwd=root, env=env, text=True, capture_output=True, check=False)
                self.assertEqual(0, proc.returncode, msg=' '.join(command) + '\n' + proc.stdout + '\n' + proc.stderr)
            self.assertTrue((root / 'docs' / 'specs' / 'WF-001' / 'tasks.json').exists())
            updated = json.loads((map_dir / 'wayfinder.json').read_text())
            self.assertTrue(wayfinder.cleared(updated))

    def test_to_spec_refuses_uncleared_map(self):
        with tempfile.TemporaryDirectory() as tmp:
            map_dir = pathlib.Path(tmp) / 'EPIC-001'; map_dir.mkdir(); (map_dir/'wayfinder.json').write_text(json.dumps(base_map()))
            args=argparse.Namespace()
            with self.assertRaises(SystemExit):
                wayfinder.cmd_to_spec(map_dir, pathlib.Path(tmp)/'feature', args)

    def test_research_ticket_can_close_with_fact_without_decision(self):
        result={'status':'pass','summary':'researched','decision':None,'rationale':'measured','evidence':['benchmark'],'new_decisions':[],'fog_resolved':[],'fog_added':[],'out_of_scope_added':[],'spec_inputs':['fact'], 'changed_paths':[],'commands':[],'assumptions':[],'residual_risks':[], 'ledger_entries':[{'type':'fact','statement':'Carrier retries duplicate webhooks','evidence':['benchmark'],'blocking':False,'supersedes':[]}]}
        wayfinder.validate_result(result, decision_type='research')

    def test_ledger_supersession_marks_old_entry(self):
        doc=base_map(); doc['ledger']=[{'id':'K-001','type':'decision','status':'active','statement':'Use polling','source_decision':'D-001','evidence':[],'blocking':False}]
        new_id=wayfinder.add_ledger_entry(doc, {'type':'decision','statement':'Use webhooks with polling reconciliation','evidence':['prototype'],'blocking':False,'supersedes':['K-001']}, 'D-002')
        self.assertEqual('superseded', doc['ledger'][0]['status']); self.assertEqual(new_id, doc['ledger'][0]['superseded_by'])

    def test_reconciliation_reports_open_decisions_even_when_fog_is_terminal(self):
        doc = base_map()
        for fog in doc["fog"]:
            fog["status"] = "deferred"
        errors = wayfinder.terminal_reconciliation_errors(doc)
        self.assertTrue(any("decision D-001 is not closed" in error for error in errors))
        self.assertFalse(wayfinder.cleared(doc))

    def test_blocking_assumption_prevents_clearance(self):
        doc=base_map()
        for item in doc['decisions']: item['status']='closed'
        for fog in doc['fog']: fog['status']='resolved'
        doc['ledger']=[{'id':'K-001','type':'assumption','status':'active','statement':'Carrier guarantees order','source_decision':'D-001','evidence':[],'blocking':True}]
        self.assertFalse(wayfinder.cleared(doc))


if __name__ == '__main__':
    unittest.main()
