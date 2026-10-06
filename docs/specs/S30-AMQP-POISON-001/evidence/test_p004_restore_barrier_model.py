import unittest

from p004_restore_barrier import RestoreCoordinator


class RestoreBarrierTest(unittest.TestCase):
    def test_lost_notification_blocks_restore_for_both_candidates(self):
        for candidate in ("readonly-record", "gate-api"):
            with self.subTest(candidate=candidate):
                coordinator = RestoreCoordinator(candidate, ["gate-a", "gate-b"], ["app-a", "app-b"])
                episode = coordinator.write_host_inhibit()
                coordinator.notify("gate-a", delivered=True)
                coordinator.notify("gate-b", delivered=False)
                coordinator.drain("app-a", confirmed=True)
                coordinator.drain("app-b", confirmed=True)
                self.assertFalse(coordinator.restore_allowed(episode))
                if candidate == "readonly-record":
                    self.assertFalse(coordinator.may_issue_permit("gate-a"))
                    self.assertFalse(coordinator.may_issue_permit("gate-b"))
                else:
                    # A delayed/lost API request may leave the gate active, but
                    # the wrapper's all-issuer barrier prevents restore.
                    self.assertTrue(coordinator.may_issue_permit("gate-b"))

    def test_every_issuer_and_app_must_confirm_exact_episode_before_restore(self):
        for candidate in ("readonly-record", "gate-api"):
            with self.subTest(candidate=candidate):
                coordinator = RestoreCoordinator(candidate, ["gate-a", "gate-b"], ["app-a", "app-b"])
                episode = coordinator.write_host_inhibit()
                for gate in coordinator.gates:
                    coordinator.notify(gate, delivered=True)
                coordinator.drain("app-a", confirmed=True)
                self.assertFalse(coordinator.restore_allowed(episode))
                coordinator.drain("app-b", confirmed=True)
                self.assertTrue(coordinator.restore_allowed(episode))

    def test_restart_with_stale_store_cannot_issue_until_host_episode_is_observed(self):
        for candidate in ("readonly-record", "gate-api"):
            with self.subTest(candidate=candidate):
                coordinator = RestoreCoordinator(candidate, ["gate-a"], ["app-a"])
                episode = coordinator.write_host_inhibit()
                coordinator.restart_gate_from_stale_active_store("gate-a")
                self.assertFalse(coordinator.may_issue_permit("gate-a"))
                coordinator.notify("gate-a", delivered=True)
                self.assertEqual(episode, coordinator.observed_episode["gate-a"])
                self.assertFalse(coordinator.may_issue_permit("gate-a"))

    def test_stale_resume_and_host_release_cannot_clear_new_episode(self):
        coordinator = RestoreCoordinator("gate-api", ["gate-a"], ["app-a"])
        old_episode = coordinator.write_host_inhibit()
        coordinator.notify("gate-a", delivered=True)
        coordinator.drain("app-a", confirmed=True)
        self.assertTrue(coordinator.restore_allowed(old_episode))
        new_episode = coordinator.write_host_inhibit()
        self.assertNotEqual(old_episode, new_episode)
        self.assertFalse(coordinator.release_host_inhibit(old_episode, audited_resume=True))
        # Gate API delivery is a barrier: prior ACTIVE state can issue until
        # the new PAUSE is durably observed, but restore cannot proceed.
        self.assertTrue(coordinator.may_issue_permit("gate-a"))
        coordinator.notify("gate-a", delivered=True)
        self.assertFalse(coordinator.may_issue_permit("gate-a"))

    def test_partial_restore_restart_remains_inhibited(self):
        coordinator = RestoreCoordinator("gate-api", ["gate-a"], ["app-a"])
        episode = coordinator.write_host_inhibit()
        coordinator.notify("gate-a", delivered=True)
        coordinator.drain("app-a", confirmed=True)
        self.assertTrue(coordinator.restore_allowed(episode))
        # Model a crash after restoring one store; durable host inhibit survives.
        coordinator.restart_gate_from_stale_active_store("gate-a")
        self.assertFalse(coordinator.may_issue_permit("gate-a"))
        self.assertFalse(coordinator.restore_allowed(episode))

    def test_unavailable_host_record_fails_closed(self):
        for candidate in ("readonly-record", "gate-api"):
            with self.subTest(candidate=candidate):
                coordinator = RestoreCoordinator(candidate, ["gate-a"], ["app-a"])
                coordinator.write_host_inhibit()
                coordinator.notify("gate-a", delivered=True)
                coordinator.drain("app-a", confirmed=True)
                coordinator.host_control_available = False
                self.assertFalse(coordinator.restore_allowed(coordinator.episode))
                self.assertFalse(coordinator.may_issue_permit("gate-a"))

    def test_host_release_requires_audited_resume_and_all_acknowledgements(self):
        coordinator = RestoreCoordinator("gate-api", ["gate-a"], ["app-a"])
        episode = coordinator.write_host_inhibit()
        coordinator.notify("gate-a", delivered=True)
        coordinator.drain("app-a", confirmed=True)
        self.assertFalse(coordinator.release_host_inhibit(episode, audited_resume=False))
        self.assertTrue(coordinator.release_host_inhibit(episode, audited_resume=True))
        self.assertTrue(coordinator.may_issue_permit("gate-a"))


if __name__ == "__main__":
    unittest.main()
