import pathlib
import os
import sys
import tempfile
import threading
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from verification.admission import AdmissionConflict, RepositoryAdmission
from verification.admission import process_start_token


class RepositoryAdmissionTest(unittest.TestCase):
    def test_verification_reservation_binds_pid_and_process_start_token(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            active = admission.reserve_verification('exec-1', {})['active']
            self.assertEqual(os.getpid(), active['context']['owner_pid'])
            self.assertEqual(process_start_token(os.getpid()), active['context']['owner_start_time'])

    def test_recovery_accepts_pid_reuse_only_when_start_token_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            admission.reserve_verification('exec-1', {})
            with mock.patch('verification.admission.process_start_token',
                            return_value='different-process-instance'):
                self.assertTrue(admission.release_unstarted_if_owner_dead('exec-1'))
            self.assertIsNone(admission.active())

    def test_recovery_does_not_release_when_process_instance_is_still_alive_or_unknown(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            admission.reserve_verification('exec-1', {})
            with mock.patch('verification.admission.process_start_token',
                            return_value=process_start_token(os.getpid())):
                self.assertFalse(admission.release_unstarted_if_owner_dead('exec-1'))
            self.assertEqual('exec-1', admission.active()['id'])

    def test_verifier_reservation_and_mutation_claim_are_mutually_exclusive(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            admission.reserve_verification('exec-1', {'plan_id': 'plan-1'})
            with self.assertRaisesRegex(AdmissionConflict, 'verification-owned'):
                admission.reserve_mutation('mutation-1', {'operation': 'claim'})
            admission.release('exec-1')
            admission.reserve_mutation('mutation-1', {'operation': 'claim'})
            with self.assertRaisesRegex(AdmissionConflict, 'mutation-owned'):
                admission.reserve_verification('exec-2', {'plan_id': 'plan-2'})

    def test_crash_left_reservation_survives_restart_and_requires_exact_release(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            RepositoryAdmission(root, 'repo-1').reserve_verification('exec-1', {'plan_id': 'plan-1'})
            restarted = RepositoryAdmission(root, 'repo-1')
            with self.assertRaisesRegex(AdmissionConflict, 'verification-owned'):
                restarted.reserve_verification('exec-2', {'plan_id': 'plan-2'})
            with self.assertRaisesRegex(AdmissionConflict, 'reservation-mismatch'):
                restarted.release('exec-2')
            restarted.release('exec-1')

    def test_verification_waits_for_atomic_lifecycle_mutation_then_reserves(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            mutation_entered = threading.Event()
            allow_mutation_commit = threading.Event()
            verification_reserved = threading.Event()
            failures = []

            def mutate():
                try:
                    with admission.mutation():
                        mutation_entered.set()
                        self.assertTrue(allow_mutation_commit.wait(2))
                except BaseException as exc:
                    failures.append(exc)

            def verify():
                try:
                    self.assertTrue(mutation_entered.wait(2))
                    admission.reserve_verification('exec-1', {'plan_id': 'plan-1'})
                    verification_reserved.set()
                except BaseException as exc:
                    failures.append(exc)

            mutation_thread = threading.Thread(target=mutate)
            verification_thread = threading.Thread(target=verify)
            mutation_thread.start()
            verification_thread.start()
            self.assertTrue(mutation_entered.wait(2))
            self.assertFalse(verification_reserved.wait(0.05))
            allow_mutation_commit.set()
            mutation_thread.join(2)
            verification_thread.join(2)
            self.assertFalse(failures)
            self.assertTrue(verification_reserved.is_set())
            self.assertEqual('exec-1', admission.active()['id'])

    def test_corrupt_or_symlinked_admission_authority_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            admission = RepositoryAdmission(root, 'repo-1')
            admission.path.write_text('{broken')
            with self.assertRaisesRegex(AdmissionConflict, 'admission-authority-invalid'):
                admission.reserve_verification('exec-1', {})
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            outside = root / 'outside.json'
            outside.write_text('{"schema_version":1,"repository_id":"repo-1","generation":0,"active":null}')
            admission = RepositoryAdmission(root, 'repo-1')
            admission.path.symlink_to(outside)
            with self.assertRaisesRegex(AdmissionConflict, 'admission-authority-invalid'):
                admission.reserve_mutation('mutation-1', {})

    def test_verification_authority_validation_and_reservation_share_one_guard(self):
        with tempfile.TemporaryDirectory() as temp:
            admission = RepositoryAdmission(pathlib.Path(temp), 'repo-1')
            with self.assertRaisesRegex(ValueError, 'stale-plan'):
                admission.reserve_verification('exec-1', {'plan_id': 'plan-1'},
                                               validate=lambda: (_ for _ in ()).throw(ValueError('stale-plan')))
            self.assertIsNone(admission.active())
            checked = []
            admission.reserve_verification('exec-1', {'plan_id': 'plan-1'},
                                           validate=lambda: checked.append('validated'))
            self.assertEqual(['validated'], checked)
            self.assertEqual('exec-1', admission.active()['id'])


if __name__ == '__main__':
    unittest.main()
