"""Abstract P-001 crash/retry schedules; stdlib only, not service evidence."""


class Gate:
    def __init__(self):
        self.leader_epoch = 1
        self.latch_epoch = 0
        self.pg_latch = "CLEAR"
        self.pg_generation = 0
        self.redis_epoch = 1
        self.redis_latch_epoch = 0
        self.redis_state = "ACTIVE"
        self.redis_generation = 0
        self.results = {}
        self.audit = []
        self.uncertain_commands = set()
        self.sticky_inhibit = False
        self.pending = None
        self.resume = None
        self.resume_requests = {}
        self.pause_episodes = {}
        self.pause_requests = {}

    def prepare_pause(self, command_id, expected_generation):
        prior_epoch = self.pause_episodes.get(command_id)
        if prior_epoch is not None:
            return prior_epoch if self.pause_requests[command_id] == expected_generation else None
        if command_id in self.results:
            return None
        self.latch_epoch += 1
        self.pg_latch = "RECOVERY_REQUIRED"
        self.pending = command_id
        self.resume = None
        self.sticky_inhibit = True
        self.pause_episodes[command_id] = self.latch_epoch
        self.pause_requests[command_id] = expected_generation
        return self.latch_epoch

    def pause(self, command_id, leader_epoch, expected_generation, lose_reply=False):
        if self.pause_requests.get(command_id) != expected_generation:
            return "COMMAND_ID_REUSED"
        prior = self.results.get(command_id)
        if prior:
            if prior[:3] != ("PAUSED", self.pause_episodes.get(command_id), expected_generation):
                return "COMMAND_ID_REUSED"
            self.audit.append((command_id, "PAUSED", "RETRY"))
            if lose_reply:
                self.uncertain_commands.add(command_id)
            else:
                self.uncertain_commands.discard(command_id)
            return "UNKNOWN" if lose_reply else "APPLIED"
        if leader_epoch != self.redis_epoch or expected_generation != self.redis_generation:
            return "CONFLICT"
        if command_id != self.pending or self.pg_latch != "RECOVERY_REQUIRED":
            return "STALE_LATCH"
        self.redis_generation += 1
        self.redis_state = "PAUSED"
        self.redis_latch_epoch = self.latch_epoch
        self.results[command_id] = ("PAUSED", self.latch_epoch, expected_generation, self.redis_generation)
        self.audit.append((command_id, "PAUSED", "APPLIED"))
        if lose_reply:
            self.uncertain_commands.add(command_id)
        else:
            self.uncertain_commands.discard(command_id)
        return "UNKNOWN" if lose_reply else "APPLIED"

    def prepare_resume(self, command_id, latch_epoch, expected_generation, barrier_generation):
        requested = (latch_epoch, expected_generation, barrier_generation)
        prior = self.results.get(command_id)
        if prior:
            return prior[0] == "ACTIVE" and self.resume_requests.get(command_id) == requested
        if self.pg_latch != "RECOVERY_REQUIRED" or latch_epoch != self.latch_epoch:
            return False
        if expected_generation != self.redis_generation or barrier_generation != self.redis_generation:
            return False
        self.resume_requests[command_id] = requested
        self.resume = (command_id, *requested)
        return True

    def resume_transition(self, command_id, leader_epoch, latch_epoch, expected_generation,
                          barrier_generation, lose_reply=False):
        requested = (latch_epoch, expected_generation, barrier_generation)
        prior = self.results.get(command_id)
        if prior:
            if prior[0] != "ACTIVE" or self.resume_requests.get(command_id) != requested:
                return "COMMAND_ID_REUSED"
            self.audit.append((command_id, "ACTIVE", "RETRY"))
            if lose_reply:
                self.uncertain_commands.add(command_id)
            else:
                self.uncertain_commands.discard(command_id)
            return "UNKNOWN" if lose_reply else "APPLIED"
        if leader_epoch != self.redis_epoch or expected_generation != self.redis_generation:
            return "CONFLICT"
        if latch_epoch != self.redis_latch_epoch:
            return "STALE_LATCH"
        if self.resume != (command_id, latch_epoch, expected_generation, barrier_generation):
            return "UNPREPARED_RESUME"
        self.redis_generation += 1
        self.redis_state = "ACTIVE"
        self.results[command_id] = ("ACTIVE", latch_epoch, expected_generation,
                                    self.redis_generation, barrier_generation)
        self.audit.append((command_id, "ACTIVE", "APPLIED"))
        if lose_reply:
            self.uncertain_commands.add(command_id)
        else:
            self.uncertain_commands.discard(command_id)
        return "UNKNOWN" if lose_reply else "APPLIED"

    def delayed_old_resume(self, command_id, leader_epoch):
        if leader_epoch != self.redis_epoch:
            return "STALE_EPOCH"
        self.redis_generation += 1
        self.redis_state = "ACTIVE"
        self.results[command_id] = ("ACTIVE", self.redis_latch_epoch,
                                    self.redis_generation - 1, self.redis_generation)
        self.audit.append((command_id, "ACTIVE", "DELAYED_OLD"))
        self.uncertain_commands.add(command_id)
        return "APPLIED"

    def takeover(self):
        self.leader_epoch += 1
        self.latch_epoch += 1
        self.pg_latch = "RECOVERY_REQUIRED"
        self.pending = f"boot-{self.leader_epoch}"
        self.resume = None
        self.sticky_inhibit = True
        return self.leader_epoch

    def install_epoch(self, epoch):
        if epoch <= self.redis_epoch:
            return False
        self.redis_epoch = epoch
        return True

    def reconcile_generation(self):
        self.pg_generation = self.redis_generation

    def finalize_active(self, *, command_id, latch_epoch, result_generation,
                        expected_generation, barrier_generation, drained):
        result = self.results.get(command_id)
        if not result or result[0] != "ACTIVE" or result[1] != latch_epoch:
            return False
        if self.resume_requests.get(command_id) != (
                latch_epoch, expected_generation, barrier_generation):
            return False
        if result[2] != expected_generation or result[4] != barrier_generation:
            return False
        if latch_epoch != self.latch_epoch or self.redis_latch_epoch != latch_epoch:
            return False
        if result_generation != self.redis_generation or result[3] != result_generation:
            return False
        if not drained or command_id in self.uncertain_commands:
            return False
        if self.pg_latch != "RECOVERY_REQUIRED":
            return False
        if self.leader_epoch != self.redis_epoch or self.pg_generation != self.redis_generation:
            return False
        if self.redis_state != "ACTIVE":
            return False
        self.pg_latch = "CLEAR"
        self.sticky_inhibit = False
        return True

    def may_issue_permit(self):
        return (
            self.pg_latch == "CLEAR" and not self.sticky_inhibit
            and self.leader_epoch == self.redis_epoch
            and self.latch_epoch == self.redis_latch_epoch
            and self.pg_generation == self.redis_generation
            and self.redis_state == "ACTIVE" and not self.uncertain_commands
        )


