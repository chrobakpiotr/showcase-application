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
from verification.planner import build_plan, required_nodes
from verification.authority import execution_plan_record
from verification.profile import load_profile, matches


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
                'tooling/agent-harness/verification/planner.py'),
            'agentic-sdd-doctor': ('docs/specs/SDD-OBS-001/tasks.json',),
            'showcase-gradle-build': (
                'modules/domain/domain.gradle',
                'apps/ecommerce/backend/src/main/java/com/cp/ecommerce/application/EcommerceApplication.java',
                'apps/ecommerce/backend/ecommerce.gradle',
                'tooling/quality/jacoco/jacoco.gradle',
                'tooling/quality/pitest/pitest.gradle',
                'tooling/quality/checkstyle/checkstyle.xml',
                'contracts/asyncapi/asyncapi.yml',
                'apps/ecommerce/frontend/src/app/app.component.ts',
                '.editorconfig',
                'lombok.config',
                'apps/ecommerce/backend/src/main/resources/application.yml'),
            'showcase-frontend-build': (
                'apps/ecommerce/frontend/package-lock.json',
                'apps/ecommerce/frontend/src/app/app.component.ts'),
            'domain-mutation-threshold': (
                'modules/domain/domain.gradle',
                'tooling/quality/pitest/pitest.gradle',
                'tooling/scripts/verify-domain-pitest.sh',
                'lombok.config'),
            'critical-postgres-regression': (
                'tooling/quality/critical-postgres-manifest.json',
                'apps/ecommerce/backend/src/test/java/com/cp/ecommerce/application/OrderReplayPostgresIntegrationTest.java',
                'modules/adapters/common/src/main/java/com/cp/ecommerce/adapter/common/validation/DefaultDomainObjectValidator.java',
                'modules/adapters/security/src/main/java/com/cp/ecommerce/adapter/security/authentication/CurrentOperatorProvider.java',
                'modules/adapters/web/src/main/java/com/cp/ecommerce/adapter/web/wishlist/resource/WishlistResource.java',
                'modules/adapters/mail/src/main/java/com/cp/ecommerce/adapter/mail/message/EmailMessageFactory.java',
                'modules/adapters/kafka/src/main/java/com/cp/ecommerce/adapter/kafka/configuration/KafkaHealthIndicator.java',
                'modules/adapters/camel/src/main/java/com/cp/ecommerce/adapter/camel/configuration/CamelPropertiesConfiguration.java',
                'modules/adapters/ai/src/main/java/com/cp/ecommerce/adapter/ai/analytics/AnalyticsAssistantAdapter.java',
                'modules/adapters/aws/aws.gradle',
                'apps/ecommerce/frontend/src/app/app.component.ts',
                'contracts/asyncapi/asyncapi.yml', 'lombok.config'),
            'critical-rabbitmq-regression': (
                'tooling/quality/critical-rabbitmq-manifest.json',
                'apps/ecommerce/backend/src/test/java/com/cp/ecommerce/application/RabbitMqFulfillmentDeliveryIntegrationTest.java',
                'modules/adapters/common/src/main/java/com/cp/ecommerce/adapter/common/validation/DefaultDomainObjectValidator.java',
                'modules/adapters/security/src/main/java/com/cp/ecommerce/adapter/security/authentication/CurrentOperatorProvider.java',
                'modules/adapters/web/src/main/java/com/cp/ecommerce/adapter/web/wishlist/resource/WishlistResource.java',
                'modules/adapters/mail/src/main/java/com/cp/ecommerce/adapter/mail/message/EmailMessageFactory.java',
                'modules/adapters/kafka/src/main/java/com/cp/ecommerce/adapter/kafka/configuration/KafkaHealthIndicator.java',
                'modules/adapters/camel/src/main/java/com/cp/ecommerce/adapter/camel/configuration/CamelPropertiesConfiguration.java',
                'modules/adapters/ai/src/main/java/com/cp/ecommerce/adapter/ai/analytics/AnalyticsAssistantAdapter.java',
                'modules/adapters/aws/aws.gradle',
                'apps/ecommerce/frontend/src/app/app.component.ts',
                'contracts/asyncapi/asyncapi.yml', 'lombok.config'),
        }
        self.assertEqual(set(expected), set(self.gates))
        family = Family('showcase-profile-completeness', '0' * 40, 'integration',
                        self.profile.content_hash, 'd' * 64)
        for gate_id, paths in expected.items():
            gate = self.gates[gate_id]
            with self.subTest(gate=gate_id):
                self.assertTrue(gate.mandatory)
                self.assertFalse(gate.cacheable)
                for path in paths:
                    self.assertTrue((ROOT / path).is_file(), path)
                    self.assertTrue(any(matches(pattern, path) for pattern in gate.inputs), path)
                    self.assertTrue(any(matches(pattern, path) for pattern in gate.applicability),
                                    f'{gate_id} will not be selected when {path} changes')
                    nodes = required_nodes(self.profile, family, SimpleNamespace(paths=(path,)))
                    self.assertIn(gate_id, {node.id for node in nodes},
                                  f'{gate_id} is not a required gate for changed input {path}')
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

    def test_quality_gate_commands_and_working_directories_are_explicit(self):
        expected = {
            'agent-harness-tests': ('python3 -m unittest discover -s tooling/agent-harness/tests -p test_*.py', '.'),
            'agentic-sdd-doctor': ('python3 tooling/agent-harness/harness.py doctor', '.'),
            'showcase-gradle-build': ('./gradlew build --continue', '.'),
            'showcase-frontend-build': ('npm run build', 'apps/ecommerce/frontend'),
            'domain-mutation-threshold': ('./tooling/scripts/verify-domain-pitest.sh', '.'),
            'critical-postgres-regression': ('./tooling/scripts/verify-critical-postgres-tests.sh', '.'),
            'critical-rabbitmq-regression': ('./tooling/scripts/verify-critical-rabbitmq-tests.sh', '.'),
        }
        self.assertEqual(set(expected), set(self.gates))
        for gate_id, (command, cwd) in expected.items():
            with self.subTest(gate=gate_id):
                self.assertEqual(command, self.gates[gate_id].command)
                self.assertEqual(cwd, self.gates[gate_id].cwd)

    def test_every_tracked_result_input_can_activate_its_gate(self):
        raw_paths = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z'])
        paths = [path.decode('utf-8') for path in raw_paths.split(b'\0') if path]
        for gate in self.profile.gates:
            uncovered = [path for path in paths
                if any(matches(pattern, path) for pattern in gate.inputs)
                and not any(matches(pattern, path) for pattern in gate.applicability)]
            with self.subTest(gate=gate.id):
                self.assertEqual([], uncovered,
                    f'declared result inputs do not select {gate.id}: {uncovered[:10]}')

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
                    task_commands=(command,) * count, origin_binding='task-completion')
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


if __name__ == '__main__':
    unittest.main()
