"""Smoke the current operator-reference CLI examples without authority."""
import json
import os
import pathlib
import re
import shlex
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]
REFERENCE = ROOT / 'docs/agentic-sdd/verification-operator-reference.md'
VERIFY = 'tooling/agent-harness/verify.py'


def _example_after_text(document, marker):
    """Return the first bash example following a documented command marker."""
    start = document.find(marker)
    if start < 0:
        raise AssertionError(f'missing operator-reference command text: {marker}')
    block = re.search(r'^```bash\s*\n([\s\S]*?)^```\s*$', document[start:], re.MULTILINE)
    if block is None:
        raise AssertionError(f'missing bash example after: {marker}')
    command = re.sub(r'\\\s*\n', ' ', block.group(1)).strip()
    return shlex.split(command)


class VerificationOperatorReferenceSmokeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.document = REFERENCE.read_text(encoding='utf-8')

    def test_documented_plan_example_is_advisory_in_synthetic_git_fixture(self):
        command = _example_after_text(self.document, 'Create an advisory plan with the required repository')
        self.assertGreaterEqual(len(command), 2)
        self.assertEqual('python3', command[0])
        self.assertEqual(VERIFY, command[1])
        self.assertIn('plan', command)
        help_result = subprocess.run(
            [command[0], VERIFY, 'plan', '--help'], cwd=ROOT,
            text=True, capture_output=True, check=True)
        usage = help_result.stdout.split('options:', 1)[0]
        usage_without_optional = re.sub(r'\[[^\]]*\]', '', usage, flags=re.DOTALL)
        required_options = set(re.findall(r'--[a-z][a-z-]*', usage_without_optional))
        documented_options = {token for token in command if token.startswith('--')}
        self.assertEqual(required_options, documented_options,
                         'documented plan options must exactly match parser-required options')

        with tempfile.TemporaryDirectory(prefix='operator-reference-') as temporary:
            fixture = pathlib.Path(temporary)
            subprocess.run(['git', 'init', '-q', str(fixture)], check=True)
            # The fixture contains only synthetic tracked inputs; the planner
            # runs from the real repo root and cannot write control state here.
            (fixture / 'base.txt').write_text('synthetic baseline\n', encoding='utf-8')
            env = dict(os.environ, GIT_AUTHOR_NAME='Operator Reference Test',
                       GIT_AUTHOR_EMAIL='operator-reference@example.invalid',
                       GIT_COMMITTER_NAME='Operator Reference Test',
                       GIT_COMMITTER_EMAIL='operator-reference@example.invalid')
            subprocess.run(['git', '-C', str(fixture), 'add', 'base.txt'], check=True, env=env)
            subprocess.run(['git', '-C', str(fixture), 'commit', '-qm', 'synthetic baseline'],
                           check=True, env=env)
            base_sha = subprocess.check_output(
                ['git', '-C', str(fixture), 'rev-parse', 'HEAD'], text=True).strip()

            replacements = {
                '<full-base-commit-sha>': base_sha,
                '<stable-family-id>': 'operator-reference-smoke-family',
                '<accepted-policy-checkpoint>': 'a' * 64,
            }
            rendered = []
            index = 0
            while index < len(command):
                token = command[index]
                if token == '--repo':
                    self.assertLess(index + 1, len(command), '--repo requires a documented value')
                    rendered.extend((token, str(fixture)))
                    index += 2
                    continue
                rendered.append(replacements.get(token, token))
                index += 1

            result = subprocess.run(rendered, cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(0, result.returncode, msg=result.stderr or result.stdout)
            record = json.loads(result.stdout)
            self.assertEqual({
                'schema_version', 'advisory', 'family_id', 'base_sha',
                'origin_policy', 'profile_hash', 'candidate_identity',
                'final_changed_surface_id', 'decisions',
            }, set(record))
            self.assertIs(record.get('advisory'), True)
            self.assertFalse((fixture / '.agent-state').exists())

    def test_documented_run_without_repo_is_blocked(self):
        command = _example_after_text(self.document, 'Execute only a plan already accepted by the lifecycle authority')
        self.assertEqual(VERIFY, command[1])
        self.assertIn('run', command)
        # Exercise the documented command shape after removing its explicit
        # repository pair, then use a synthetic dummy identity. This is a
        # parser fail-closed check and cannot resolve or execute a real plan.
        without_repo = []
        index = 0
        while index < len(command):
            if command[index] == '--repo':
                self.assertLess(index + 1, len(command))
                index += 2
                continue
            without_repo.append(command[index])
            index += 1
        self.assertIn('--plan-id', without_repo)
        plan_index = without_repo.index('--plan-id')
        self.assertLess(plan_index + 1, len(without_repo))
        without_repo[plan_index + 1] = 'synthetic-unaccepted-plan-id'
        result = subprocess.run(without_repo, cwd=ROOT, text=True, capture_output=True)
        self.assertEqual(5, result.returncode, msg=result.stderr or result.stdout)
        self.assertEqual('verification-blocked', json.loads(result.stdout).get('status'))


if __name__ == '__main__':
    unittest.main()
