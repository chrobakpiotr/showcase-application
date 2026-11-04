"""AH5-12b: runner.py launches through agent_harness.execution (library-owned; runs in a Showcase checkout)."""
import argparse
import contextlib
import importlib.util
import io
import json
import os
import pathlib
import shlex
import stat
import subprocess
import sys
import tempfile
import textwrap
import unittest
from unittest import mock

from agent_harness import execution

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'runner.py'
spec = importlib.util.spec_from_file_location('sdd_runner_launch', MODULE_PATH)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

CODEX_RESULT = {'status': 'pass', 'summary': 'ok'}
FAKE_CODEX = textwrap.dedent('''\
    #!/usr/bin/env python3
    import json, os, sys, time
    argv = sys.argv[1:]
    if argv[:1] == ["sandbox"]:
        if os.environ.get("FAKE_SANDBOX") == "broken":
            sys.exit(1)
        os.execvp(argv[argv.index("--") + 1], argv[argv.index("--") + 1:])
    if argv[:1] == ["--version"]:
        print("codex-cli 0.160.0"); sys.exit(0)
    prompt = sys.stdin.read()
    mode = os.environ.get("FAKE_MODE", "ok")
    if mode == "sleep":
        time.sleep(60)
    print(json.dumps({"type": "turn.completed",
                      "usage": {"input_tokens": 3, "cached_input_tokens": 0, "output_tokens": 2}}))
    if mode.startswith("secret"):
        print("token sk-ant-" + "a" * 40)
    if mode != "nolast":
        with open(argv[argv.index("--output-last-message") + 1], "w") as f:
            f.write("{not json") if mode == "badjson" else json.dump(RESULT, f)
    with open(os.environ["FAKE_LOG"], "a") as f:
        f.write(json.dumps({"argv": argv, "stdin": prompt}) + "\\n")
    sys.exit(3 if mode == "secretfail" else 0)
''').replace('RESULT', repr(CODEX_RESULT))


def git(root, *args):
    subprocess.run(['git', *args], cwd=root, check=True, capture_output=True)


class RunnerLaunchTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = pathlib.Path(tmp.name).resolve()
        self.repo, self.runs, bin_dir = self.root / 'repo', self.root / 'runs', self.root / 'bin'
        for path in (self.repo, self.runs, bin_dir):
            path.mkdir()
        git(self.repo, 'init', '-q')
        git(self.repo, 'config', 'user.email', 't@example.com')
        git(self.repo, 'config', 'user.name', 'T')
        (self.repo / 'a.txt').write_text('a\n')
        git(self.repo, 'add', 'a.txt')
        git(self.repo, 'commit', '-qm', 'base')
        self.worktree = self.root / 'wt'
        git(self.repo, 'worktree', 'add', '-q', str(self.worktree))
        codex = bin_dir / 'codex'
        codex.write_text(FAKE_CODEX)
        codex.chmod(codex.stat().st_mode | stat.S_IXUSR)
        self.log = self.root / 'calls.jsonl'
        self.packet = self.root / 'packet.json'
        self.packet.write_text(json.dumps({'feature': 'F', 'task': 'T-1', 'role': 'builder',
                                           'agent_profile': 'builder'}))
        # Keep the interpreter available to the fake CLI's /usr/bin/env shebang
        # even in python:*-slim, where Python is installed under /usr/local/bin.
        env = {'PATH': f'{bin_dir}:{pathlib.Path(sys.executable).parent}:/usr/bin:/bin',
               'HOME': str(self.root), 'FAKE_LOG': str(self.log),
               'OPENAI_API_KEY': 'sk-should-never-reach-the-cli'}
        writable = lambda ws: [pathlib.Path(ws).resolve()]
        # The unresolved-state registry path is derived from REPO's Git common dir. Bind it to the fixture repository:
        # the real checkout must not be read by a test, and with HOME replaced above Git would not see a CI
        # container's global `safe.directory` for it (this only passed in one-process runs through a warm
        # `state_snapshots._common_dir_cached`).
        for patch in (mock.patch.dict(os.environ, env, clear=True),
                      mock.patch.object(runner, 'REPO', self.repo),
                      mock.patch.object(runner, 'RUNS', self.runs),
                      mock.patch.object(runner, 'render_prompt', return_value='fix the bug ✓'),
                      mock.patch.object(runner, 'validate_packet_integrity'),
                      mock.patch.object(runner, 'validate_result'),
                      mock.patch.object(runner, 'enforce_postconditions'),
                      mock.patch.object(runner, 'cli_version', return_value='codex-cli 0.160.0'),
                      mock.patch.object(execution, '_agent_writable_roots', writable),
                      mock.patch.object(execution, '_default_isolation_probe',
                                        return_value=['sh', '-c', f'echo {execution._ISOLATION_MARKER}'])):
            patch.start()
            self.addCleanup(patch.stop)

    def main(self, *extra, mode='ok', sandbox='ok'):
        argv = ['runner.py', str(self.packet), '--provider', 'codex', '--worktree', str(self.worktree),
                '--skip-verification', *extra]
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(sys, 'argv', argv), mock.patch.dict(os.environ, {'FAKE_MODE': mode, 'FAKE_SANDBOX': sandbox}), \
                contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            try:
                runner.main()
                code = 0
            except SystemExit as exc:
                code = exc.code
        return code, out.getvalue(), err.getvalue()

    def run_dirs(self):
        return [p.parent for p in self.runs.rglob('provenance.json')]

    def provenance(self):
        (run,) = self.run_dirs()
        return run, json.loads((run / 'provenance.json').read_text())

    def test_without_a_model_the_run_is_refused_before_anything_is_written(self):
        """D1: no --model and no configured model: refused, no run dir, no CLI call."""
        code, _, err = self.main()
        self.assertNotEqual(0, code)
        self.assertIn('pass --model', err)
        self.assertEqual([], list(self.runs.iterdir()))
        self.assertFalse(self.log.exists())
        with mock.patch.dict(runner.DEFAULT_MODELS, {'codex': 'gpt-x'}):
            self.assertEqual(0, self.main()[0])
        self.assertEqual(('gpt-x', 'configuration'), tuple(self.provenance()[1][k]
                                                          for k in ('model_requested', 'model_source')))

    def test_preflight_starts_nothing_and_reports_a_refusal(self):
        """Row 5: Showcase can check a run before the lifecycle starts an attempt."""
        code, out, _ = self.main('--model', 'm', '--preflight')
        self.assertEqual((0, 'preflight: ok\n'), (code, out))
        self.assertEqual([], list(self.runs.iterdir()))
        self.assertFalse(self.log.exists())
        with mock.patch.object(runner, 'RUNS', self.worktree / '.agent-runs'):  # evidence the agent could write
            code, _, err = self.main('--model', 'm', '--preflight')
        self.assertNotEqual(0, code)
        self.assertIn('preflight refused: evidence root is writable', err)
        self.assertFalse((self.worktree / '.agent-runs').exists())

    def test_preflight_refuses_an_unavailable_sandbox_and_launch_checks_again(self):
        """A passed preflight is no standing permission: the launch runs the same checks again."""
        code, _, err = self.main('--model', 'm', '--preflight', sandbox='broken')
        self.assertNotEqual(0, code)
        self.assertIn('preflight refused: sandbox unavailable', err)
        self.assertEqual([], list(self.runs.iterdir()))
        self.assertEqual(0, self.main('--model', 'm', '--preflight')[0])
        code, _, err = self.main('--model', 'm', sandbox='broken')  # the sandbox broke after the preflight
        self.assertNotEqual(0, code)
        self.assertEqual('preflight-refused', self.provenance()[1]['status'])
        self.assertFalse(self.log.exists())  # the agent never started

    def test_print_command_renders_what_a_run_launches(self):
        """AH5-17: the backend's argv, marked and without the prompt; it starts nothing and writes nothing."""
        code, out, err = self.main('--model', 'm', '--reasoning', 'high', '--print-command')
        self.assertEqual(0, code)
        self.assertTrue(out.startswith('# launched command, prompt on stdin: codex exec'), out)
        self.assertNotIn('LEGACY', err)
        self.assertNotIn('fix the bug', out)
        self.assertNotIn('sk-should-never-reach-the-cli', out)
        self.assertFalse(self.log.exists())
        self.assertEqual([], list(self.runs.iterdir()))
        preview = shlex.split(out.strip().removeprefix('# launched command, prompt on stdin: '))

        self.assertEqual(0, self.main('--model', 'm', '--reasoning', 'high')[0])
        run, _ = self.provenance()
        launched = json.loads(self.log.read_text().splitlines()[-1])['argv']
        slot = execution._slot('preview')
        real = [str(run / 'evidence'), next(p.stem for p in (run / 'evidence' / 'executions').glob('*.json'))]
        normal = ['codex', *[item.replace(real[0], '<EVIDENCE_ROOT>').replace(real[1], slot) for item in launched]]
        self.assertEqual(normal, preview)

    def test_print_command_refuses_without_a_model(self):
        code, _, err = self.main('--print-command')
        self.assertNotEqual(0, code)
        self.assertIn('no model', err)

    def test_print_command_without_the_cli_uses_the_tested_profile(self):
        with mock.patch.object(runner, 'cli_version', return_value=None):
            code, out, err = self.main('--model', 'm', '--print-command')
        self.assertEqual(0, code)
        self.assertIn('Preview only', err)
        self.assertIn('<EVIDENCE_ROOT>/executions/', shlex.split(out.split(': ', 1)[1])[shlex.split(out.split(': ', 1)[1]).index('--output-schema') + 1])

    def rendered(self, provider, read_only=False):
        args = argparse.Namespace(provider=provider, sandbox='read-only' if read_only else 'workspace-write',
                                  reasoning=None, max_turns=2, max_budget_usd=None, agent_timeout=60)
        schema = {'$schema': 'https://json-schema.org/draft/2020-12/schema', 'type': 'object'}
        options = runner.agent_cli_options(args, read_only, schema)
        backend = execution.AgentCliBackend(provider, 'prompt', env={'PATH': '/nonexistent'}, cli_options=options)
        return backend.render_argv(runner.agent_request(args, {'packet_sha256': 'x'}, 'm', options, 'preview'),
                                   {'workspace': str(self.worktree), 'evidence_root': '/EVIDENCE_ROOT'})

    def test_the_rendered_codex_command_is_ephemeral_json_and_sandboxed(self):
        argv = self.rendered('codex')
        for flag in ('--ephemeral', '--json', 'sandbox_workspace_write.network_access=false',
                     'sandbox_workspace_write.writable_roots=[]'):
            self.assertIn(flag, argv)
        self.assertNotIn(str(runner.unknown_state_registry.registry_path(self.repo).parent), argv)

    def test_the_rendered_claude_command_is_restricted_and_denies_the_marker_directory(self):
        marker = str(runner.unknown_state_registry.registry_path(self.repo).parent)
        argv = self.rendered('claude')
        self.assertIn('--restricted', argv)
        self.assertIn('--strict-mcp-config', argv)
        self.assertNotIn('--bare', argv)
        self.assertNotIn('$schema', json.loads(argv[argv.index('--json-schema') + 1]))
        settings = json.loads(argv[argv.index('--settings') + 1])
        self.assertIn(marker, settings['sandbox']['filesystem']['denyRead'])
        self.assertIn(marker, settings['sandbox']['filesystem']['denyWrite'])
        for tool in ('Read', 'Edit', 'Write'):
            self.assertTrue(any(rule.startswith(f'{tool}(') and marker.lstrip('/') in rule
                                for rule in settings['permissions']['deny']), tool)

    def test_a_run_records_the_library_result_and_reads_the_task_result_from_its_artifacts(self):
        code, out, _ = self.main('--model', 'm', '--reasoning', 'high', '--agent-timeout', '120')
        self.assertEqual(0, code)
        run, provenance = self.provenance()
        self.assertEqual(str(run / 'result.json'), out.strip().splitlines()[-1])
        self.assertEqual('pass', json.loads((run / 'result.json').read_text())['status'])
        record = provenance['agent_execution']
        self.assertEqual(('completed', 0, None), (record['outcome'], record['exit_code'], record['withheld_reason']))
        self.assertEqual(('m', 'argument', 120, 0), (provenance['model_requested'], provenance['model_source'],
                                                     provenance['agent_timeout_seconds'],
                                                     provenance['provider_exit_code']))
        self.assertEqual(3, provenance['provider_metadata']['usage']['input_tokens'])
        (call,) = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertEqual('fix the bug ✓', call['stdin'])  # row 1: the full prompt on stdin, not in argv
        self.assertNotIn('fix the bug ✓', call['argv'])
        for flag in ('approval_policy="never"', 'web_search="disabled"', 'sandbox_workspace_write.network_access=false',
                     'model_reasoning_effort="high"', 'forced_login_method="chatgpt"', '--output-schema'):
            self.assertIn(flag, call['argv'])
        self.assertEqual('workspace-write', call['argv'][call['argv'].index('--sandbox') + 1])
        self.assertEqual(('forced_login_method="chatgpt"', False),
                         (record['requested_login'], record['billing_source_confirmed']))
        self.assertIsNone(record['provider_error'])
        self.assertNotIn('sk-should-never-reach-the-cli', ((run / record['artifacts']['agent-output']).parent / 'launch.json').read_text())
        # D3: stdout/stderr are read from the artifacts, no second copy; an old run's logs read as they are
        self.assertFalse((run / 'stdout.log').exists())
        self.assertIn('turn.completed', runner.run_output(run, 'stdout'))
        legacy = self.root / 'legacy'
        legacy.mkdir()
        (legacy / 'stdout.log').write_text('old')
        self.assertEqual('old', runner.run_output(legacy, 'stdout'))

    def test_a_reviewer_run_is_read_only(self):
        self.main('--model', 'm', '--profile', 'security-reviewer')
        (call,) = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertEqual('read-only', call['argv'][call['argv'].index('--sandbox') + 1])

    def test_withheld_output_keeps_the_process_outcome_and_gives_no_task_result(self):
        """D4/D6: publication is refused, the process facts stay, nothing is parsed from withheld output."""
        code, _, err = self.main('--model', 'm', mode='secret')
        self.assertNotEqual(0, code)
        self.assertIn('output withheld (CREDENTIAL_SHAPED_OUTPUT)', err)
        self.assertNotIn('sk-ant-', err)
        run, provenance = self.provenance()
        self.assertEqual('output-withheld', provenance['status'])
        record = provenance['agent_execution']
        self.assertEqual(('completed', 0, 'CREDENTIAL_SHAPED_OUTPUT'),
                         (record['outcome'], record['exit_code'], record['withheld_reason']))
        self.assertNotIn('agent-output', record['artifacts'])
        self.assertEqual({'usage': None, 'cost_usd': None}, provenance['provider_metadata'])
        self.assertFalse((run / 'result.json').exists())

    def test_a_process_failure_keeps_its_status_even_when_the_output_is_withheld(self):
        """Row 11: a non-zero exit stays provider-error; the withheld reason is recorded beside it."""
        self.assertNotEqual(0, self.main('--model', 'm', mode='secretfail')[0])
        _, provenance = self.provenance()
        self.assertEqual(('provider-error', 3, 'CREDENTIAL_SHAPED_OUTPUT'),
                         (provenance['status'], provenance['agent_execution']['exit_code'],
                          provenance['agent_execution']['withheld_reason']))

    def test_no_last_message_means_no_task_result(self):
        """Row 9: Codex without its last message gives no task result."""
        code, _, err = self.main('--model', 'm', mode='nolast')
        self.assertNotEqual(0, code)
        self.assertIn('codex wrote no last message', err)
        run, _ = self.provenance()
        self.assertFalse((run / 'result.json').exists())

    def test_a_refusal_at_launch_is_recorded_as_preflight_refused(self):
        with mock.patch.object(runner, 'RUNS', self.worktree / '.agent-runs'):  # evidence the agent could write
            code, _, err = self.main('--model', 'm')
            (run,) = [p.parent for p in (self.worktree / '.agent-runs').rglob('provenance.json')]
        self.assertNotEqual(0, code)
        self.assertIn('refused before launch: evidence root is writable', err)
        self.assertEqual('preflight-refused', json.loads((run / 'provenance.json').read_text())['status'])
        self.assertFalse(self.log.exists())

    def test_the_agent_timeout_cancels_the_run(self):
        """D2: --agent-timeout bounds the agent; the outcome is recorded, never a task result."""
        code, _, err = self.main('--model', 'm', '--agent-timeout', '1', mode='sleep')
        self.assertNotEqual(0, code)
        self.assertIn('ended timeout', err)
        _, provenance = self.provenance()
        self.assertEqual(('provider-error', 'timeout', None),
                         (provenance['status'], provenance['agent_execution']['outcome'],
                          provenance['agent_execution']['exit_code']))

    def test_claude_options_keep_the_profile_permissions(self):
        """Rows 2-3: the flags claude_command passed, as validated options; reviewers cannot write."""
        args = mock.Mock(provider='claude', max_turns=7, max_budget_usd=1.5)
        schema = {'$schema': 'x', 'type': 'object'}
        builder = runner.agent_cli_options(args, False, schema, worktree=self.worktree)
        reviewer = runner.agent_cli_options(args, True, schema, worktree=self.worktree)
        self.assertEqual({'type': 'object'}, builder['output_schema'])
        self.assertEqual(['Read', 'Edit', 'Write', 'Glob', 'Grep', 'Bash'], builder['tools'])
        self.assertEqual(('read-only', ['Read', 'Glob', 'Grep', 'Bash']), (reviewer['sandbox_mode'], reviewer['tools']))
        for options in (builder, reviewer):
            self.assertEqual(runner.DENIED_BASH, options['denied_commands'])
            self.assertEqual((7, 1.5, True, 'dontAsk'),
                             tuple(options[k] for k in ('max_turns', 'max_budget_usd', 'restricted', 'permission_mode')))
            self.assertIn(str(runner.unknown_state_registry.registry_path(self.worktree).parent), options['deny_read'])
            execution.AgentCliBackend('claude', 'x', cli_options=options)  # the library accepts them

