"""Abstract checks for the accepted renewable-lease admission contract.

This is a deterministic local state model, not a JWT, HTTP, AMQP, or timing
implementation. The lease deadline is supplied as monotonic time by the test.
"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class Admission:
    generation: int = 1
    paused: bool = False
    pending_nonce: Optional[str] = None
    installed_nonce: Optional[str] = None
    lease_deadline: float = 0.0
    active: int = 0
    draining: bool = False

    def renew(self, nonce: str) -> None:
        assert not self.paused and not self.draining
        assert self.pending_nonce is None
        self.pending_nonce = nonce

    def install(self, nonce: str, deadline: float, now: float) -> bool:
        if (self.paused or self.draining or nonce != self.pending_nonce
                or deadline <= now):
            return False
        self.installed_nonce = nonce
        self.lease_deadline = deadline
        self.pending_nonce = None
        return True

    def start(self, now: float) -> bool:
        if (self.paused or self.draining or self.installed_nonce is None
                or now >= self.lease_deadline):
            return False
        self.active += 1
        return True

    def pause(self) -> None:
        self.paused = True
        self.draining = True
        self.pending_nonce = None
        self.installed_nonce = None
        self.generation += 1

    def finish(self) -> None:
        assert self.active > 0
        self.active -= 1


def check(condition: bool, label: str) -> None:
    assert condition, label
    print(f"PASS {label}")


def main() -> None:
    a = Admission()
    a.renew("n1")
    check(a.install("n1", 5.0, 0.0), "current renewal response installs")
    check(a.start(1.0) and a.start(2.0), "one installed lease admits multiple starts")
    check(not a.install("n1", 6.0, 2.0), "replayed renewal response is rejected")
    check(not a.start(5.0), "expired lease blocks new starts")
    check(a.active == 2, "lease expiry leaves active handlers running")

    b = Admission()
    b.renew("late")
    b.pause()
    check(not b.install("late", 5.0, 1.0), "late response cannot install after pause")
    check(not b.start(1.0), "pause blocks all new starts")
    check(b.generation == 2 and b.active == 0, "pause advances generation and drains")

    c = Admission()
    c.renew("valid")
    c.install("valid", 5.0, 0.0)
    c.start(4.0)
    c.pause()
    check(c.active == 1 and not c.start(4.1), "pause blocks new work but preserves active handler")
    c.finish()
    check(c.active == 0, "drain completes only after active handler finishes")


if __name__ == "__main__":
    main()
