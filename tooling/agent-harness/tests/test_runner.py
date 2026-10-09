import importlib.util
import argparse
import contextlib
import io
import json
import pathlib
import tempfile
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'runner.py'
spec = importlib.util.spec_from_file_location('sdd_runner', MODULE_PATH)
runner = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(runner)
import harness


def packet_lifecycle_binding(root, caller_packet, active_packet=None):
    feature_dir = root / 'docs' / 'specs' / caller_packet['feature']
    feature_dir.mkdir(parents=True, exist_ok=True)
    active_packet = active_packet or caller_packet
    active = {
        'packet': active_packet,
        'revision_id': harness.packet_revision_id(active_packet),
        'contract_sha256': harness.packet_bound_semantic_contract_sha256(feature_dir, active_packet),
    }
    return feature_dir, active


def patch_packet_lifecycle(root, caller_packet, active_packet=None):
    feature_dir, active = packet_lifecycle_binding(root, caller_packet, active_packet)
    return (
        mock.patch('harness.load_validated', return_value={'feature': caller_packet['feature']}),
        mock.patch('harness.resolve_active_packet', return_value=active),
    )


def accepted_task_plan(feature_id, task_id, task_commands):
    return {
        'feature_id': feature_id,
        'task_id': task_id,
        'task_commands': task_commands,
        'origin_binding': 'task-completion',
        'family': {'origin_policy': 'task-completion'},
    }


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

    def test_codex_and_claude_result_schemas_are_projected_per_exact_cli_version(self):
        canonical = runner.load(runner.REPO / 'tooling/agent-harness/schemas/task-result.schema.json')
        codex, codex_dropped = runner.project_task_result_schema(canonical, 'codex', 'codex-cli 0.160.0')
        claude, claude_dropped = runner.project_task_result_schema(canonical, 'claude', '2.1.289 (Claude Code)')

        self.assertFalse(codex['additionalProperties'])
        self.assertEqual(set(codex['properties']), set(codex['required']))
        self.assertFalse(codex['properties']['tdd_evidence']['additionalProperties'])
        self.assertEqual(set(codex['properties']['tdd_evidence']['properties']),
                         set(codex['properties']['tdd_evidence']['required']))
        self.assertNotIn('uniqueItems', codex['properties']['rework_tasks'])
        self.assertEqual(['/properties/rework_tasks/uniqueItems'], codex_dropped)
        self.assertIn('uniqueItems', claude['properties']['rework_tasks'])
        self.assertEqual([], claude_dropped)

        valid = {
            'status': 'pass', 'summary': 'ok', 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': [], 'findings': [], 'rework_tasks': [],
            'tdd_evidence': {'red': 'failed', 'green': 'passed', 'refactor': 'passed'},
        }
        self.assertEqual([], runner.canonical_schema_errors(valid, canonical))
        self.assertEqual([], runner.canonical_schema_errors(valid, codex))
        self.assertEqual([], runner.canonical_schema_errors(valid, claude))

    def test_canonical_validation_enforces_dropped_unique_items_constraint(self):
        canonical = runner.load(runner.REPO / 'tooling/agent-harness/schemas/task-result.schema.json')
        projected, dropped = runner.project_task_result_schema(canonical, 'codex', 'codex-cli 0.160.0')
        self.assertIn('/properties/rework_tasks/uniqueItems', dropped)
        invalid = {
            'status': 'fail', 'summary': 'bad duplicate', 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': [], 'findings': [],
            'rework_tasks': ['T-001', 'T-001'],
            'tdd_evidence': {'red': 'r', 'green': 'g', 'refactor': 'f'},
        }
        self.assertEqual([], runner.canonical_schema_errors(invalid, projected))
        self.assertTrue(any('uniqueItems' in error for error in runner.canonical_schema_errors(invalid, canonical)))
        with self.assertRaises(SystemExit):
            runner.validate_result(invalid, canonical_schema=canonical)

    def test_canonical_validation_enforces_nested_enum_pattern_and_unknown_fields(self):
        canonical = runner.load(runner.REPO / 'tooling/agent-harness/schemas/task-result.schema.json')
        valid = {
            'status': 'fail', 'summary': 'ok', 'changed_paths': [], 'commands': [],
            'assumptions': [], 'residual_risks': [], 'rework_tasks': ['T-001'],
            'tdd_evidence': {'red': 'r', 'green': 'g', 'refactor': 'f'},
        }
        for mutate in (
            lambda value: value.update(status='unknown'),
            lambda value: value.update(rework_tasks=['bad-id']),
            lambda value: value['tdd_evidence'].update(extra='x'),
        ):
            invalid = json.loads(json.dumps(valid))
            mutate(invalid)
            self.assertTrue(runner.canonical_schema_errors(invalid, canonical))

    def test_unknown_provider_version_or_schema_keyword_fails_closed(self):
        canonical = runner.load(runner.REPO / 'tooling/agent-harness/schemas/task-result.schema.json')
        with self.assertRaises(ValueError):
            runner.project_task_result_schema(canonical, 'codex', 'codex-cli 0.161.0')
        with self.assertRaises(ValueError):
            runner.project_task_result_schema(canonical, 'unknown', '1.0')
        incompatible = json.loads(json.dumps(canonical))
        incompatible['patternProperties'] = {'.*': {'type': 'string'}}
        with self.assertRaises(ValueError):
            runner.project_task_result_schema(incompatible, 'codex', 'codex-cli 0.160.0')
        incompatible = json.loads(json.dumps(canonical))
        incompatible['properties']['findings']['prefixItems'] = [{'type': 'string'}]
        self.assertTrue(runner.canonical_schema_errors({}, incompatible))

    def test_projection_manifest_hashes_exact_provider_schema_bytes(self):
        canonical_bytes = runner.TASK_RESULT_SCHEMA_PATH.read_bytes()
        _, projected_bytes, record = runner.task_result_projection(
            canonical_bytes, 'codex', 'codex-cli 0.160.0')
        self.assertEqual('codex', record['provider'])
        self.assertEqual('codex-cli 0.160.0', record['cli_version'])
        self.assertEqual(runner.hashlib.sha256(canonical_bytes).hexdigest(),
                         record['canonical_schema_sha256'])
        self.assertEqual(runner.hashlib.sha256(projected_bytes).hexdigest(),
                         record['projection_sha256'])
        self.assertEqual(['/properties/rework_tasks/uniqueItems'], record['dropped_constraints'])

    def test_claude_command_uses_restricted_oauth_auth_not_bare_mode(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            schema = root / 'result.schema.json'
            schema.write_text(json.dumps({'type': 'object', 'properties': {}, 'required': []}), encoding='utf-8')
            args = argparse.Namespace(
                print_command=True, review_existing=False, profile=None, max_turns=1,
                max_budget_usd=None, model=None,
            )
            cmd = runner.claude_command(args, 'prompt', root, schema_path=schema)
            self.assertIn('--restricted', cmd)
            self.assertNotIn('--bare', cmd)
            self.assertIn('--strict-mcp-config', cmd)

    def test_codex_command_accepts_explicit_output_schema(self):
        args = argparse.Namespace(print_command=True, sandbox='read-only', model=None, reasoning='high')
        schema = pathlib.Path('/tmp/design-result.schema.json')
        cmd = runner.codex_command(
            args, 'prompt', pathlib.Path('/tmp/worktree'), pathlib.Path('/tmp/result.json'), schema_path=schema
        )
        self.assertEqual(str(schema), cmd[cmd.index('--output-schema') + 1])


    def test_claude_command_strips_meta_schema_without_mutating_canonical_schema(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            schema = root / 'result.schema.json'
            canonical = {
                '$schema': 'https://json-schema.org/draft/2020-12/schema',
                'type': 'object',
                'required': ['value'],
                'properties': {'value': {'type': ['string', 'null']}},
            }
            schema.write_text(json.dumps(canonical), encoding='utf-8')

            args = argparse.Namespace(
                print_command=True,
                review_existing=False,
                profile=None,
                max_turns=1,
                max_budget_usd=None,
                model=None,
            )
            cmd = runner.claude_command(args, 'prompt', root, schema_path=schema)

            rendered = json.loads(cmd[cmd.index('--json-schema') + 1])
            self.assertNotIn('$schema', rendered)
            self.assertEqual('object', rendered['type'])
            self.assertEqual(['string', 'null'], rendered['properties']['value']['type'])

            persisted = json.loads(schema.read_text(encoding='utf-8'))
            self.assertEqual(
                'https://json-schema.org/draft/2020-12/schema',
                persisted['$schema'],
            )

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

    def test_claude_result_rejects_unstructured_result_fallback(self):
        raw = json.dumps({'result': json.dumps({'status': 'pass'})})
        with self.assertRaises(SystemExit):
            runner.extract_claude_result(raw)

    def test_claude_print_command_uses_a_real_generated_projection(self):
        args = argparse.Namespace(
            provider='claude', print_command=True, review_existing=False, profile=None,
            max_turns=1, max_budget_usd=None, model=None,
        )
        with tempfile.TemporaryDirectory() as tmp:
            preview_root = pathlib.Path(tmp)
            with mock.patch.object(runner, 'RUNS', preview_root):
                cmd, version = runner.preview_command(
                    args, 'prompt', preview_root, preview_root / 'result.json', '2.1.289 (Claude Code)')
            self.assertEqual('2.1.289 (Claude Code)', version)
            schema = json.loads(cmd[cmd.index('--json-schema') + 1])
            self.assertFalse(schema['additionalProperties'])

    def test_print_command_without_provider_cli_uses_explicit_preview_profile(self):
        args = argparse.Namespace(
            provider='claude', print_command=True, review_existing=False, profile=None,
            max_turns=1, max_budget_usd=None, model=None,
        )
        with tempfile.TemporaryDirectory() as tmp:
            preview_root = pathlib.Path(tmp)
            stderr = io.StringIO()
            with mock.patch.object(runner, 'RUNS', preview_root), mock.patch.object(runner, 'cli_version', return_value=None), \
                    contextlib.redirect_stderr(stderr):
                cmd, version = runner.preview_command(
                    args, 'prompt', preview_root, preview_root / 'result.json', None)
            self.assertEqual('2.1.289 (Claude Code)', version)
            self.assertIn('Preview only', stderr.getvalue())
            self.assertFalse(json.loads(cmd[cmd.index('--json-schema') + 1])['additionalProperties'])

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

    def test_builder_postcondition_rejects_ignored_write_outside_allowed_paths(self):
        import subprocess
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / '.gitignore').write_text('ignored-output/\n', encoding='utf-8')
            (root / 'source.txt').write_text('base\n', encoding='utf-8')
            subprocess.run(['git', 'add', '.gitignore', 'source.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            before = runner.git_snapshot(root)

            (root / 'ignored-output' / 'nested').mkdir(parents=True)
            (root / 'ignored-output' / 'nested' / 'result.txt').write_text('unauthorized\n', encoding='utf-8')
            result = {'changed_paths': ['ignored-output/nested/result.txt']}

            self.assertIn('ignored-output/nested/result.txt', runner.git_changed_paths(root))
            with self.assertRaises(SystemExit):
                runner.enforce_postconditions(
                    {'allowed_paths': ['source.txt']}, root, before, result)

    def test_changed_path_inventory_preserves_newline_filenames(self):
        import subprocess
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'base.txt').write_text('base\n', encoding='utf-8')
            subprocess.run(['git', 'add', 'base.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            unusual = 'odd\nname.txt'
            (root / unusual).write_text('candidate\n', encoding='utf-8')

            self.assertEqual([unusual], runner.git_changed_paths(root))


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


    def test_runner_without_lifecycle_accepted_plan_fails_closed_before_payload(self):
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
            self.assertEqual('verification-blocked', results[-1]['machine_category'])
            self.assertEqual('VERIFICATION_EXECUTION_PLAN_REQUIRED', results[-1]['reason_code'])
            self.assertEqual(['a.txt'], before_paths)
            self.assertEqual(before_paths, runner.git_changed_paths(root))

    def test_runner_consumes_only_the_supplied_accepted_plan(self):
        import json
        import subprocess
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'base.txt').write_text('base\n', encoding='utf-8')
            subprocess.run(['git', 'add', 'base.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            completed = subprocess.CompletedProcess([], 0,
                json.dumps({'outcome': 'PASS', 'plan_id': 'verification-plan-v1:sha256:test'}), '')
            packet = {'feature': 'TEST', 'task': 'T-1', 'role': 'builder', 'agent_profile': 'builder',
                'feature_fingerprint': 'a' * 64, 'allowed_paths': ['src/**'], 'verification': [
                'python3 -m unittest tests.test_profile',
                'python3 -m unittest tests.test_profile',
                'custom-legacy-check --strict',
            ]}
            accepted = accepted_task_plan('TEST', 'T-1', [
                {'command': command, 'cwd': '.'} for command in packet['verification']
            ])
            real_run = runner.subprocess.run
            def execute(command, *args, **kwargs):
                if command[:3] == [runner.sys.executable, str(runner.HERE / 'verify.py'), 'run']:
                    return completed
                return real_run(command, *args, **kwargs)
            packet_patches = patch_packet_lifecycle(root, packet)
            with mock.patch.object(runner.subprocess, 'run', side_effect=execute) as run:
                with packet_patches[0], packet_patches[1], \
                        mock.patch('verification.authority.resolve_execution',
                            return_value=(accepted, None, None, [])) as resolve:
                    ok, results = runner.run_verification(packet, root, root, 30,
                        accepted_plan_id='verification-plan-v1:sha256:test')
            self.assertTrue(ok)
            self.assertEqual('PASS', results[0]['outcome'])
            resolve.assert_called_once_with(root, 'verification-plan-v1:sha256:test')
            command = next(call.args[0] for call in run.call_args_list
                           if call.args[0][:3] == [runner.sys.executable, str(runner.HERE / 'verify.py'), 'run'])
            self.assertIn('--plan-id', command)
            self.assertNotIn('--base', command)
            self.assertNotIn('custom-legacy-check', command)
            self.assertNotIn('tests.test_profile', command)

    def test_runner_rejects_accepted_plan_for_another_task_before_launch(self):
        import subprocess
        accepted = accepted_task_plan('TEST', 'T-1', [
            {'command': 'python3 -m unittest tests.test_x', 'cwd': '.'}
        ])
        for packet in (
            {'feature': 'TEST', 'task': 'T-2', 'role': 'builder', 'agent_profile': 'builder',
             'feature_fingerprint': 'a' * 64, 'allowed_paths': ['src/**'],
             'verification': ['python3 -m unittest tests.test_x']},
            {'feature': 'TEST', 'task': 'T-1', 'role': 'builder', 'agent_profile': 'builder',
             'feature_fingerprint': 'a' * 64, 'allowed_paths': ['src/**'],
             'verification': ['python3 -m unittest tests.other']},
        ):
            with self.subTest(packet=packet), tempfile.TemporaryDirectory() as tmp:
                root = pathlib.Path(tmp)
                packet_patches = patch_packet_lifecycle(root, packet)
                with mock.patch.object(runner.subprocess, 'run') as launch, \
                        packet_patches[0], packet_patches[1], \
                        mock.patch('verification.authority.resolve_execution',
                            return_value=(accepted, None, None, [])):
                    passed, result = runner.run_verification(packet, root, root, 5,
                        accepted_plan_id='verification-plan-v1:sha256:' + 'a' * 64)
            self.assertFalse(passed)
            self.assertEqual('verification-blocked', result[0]['machine_category'])
            self.assertEqual('ACCEPTED_PLAN_TASK_MISMATCH', result[0]['reason_code'])
            launch.assert_not_called()

    def test_runner_rejects_integration_origin_plan_for_task_completion(self):
        import json
        import subprocess

        task_command = 'python3 -m unittest tests.test_x'
        packet = {
            'feature': 'TEST', 'task': 'T-1', 'role': 'builder', 'agent_profile': 'builder',
            'feature_fingerprint': 'a' * 64, 'allowed_paths': ['src/**'],
            'verification': [task_command],
        }
        integration_plan = {
            'feature_id': 'TEST', 'task_id': 'T-1',
            'task_commands': [{'command': task_command, 'cwd': '.'}],
            'origin_binding': 'integration',
            'family': {'origin_policy': 'integration'},
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'base.txt').write_text('base\n', encoding='utf-8')
            subprocess.run(['git', 'add', 'base.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            plan_id = 'verification-plan-v2:sha256:' + 'a' * 64
            real_run = runner.subprocess.run

            def launch(command, *args, **kwargs):
                if command[:3] == [runner.sys.executable, str(runner.HERE / 'verify.py'), 'run']:
                    return subprocess.CompletedProcess(command, 0,
                        json.dumps({'outcome': 'PASS', 'plan_id': plan_id}), '')
                return real_run(command, *args, **kwargs)

            packet_patches = patch_packet_lifecycle(root, packet)
            with mock.patch.object(runner.subprocess, 'run', side_effect=launch) as run, \
                    packet_patches[0], packet_patches[1], \
                    mock.patch('verification.authority.resolve_execution',
                        return_value=(integration_plan, None, None, [])):
                passed, result = runner.run_verification(packet, root, root, 5,
                    sandbox_mode='required', accepted_plan_id=plan_id)

        self.assertFalse(passed)
        self.assertEqual('verification-blocked', result[0]['machine_category'])
        self.assertEqual('ACCEPTED_PLAN_ORIGIN_MISMATCH', result[0]['reason_code'])
        self.assertFalse(any(call.args[0][:3] == [
            runner.sys.executable, str(runner.HERE / 'verify.py'), 'run'
        ] for call in run.call_args_list))

    def test_worktree_snapshot_rejects_nested_git_authority(self):
        import subprocess

        for nested_kind in ('untracked-repository', 'submodule'):
            with self.subTest(nested_kind=nested_kind), tempfile.TemporaryDirectory() as tmp:
                root = pathlib.Path(tmp) / 'superproject'
                root.mkdir()
                subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
                subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
                subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
                (root / 'base.txt').write_text('base\n', encoding='utf-8')
                subprocess.run(['git', 'add', 'base.txt'], cwd=root, check=True)
                subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)

                nested = root / 'vendor' / 'nested'
                if nested_kind == 'untracked-repository':
                    nested.mkdir(parents=True)
                    subprocess.run(['git', 'init', '-q'], cwd=nested, check=True)
                    subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=nested, check=True)
                    subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=nested, check=True)
                    (nested / 'inside.txt').write_text('nested content\n', encoding='utf-8')
                    subprocess.run(['git', 'add', 'inside.txt'], cwd=nested, check=True)
                    subprocess.run(['git', 'commit', '-qm', 'nested'], cwd=nested, check=True)
                else:
                    module = pathlib.Path(tmp) / 'module'
                    module.mkdir()
                    subprocess.run(['git', 'init', '-q'], cwd=module, check=True)
                    subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=module, check=True)
                    subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=module, check=True)
                    (module / 'inside.txt').write_text('module content\n', encoding='utf-8')
                    subprocess.run(['git', 'add', 'inside.txt'], cwd=module, check=True)
                    subprocess.run(['git', 'commit', '-qm', 'module'], cwd=module, check=True)
                    subprocess.run(['git', '-c', 'protocol.file.allow=always', 'submodule', 'add',
                        str(module), str(nested.relative_to(root))], cwd=root, check=True,
                        capture_output=True)
                    subprocess.run(['git', 'commit', '-qam', 'add submodule'], cwd=root, check=True)

                with self.assertRaisesRegex(RuntimeError, 'nested Git repository or submodule'):
                    runner.git_snapshot(root)

    def test_runner_rejects_packet_scope_change_with_same_commands(self):
        import subprocess
        packet = {
            'feature': 'TEST', 'task': 'T-1', 'feature_fingerprint': 'a' * 64,
            'role': 'builder', 'agent_profile': 'builder',
            'allowed_paths': ['src/safe/**'], 'verification': ['python3 -m unittest tests.test_x'],
        }
        caller_packet = dict(packet, allowed_paths=['**'])
        accepted = accepted_task_plan('TEST', 'T-1', [
            {'command': 'python3 -m unittest tests.test_x', 'cwd': '.'}
        ])
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'base.txt').write_text('base\n', encoding='utf-8')
            subprocess.run(['git', 'add', 'base.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            feature_dir, lifecycle_binding = packet_lifecycle_binding(root, caller_packet, packet)
            plan_id = 'verification-plan-v1:sha256:' + 'a' * 64
            real_run = runner.subprocess.run
            def run_or_verify(command, *args, **kwargs):
                if command[:3] == [runner.sys.executable, str(runner.HERE / 'verify.py'), 'run']:
                    return subprocess.CompletedProcess(command, 0,
                        json.dumps({'outcome': 'PASS', 'plan_id': plan_id}), '')
                return real_run(command, *args, **kwargs)
            with mock.patch.object(runner.subprocess, 'run', side_effect=run_or_verify) as launch, \
                    mock.patch('verification.authority.resolve_execution',
                        return_value=(accepted, None, None, [])), \
                    mock.patch('harness.load_validated', return_value={'feature': 'TEST'}), \
                    mock.patch('harness.resolve_active_packet', return_value=lifecycle_binding):
                passed, result = runner.run_verification(caller_packet, root, root, 5,
                    accepted_plan_id=plan_id)
        self.assertFalse(passed)
        self.assertEqual('verification-blocked', result[0]['machine_category'])
        self.assertEqual('ACCEPTED_PACKET_MISMATCH', result[0]['reason_code'])
        self.assertFalse(any(call.args[0][:3] == [
            runner.sys.executable, str(runner.HERE / 'verify.py'), 'run'
        ] for call in launch.call_args_list))

    def test_runner_rejects_symlinked_specs_root_before_external_resolution(self):
        import subprocess
        with tempfile.TemporaryDirectory() as tmp, tempfile.TemporaryDirectory() as outside_tmp:
            root = pathlib.Path(tmp)
            outside = pathlib.Path(outside_tmp)
            (root / 'docs').mkdir()
            (outside / 'TEST').mkdir()
            marker = outside / 'TEST' / 'marker.txt'
            marker.write_text('external state\n', encoding='utf-8')
            (root / 'docs' / 'specs').symlink_to(outside, target_is_directory=True)
            packet = {'feature': 'TEST', 'task': 'T-1', 'role': 'builder', 'agent_profile': 'builder',
                'feature_fingerprint': 'a' * 64, 'allowed_paths': ['src/**'], 'verification': []}

            with mock.patch('harness.load_validated') as load_feature, \
                    mock.patch('harness.resolve_active_packet') as resolve_packet, \
                    mock.patch('verification.authority.resolve_execution') as resolve_plan, \
                    mock.patch.object(runner.subprocess, 'run') as launch:
                passed, result = runner.run_verification(packet, root, root, 5,
                    accepted_plan_id='verification-plan-v1:sha256:' + 'a' * 64)
            self.assertFalse(passed)
            self.assertEqual('verification-blocked', result[0]['machine_category'])
            self.assertEqual('ACCEPTED_PACKET_UNAVAILABLE', result[0]['reason_code'])
            load_feature.assert_not_called()
            resolve_packet.assert_not_called()
            resolve_plan.assert_not_called()
            launch.assert_not_called()
            self.assertEqual('external state\n', marker.read_text(encoding='utf-8'))

    def test_runner_rejects_pass_result_bound_to_another_plan(self):
        import json
        import subprocess
        accepted = 'verification-plan-v1:sha256:' + 'a' * 64
        for response in (
            {'outcome': 'PASS', 'plan_id': 'verification-plan-v1:sha256:' + 'b' * 64},
            {'outcome': 'PASS'},
        ):
            with self.subTest(response=response), tempfile.TemporaryDirectory() as tmp:
                root = pathlib.Path(tmp)
                subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
                subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
                subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
                (root / 'base.txt').write_text('base\n', encoding='utf-8')
                subprocess.run(['git', 'add', 'base.txt'], cwd=root, check=True)
                subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
                completed = subprocess.CompletedProcess([], 0, json.dumps(response), '')
                packet = {'feature': 'TEST', 'task': 'T-1', 'role': 'builder', 'agent_profile': 'builder',
                    'feature_fingerprint': 'a' * 64, 'allowed_paths': ['src/**'], 'verification': []}
                accepted_record = accepted_task_plan('TEST', 'T-1', [])
                real_run = runner.subprocess.run
                def execute(command, *args, **kwargs):
                    if command[:3] == [runner.sys.executable, str(runner.HERE / 'verify.py'), 'run']:
                        return completed
                    return real_run(command, *args, **kwargs)
                packet_patches = patch_packet_lifecycle(root, packet)
                with mock.patch.object(runner.subprocess, 'run', side_effect=execute), \
                        packet_patches[0], packet_patches[1], \
                        mock.patch('verification.authority.resolve_execution',
                            return_value=(accepted_record, None, None, [])):
                    ok, results = runner.run_verification(packet, root, root, 30,
                        accepted_plan_id=accepted)
            self.assertFalse(ok)
            self.assertEqual('verification-blocked', results[0]['machine_category'])
            self.assertEqual('ACCEPTED_PLAN_RESULT_MISMATCH', results[0]['reason_code'])

    def test_verifier_mutations_cannot_return_pass(self):
        import json
        import subprocess
        import sys

        for mutation_kind in ('tracked', 'ignored', 'symlink'):
            with self.subTest(mutation_kind=mutation_kind), tempfile.TemporaryDirectory() as tmp:
                root = pathlib.Path(tmp)
                subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
                subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
                subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
                (root / '.gitignore').write_text('ignored-output/\n', encoding='utf-8')
                (root / 'tracked.txt').write_text('before\n', encoding='utf-8')
                subprocess.run(['git', 'add', '.gitignore', 'tracked.txt'], cwd=root, check=True)
                subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
                plan_id = 'verification-plan-v1:sha256:' + 'a' * 64
                real_run = runner.subprocess.run

                def run_or_mutate(command, *args, **kwargs):
                    if command[:3] == [sys.executable, str(runner.HERE / 'verify.py'), 'run']:
                        if mutation_kind == 'tracked':
                            (root / 'tracked.txt').write_text('after\n', encoding='utf-8')
                        elif mutation_kind == 'ignored':
                            ignored = root / 'ignored-output' / 'nested' / 'result.txt'
                            ignored.parent.mkdir(parents=True)
                            ignored.write_text('generated\n', encoding='utf-8')
                        else:
                            outside = root.parent / f'{root.name}-absent-target'
                            (root / 'candidate-link').symlink_to(outside)
                        return subprocess.CompletedProcess(command, 0,
                            json.dumps({'outcome': 'PASS', 'plan_id': plan_id}), '')
                    return real_run(command, *args, **kwargs)

                packet = {'feature': 'TEST', 'task': 'T-1', 'role': 'builder', 'agent_profile': 'builder',
                    'feature_fingerprint': 'a' * 64, 'allowed_paths': ['src/**'], 'verification': []}
                accepted = accepted_task_plan('TEST', 'T-1', [])
                packet_patches = patch_packet_lifecycle(root, packet)
                with mock.patch.object(runner.subprocess, 'run', side_effect=run_or_mutate), \
                        packet_patches[0], packet_patches[1], \
                        mock.patch('verification.authority.resolve_execution',
                            return_value=(accepted, None, None, [])):
                    passed, result = runner.run_verification(packet, root, root, 5,
                        sandbox_mode='required', accepted_plan_id=plan_id)

                self.assertFalse(passed)
                self.assertEqual('verification-blocked', result[0]['machine_category'])
                self.assertEqual('VERIFICATION_WORKTREE_MUTATED', result[0]['reason_code'])

    def test_verifier_timeout_checks_worktree_before_returning_timeout(self):
        import subprocess
        import sys
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'tracked.txt').write_text('before\n', encoding='utf-8')
            subprocess.run(['git', 'add', 'tracked.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            plan_id = 'verification-plan-v1:sha256:' + 'a' * 64
            real_run = runner.subprocess.run

            def run_or_mutate(command, *args, **kwargs):
                if command[:3] == [sys.executable, str(runner.HERE / 'verify.py'), 'run']:
                    (root / 'tracked.txt').write_text('changed before timeout\n', encoding='utf-8')
                    raise subprocess.TimeoutExpired(command, kwargs.get('timeout', 5))
                return real_run(command, *args, **kwargs)

            packet = {'feature': 'TEST', 'task': 'T-1', 'role': 'builder', 'agent_profile': 'builder',
                'feature_fingerprint': 'a' * 64, 'allowed_paths': ['src/**'], 'verification': []}
            accepted = accepted_task_plan('TEST', 'T-1', [])
            packet_patches = patch_packet_lifecycle(root, packet)
            with mock.patch.object(runner.subprocess, 'run', side_effect=run_or_mutate), \
                    packet_patches[0], packet_patches[1], \
                    mock.patch('verification.authority.resolve_execution',
                        return_value=(accepted, None, None, [])):
                passed, result = runner.run_verification(packet, root, root, 5,
                    sandbox_mode='required', accepted_plan_id=plan_id)

        self.assertFalse(passed)
        self.assertEqual('verification-blocked', result[0]['machine_category'])
        self.assertEqual('VERIFICATION_WORKTREE_MUTATED', result[0]['reason_code'])

    def test_task_completion_keeps_duplicate_and_legacy_commands_fresh_with_cached_pass(self):
        import dataclasses
        import subprocess
        from verification.model import Evidence, Family, Gate, Profile
        from verification.planner import build_plan
        from verification.profile import command_identity
        from verification.serialization import digest, evidence_record

        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.email', 'test@example.com'], cwd=root, check=True)
            subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=root, check=True)
            (root / 'source.txt').write_text('base\n', encoding='utf-8')
            subprocess.run(['git', 'add', 'source.txt'], cwd=root, check=True)
            subprocess.run(['git', 'commit', '-qm', 'base'], cwd=root, check=True)
            base_sha = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=root, check=True,
                capture_output=True, text=True).stdout.strip()

            mapped_command = 'python3 -c "print(1)"'
            gate = Gate('mapped_check', mapped_command, command_identity(mapped_command),
                cacheable=True, sandbox='required', retry_policy='allow')
            profile = Profile(1, 'd' * 64, (gate,))
            integration_family = Family('integration_run', base_sha, 'integration', profile.content_hash,
                'e' * 64)
            integration_plan = build_plan(root, profile, integration_family,
                task_commands=[{'command': mapped_command, 'cwd': '.'}])
            integration_decision = integration_plan.decisions[0]
            cached = Evidence(2, 'cached_pass', integration_family.id, 'owner', gate.id,
                integration_decision.repository_id, profile.content_hash, integration_family.policy_checkpoint,
                gate.command_hash, 'integration', integration_decision.fingerprint,
                integration_decision.fingerprint, gate.sandbox, gate.retry_policy, 1, 0, 1.0, 2.0,
                'pass', (), integration_decision.dependencies, '')
            receipt = evidence_record(cached, include_receipt=False)
            cached = dataclasses.replace(cached, receipt_hash=digest(receipt))

            task_family = Family('task_run', base_sha, 'task-completion', profile.content_hash, 'f' * 64)
            plan = build_plan(root, profile, task_family, task_commands=[
                {'command': mapped_command, 'cwd': '.'},
                {'command': mapped_command, 'cwd': '.'},
                {'command': 'python3 -c "print(2)"', 'cwd': '.'},
            ], evidence={gate.id: cached})

        mapped_occurrences = [d for d in plan.decisions if d.node.occurrence and
            d.node.profile_gate_id == gate.id]
        legacy_occurrences = [d for d in plan.decisions if d.node.occurrence and
            d.node.profile_gate_id is None]
        self.assertEqual(2, len(mapped_occurrences))
        self.assertEqual(2, len({d.node.id for d in mapped_occurrences}))
        self.assertEqual(1, len(legacy_occurrences))
        self.assertTrue(all(d.action == 'RUN' and d.decision == 'RUN_NOW'
                            for d in plan.decisions))



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
