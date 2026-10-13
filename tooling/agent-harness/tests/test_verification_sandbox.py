import importlib.util
import os
import pathlib
import tempfile
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'verification_sandbox.py'
spec = importlib.util.spec_from_file_location('sdd_verification_sandbox', MODULE_PATH)
vs = importlib.util.module_from_spec(spec)
assert spec.loader is not None
import sys
sys.modules[spec.name] = vs
spec.loader.exec_module(vs)


class VerificationSandboxTest(unittest.TestCase):
    def test_shared_executable_resolver_ignores_inherited_path_and_records_digest(self):
        with mock.patch.object(vs, 'SYSTEM_EXECUTABLE_DIRECTORIES', ('/usr/bin',)), \
             mock.patch.dict(vs.os.environ, {'PATH': '/tmp/hostile:/usr/bin'}, clear=True):
            binding = vs.resolve_executable('true')
        self.assertEqual('/usr/bin/true', binding['path'])
        self.assertEqual(64, len(binding['sha256']))
        vs.verify_executable_binding(binding)

    def test_shared_resolver_binds_only_the_exact_active_interpreter(self):
        active = str(pathlib.Path(sys.executable).resolve(strict=True))
        binding = vs.resolve_executable(active, active_interpreter=True)
        self.assertEqual({'command': active, 'path': active,
                          'sha256': vs._sha256_file(pathlib.Path(active))}, binding)
        vs.verify_executable_binding(binding)
        with self.assertRaises(vs.ExecutableResolutionError):
            vs.resolve_executable('python3', active_interpreter=True)

        with tempfile.TemporaryDirectory(dir=pathlib.Path.home()) as temporary:
            root = pathlib.Path(temporary).resolve()
            fake = root / 'python3'
            fake.write_text('#!/bin/sh\nexit 0\n')
            fake.chmod(0o700)
            with self.assertRaises(vs.ExecutableResolutionError):
                vs.resolve_executable(str(fake), worktree=root, active_interpreter=True)
            with self.assertRaises(vs.ExecutableResolutionError):
                vs.resolve_executable(str(fake), active_interpreter=True)

    def test_active_interpreter_inside_worktree_sandbox_home_or_tmpdir_is_refused(self):
        for exclusion in ('worktree', 'sandbox_home', 'tmpdir'):
            with tempfile.TemporaryDirectory(dir=pathlib.Path.home()) as temporary:
                root = pathlib.Path(temporary).resolve()
                executable = root / 'bin' / 'python3'
                executable.parent.mkdir()
                executable.write_text('#!/bin/sh\nexit 0\n')
                executable.chmod(0o700)
                kwargs = {} if exclusion == 'tmpdir' else {exclusion: root}
                environment = {'TMPDIR': str(root)} if exclusion == 'tmpdir' else {}
                with self.subTest(exclusion=exclusion), \
                     mock.patch.object(vs.sys, 'executable', str(executable)), \
                     mock.patch.dict(vs.os.environ, environment, clear=False), \
                     self.assertRaisesRegex(vs.ExecutableResolutionError,
                                            '^executable-path-excluded$'):
                    vs.resolve_executable(str(executable), active_interpreter=True, **kwargs)

    def test_backend_discovery_uses_the_shared_resolver(self):
        executable_path = str(pathlib.Path(sys.executable).resolve())
        with mock.patch.object(vs.platform, 'system', return_value='Linux'), \
             mock.patch.object(vs.shutil, 'which', side_effect=AssertionError('unbound lookup')), \
             mock.patch.object(vs, 'resolve_executable', side_effect=lambda name, **_kwargs: {
                 'command': name, 'path': executable_path, 'sha256': 'a' * 64}) as resolve:
            candidates = vs.discover_backends()
        self.assertEqual({'codex-sandbox', 'bubblewrap', 'docker-container'},
                         {item.kind for item in candidates})
        self.assertEqual(['codex', 'bwrap', 'sandbox-exec', 'docker'],
                         [call.args[0] for call in resolve.call_args_list])
        self.assertTrue(all(item.state == 'DISCOVERED' and item.qualification is None
                            for item in candidates))

    def test_shared_executable_resolver_rejects_hostile_paths_and_detects_swap(self):
        with tempfile.TemporaryDirectory(dir=pathlib.Path.home()) as temporary:
            root = pathlib.Path(temporary).resolve()
            executable = root / 'trusted-tool'
            executable.write_text('#!/bin/sh\nexit 0\n')
            executable.chmod(0o700)
            binding = vs.resolve_executable('trusted-tool', toolchain_directories=(str(root),),
                                            tmpdir='/definitely-not-a-temp-root')
            executable.write_text('#!/bin/sh\nexit 1\n')
            with self.assertRaises(vs.ExecutableResolutionError):
                vs.verify_executable_binding(binding)
            with self.assertRaises(vs.ExecutableResolutionError):
                vs.resolve_executable('./trusted-tool', toolchain_directories=(str(root),),
                                      tmpdir='/definitely-not-a-temp-root')

    def test_shared_executable_resolver_rejects_worktree_and_writable_search_roots(self):
        with tempfile.TemporaryDirectory(dir=pathlib.Path.home()) as temporary:
            root = pathlib.Path(temporary).resolve()
            executable = root / 'fake-python3'
            executable.write_text('#!/bin/sh\nexit 0\n')
            executable.chmod(0o700)
            with self.assertRaises(vs.ExecutableResolutionError):
                vs.resolve_executable('fake-python3', toolchain_directories=(str(root),),
                                      worktree=root, tmpdir='/definitely-not-a-temp-root')
        with self.assertRaises(vs.ExecutableResolutionError):
            vs.resolve_executable('true', toolchain_directories=('/tmp',))

    def test_control_store_read_probe_fails_when_any_process_depth_can_read(self):
        import subprocess
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp).resolve()
            control = root / '.agent-runs' / 'control' / 'verification-v2'
            control.mkdir(parents=True)
            worktree = root / 'worktree'
            worktree.mkdir()
            candidate = vs.BackendCandidate('codex-sandbox', 'test', '/usr/bin/codex', 'Darwin')

            def uncontained_probe(_candidate, code, args, *, cwd, **_kwargs):
                result = subprocess.run([sys.executable, '-c', code, *args], cwd=cwd,
                                        capture_output=True, text=True, check=False)
                return result.returncode, result.stdout.strip(), result.stderr.strip()

            for check_id in ('Q02', 'Q03', 'Q04'):
                with self.subTest(check_id=check_id), \
                     mock.patch.object(vs, '_run_probe', side_effect=uncontained_probe):
                    outcome = vs._active_control_read_probe(candidate, check_id,
                        (str(worktree),), (str(root / '.agent-runs'),), worktree)
                self.assertEqual('FAIL', outcome[0])
                self.assertEqual('CONTROL_READ_ALLOWED', outcome[1])
                self.assertIn('trusted-control-read-probe', outcome[2])

    def test_legacy_strong_isolation_is_not_v2_qualification(self):
        with tempfile.TemporaryDirectory() as tmp, \
             mock.patch.object(vs, '_codex_helper', return_value=vs.SandboxPlan(
                 ['sandbox','python3','-V'], {}, 'claimed', True, {'network':'disabled'})), \
             mock.patch.object(vs, '_bubblewrap', return_value=None), \
             mock.patch.object(vs, '_macos_sandbox_exec', return_value=None):
            root = pathlib.Path(tmp)
            plan = vs.build_plan(['python3', '-V'], root, root / 'run', 'auto')
            self.assertFalse(vs.qualifies_v2(plan))

    def test_unsupported_backend_fails_closed_for_every_mode(self):
        with tempfile.TemporaryDirectory() as tmp, \
             mock.patch.object(vs, '_codex_helper', return_value=None), \
             mock.patch.object(vs, '_bubblewrap', return_value=None), \
             mock.patch.object(vs, '_macos_sandbox_exec', return_value=None):
            root = pathlib.Path(tmp)
            for mode in ('auto', 'off', 'required'):
                if mode == 'required':
                    with self.assertRaises(RuntimeError):
                        vs.build_plan(['python3', '-V'], root, root / mode, mode)
                else:
                    plan = vs.build_plan(['python3', '-V'], root, root / mode, mode)
                    self.assertFalse(vs.qualifies_v2(plan))
    def test_off_is_explicit_and_not_strong(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            plan = vs.build_plan(['python3', '-V'], root, root / 'run', 'off')
            self.assertEqual('off', plan.backend)
            self.assertFalse(plan.strong_isolation)

    def test_required_fails_when_no_backend_exists(self):
        with tempfile.TemporaryDirectory() as tmp, \
             mock.patch.object(vs, '_codex_helper', return_value=None), \
             mock.patch.object(vs, '_bubblewrap', return_value=None), \
             mock.patch.object(vs, '_macos_sandbox_exec', return_value=None):
            root = pathlib.Path(tmp)
            with self.assertRaises(RuntimeError):
                vs.build_plan(['python3', '-V'], root, root / 'run', 'required')

    def test_auto_degrades_explicitly_when_platform_has_no_strong_backend(self):
        with tempfile.TemporaryDirectory() as tmp, \
             mock.patch.object(vs, '_codex_helper', return_value=None), \
             mock.patch.object(vs, '_bubblewrap', return_value=None), \
             mock.patch.object(vs, '_macos_sandbox_exec', return_value=None):
            root = pathlib.Path(tmp)
            plan = vs.build_plan(['python3', '-V'], root, root / 'run', 'auto')
            self.assertEqual('allowlist-only', plan.backend)
            self.assertFalse(plan.strong_isolation)

    def test_explicit_gradle_read_only_cache_is_forwarded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            wrapper = root / 'gradlew'
            wrapper.write_text('#!/bin/sh\nexit 0\n')
            wrapper.chmod(0o700)
            cache = root / 'ro-cache'
            (cache / 'modules-2').mkdir(parents=True)
            with mock.patch.dict('os.environ', {'AGENTIC_SDD_GRADLE_RO_DEP_CACHE': str(cache)}, clear=False):
                plan = vs.build_plan(['./gradlew', 'test'], root, root / 'run', 'off')
            self.assertEqual(str(cache.resolve()), plan.env.get('GRADLE_RO_DEP_CACHE'))

    def test_codex_sandbox_helper_uses_documented_command_shape(self):
        binding = {'command': 'codex', 'path': '/usr/local/bin/codex', 'sha256': '0' * 64}
        with tempfile.TemporaryDirectory() as tmp, \
             mock.patch.object(vs, 'resolve_executable', return_value=binding), \
             mock.patch.object(vs.platform, 'system', return_value='Darwin'):
            root = pathlib.Path(tmp)
            env = vs._safe_env(root, root / 'home')
            plan = vs._codex_helper(['python3', '-V'], root, env)
            self.assertIsNotNone(plan)
            self.assertEqual(
                ['/usr/local/bin/codex', 'sandbox', 'macos', '--full-auto', 'python3', '-V'],
                plan.argv,
            )


    def test_safe_env_does_not_inherit_provider_or_ci_secrets(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(
            vs.os.environ,
            {
                'PATH': '/usr/bin:/bin',
                'CI': 'true',
                'GITHUB_TOKEN': 'github-secret',
                'OPENAI_API_KEY': 'openai-secret',
                'ANTHROPIC_API_KEY': 'anthropic-secret',
                'AWS_SECRET_ACCESS_KEY': 'aws-secret',
                'PYTHONPATH': '/attacker/python',
                'PYTHONHOME': '/attacker/python-home',
                'PYTHONSTARTUP': '/attacker/startup.py',
                'PYTHONUSERBASE': '/attacker/userbase',
                'NODE_OPTIONS': '--require /attacker/node.js',
                'LD_PRELOAD': '/attacker/preload.so',
                'LD_LIBRARY_PATH': '/attacker/lib',
                'DYLD_INSERT_LIBRARIES': '/attacker/inject.dylib',
                'BASH_ENV': '/attacker/bashrc',
                'ENV': '/attacker/env',
                'GIT_EXEC_PATH': '/attacker/git',
                'JAVA_TOOL_OPTIONS': '-javaagent:/attacker/agent.jar',
            },
            clear=True,
        ):
            root = pathlib.Path(tmp)
            env = vs._safe_env(root, root / 'home')
            expected_path = os.pathsep.join(str(path) for path in vs.trusted_executable_directories())
            self.assertEqual(expected_path, env['PATH'])
            self.assertEqual('true', env['CI'])
            self.assertNotIn('GITHUB_TOKEN', env)
            self.assertNotIn('OPENAI_API_KEY', env)
            self.assertNotIn('ANTHROPIC_API_KEY', env)
            self.assertNotIn('AWS_SECRET_ACCESS_KEY', env)
            for key in ('PYTHONPATH', 'PYTHONHOME', 'PYTHONSTARTUP', 'PYTHONUSERBASE',
                        'NODE_OPTIONS', 'LD_PRELOAD', 'LD_LIBRARY_PATH',
                        'DYLD_INSERT_LIBRARIES', 'BASH_ENV', 'ENV', 'GIT_EXEC_PATH',
                        'JAVA_TOOL_OPTIONS'):
                with self.subTest(key=key):
                    self.assertNotIn(key, env)
            self.assertEqual(str(root / 'home'), env['HOME'])

    def test_safe_env_drops_undeclared_toolchain_selectors(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp).resolve()
            with mock.patch.dict(vs.os.environ, {'JAVA_HOME': str(root)}, clear=True):
                env = vs._safe_env(root, root / 'home')
            self.assertNotIn('JAVA_HOME', env)

    def test_trusted_issuer_registry_is_a_protected_authority_path(self):
        repository = pathlib.Path(__file__).resolve().parents[3]
        protected = vs.derive_protected_authority_roots(repository)
        self.assertTrue(any(path.name == 'agent-harness' and path.parent.name == 'tooling' and
                            (path / 'human-issuer-registry.json').is_file() for path in protected))

    def test_bubblewrap_rebinds_trusted_issuer_registry_read_only(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            registry = root / 'tooling/agent-harness/human-issuer-registry.json'
            registry.parent.mkdir(parents=True)
            registry.write_text('{"schema_version":1,"issuers":[]}')
            (root / '.agent-runs/control/verification-v2').mkdir(parents=True)
            with mock.patch.object(vs, 'derive_protected_authority_roots', return_value=(registry,)), \
                 mock.patch.object(vs, 'resolve_executable', return_value={
                     'command': 'bwrap', 'path': '/usr/bin/bwrap', 'sha256': '0' * 64}), \
                 mock.patch.object(vs.platform, 'system', return_value='Linux'):
                plan = vs._bubblewrap(['python3', '-c', 'pass'], root, root / 'home', {})
            self.assertIsNotNone(plan)
            index = plan.argv.index('--ro-bind', plan.argv.index('--ro-bind') + 1)
            self.assertEqual([str(registry), str(registry)], plan.argv[index + 1:index + 3])

    def test_macos_policy_denies_reads_from_verification_control_store(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp).resolve()
            protected = root / '.agent-runs'
            control = protected / 'control' / 'verification-v2'
            control.mkdir(parents=True)
            profile = vs.macos_sandbox_profile((root / 'worktree',), (protected,))
            self.assertIn(f'(deny file-read* (subpath "{control}"))', profile)
            self.assertIn(f'(deny file-write* (subpath "{control}"))', profile)

    def test_bubblewrap_hides_verification_control_store_contents(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp).resolve()
            control = root / '.agent-runs' / 'control' / 'verification-v2'
            control.mkdir(parents=True)
            secret = control / 'grants.json'
            secret.write_text('trusted grant')
            with mock.patch.object(vs, 'derive_protected_authority_roots', return_value=(root / '.agent-runs',)), \
                 mock.patch.object(vs, 'resolve_executable', return_value={
                     'command': 'bwrap', 'path': '/usr/bin/bwrap', 'sha256': '0' * 64}), \
                 mock.patch.object(vs.platform, 'system', return_value='Linux'):
                plan = vs._bubblewrap(['python3', '-c', 'pass'], root / 'worktree', root / 'home', {})
            self.assertIsNotNone(plan)
            index = plan.argv.index('--tmpfs')
            self.assertEqual(str(control), plan.argv[index + 1])


if __name__ == '__main__':
    unittest.main()
