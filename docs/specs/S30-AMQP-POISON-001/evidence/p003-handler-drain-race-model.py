#!/usr/bin/env python3
"""Disposable deterministic state model for P-003 local handler admission/drain."""

from dataclasses import dataclass
from itertools import permutations


PERMIT_TTL = 5


@dataclass
class Instance:
    gate_state: str = "ACTIVE"
    generation: int = 4
    instance_id: str = "instance-A"
    incarnation: int = 9
    registration_id: str = "registration-A-1"
    registration_generation: int = 4
    now: int = 100
    permit_issued_at: int = 100
    permit_expiry: int = 105
    channel_open: bool = True
    active: int = 0
    draining_ack: bool = False
    ready: bool = True
    local_draining: bool = False

    def snapshot_start(self):
        return (self.generation, self.instance_id, self.incarnation,
                self.registration_id, self.registration_generation,
                self.permit_issued_at, self.permit_expiry)

    def start_handler(self, snapshot):
        (generation, instance_id, incarnation, registration_id,
         registration_generation, issued_at, expiry) = snapshot
        if (not self.ready or self.local_draining or self.gate_state != "ACTIVE" or not self.channel_open
                or generation != self.generation
                or instance_id != self.instance_id
                or incarnation != self.incarnation
                or registration_id != self.registration_id
                or registration_generation != self.registration_generation
                or registration_generation != generation
                or self.now < issued_at or self.now >= expiry
                or expiry <= issued_at
                or expiry - issued_at > PERMIT_TTL):
            return False
        self.active += 1
        return True

    def pause(self):
        if self.gate_state == "ACTIVE":
            self.gate_state = "DRAINING"
            self.generation += 1
            self.ready = False
            self.local_draining = True

    def expire_permit(self):
        if self.now >= self.permit_expiry:
            self.ready = False
            self.local_draining = True

    def finish_handler(self):
        assert self.active > 0
        self.active -= 1

    def close_channel(self):
        assert self.local_draining
        assert self.active == 0
        self.channel_open = False

    def drain_claim(self):
        return (self.generation, self.instance_id, self.incarnation,
                self.registration_id, self.registration_generation)

    def acknowledge_drain(self, claim):
        (gate_generation, instance_id, incarnation, registration_id,
         registration_generation) = claim
        assert self.local_draining
        assert gate_generation == self.generation
        assert instance_id == self.instance_id
        assert incarnation == self.incarnation
        assert registration_id == self.registration_id
        assert registration_generation == self.registration_generation
        assert not self.channel_open and self.active == 0
        self.draining_ack = True


checks = 0


def check(condition, message):
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


def test_admission_pause_interleavings():
    # Snapshot and commit bracket PAUSE in every legal ordering. Commit is the
    # atomic admission point: a snapshot cannot reserve future admission.
    schedules = 0
    for schedule in permutations(("snapshot", "pause", "commit")):
        if schedule.index("snapshot") > schedule.index("commit"):
            continue
        schedules += 1
        instance = Instance()
        snapshot = None
        admitted = None
        for action in schedule:
            if action == "snapshot":
                snapshot = instance.snapshot_start()
            elif action == "pause":
                instance.pause()
            else:
                admitted = instance.start_handler(snapshot)
        check(admitted is (schedule.index("commit") < schedule.index("pause")),
              f"unexpected admission result for {schedule}")
        if schedule.index("pause") < schedule.index("commit"):
            check(instance.active == 0, f"handler started after DRAINING: {schedule}")
    check(schedules == 3, f"expected 3 legal schedules, got {schedules}")
    return schedules


def test_active_drain_order():
    instance = Instance()
    check(instance.start_handler(instance.snapshot_start()), "valid initial start rejected")
    instance.pause()
    check(instance.gate_state == "DRAINING", "PAUSE did not enter DRAINING")
    check(instance.active == 1, "PAUSE incorrectly discarded active handler")
    check(not instance.start_handler(instance.snapshot_start()),
          "new handler admitted while draining")
    rejected_close = False
    try:
        instance.close_channel()
    except AssertionError:
        rejected_close = True
    check(rejected_close and instance.channel_open,
          "channel close must wait for active handler completion")
    claim = instance.drain_claim()
    rejected_ack = False
    try:
        instance.acknowledge_drain(claim)
    except AssertionError:
        rejected_ack = True
    check(rejected_ack and not instance.draining_ack,
          "drain acknowledgement must wait for channel close")
    instance.finish_handler()
    check(instance.active == 0, "handler completion did not decrement active count")
    instance.close_channel()
    check(not instance.channel_open, "channel did not close after drain")

    stale_gate_claim = (claim[0] - 1, *claim[1:])
    rejected_stale_generation = False
    try:
        instance.acknowledge_drain(stale_gate_claim)
    except AssertionError:
        rejected_stale_generation = True
    check(rejected_stale_generation and not instance.draining_ack,
          "stale gate-generation drain acknowledgement accepted")

    foreign_registration_claim = (*claim[:3], "registration-foreign", *claim[4:])
    rejected_foreign_registration = False
    try:
        instance.acknowledge_drain(foreign_registration_claim)
    except AssertionError:
        rejected_foreign_registration = True
    check(rejected_foreign_registration and not instance.draining_ack,
          "foreign-registration drain acknowledgement accepted")

    stale_registration_generation = (*claim[:4], claim[4] - 1)
    rejected_stale_registration = False
    try:
        instance.acknowledge_drain(stale_registration_generation)
    except AssertionError:
        rejected_stale_registration = True
    check(rejected_stale_registration and not instance.draining_ack,
          "stale-registration-generation drain acknowledgement accepted")

    instance.acknowledge_drain(claim)
    check(instance.draining_ack, "drain acknowledgement missing after close")


