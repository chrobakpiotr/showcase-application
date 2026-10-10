import itertools
import json
from unittest import mock
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from verification.model import Artifact, Gate, InvalidPolicy, Probe
from verification.profile import load_profile, matches
from verification.serialization import SafetyPolicy, safe_record


def profile(*gates, safety=None):
    return load_profile({'schema_version': 1, 'gates': list(gates)}, safety=safety)


def gate(id='unit', **changes):
    return dict({'id': id, 'command': 'python3 -m unittest', 'inputs': ['src/**'],
                 'mandatory': True, 'cacheable': True,
                 'required_origin': 'independent',
                 'independent_execution_classes': ['harness-managed-independent-execution-v1'],
                 'independent_registration_classes': []}, **changes)


class ProfileTest(unittest.TestCase):
    def test_profile_schema_fields_match_runtime_gate_loader(self):
        schema_path = pathlib.Path(__file__).resolve().parents[1] / 'schemas' / 'verification-profile.schema.json'
        schema = json.loads(schema_path.read_text(encoding='utf-8'))
        runtime_fields = set(Gate.__dataclass_fields__) - {'command_hash'}
        gate_schema = schema['properties']['gates']['items']
        self.assertFalse(schema['additionalProperties'])
        self.assertEqual({'schema_version', 'gates'}, set(schema['properties']))
        self.assertEqual({'schema_version', 'gates'}, set(schema['required']))
        self.assertEqual(1, schema['properties']['schema_version']['const'])
        self.assertFalse(gate_schema['additionalProperties'])
        self.assertEqual(runtime_fields, set(gate_schema['properties']))
        self.assertEqual({'id', 'command', 'inputs', 'required_origin',
                          'independent_execution_classes', 'independent_registration_classes'},
                         set(gate_schema['required']))
        probes = gate_schema['properties']['probes']['items']
        self.assertFalse(probes['additionalProperties'])
        self.assertEqual(set(Probe.__dataclass_fields__), set(probes['properties']))
        self.assertEqual({'id', 'format'}, set(probes['required']))
        consumes = gate_schema['properties']['consumes']['items']
        self.assertFalse(consumes['additionalProperties'])
        self.assertEqual(set(Artifact.__dataclass_fields__), set(consumes['properties']))
        self.assertEqual({'producer', 'path'}, set(consumes['required']))

        actual = json.loads((pathlib.Path(__file__).resolve().parents[1]
                             / 'verification-profiles' / 'showcase.json').read_text(encoding='utf-8'))
        actual['gates'][0]['obsolete_policy_field'] = True
        with self.assertRaises(InvalidPolicy):
            load_profile(actual)

    def test_glob_table(self):
        for pattern, path, expected in [
            ('src/**/x?.py', 'src/x1.py', True), ('src/**/x?.py', 'src/a/b/x1.py', True),
            ('src/*', 'src/a/b', False), ('src/*', 'src/', False),
            ('**', 'a b/雪.py', True), ('*.py', 'A.PY', False),
            ('a/**', 'a', True), ('a**b', 'ab', False),
        ]:
            with self.subTest(pattern=pattern, path=path):
                if pattern == 'a**b':
                    with self.assertRaises(InvalidPolicy): matches(pattern, path)
                else: self.assertEqual(expected, matches(pattern, path))
        for bad in ['/a', '../a', 'a/../b', 'a\\b', '[ab]', '{a,b}', 'a//b']:
            with self.subTest(bad=bad), self.assertRaises(InvalidPolicy): matches(bad, 'a')

    def test_reject_ambiguous_and_cyclic_profiles(self):
        for gates in [(gate(), gate()), (gate('a'), gate('b')),
                      (gate('a', depends_on=['b']), gate('b', command='python3 -V', depends_on=['a'])),
                      (gate(depends_on=['absent']),), (gate(unknown=True),),
                      (gate(probes={}),), (gate(consumes={}),), (gate(cacheable='true'),)]:
            with self.subTest(gates=gates), self.assertRaises(InvalidPolicy): profile(*gates)

    def test_command_and_cwd_are_exact(self):
        p = profile(gate('a'), gate('b', command='python3  -m unittest'),
                    gate('c', cwd='sub'))
        self.assertEqual(3, len({g.command_hash for g in p.gates}))

    def test_origin_policy_is_explicit_and_manual_principal_is_registry_bound(self):
        from verification.profile import registered_manual_principal
        registry = {'schema_version': 1, 'issuers': [
            {'issuer_id': 'issuer-a', 'reviewer_principal': 'human:alice',
             'enabled': True, 'revoked': False, 'actions': ['manual-review']}]}
        raw = gate(required_origin='manual', independent_execution_classes=[],
                   independent_registration_classes=[],
                   required_manual_reviewer_principal='human:alice')
        missing_origin = gate()
        missing_origin.pop('required_origin')
        with tempfile.TemporaryDirectory() as temporary:
            source = pathlib.Path(temporary) / 'profile.json'
            source.write_text(json.dumps({'schema_version': 1, 'gates': [missing_origin]}))
            with self.assertRaises(InvalidPolicy):
                load_profile(source)
        with self.assertRaises(InvalidPolicy):
            profile(gate(required_origin='manual', independent_execution_classes=[],
                         independent_registration_classes=[]))
        accepted = load_profile({'schema_version': 1, 'gates': [raw]},
                                manual_issuer_registry=registry)
        self.assertEqual('human:alice', accepted.gates[0].required_manual_reviewer_principal)
        self.assertEqual('human:alice', registered_manual_principal(registry, 'human:alice'))

    def test_origin_policy_rejects_unqualified_or_ambiguous_manual_authority(self):
        enabled = {'issuer_id': 'issuer-a', 'reviewer_principal': 'human:alice',
                   'enabled': True, 'revoked': False, 'actions': ['manual-review']}
        manual_gate = gate(required_origin='manual', independent_execution_classes=[],
                           independent_registration_classes=[],
                           required_manual_reviewer_principal='human:alice')
        invalid = [
            (manual_gate, None),
            (dict(manual_gate, independent_registration_classes=['manual-review']),
             {'schema_version': 1, 'issuers': [enabled]}),
            (gate(required_origin='task'), {'schema_version': 1, 'issuers': [enabled]}),
            (manual_gate, {'schema_version': 1, 'issuers': [enabled, dict(enabled, issuer_id='issuer-b')]}),
            (manual_gate, {'schema_version': 1, 'issuers': [dict(enabled, enabled=False)]}),
            (manual_gate, {'schema_version': 1, 'issuers': [dict(enabled, revoked=True)]}),
            (manual_gate, {'schema_version': 1, 'issuers': [dict(enabled, actions=['critical-gate-retry'])]}),
        ]
        for value, registry in invalid:
            with self.subTest(value=value, registry=registry), self.assertRaises(InvalidPolicy):
                load_profile({'schema_version': 1, 'gates': [value]},
                             manual_issuer_registry=registry)

    def test_nonmanual_principal_and_incomplete_independent_policy_are_rejected(self):
        registry = {'schema_version': 1, 'issuers': [
            {'issuer_id': 'issuer-a', 'reviewer_principal': 'human:alice',
             'enabled': True, 'revoked': False, 'actions': ['manual-review']}]}
        cases = [
            gate(required_origin='task', required_manual_reviewer_principal='human:alice'),
            gate(required_origin='independent', independent_execution_classes=[]),
            gate(required_origin='independent', sandbox='off'),
        ]
        for value in cases:
            with self.subTest(value=value), self.assertRaises(InvalidPolicy):
                load_profile({'schema_version': 1, 'gates': [value]},
                             manual_issuer_registry=registry)

    def test_persisted_manual_profile_requires_explicit_origin_and_principal(self):
        manual = gate(required_origin='manual', independent_execution_classes=[],
                      required_manual_reviewer_principal='human:alice')
        registry = {'schema_version': 1, 'issuers': [
            {'issuer_id': 'issuer-a', 'reviewer_principal': 'human:alice',
             'enabled': True, 'revoked': False, 'actions': ['manual-review']}]}
        for field in ('required_origin', 'required_manual_reviewer_principal'):
            candidate = dict(manual)
            candidate.pop(field)
            with tempfile.TemporaryDirectory() as temporary:
                path = pathlib.Path(temporary) / 'profile.json'
                path.write_text(json.dumps({'schema_version': 1, 'gates': [candidate]}))
                with self.subTest(field=field), self.assertRaises(InvalidPolicy):
                    load_profile(path, manual_issuer_registry=registry)

    def test_profile_hash_binds_manual_principal_and_per_gate_origin(self):
        registry = {'schema_version': 1, 'issuers': [
            {'issuer_id': 'issuer-a', 'reviewer_principal': 'human:alice',
             'enabled': True, 'revoked': False, 'actions': ['manual-review']},
            {'issuer_id': 'issuer-b', 'reviewer_principal': 'human:bob',
             'enabled': True, 'revoked': False, 'actions': ['manual-review']}]}
        def load(origin, principal=None):
            raw = gate(required_origin=origin,
                       independent_execution_classes=(['harness-managed-independent-execution-v1']
                                                      if origin == 'independent' else []),
                       independent_registration_classes=[])
            if principal is not None:
                raw['required_manual_reviewer_principal'] = principal
            return load_profile({'schema_version': 1, 'gates': [raw]},
                                manual_issuer_registry=registry)
        baseline = load('manual', 'human:alice')
        self.assertNotEqual(baseline.content_hash, load('manual', 'human:bob').content_hash)
        self.assertNotEqual(baseline.content_hash, load('independent').content_hash)

    def test_mapping_profile_hash_binds_effective_default_origin_policy(self):
        implicit = load_profile({'schema_version': 1, 'gates': [{
            'id': 'task-gate', 'command': 'python3 -V', 'inputs': [], 'sandbox': 'off'}]})
        explicit = load_profile({'schema_version': 1, 'gates': [{
            'id': 'task-gate', 'command': 'python3 -V', 'inputs': [], 'sandbox': 'off',
            'required_origin': 'task', 'independent_execution_classes': [],
            'independent_registration_classes': []}]})
        self.assertEqual('task', implicit.gates[0].required_origin)
        self.assertEqual(implicit.content_hash, explicit.content_hash)

    def test_untrusted_task_command_cannot_upgrade_profile_gate_origin(self):
        from verification.model import Family
        from verification.planner import required_nodes
        trusted = load_profile({'schema_version': 1, 'gates': [gate(required_origin='independent')]})
        family = Family('origin-test', 'a' * 40, 'task-completion', trusted.content_hash, 'b' * 64)
        nodes = required_nodes(trusted, family, type('Surface', (), {'paths': ('src/main.py',)})(),
                               task_commands=['python3 -m unittest'])
        self.assertEqual(1, sum(node.occurrence for node in nodes))
        self.assertEqual(1, sum(not node.occurrence for node in nodes))

    def test_invalid_artifact_overlap_and_producer(self):
        for gates in [(gate(produces=['src/result']),),
                      (gate(consumes=[{'producer': 'absent', 'path': 'out/data'}]),),
                      (gate(produces=['../escape']),), (gate(inputs=['out/data/**'], produces=['out/data']),)]:
            with self.assertRaises(InvalidPolicy): profile(*gates)

    def test_adversarial_globs_use_bounded_segment_matching(self):
        from verification import profile as matching
        # Historical RED: 16 repetitions exceeded two seconds. Prevent regex
        # dispatch as a deterministic oracle instead of a wall-clock deadline.
        with mock.patch.object(matching.re, 'compile', side_effect=AssertionError('regex glob')):
            for n in (16, 20, 24, 64):
                self.assertFalse(matches('*a' * n + 'b', 'a' * (2 * n)))
                self.assertTrue(matches('*a' * n + 'b', 'a' * (2 * n) + 'b'))

    def test_segment_match_semantics_exhaustively(self):
        from functools import lru_cache
        @lru_cache(None)
        def reference(pattern, text):
            if not pattern: return not text
            if pattern[0] == '*':
                return reference(pattern[1:], text) or bool(text and reference(pattern, text[1:]))
            return bool(text and (pattern[0] == '?' or pattern[0] == text[0])
                        and reference(pattern[1:], text[1:]))
        for size in range(1, 5):
            for parts in itertools.product('ab*?', repeat=size):
                pattern = ''.join(parts)
                if '**' in pattern: continue
                for length in range(1, 5):
                    for letters in itertools.product('ab', repeat=length):
                        text = ''.join(letters)
                        self.assertEqual(reference(pattern, text), matches(pattern, text), (pattern, text))

    def test_safe_boundary(self):
        safety = SafetyPolicy(('CANARY-secret-12345',))
        for field in ['command', 'id', 'description']:
            with self.subTest(field=field), self.assertRaises(InvalidPolicy):
                profile(gate(**{field: 'CANARY-secret-12345'}), safety=safety)
        for value in [{'stdout': 'anything'}, {'usage': {'evil': 'CANARY-secret-12345'}},
                      {'reason': 'CANARY-secret-12345'}, {'command': 'python3 -V'}]:
            with self.assertRaises(ValueError): safe_record(value, safety=safety)
        self.assertEqual({'status': 'pass', 'duration_seconds': 1.2},
                         safe_record({'status': 'pass', 'duration_seconds': 1.2}, safety=safety))


if __name__ == '__main__': unittest.main()