def check(name, actual, expected):
    assert actual == expected, f"{name}: expected {expected!r}, got {actual!r}"
    print(f"PASS: {name}")


def main():
    # A delayed old RESUME may land before new epoch install. A newer latch
    # epoch must prevent its result from clearing the recovery latch.
    gate = Gate()
    gate.prepare_pause("pause-1", 0)
    gate.pause("pause-1", 1, 0, lose_reply=True)
    check("uncertain PAUSE cannot issue permit", gate.may_issue_permit(), False)
    gate.delayed_old_resume("resume-old", 1)
    old_generation = gate.redis_generation
    check("new leader epoch advances", gate.takeover(), 2)
    check("new Redis epoch installs", gate.install_epoch(2), True)
    check("old leader write after install is rejected", gate.delayed_old_resume("late-old", 1), "STALE_EPOCH")
    gate.reconcile_generation()
    check("stale RESUME cannot clear newer latch", gate.finalize_active(
        command_id="resume-old", latch_epoch=1, result_generation=old_generation,
        expected_generation=1, barrier_generation=1, drained=True), False)
    check("stale RESUME cannot issue permit", gate.may_issue_permit(), False)

    # A same-command PAUSE retry reuses its latch epoch and resolves a lost
    # reply without applying the state transition or generation twice.
    gate = Gate()
    first_pause_epoch = gate.prepare_pause("pause-uncertain", 0)
    gate.pause("pause-uncertain", 1, 0, lose_reply=True)
    generation = gate.redis_generation
    check("uncertain PAUSE retry reuses latch epoch",
          gate.prepare_pause("pause-uncertain", 0), first_pause_epoch)
    check("uncertain PAUSE retry resolves stored result",
          gate.pause("pause-uncertain", 1, 0), "APPLIED")
    check("uncertain PAUSE retry does not advance generation", gate.redis_generation, generation)
    check("uncertain PAUSE retry records another attempt",
          gate.audit[-1], ("pause-uncertain", "PAUSED", "RETRY"))
    check("resolved PAUSE retry remains globally inhibited", gate.may_issue_permit(), False)

    # Retrying an old command after takeover resolves only its recorded result;
    # it cannot roll back or clear the newer recovery latch.
    gate = Gate()
    gate.prepare_pause("pause-before-takeover", 0)
    gate.pause("pause-before-takeover", 1, 0, lose_reply=True)
    old_latch_epoch = gate.pause_episodes["pause-before-takeover"]
    gate.takeover()
    gate.install_epoch(2)
    gate.reconcile_generation()
    check("post-takeover retry keeps original PAUSE epoch",
          gate.prepare_pause("pause-before-takeover", 0), old_latch_epoch)
    check("post-takeover exact PAUSE retry resolves old result",
          gate.pause("pause-before-takeover", 2, 0), "APPLIED")
    check("post-takeover retry leaves newer latch intact", gate.latch_epoch, old_latch_epoch + 1)
    check("post-takeover retry cannot issue permit", gate.may_issue_permit(), False)

    # The bad path found by independent evaluation: arbitrary ACTIVE state plus
    # a copied generation is not proof of a RESUME for the current latch.
    gate = Gate()
    gate.prepare_pause("pause-2", 0)
    gate.delayed_old_resume("unbound-resume", 1)
    gate.reconcile_generation()
    check("unbound ACTIVE result cannot clear latch", gate.finalize_active(
        command_id="unbound-resume", latch_epoch=0, result_generation=1,
        expected_generation=0, barrier_generation=0, drained=True), False)

    # A current RESUME must name the current latch and generation barrier.
    gate = Gate()
    gate.prepare_pause("pause-3", 0)
    check("changed expected generation rejected before Redis result",
          gate.pause("pause-3", 1, 1), "COMMAND_ID_REUSED")
    check("pre-result command reuse leaves Redis generation", gate.redis_generation, 0)
    check("changed expected generation cannot prepare same ID",
          gate.prepare_pause("pause-3", 1), None)
    check("current PAUSE applies", gate.pause("pause-3", 1, 0), "APPLIED")
    generation = gate.redis_generation
    audit_count = len(gate.audit)
    check("exact PAUSE retry returns stored result", gate.pause("pause-3", 1, 0), "APPLIED")
    check("exact retry advances no generation", gate.redis_generation, generation)
    check("exact retry appends retry audit", len(gate.audit), audit_count + 1)
    check("changed expected generation cannot reuse ID", gate.pause("pause-3", 1, 1), "COMMAND_ID_REUSED")
    check("wrong latch epoch cannot prepare RESUME", gate.prepare_resume("old", 0, 1, 1), False)
    check("current latch/generation prepares RESUME", gate.prepare_resume("resume-3", 1, 1, 1), True)
    check("uncertain RESUME reply stays unknown", gate.resume_transition(
        "resume-3", 1, 1, 1, 1, lose_reply=True), "UNKNOWN")
    gate.reconcile_generation()
    check("uncertain RESUME cannot clear latch", gate.finalize_active(
        command_id="resume-3", latch_epoch=1, result_generation=2,
        expected_generation=1, barrier_generation=1, drained=True), False)
    check("uncertain RESUME cannot issue permit", gate.may_issue_permit(), False)
    generation = gate.redis_generation
    check("same RESUME retry resolves stored outcome", gate.prepare_resume("resume-3", 1, 1, 1), True)
    check("RESUME retry does not advance generation", gate.resume_transition(
        "resume-3", 1, 1, 1, 1), "APPLIED")
    check("RESUME retry preserves generation", gate.redis_generation, generation)
    check("RESUME retry records another attempt", gate.audit[-1], ("resume-3", "ACTIVE", "RETRY"))
    gate.reconcile_generation()
    check("same-command durable retry can clear exact latch", gate.finalize_active(
        command_id="resume-3", latch_epoch=1, result_generation=2,
        expected_generation=1, barrier_generation=1, drained=True), True)
    check("resolved RESUME enables matching permit", gate.may_issue_permit(), True)

    # A successful retry for an older PAUSE must not resolve a different
    # uncertain RESUME command's durability result.
    gate = Gate()
    gate.prepare_pause("pause-old", 0)
    gate.pause("pause-old", 1, 0)
    gate.prepare_resume("resume-uncertain", 1, 1, 1)
    gate.resume_transition("resume-uncertain", 1, 1, 1, 1, lose_reply=True)
    check("historical PAUSE retry resolves only itself",
          gate.pause("pause-old", 1, 0), "APPLIED")
    gate.reconcile_generation()
    check("unrelated retry cannot clear uncertain RESUME", gate.finalize_active(
        command_id="resume-uncertain", latch_epoch=1, result_generation=2,
        expected_generation=1, barrier_generation=1, drained=True), False)
    check("unrelated retry cannot issue permit", gate.may_issue_permit(), False)

    gate = Gate()
    gate.prepare_pause("pause-4", 0)
    gate.pause("pause-4", 1, 0)
    gate.prepare_resume("resume-4", 1, 1, 1)
    check("drained RESUME applies", gate.resume_transition("resume-4", 1, 1, 1, 1), "APPLIED")
    gate.reconcile_generation()
    check("incomplete drain cannot clear latch", gate.finalize_active(
        command_id="resume-4", latch_epoch=1, result_generation=2,
        expected_generation=1, barrier_generation=1, drained=False), False)
    check("complete drain may finalize matching RESUME", gate.finalize_active(
        command_id="resume-4", latch_epoch=1, result_generation=2,
        expected_generation=1, barrier_generation=1, drained=True), True)
    prior_latch_epoch = gate.latch_epoch
    check("RESUME command ID cannot begin a PAUSE", gate.prepare_pause("resume-4", 0), None)
    check("reused command leaves latch epoch unchanged", gate.latch_epoch, prior_latch_epoch)
    check("reused command leaves active state unchanged", gate.pg_latch, "CLEAR")

    print("43 deterministic assertions passed; abstract schedules only.")


if __name__ == "__main__":
    main()
