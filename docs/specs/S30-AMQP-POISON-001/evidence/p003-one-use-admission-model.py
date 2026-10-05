#!/usr/bin/env python3
"""Deterministic model of per-handler permit admission and PAUSE races."""

from __future__ import annotations

from dataclasses import dataclass, field
from threading import Barrier, Lock, Thread

TTL_NS = 5_000_000_000
SAFETY_NS = 100_000_000


@dataclass(frozen=True)
class Permit:
    nonce: str
    jti: str
    instance: str
    incarnation: str
    connection: str
    registration_id: str
    generation: int
    restore_episode: str
    issued_at_ns: int
    expires_in_ns: int


@dataclass(frozen=True)
class Pending:
    nonce: str
    request_started_ns: int
    instance: str
    incarnation: str
    connection: str
    registration_id: str
    generation: int
    restore_episode: str


@dataclass
class Instance:
    instance: str = "orders-1"
    incarnation: str = "boot-a"
    connection: str = "rabbit-record-a"
    registration_id: str = "registration-a"
    generation: int = 8
    restore_episode: str = "restore-a"
    now_ns: int = 10_000_000_000
    state: str = "ACTIVE"
    ready: bool = True
    channel_open: bool = True
    active_handlers: int = 0
    pending: dict[str, Pending] = field(default_factory=dict)
    used_jtis_until_ns: dict[str, int] = field(default_factory=dict)
    drain_ack: bool = False
    lock: Lock = field(default_factory=Lock, repr=False)

    def _prune_expired_jtis(self) -> None:
        expired = [jti for jti, deadline in self.used_jtis_until_ns.items()
                   if deadline <= self.now_ns]
        for jti in expired:
            del self.used_jtis_until_ns[jti]

    def begin_request(self, nonce: str) -> Pending | None:
        with self.lock:
            self._prune_expired_jtis()
            if self.state != "ACTIVE" or not self.channel_open or nonce in self.pending:
                return None
            request = Pending(
                nonce, self.now_ns, self.instance, self.incarnation,
                self.connection, self.registration_id, self.generation,
                self.restore_episode,
            )
            self.pending[nonce] = request
            return request

    def accept_response(self, request: Pending, permit: Permit) -> bool:
        # Models gate HTTP latency: no local lock is held during the request.
        with self.lock:
            self._prune_expired_jtis()
            if self.pending.get(request.nonce) != request:
                return False
            del self.pending[request.nonce]
            deadline = request.request_started_ns + min(TTL_NS, permit.expires_in_ns) - SAFETY_NS
            valid = (
                self.state == "ACTIVE"
                and self.ready
                and self.channel_open
                and request.instance == self.instance == permit.instance
                and request.incarnation == self.incarnation == permit.incarnation
                and request.connection == self.connection == permit.connection
                and request.registration_id == self.registration_id == permit.registration_id
                and request.generation == self.generation == permit.generation
                and request.restore_episode == self.restore_episode == permit.restore_episode
                and request.nonce == permit.nonce
                and permit.jti not in self.used_jtis_until_ns
                and 0 < permit.expires_in_ns <= TTL_NS
                and permit.issued_at_ns <= self.now_ns
                and self.now_ns < deadline
            )
            if not valid:
                return False
            self.used_jtis_until_ns[permit.jti] = deadline
            self.active_handlers += 1
            return True

    def pause(self) -> None:
        with self.lock:
            self.state = "DRAINING"
            self.ready = False
            self.pending.clear()
            self._close_if_drained()

    def finish_handler(self) -> None:
        with self.lock:
            assert self.active_handlers > 0
            self.active_handlers -= 1
            self._close_if_drained()

    def _close_if_drained(self) -> None:
        if self.state == "DRAINING" and self.active_handlers == 0:
            self.channel_open = False
            self.drain_ack = True


def permit_for(request: Pending, *, jti: str | None = None, **changes) -> Permit:
    claims = dict(
        nonce=request.nonce,
        jti=jti or f"jti-{request.nonce}",
        instance=request.instance,
        incarnation=request.incarnation,
        connection=request.connection,
        registration_id=request.registration_id,
        generation=request.generation,
        restore_episode=request.restore_episode,
        issued_at_ns=request.request_started_ns,
        expires_in_ns=TTL_NS,
    )
    claims.update(changes)
    return Permit(**claims)


checks = 0


def check(value: bool, message: str) -> None:
    global checks
    checks += 1
    if not value:
        raise AssertionError(message)


def blocked_http_then_pause() -> None:
    instance = Instance()
    response_wait = Barrier(2)
    release_response = Barrier(2)
    result: list[bool] = []

    def request_worker() -> None:
        request = instance.begin_request("blocked")
        assert request is not None
        response_wait.wait()
        release_response.wait()
        result.append(instance.accept_response(request, permit_for(request)))

    worker = Thread(target=request_worker)
    worker.start()
    response_wait.wait()
    instance.pause()
    release_response.wait()
    worker.join()
    check(result == [False], "late HTTP response started a handler after PAUSE")
    check(instance.active_handlers == 0 and instance.drain_ack,
          "paused instance did not drain with no started handlers")