class StructuredLaunchTest(unittest.TestCase):
    """AH5-16a: design, wayfinder and contract authoring launch through the runner's library path."""

    setUp = RunnerLaunchTest.setUp  # the same fixture: fake codex, isolation probe, writable roots

    SCHEMA = {'type': 'object', 'properties': {'status': {'type': 'string'}}, 'required': ['status'],  # noqa: RUF012
              'additionalProperties': False}

    def launch(self, *, model='m', read_only=False, mode='ok', sandbox='ok'):
        args = argparse.Namespace(provider='codex', model=model, reasoning='high', max_turns=20, max_budget_usd=None,
                                  agent_timeout=120)
        out = self.runs / 'design' / 'F' / 'run' / 'stage'
        out.mkdir(parents=True, exist_ok=True)
        provenance = {'status': 'running'}
        with mock.patch.dict(os.environ, {'FAKE_MODE': mode, 'FAKE_SANDBOX': sandbox}):
            try:
                result = runner.launch_structured_agent(args, 'design it \u2713', self.worktree, out, self.SCHEMA,
                                                        read_only=read_only, provenance=provenance)
            except runner.AgentLaunchFailed as exc:
                return None, provenance, exc
        return result, provenance, None

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_a_structured_call_returns_its_result_and_records_the_execution(self):
        result, provenance, failure = self.launch()
        self.assertEqual((CODEX_RESULT, None), (result, failure))
        self.assertEqual(('m', 'argument', 120, 0, 'completed'),
                         (provenance['model_requested'], provenance['model_source'],
                          provenance['agent_timeout_seconds'], provenance['provider_exit_code'],
                          provenance['agent_execution']['outcome']))
        self.assertEqual('running', provenance['status'])  # the caller records the status, not the launch
        [call] = self.calls()
        self.assertEqual('design it \u2713', call['stdin'])  # row 1: the prompt on stdin, never in argv
        self.assertNotIn('design it', ' '.join(call['argv']))
        argv = call['argv']
        self.assertEqual(('workspace-write', 'm', '-'), (argv[argv.index('--sandbox') + 1], argv[argv.index('-m') + 1],
                                                         argv[-1]))
        self.assertIn('model_reasoning_effort="high"', argv)

    def test_a_read_only_stage_runs_in_the_read_only_sandbox(self):
        self.launch(read_only=True)
        argv = self.calls()[-1]['argv']
        self.assertEqual('read-only', argv[argv.index('--sandbox') + 1])

    def test_without_a_model_nothing_is_launched(self):
        with self.assertRaises(SystemExit):
            self.launch(model=None)
        self.assertEqual([], self.calls())

    def test_refused_failed_and_withheld_launches_name_their_status_and_their_evidence(self):
        cases = {'refused': ({'sandbox': 'broken'}, 'preflight-refused', 'refused before launch'),
                 'failed': ({'mode': 'secretfail'}, 'provider-error', 'ended completed (exit 3'),
                 'withheld': ({'mode': 'secret'}, 'output-withheld', 'output withheld')}
        for name, (kwargs, status, message) in cases.items():
            with self.subTest(name):
                result, provenance, failure = self.launch(**kwargs)
                self.assertIsNone(result)
                self.assertEqual(status, failure.status)
                self.assertIn(message, str(failure))
                self.assertIn(f'; logs: {self.runs}', str(failure))  # where the evidence is
                self.assertIn('agent_execution', provenance)

    def test_a_result_that_cannot_be_read_is_a_harness_error_naming_it(self):
        """As before AH5-16a: no result file, or an unreadable one, ends the stage with exit 2 (a harness error)."""
        for mode, messages in (('nolast', ('codex wrote no last message; logs: ',)),
                               ('badjson', (str(self.runs), 'Expecting property name'))):
            stderr = io.StringIO()
            with self.subTest(mode), contextlib.redirect_stderr(stderr), self.assertRaises(SystemExit) as caught:
                self.launch(mode=mode)
            self.assertEqual(2, caught.exception.code)
            for message in messages:
                self.assertIn(message, stderr.getvalue())

    def test_the_request_is_bounded_by_the_agent_timeout(self):
        real = runner._execution.launch
        with mock.patch.object(runner._execution, 'launch', side_effect=real) as launch:
            self.launch()
        self.assertEqual(120, launch.call_args.args[0]['timeout_seconds'])

    def test_the_agent_timeout_must_be_1_to_86400_seconds(self):
        for value in (0, -1, 86401):
            with self.subTest(value), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                runner.check_agent_timeout(argparse.Namespace(agent_timeout=value))
        for value in (1, 86400):
            runner.check_agent_timeout(argparse.Namespace(agent_timeout=value))

    def test_claude_launches_deny_the_marker_directory_for_reading_and_writing(self):
        args = argparse.Namespace(provider='claude', sandbox='workspace-write', reasoning=None, max_turns=5,
                                  max_budget_usd=None)
        marker = str(runner.unknown_state_registry.registry_path(runner.REPO).parent)
        for worktree in (self.worktree, None):  # the marker derives from REPO: no caller can leave it out
            with self.subTest(worktree=worktree):
                options = runner.agent_cli_options(args, False, self.SCHEMA, worktree=worktree)
                self.assertEqual([marker], options['deny_write'])
                self.assertIn(marker, options['deny_read'])
        execution.AgentCliBackend('claude', 'x', cli_options=options)  # the library accepts them (>= 0.25.0)

    def test_a_claude_stage_gets_its_sandbox_its_tools_and_the_marker_denials(self):
        marker = str(runner.unknown_state_registry.registry_path(runner.REPO).parent)
        args = argparse.Namespace(provider='claude', model='m', reasoning=None, max_turns=5, max_budget_usd=None,
                                  agent_timeout=120)
        out = self.runs / 'design' / 'F' / 'run' / 'claude'
        for read_only in (True, False):
            with self.subTest(read_only=read_only), \
                    mock.patch.object(runner._execution, 'AgentCliBackend', side_effect=RuntimeError('stop')) as backend, \
                    self.assertRaisesRegex(RuntimeError, 'stop'):
                runner.launch_structured_agent(args, 'p', self.worktree, out, self.SCHEMA, read_only=read_only,
                                               provenance={})
            options = backend.call_args.kwargs['cli_options']
            self.assertEqual('read-only' if read_only else 'workspace-write', options['sandbox_mode'])
            self.assertEqual(not read_only, {'Edit', 'Write'} <= set(options['tools']))
            self.assertEqual(([marker], True), (options['deny_write'], marker in options['deny_read']))


