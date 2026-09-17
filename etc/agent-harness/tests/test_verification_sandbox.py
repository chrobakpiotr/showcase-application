import importlib.util
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
            cache = root / 'ro-cache'
            (cache / 'modules-2').mkdir(parents=True)
            with mock.patch.dict('os.environ', {'AGENTIC_SDD_GRADLE_RO_DEP_CACHE': str(cache)}, clear=False):
                plan = vs.build_plan(['./gradlew', 'test'], root, root / 'run', 'off')
            self.assertEqual(str(cache.resolve()), plan.env.get('GRADLE_RO_DEP_CACHE'))

    def test_codex_sandbox_helper_uses_documented_command_shape(self):
        with tempfile.TemporaryDirectory() as tmp, \
             mock.patch.object(vs.shutil, 'which', return_value='/usr/local/bin/codex'), \
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
            },
            clear=True,
        ):
            root = pathlib.Path(tmp)
            env = vs._safe_env(root, root / 'home')
            self.assertEqual('/usr/bin:/bin', env['PATH'])
            self.assertEqual('true', env['CI'])
            self.assertNotIn('GITHUB_TOKEN', env)
            self.assertNotIn('OPENAI_API_KEY', env)
            self.assertNotIn('ANTHROPIC_API_KEY', env)
            self.assertNotIn('AWS_SECRET_ACCESS_KEY', env)
            self.assertEqual(str(root / 'home'), env['HOME'])


if __name__ == '__main__':
    unittest.main()