def response_wins_then_pause() -> None:
    instance = Instance()
    request = instance.begin_request("before-pause")
    assert request is not None
    check(instance.accept_response(request, permit_for(request)),
          "valid response before PAUSE was rejected")
    instance.pause()
    check(instance.active_handlers == 1 and instance.channel_open and not instance.drain_ack,
          "PAUSE discarded or falsely drained a started handler")
    instance.finish_handler()
    check(instance.drain_ack and not instance.channel_open,
          "channel did not close after the started handler finished")


def concurrent_response_pause_race() -> None:
    for attempt in range(64):
        instance = Instance()
        request = instance.begin_request(f"race-{attempt}")
        assert request is not None
        permit = permit_for(request)
        start = Barrier(3)
        admitted: list[bool] = []

        def respond() -> None:
            start.wait()
            admitted.append(instance.accept_response(request, permit))

        def pause() -> None:
            start.wait()
            instance.pause()

        response_thread = Thread(target=respond)
        pause_thread = Thread(target=pause)
        response_thread.start()
        pause_thread.start()
        start.wait()
        response_thread.join()
        pause_thread.join()
        if admitted[0]:
            check(instance.active_handlers == 1 and instance.channel_open and not instance.drain_ack,
                  "response-winning race did not preserve active handler until drain")
            instance.finish_handler()
        else:
            check(instance.active_handlers == 0 and instance.drain_ack and not instance.channel_open,
                  "pause-winning race admitted or stranded a handler")


def deadline_and_one_use() -> None:
    instance = Instance()
    request = instance.begin_request("deadline")
    assert request is not None
    permit = permit_for(request)
    instance.now_ns = request.request_started_ns + TTL_NS - SAFETY_NS - 1
    check(instance.accept_response(request, permit), "valid pre-deadline permit rejected")
    replay = instance.begin_request("replay-before-deadline")
    assert replay is not None
    repeated = permit_for(replay, jti=permit.jti)
    check(not instance.accept_response(replay, repeated),
          "reused jti admitted before its prior permit deadline")
    # A consumed one-use permit's later expiry does not affect its handler.
    instance.now_ns += 2 * SAFETY_NS
    check(instance.active_handlers == 1 and instance.ready and instance.channel_open,
          "consumed permit expiry revoked active work or closed the channel")
    cleanup_request = instance.begin_request("cache-cleanup")
    assert cleanup_request is not None
    check(permit.jti not in instance.used_jtis_until_ns,
          "expired jti remained after the next admission-lock cleanup")
    instance.finish_handler()

    expired_instance = Instance()
    expired_request = expired_instance.begin_request("expired")
    assert expired_request is not None
    expired_instance.now_ns = expired_request.request_started_ns + TTL_NS - SAFETY_NS
    check(not expired_instance.accept_response(expired_request, permit_for(expired_request)),
          "permit accepted at conservative deadline")


def connection_restore_and_nonce_binding() -> None:
    instance = Instance()
    request = instance.begin_request("bound")
    assert request is not None
    instance.connection = "rabbit-record-b"
    check(not instance.accept_response(request, permit_for(request)),
          "permit admitted after broker connection record changed")

    replaced_registration = Instance()
    old_registration_request = replaced_registration.begin_request("old-registration")
    assert old_registration_request is not None
    replaced_registration.registration_id = "registration-b"
    check(not replaced_registration.accept_response(
        old_registration_request, permit_for(old_registration_request)
    ), "permit admitted after registration changed without a generation change")

    restored = Instance()
    old_request = restored.begin_request("old-episode")
    assert old_request is not None
    restored.restore_episode = "restore-b"
    check(not restored.accept_response(old_request, permit_for(old_request)),
          "permit admitted after restore episode changed")

    wrong_nonce_instance = Instance()
    nonce_request = wrong_nonce_instance.begin_request("expected")
    assert nonce_request is not None
    check(not wrong_nonce_instance.accept_response(
        nonce_request, permit_for(nonce_request, nonce="different")
    ), "response for another pending request nonce was admitted")


def main() -> None:
    blocked_http_then_pause()
    response_wins_then_pause()
    concurrent_response_pause_race()
    deadline_and_one_use()
    connection_restore_and_nonce_binding()
    print(f"P003_ONE_USE_ADMISSION_MODEL PASS checks={checks}")
    print("covered: HTTP outside lock, late response after PAUSE, 64 concurrent response/PAUSE races,")
    print("         conservative request-start deadline, one-use jti, consumed-token expiry,")
    print("         connection/restore/nonce revalidation, drain after active completion")
    print("scope: deterministic local-state model only; no JWT, network, Rabbit, or runtime proof")


if __name__ == "__main__":
    main()