class AwaitLaunchTest(unittest.TestCase):
    """AH5-16a review F-2: a terminating signal while an agent runs stops the agent, not only this process."""

    class Handle:
        def __init__(self):
            import threading
            self.cancelled = threading.Event()

        def result(self, timeout=None):
            if not self.cancelled.wait(5 if timeout is None else timeout):
                raise TimeoutError
            return {'outcome': 'cancelled'}

        def cancel(self):
            self.cancelled.set()

    def test_sigterm_and_sighup_cancel_the_execution_and_exit(self):
        import signal
        import threading
        for signum in (signal.SIGTERM, signal.SIGHUP):
            with self.subTest(signum=signum):
                before = signal.getsignal(signum)
                handle = self.Handle()
                threading.Timer(0.2, os.kill, (os.getpid(), signum)).start()
                with self.assertRaises(SystemExit) as caught:
                    runner.await_launch(handle)
                self.assertEqual(128 + signum, caught.exception.code)
                self.assertTrue(handle.cancelled.is_set())
                self.assertIs(before, signal.getsignal(signum))  # restored

    def test_a_finished_launch_returns_its_result_and_restores_the_handlers(self):
        import signal
        before = signal.getsignal(signal.SIGTERM)
        handle = self.Handle()
        handle.cancel()
        self.assertEqual({'outcome': 'cancelled'}, runner.await_launch(handle))
        self.assertIs(before, signal.getsignal(signal.SIGTERM))

    def test_an_interrupted_wait_cancels_and_grants_the_agent_its_grace(self):
        handle = mock.Mock()
        handle.result.side_effect = [KeyboardInterrupt, {'outcome': 'cancelled'}]
        with self.assertRaises(KeyboardInterrupt):
            runner.await_launch(handle)
        handle.cancel.assert_called_once_with()
        self.assertEqual([mock.call(), mock.call(timeout=30)], handle.result.call_args_list)


    def test_a_second_signal_during_the_grace_still_exits_by_systemexit(self):
        """Delta review N-7: the handlers stay installed while the cancelled agent gets its 30 s."""
        import signal
        import threading
        seen = []
        previous = signal.signal(signal.SIGTERM, lambda signum, frame: seen.append(signum))
        self.addCleanup(signal.signal, signal.SIGTERM, previous)
        cancelled, never = threading.Event(), threading.Event()
        handle = mock.Mock()
        handle.result.side_effect = lambda timeout=None: (cancelled if timeout is None else never).wait(
            10 if timeout is None else timeout)
        handle.cancel.side_effect = cancelled.set
        for delay in (0.2, 0.6):
            threading.Timer(delay, os.kill, (os.getpid(), signal.SIGTERM)).start()
        with self.assertRaises(SystemExit) as caught:
            runner.await_launch(handle)
        self.assertEqual(128 + signal.SIGTERM, caught.exception.code)
        self.assertEqual([], seen)  # neither signal reached the previous handler
        self.assertEqual(mock.call(timeout=30), handle.result.call_args)  # the second ended the grace wait

