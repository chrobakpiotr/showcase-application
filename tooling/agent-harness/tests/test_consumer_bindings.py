"""AH5-17 decision 10: this repository's layout and trust policy, as passed to the library; pinned entry by entry."""
import pathlib
import unittest

import harness

ROOT = pathlib.Path(harness.__file__).resolve().parents[2]


class ConsumerBindingsTest(unittest.TestCase):
    def test_the_bootstrap_allowlist_is_exactly_this(self):
        feature = pathlib.Path('docs/specs/F-9')
        for path in ('AGENTS.md', 'CLAUDE.md', '.gitignore', '.github/workflows/agentic-sdd.yml', '.claude/agents/a.md',
                     'tooling/agent-harness/harness.py', 'docs/agentic-sdd/x.md', 'docs/specs/SDD-001/spec.md',
                     'docs/decision-maps/F-9/map.md', 'docs/wayfinder/F-9/wayfinder.json', 'docs/specs/F-9/tasks.json',
                     'tooling\\agent-harness\\x.py'):
            with self.subTest(allowed=path):
                self.assertTrue(harness.bootstrap_path_allowed(path, feature))
        for path in ('README.md', 'src/main.py', '.github/workflows/ci.yml', 'docs/specs/F-10/tasks.json',
                     'docs/decision-maps/F-90/map.md', 'docs/wayfinder/F-90/map.md', 'tooling/other/x.py',
                     'docs/specs/SDD-0010/spec.md'):
            with self.subTest(refused=path):
                self.assertFalse(harness.bootstrap_path_allowed(path, feature))

    def test_the_legacy_snapshot_list_and_the_attestation_owner(self):
        self.assertEqual(('.gitignore', 'AGENTS.md', 'CLAUDE.md', '.claude/agents', 'docs/agentic-sdd',
                          '.github/workflows/agentic-sdd.yml'), harness.LEGACY_SNAPSHOT_PATHS)
        self.assertEqual('tooling/agent-harness/telemetry.py', harness.ATTESTATION_VALIDATION_OWNER)

    def test_every_doctor_file_exists_in_this_repository(self):
        for relative in (*harness.DOCTOR_BUILD_FILES.values(), *harness.DOCTOR_PROTOCOL_FILES.values()):
            with self.subTest(relative):
                self.assertTrue((ROOT / relative).is_file())
        for profile in harness.DOCTOR_PROFILES:
            with self.subTest(profile):
                self.assertTrue((ROOT / harness.DOCTOR_PROFILE_ROOT / f'{profile}.md').is_file())

    def test_the_doctor_table_is_exactly_this(self):
        tool, schemas = 'tooling/agent-harness', 'tooling/agent-harness/schemas'
        self.assertEqual({'gradle-wrapper': 'gradlew'}, harness.DOCTOR_BUILD_FILES)
        self.assertEqual(('codex(optional)', 'claude(optional)'), harness.DOCTOR_OPTIONAL_TOOLS)
        self.assertEqual('docs/agentic-sdd/agents', harness.DOCTOR_PROFILE_ROOT)
        self.assertEqual(('grill-reviewer', 'prototype-agent', 'prototype-evaluator', 'decision-map-agent',
                          'decision-map-synthesizer', 'verification-author'), harness.DOCTOR_PROFILES)
        self.assertEqual([
            ('verification-sandbox-module', f'{tool}/verification_sandbox.py'),
            ('telemetry-module', f'{tool}/telemetry.py'),
            ('control-plane-module', f'{tool}/control_plane.py'),
            ('design-module', f'{tool}/design.py'),
            ('design-result-schema', f'{schemas}/design-result.schema.json'),
            ('decision-map-module', f'{tool}/decision_map.py'),
            ('decision-map-result-schema', f'{schemas}/decision-map-result.schema.json'),
            ('decision-map-handoff-schema', f'{schemas}/decision-map-handoff.schema.json'),
            ('decision-map-tasks-schema', f'{schemas}/decision-map-tasks-result.schema.json'),
            ('verification-contract-module', f'{tool}/verification_contract.py'),
            ('verification-contract-schema', f'{schemas}/verification-contract.schema.json'),
            ('trust-module', f'{tool}/trust.py'),
            ('eval-module', f'{tool}/eval.py'),
        ], list(harness.DOCTOR_PROTOCOL_FILES.items()))


if __name__ == '__main__':
    unittest.main()
