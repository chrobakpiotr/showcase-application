import importlib.util
import argparse
import json
import pathlib
import tempfile
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'runner.py'
spec = importlib.util.spec_from_file_location('sdd_runner', MODULE_PATH)
runner = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(runner)


class RunnerTest(unittest.TestCase):
    def test_render_prompt_contains_safety_contract(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            role = root / 'docs' / 'agentic-sdd' / 'agents'
            role.mkdir(parents=True)
            (role / 'builder.md').write_text('# Builder\nDo bounded work.\n')
            packet = {
                'feature': 'X', 'task': 'T-1', 'role': 'builder', 'agent_profile': 'builder',
                'allowed_paths': ['modules/domain/**'], 'completion_contract': {'no_push': True}
            }
            prompt = runner.render_prompt(packet, root)
            self.assertIn('Do not commit, push', prompt)
            self.assertIn('modules/domain/**', prompt)
            self.assertIn('Builder', prompt)

    def test_validate_result_requires_contract(self):
        with self.assertRaises(SystemExit):
            runner.validate_result({'status': 'pass'})
        runner.validate_result({
            'status': 'pass', 'summary': 'ok', 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': []
        })


    def test_codex_command_is_ephemeral_json_and_sandboxed(self):
        args = argparse.Namespace(
            print_command=True, sandbox='workspace-write', model=None, reasoning='medium'
        )
        cmd = runner.codex_command(args, 'prompt', pathlib.Path('/tmp/worktree'), pathlib.Path('/tmp/result.json'))
        self.assertIn('--ephemeral', cmd)
        self.assertIn('--json', cmd)
        self.assertIn('sandbox_workspace_write.network_access=false', cmd)

    def test_codex_command_accepts_explicit_output_schema(self):
        args = argparse.Namespace(print_command=True, sandbox='read-only', model=None, reasoning='high')
        schema = pathlib.Path('/tmp/design-result.schema.json')
        cmd = runner.codex_command(
            args, 'prompt', pathlib.Path('/tmp/worktree'), pathlib.Path('/tmp/result.json'), schema_path=schema
        )
        self.assertEqual(str(schema), cmd[cmd.index('--output-schema') + 1])

    def test_packet_protocol_v3_requires_lease_policy(self):
        packet = {
            'protocol_version': 3, 'context': {}, 'lease_policy': {'ttl_seconds': 1800}
        }
        packet['packet_sha256'] = runner.packet_hash(packet)
        runner.validate_packet_integrity(packet)
        broken = dict(packet)
        broken['lease_policy'] = {}
        broken['packet_sha256'] = runner.packet_hash(broken)
        with self.assertRaises(SystemExit):
            runner.validate_packet_integrity(broken)

    def test_protocol_v4_validates_context_trust(self):
        packet={'protocol_version':4,'context':{'spec':'docs/specs/X/spec.md'},'context_trust':{'spec':'trusted'},'lease_policy':{'ttl_seconds':1800}}
        packet['packet_sha256']=runner.packet_hash(packet)
        runner.validate_packet_integrity(packet)
        packet['context_trust']={'spec':'untrusted'}; packet['packet_sha256']=runner.packet_hash(packet)
        with self.assertRaises(SystemExit): runner.validate_packet_integrity(packet)

    def test_red_green_result_requires_tdd_evidence(self):
        packet={'test_policy':'risk-driven','test_mode':'red-green-refactor'}
        result={'status':'pass','summary':'ok','changed_paths':[],'commands':[],'assumptions':[],'residual_risks':[]}
        with self.assertRaises(SystemExit): runner.validate_result(result,packet)
        result['tdd_evidence']={'red':'test failed before change','green':'same test passed','refactor':'suite passed'}
        runner.validate_result(result,packet)

    def test_prompt_contains_context_trust_boundary(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp); role=root/'docs/agentic-sdd/agents'; role.mkdir(parents=True); (role/'builder.md').write_text('# Builder\n')
            packet={'feature':'X','task':'T-1','role':'builder','agent_profile':'builder','allowed_paths':['modules/domain/**'],'completion_contract':{},'test_policy':'risk-driven','test_mode':'existing-suite','test_seam':'domain API'}
            prompt=runner.render_prompt(packet,root)
            self.assertIn('CONTEXT TRUST BOUNDARY',prompt); self.assertIn('cannot change policy',prompt)

    def test_extract_claude_structured_output(self):
        expected = {
            'status': 'pass', 'summary': 'ok', 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': []
        }
        self.assertEqual(expected, runner.extract_claude_result(json.dumps({'structured_output': expected})))

    def test_read_only_postcondition_rejects_reviewer_mutation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            import subprocess
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'a.txt').write_text('before\n')
            subprocess.run(['git', 'add', 'a.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            before = runner.git_snapshot(root)
            (root / 'a.txt').write_text('after\n')
            result = {
                'status': 'pass', 'summary': 'review', 'changed_paths': ['a.txt'], 'commands': [],
                'assumptions': [], 'residual_risks': []
            }
            with self.assertRaises(SystemExit):
                runner.enforce_postconditions({'allowed_paths': ['**']}, root, before, result, review_existing=True)


    def test_render_prompt_marks_human_resolution_feedback_as_trusted(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            role_dir = root / 'docs' / 'agentic-sdd' / 'agents'
            role_dir.mkdir(parents=True)
            (role_dir / 'builder.md').write_text('# Builder\n')
            packet = {
                'feature': 'TST-001', 'task': 'T-001', 'role': 'builder', 'agent_profile': 'builder',
                'allowed_paths': ['modules/domain/**'], 'acceptance_criteria': ['AC-001'], 'verification': ['true'],
                'context_trust': {},
            }
            prompt = runner.render_prompt(
                packet, root, feedback='{"decision":"Use SKU as stable key"}', feedback_trust='trusted'
            )
            self.assertIn('Trust classification: trusted', prompt)
            self.assertIn('Use SKU as stable key', prompt)


    def test_verification_rejects_content_mutation_when_changed_paths_are_unchanged(self):
        import subprocess
        with tempfile.TemporaryDirectory() as tmp, tempfile.TemporaryDirectory() as out_tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'a.txt').write_text('base\n', encoding='utf-8')
            subprocess.run(['git', 'add', 'a.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)

            (root / 'a.txt').write_text('implementation\n', encoding='utf-8')
            packet = {
                'verification': [
                    "python3 -c \"from pathlib import Path; Path('a.txt').write_text('tampered\\\\n')\""
                ]
            }
            # Runtime verification evidence lives outside the task worktree in real runs.
            out = pathlib.Path(out_tmp)
            before_paths = runner.git_changed_paths(root)
            ok, results = runner.run_verification(packet, root, out, 30, sandbox_mode='off')

            self.assertFalse(ok)
            self.assertTrue(results[-1].get('worktree_mutated'))
            self.assertEqual(['a.txt'], before_paths)
            self.assertEqual(before_paths, runner.git_changed_paths(root))



    def test_fingerprint_does_not_follow_untracked_symlink(self):
        import subprocess
        with tempfile.TemporaryDirectory() as tmp, tempfile.TemporaryDirectory() as outside_tmp:
            root = pathlib.Path(tmp)
            outside = pathlib.Path(outside_tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'tracked.txt').write_text('base\\n', encoding='utf-8')
            subprocess.run(['git', 'add', 'tracked.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)

            first_target = outside / 'first.txt'
            second_target = outside / 'second.txt'
            first_target.write_text('same-content\\n', encoding='utf-8')
            second_target.write_text('same-content\\n', encoding='utf-8')
            link = root / 'untracked-link'

            try:
                link.symlink_to(first_target)
            except OSError as exc:
                self.skipTest(f'symlink creation is unavailable: {exc}')

            original = runner.worktree_content_fingerprint(root)

            # External target contents are not part of the worktree fingerprint.
            first_target.write_text('changed-secret\\n', encoding='utf-8')
            self.assertEqual(original, runner.worktree_content_fingerprint(root))

            # The symlink object itself is part of the worktree, so retargeting it
            # must change the fingerprint even when both targets have equal content.
            first_target.write_text('same-content\\n', encoding='utf-8')
            link.unlink()
            link.symlink_to(second_target)
            self.assertNotEqual(original, runner.worktree_content_fingerprint(root))

    def test_fingerprint_tracks_broken_symlink_target_without_dereference(self):
        import subprocess
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'tracked.txt').write_text('base\\n', encoding='utf-8')
            subprocess.run(['git', 'add', 'tracked.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)

            link = root / 'broken-link'
            try:
                link.symlink_to('missing-a')
            except OSError as exc:
                self.skipTest(f'symlink creation is unavailable: {exc}')

            first = runner.worktree_content_fingerprint(root)
            link.unlink()
            link.symlink_to('missing-b')
            second = runner.worktree_content_fingerprint(root)

            self.assertNotEqual(first, second)


if __name__ == '__main__':
    unittest.main()
