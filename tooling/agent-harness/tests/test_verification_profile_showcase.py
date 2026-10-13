"""Showcase's checked-in gates remain complete and use shared planning semantics."""
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from types import SimpleNamespace

ROOT = pathlib.Path(__file__).resolve().parents[3]
HARNESS = ROOT / 'tooling' / 'agent-harness'
PROFILE_PATH = HARNESS / 'verification-profiles' / 'showcase.json'
sys.path.insert(0, str(HARNESS))

from verification.model import Decision, Family, Plan
from verification.planner import build_plan, required_nodes, seal_pass
from verification.authority import execution_plan_record
from verification.profile import load_profile, matches
from verification.fingerprint import observe_artifacts


class ShowcaseProfileTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.profile = load_profile(PROFILE_PATH)
        cls.data = json.loads(PROFILE_PATH.read_text(encoding='utf-8'))
        cls.gates = {gate.id: gate for gate in cls.profile.gates}

    def test_profile_covers_repository_quality_gate_inputs(self):
        expected = {
            'agent-harness-tests': (
                'tooling/agent-harness/tests/test_verification_profile_showcase.py',
                'tooling/agent-harness/verification/planner.py',
                'tooling/agent-harness/schemas/verification-profile.schema.json'),
            'agentic-sdd-doctor': (
                'docs/specs/SDD-OBS-001/tasks.json',
                'docs/agentic-sdd/constitution.md',
                'AGENTS.md'),
            'showcase-gradle-build': (
                'modules/domain/domain.gradle',
                'apps/ecommerce/backend/src/main/java/com/example/App.java',
                'apps/ecommerce/backend/src/test/java/com/example/AppTest.java',
                'apps/ecommerce/frontend/src/app/app.component.ts',
                'apps/ecommerce/frontend/package-lock.json',
                'tooling/quality/jacoco/jacoco.gradle',
                'tooling/quality/pitest/pitest.gradle',
                'gradle/wrapper/gradle-wrapper.properties'),
            'showcase-frontend-build': (
                'apps/ecommerce/frontend/package.json',
                'apps/ecommerce/frontend/package-lock.json',
                'apps/ecommerce/frontend/angular.json',
                'apps/ecommerce/frontend/tsconfig.app.json',
                'apps/ecommerce/frontend/src/app/app.component.ts'),
            'domain-mutation-threshold': (
                'modules/domain/domain.gradle',
                'tooling/quality/pitest/pitest.gradle',
                'tooling/scripts/verify-domain-pitest.sh',
                'tooling/scripts/verify_pit_report.py',
                'tooling/scripts/tests/test_verify_pit_report.py'),
            'critical-postgres-regression': (
                'tooling/quality/critical-postgres-manifest.json',
                'apps/ecommerce/backend/ecommerce.gradle',
                'apps/ecommerce/backend/src/test/java/com/example/CriticalTest.java',
                'tooling/scripts/verify_critical_postgres_results.py',
                'tooling/scripts/tests/test_verify_critical_postgres_results.py'),
            'critical-rabbitmq-regression': (
                'tooling/quality/critical-rabbitmq-manifest.json',
                'apps/ecommerce/backend/ecommerce.gradle',
                'apps/ecommerce/backend/src/test/java/com/example/RabbitTest.java',
                'tooling/scripts/verify_critical_rabbitmq_results.py',
                'tooling/scripts/tests/test_verify_critical_rabbitmq_results.py'),
        }
        self.assertEqual(set(expected), set(self.gates))
        for gate_id, paths in expected.items():
            gate = self.gates[gate_id]
            with self.subTest(gate=gate_id):
                self.assertTrue(gate.mandatory)
                self.assertFalse(gate.cacheable)
                for path in paths:
                    self.assertTrue(any(matches(pattern, path) for pattern in gate.inputs), path)
                    self.assertTrue(any(matches(pattern, path) for pattern in gate.applicability),
                                    f'{gate_id} is not selected for {path}')
        for gate_id in ('domain-mutation-threshold', 'critical-postgres-regression',
                        'critical-rabbitmq-regression'):
            gate = self.gates[gate_id]
            self.assertTrue(gate.critical)
            self.assertEqual('forbid', gate.retry_policy)
            self.assertIsInstance(gate.retry_controls, tuple)
        postgres = self.gates['critical-postgres-regression']
        rabbit = self.gates['critical-rabbitmq-regression']
        self.assertEqual(('critical-postgres-gate-disables-gradle-test-retry-v1',), postgres.retry_controls)
        self.assertEqual(('critical-rabbit-gate-disables-gradle-test-retry-v1',), rabbit.retry_controls)

    def test_profile_is_deterministic_and_gate_commands_are_unambiguous(self):
        again = load_profile(PROFILE_PATH)
        self.assertEqual(self.profile.content_hash, again.content_hash)
        self.assertEqual(len(self.profile.gates), len({g.command_hash for g in self.profile.gates}))
        for gate in self.profile.gates:
            self.assertTrue(gate.inputs, gate.id)
            self.assertTrue(gate.applicability, gate.id)
            self.assertIn(gate.category, {
                'harness-tests', 'harness-validation', 'application-build',
                'frontend-build', 'mutation-testing', 'critical-integration'})

    def test_direct_cli_and_shared_planner_emit_identical_advisory_plan(self):
        with tempfile.TemporaryDirectory(prefix='showcase-profile-') as temporary:
            repo = pathlib.Path(temporary)
            subprocess.run(['git', 'init', '-q', str(repo)], check=True)
            (repo / 'base.txt').write_text('base\n', encoding='utf-8')
            env = dict(os.environ, GIT_AUTHOR_NAME='Profile Test', GIT_AUTHOR_EMAIL='profile@example.invalid',
                       GIT_COMMITTER_NAME='Profile Test', GIT_COMMITTER_EMAIL='profile@example.invalid')
            subprocess.run(['git', '-C', str(repo), 'add', 'base.txt'], check=True, env=env)
            subprocess.run(['git', '-C', str(repo), 'commit', '-qm', 'base'], check=True, env=env)
            base = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
            for relative in (
                'tooling/agent-harness/tests/test_planner.py',
                'apps/ecommerce/frontend/src/app.ts',
                'modules/domain/domain.gradle',
                'apps/ecommerce/backend/src/test/java/example/IntegrationTest.java',
            ):
                path = repo / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('sample input\n', encoding='utf-8')

            family = Family('showcase-test-family', base, 'integration', self.profile.content_hash, 'a' * 64)
            expected = build_plan(repo, self.profile, family).to_record()
            result = subprocess.run([
                sys.executable, str(HARNESS / 'verify.py'), 'plan', '--repo', str(repo),
                '--profile', 'showcase', '--base-sha', base,
                '--family-id', family.id, '--policy-checkpoint', family.policy_checkpoint,
                '--origin-policy', family.origin_policy,
            ], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            self.assertEqual(expected, json.loads(result.stdout))

    def test_repeated_task_occurrences_remain_distinct(self):
        with tempfile.TemporaryDirectory(prefix='showcase-occurrences-') as temporary:
            repo = pathlib.Path(temporary)
            subprocess.run(['git', 'init', '-q', str(repo)], check=True)
            (repo / 'build.gradle').write_text('plugins {}\n', encoding='utf-8')
            env = dict(os.environ, GIT_AUTHOR_NAME='Profile Test', GIT_AUTHOR_EMAIL='profile@example.invalid',
                       GIT_COMMITTER_NAME='Profile Test', GIT_COMMITTER_EMAIL='profile@example.invalid')
            subprocess.run(['git', '-C', str(repo), 'add', 'build.gradle'], check=True, env=env)
            subprocess.run(['git', '-C', str(repo), 'commit', '-qm', 'base'], check=True, env=env)
            base = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
            family = Family('showcase-task-family', base, 'task-completion', self.profile.content_hash, 'b' * 64)
            command = './gradlew build --continue'
            plan = build_plan(repo, self.profile, family, task_commands=(command, command))
            occurrences = [decision for decision in plan.decisions if decision.node.occurrence]
            self.assertEqual(2, len(occurrences))
            self.assertNotEqual(occurrences[0].node.id, occurrences[1].node.id)
            self.assertEqual('showcase-gradle-build', occurrences[0].node.profile_gate_id)

    def test_mapped_applicable_gate_keeps_separate_profile_requirement(self):
        family = Family('showcase-obligations', '0' * 40, 'task-completion',
                        self.profile.content_hash, 'b' * 64)
        command = './gradlew build --continue'
        for count in (1, 2):
            with self.subTest(occurrences=count):
                nodes = required_nodes(self.profile, family,
                    SimpleNamespace(paths=('modules/domain/src/main/java/Example.java',)),
                    task_commands=(command,) * count)
                builds = [n for n in nodes if n.gate.command == command]
                self.assertEqual(count + 1, len(builds))
                self.assertEqual(1, sum(not n.occurrence for n in builds))
                self.assertEqual(count, sum(n.occurrence for n in builds))
                self.assertEqual(count + 1, len({n.id for n in builds}))
                plan = Plan(family, tuple(Decision(n, None, 'RUN_NOW', 'RUN',
                    'non-cacheable-policy', 'fixture') for n in nodes))
                record = execution_plan_record('fixture', 'a' * 64, 1, 'showcase',
                    self.profile, plan, task_id='T-1', task_attempt=1,
                    task_commands=(command,) * count, origin_binding='task-completion', repository=ROOT)
                obligations = [o for o in record['obligations'] if o['command'] == command]
                ids = {o['obligation_id'] for o in obligations}
                self.assertEqual(count + 1, len(ids))
                self.assertEqual(count + 1, sum(u['obligation_ids'][0] in ids
                    for u in record['execution_units']))
                self.assertTrue(all(len(u['obligation_ids']) == 1 for u in record['execution_units']))

    def test_mapping_does_not_make_non_applicable_profile_gate_required(self):
        family = Family('showcase-non-applicable', '0' * 40, 'task-completion',
                        self.profile.content_hash, 'b' * 64)
        nodes = required_nodes(self.profile, family, SimpleNamespace(paths=('README.md',)),
                               task_commands=('./gradlew build --continue',))
        builds = [n for n in nodes if n.gate.command == './gradlew build --continue']
        self.assertEqual(1, len(builds))
        self.assertTrue(builds[0].occurrence)

    def test_integration_mapping_retains_one_profile_node(self):
        family = Family('showcase-integration', '0' * 40, 'integration',
                        self.profile.content_hash, 'b' * 64)
        nodes = required_nodes(self.profile, family,
            SimpleNamespace(paths=('modules/domain/src/main/java/Example.java',)),
            task_commands=('./gradlew build --continue',) * 2)
        builds = [n for n in nodes if n.gate.command == './gradlew build --continue']
        self.assertEqual(1, len(builds))
        self.assertFalse(builds[0].occurrence)

    def test_focused_test_overwrite_invalidates_full_test_before_coverage_reuse(self):
        with tempfile.TemporaryDirectory(prefix='showcase-artifact-overwrite-') as temporary:
            repo = pathlib.Path(temporary)
            subprocess.run(['git', 'init', '-q', str(repo)], check=True)
            source = repo / 'src' / 'Example.java'
            source.parent.mkdir(parents=True)
            source.write_text('class Example {}\n', encoding='utf-8')
            artifact = repo / 'artifacts' / 'jacoco.exec'
            artifact.parent.mkdir()
            artifact.write_bytes(b'full-suite-execution-data')
            env = dict(os.environ, GIT_AUTHOR_NAME='Profile Test', GIT_AUTHOR_EMAIL='profile@example.invalid',
                       GIT_COMMITTER_NAME='Profile Test', GIT_COMMITTER_EMAIL='profile@example.invalid')
            subprocess.run(['git', '-C', str(repo), 'add', 'src/Example.java', 'artifacts/jacoco.exec'],
                           check=True, env=env)
            subprocess.run(['git', '-C', str(repo), 'commit', '-qm', 'base'], check=True, env=env)
            base = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
            profile = load_profile({'schema_version': 1, 'gates': [
                {'id': 'full-test', 'command': 'python3 -m unittest full', 'inputs': ['src/**'],
                 'applicability': ['**'], 'mandatory': True, 'cacheable': True,
                 'produces': ['artifacts/jacoco.exec']},
                {'id': 'coverage', 'command': 'python3 -m unittest coverage', 'inputs': ['src/**'],
                 'applicability': ['**'], 'depends_on': ['full-test'],
                 'consumes': [{'producer': 'full-test', 'path': 'artifacts/jacoco.exec'}],
                 'mandatory': True, 'cacheable': True},
            ]})
            family = Family('showcase-artifact-family', base, 'integration',
                            profile.content_hash, 'f' * 64)

            initial = build_plan(repo, profile, family)
            full = next(item for item in initial.decisions if item.node.id == 'full-test')
            full_evidence = seal_pass(full, full, family=family, evidence_id='full-test-pass',
                ownership_token='attempt-1', started_at=1, ended_at=2,
                artifacts=observe_artifacts(repo, ('artifacts/jacoco.exec',)))
            after_full = build_plan(repo, profile, family, evidence={'full-test': full_evidence})
            coverage = next(item for item in after_full.decisions if item.node.id == 'coverage')
            coverage_evidence = seal_pass(coverage, coverage, family=family,
                evidence_id='coverage-pass', ownership_token='attempt-1', started_at=3, ended_at=4,
                artifacts=())
            reusable = build_plan(repo, profile, family,
                evidence={'full-test': full_evidence, 'coverage': coverage_evidence})
            self.assertEqual('REUSE', next(item for item in reusable.decisions
                if item.node.id == 'coverage').action)

            artifact.write_bytes(b'focused-test-overwrite')
            stale = build_plan(repo, profile, family,
                evidence={'full-test': full_evidence, 'coverage': coverage_evidence})
            stale_full = next(item for item in stale.decisions if item.node.id == 'full-test')
            stale_coverage = next(item for item in stale.decisions if item.node.id == 'coverage')
            self.assertEqual(('RUN', 'artifact-mismatch'), (stale_full.action, stale_full.reason))
            self.assertEqual('RUN', stale_coverage.action)
            self.assertFalse(stale_coverage.dependencies_reusable)


if __name__ == '__main__':
    unittest.main()
