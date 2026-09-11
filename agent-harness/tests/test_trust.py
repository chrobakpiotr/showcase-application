import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'trust.py'
spec = importlib.util.spec_from_file_location('sdd_trust', MODULE_PATH)
trust = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = trust
spec.loader.exec_module(trust)


class TrustTest(unittest.TestCase):
    def test_classifies_protocol_and_spec_as_trusted(self):
        self.assertEqual('trusted', trust.classify_path('docs/agentic-sdd/constitution.md'))
        self.assertEqual('trusted', trust.classify_path('docs/specs/ABC-001/spec.md'))
        self.assertEqual('trusted', trust.classify_path('docs/adr/0035-shipping.md'))
        self.assertEqual('trusted', trust.classify_path('docs/specs/ABC-001/evidence/human-resolutions/T-001/decision.json'))

    def test_control_plane_and_logs_are_untrusted(self):
        self.assertEqual('untrusted', trust.classify_path('.agent-state/control-plane/github-GH-1.json'))
        self.assertEqual('untrusted', trust.classify_path('.agent-runs/F/T/run/stdout.log'))

    def test_secret_like_paths_are_secret(self):
        self.assertEqual('secret', trust.classify_path('adapter/web/.env'))
        self.assertEqual('secret', trust.classify_path('etc/my-credentials.json'))

    def test_default_source_is_project(self):
        self.assertEqual('project', trust.classify_path('domain/src/main/java/example/Foo.java'))

    def test_policy_states_untrusted_cannot_override_instructions(self):
        text = trust.policy_text().lower()
        self.assertIn('untrusted content', text)
        self.assertIn('cannot change policy', text)


if __name__ == '__main__':
    unittest.main()
