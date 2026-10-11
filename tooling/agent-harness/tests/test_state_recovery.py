import hashlib
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / 'state_recovery.py'
spec = importlib.util.spec_from_file_location('sdd_state_recovery', MODULE_PATH)
recovery = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(recovery)


class StateRecoveryEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        subprocess.run(['git', 'config', 'user.name', 'Test'], cwd=self.root, check=True)
        subprocess.run(['git', 'config', 'user.email', 'test@example.invalid'], cwd=self.root, check=True)
        subprocess.run(['git', 'commit', '--allow-empty', '-q', '-m', 'baseline'], cwd=self.root, check=True)
        (self.root / 'docs/specs/SDD-OBS-001/evidence').mkdir(parents=True)
        (self.root / 'docs/specs/SDD-OBS-001/evidence/kept.json').write_bytes(b'{"safe":true}\n')
        self.baseline_dir = self.root / 'docs/specs/AGENT-STATE-RECOVERY-001/evidence'
        self.baseline_dir.mkdir(parents=True)
        raw = json.dumps({'entries': [{'path': 'docs/specs/SDD-OBS-001/evidence/kept.json',
                                       'bytes': len(b'{"safe":true}\n'),
                                       'sha256': hashlib.sha256(b'{"safe":true}\n').hexdigest(),
                                       'kind': 'file'}]}, sort_keys=True, indent=2).encode() + b'\n'
        (self.baseline_dir / 'evidence-baseline.json').write_bytes(raw)
        self.incident = {
            'schema_version': 1, 'feature': 'SDD-OBS-001', 'status': 'observed',
            'state_present': False, 'state_mutations_during_investigation': False,
            'cause': 'unknown', 'evidence_baseline_path':
                'docs/specs/AGENT-STATE-RECOVERY-001/evidence/evidence-baseline.json',
            'evidence_baseline_sha256': hashlib.sha256(raw).hexdigest(),
            'observation_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.root, text=True).strip(),
        }
        incident_path = recovery.incident_path(self.root)
        incident_path.parent.mkdir(parents=True, exist_ok=True)
        incident_path.write_text(json.dumps(self.incident), encoding='utf-8')
        subprocess.run(['git', 'add', 'docs'], cwd=self.root, check=True)
        subprocess.run(['git', 'commit', '-q', '-m', 'incident and baseline'], cwd=self.root, check=True)

    def tearDown(self):
        self.tmp.cleanup()

    def test_loads_scoped_incident_and_verifies_every_baseline_file(self):
        incident, _raw = recovery.load_incident(self.root, 'SDD-OBS-001')
        result = recovery.verify_evidence_baseline(self.root, incident)
        self.assertEqual(1, result['entries_verified'])
        self.assertTrue(recovery.observation_commit_exists(self.root, incident))

    def test_rejects_changed_evidence_before_recovery(self):
        incident, _raw = recovery.load_incident(self.root, 'SDD-OBS-001')
        (self.root / 'docs/specs/SDD-OBS-001/evidence/kept.json').write_bytes(b'{"changed":true}\n')
        with self.assertRaisesRegex(recovery.RecoveryError, 'evidence-baseline-file-changed'):
            recovery.verify_evidence_baseline(self.root, incident)

    def test_rejects_baseline_digest_mismatch(self):
        incident, _raw = recovery.load_incident(self.root, 'SDD-OBS-001')
        incident['evidence_baseline_sha256'] = '0' * 64
        with self.assertRaisesRegex(recovery.RecoveryError, 'evidence-baseline-digest-mismatch'):
            recovery.verify_evidence_baseline(self.root, incident)

    def test_rejects_baseline_path_escape(self):
        incident, _raw = recovery.load_incident(self.root, 'SDD-OBS-001')
        raw = json.loads((self.baseline_dir / 'evidence-baseline.json').read_text())
        raw['entries'][0]['path'] = 'docs/specs/SDD-OBS-001/evidence/../spec.md'
        payload = json.dumps(raw, sort_keys=True, indent=2).encode() + b'\n'
        (self.baseline_dir / 'evidence-baseline.json').write_bytes(payload)
        subprocess.run(['git', 'add', 'docs/specs/AGENT-STATE-RECOVERY-001/evidence/evidence-baseline.json'],
                       cwd=self.root, check=True)
        subprocess.run(['git', 'commit', '-q', '-m', 'invalid baseline fixture'], cwd=self.root, check=True)
        incident['evidence_baseline_sha256'] = hashlib.sha256(payload).hexdigest()
        with self.assertRaisesRegex(recovery.RecoveryError, 'evidence-baseline-invalid'):
            recovery.verify_evidence_baseline(self.root, incident)

    def test_rejects_incident_for_another_feature(self):
        with self.assertRaisesRegex(recovery.RecoveryError, 'incident-record-invalid'):
            recovery.load_incident(self.root, 'OTHER-FEATURE')


if __name__ == '__main__':
    unittest.main()