def test_generation_and_incarnation_fences():
    stale_generation = Instance()
    snapshot = stale_generation.snapshot_start()
    stale_generation.generation += 1
    check(not stale_generation.start_handler(snapshot), "stale generation admitted")

    stale_incarnation = Instance()
    snapshot = stale_incarnation.snapshot_start()
    stale_incarnation.incarnation += 1
    check(not stale_incarnation.start_handler(snapshot), "stale incarnation admitted")

    # Keep all claims equal except instance ID to isolate that predicate.
    valid_snapshot = Instance().snapshot_start()
    foreign_instance = Instance(instance_id="instance-B")
    check(not foreign_instance.start_handler(valid_snapshot),
          "another instance's start snapshot admitted")

    stale_registration_id = Instance()
    snapshot = stale_registration_id.snapshot_start()
    stale_registration_id.registration_id = "registration-A-2"
    check(not stale_registration_id.start_handler(snapshot),
          "stale registration ID admitted")

    stale_registration_generation = Instance()
    snapshot = stale_registration_generation.snapshot_start()
    stale_registration_generation.registration_generation += 1
    check(not stale_registration_generation.start_handler(snapshot),
          "stale registration generation admitted")


def test_five_second_deadline():
    before = Instance(now=100, permit_issued_at=100, permit_expiry=105)
    snapshot = before.snapshot_start()
    before.now = 104
    check(before.start_handler(snapshot), "permit rejected before five-second expiry")

    at_deadline = Instance(now=100, permit_issued_at=100, permit_expiry=105)
    snapshot = at_deadline.snapshot_start()
    at_deadline.now = 105
    check(not at_deadline.start_handler(snapshot), "permit accepted at expiry boundary")

    after = Instance(now=100, permit_issued_at=100, permit_expiry=105)
    snapshot = after.snapshot_start()
    after.now = 106
    check(not after.start_handler(snapshot), "offline start accepted after expiry")

    overlong = Instance(now=100, permit_issued_at=100, permit_expiry=106)
    check(not overlong.start_handler(overlong.snapshot_start()),
          "permit validity window longer than five seconds accepted")

    future = Instance(now=99, permit_issued_at=100, permit_expiry=105)
    check(not future.start_handler(future.snapshot_start()),
          "permit accepted before its issued-at time")


def test_active_handler_outlives_permit():
    instance = Instance(now=100, permit_issued_at=100, permit_expiry=105)
    check(instance.start_handler(instance.snapshot_start()),
          "valid handler did not start before gate loss")
    instance.now = 105  # renewal is unavailable; local permit reaches its deadline
    instance.expire_permit()
    check(not instance.ready and instance.local_draining,
          "instance remained ready after permit expiry")
    check(instance.active == 1,
          "permit expiry incorrectly discarded an already active handler")
    check(not instance.start_handler(instance.snapshot_start()),
          "new handler started after permit expiry")

    rejected_close = False
    try:
        instance.close_channel()
    except AssertionError:
        rejected_close = True
    check(rejected_close and instance.channel_open,
          "channel closed before the expired-permit handler finished")
    claim = instance.drain_claim()
    rejected_ack = False
    try:
        instance.acknowledge_drain(claim)
    except AssertionError:
        rejected_ack = True
    check(rejected_ack and not instance.draining_ack,
          "drain acknowledged while the expired-permit handler remained active")

    instance.finish_handler()
    instance.close_channel()
    instance.acknowledge_drain(claim)
    check(not instance.channel_open and instance.draining_ack,
          "expired-permit handler did not drain after completion")


def main():
    schedules = test_admission_pause_interleavings()
    test_active_drain_order()
    test_generation_and_incarnation_fences()
    test_five_second_deadline()
    test_active_handler_outlives_permit()
    print(f"P003_HANDLER_DRAIN_MODEL PASS checks={checks} admission_schedules={schedules}")
    print("covered: serialized admission vs PAUSE, active completion -> channel close -> drain ack,")
    print("         instance/registration/generation fences, expiry with active handler drain,")
    print("         issued-at/expiry max 5s window,")
    print("         drain-ack generation/instance/incarnation/registration fencing")
    print("scope: abstract deterministic model only; no integration or timing claim")


if __name__ == "__main__":
    main()