class AwaitLaunchWiringTest(StructuredLaunchTest):
    """Delta review: both launch sites wait through await_launch, so the signal handling cannot be dropped silently."""

    def spy(self):
        handles = []
        real_launch = runner._execution.launch

        def launch(*args, **kwargs):
            handles.append(real_launch(*args, **kwargs))
            return handles[-1]
        awaited = mock.Mock(wraps=runner.await_launch)
        return handles, awaited, launch

    def test_the_structured_launch_waits_through_await_launch(self):
        handles, awaited, launch = self.spy()
        with mock.patch.object(runner._execution, 'launch', launch), mock.patch.object(runner, 'await_launch', awaited):
            result, _, failure = self.launch()
        self.assertEqual((CODEX_RESULT, None), (result, failure))
        self.assertEqual([mock.call(h) for h in handles], awaited.call_args_list)
        self.assertEqual(1, len(handles))

    def test_a_task_run_waits_through_await_launch(self):
        handles, awaited, launch = self.spy()
        with mock.patch.object(runner._execution, 'launch', launch), mock.patch.object(runner, 'await_launch', awaited):
            code, _, err = RunnerLaunchTest.main(self, '--model', 'm')
        self.assertEqual(0, code, err)
        self.assertEqual([mock.call(h) for h in handles], awaited.call_args_list)
        self.assertEqual(1, len(handles))


class FailureMessageTest(StructuredLaunchTest):
    def test_a_failed_launch_names_its_error_code_and_the_provider_s_words(self):
        launched = {'outcome': 'error', 'exit_code': 3, 'error_code': 'provider_quota', 'resolved_model': None,
                    'request_id': 'x', 'artifacts': []}
        record = {'artifacts': {}, 'withheld_reason': None, 'provider_error': {'message': 'quota resets at 17:00'}}
        handle = mock.Mock()
        handle.result.return_value = launched
        with mock.patch.object(runner._execution, 'launch', return_value=handle), \
                mock.patch.object(runner, 'execution_record', return_value=record):
            _, _, failure = self.launch()
        self.assertEqual('provider-error', failure.status)
        for part in ('ended error (exit 3, provider_quota: quota resets at 17:00); logs: ', str(self.runs)):
            self.assertIn(part, str(failure))


if __name__ == '__main__':
    unittest.main()
