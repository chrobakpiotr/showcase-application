# UNEXECUTED-START-ROLLBACK-001 — preserve attempt-binding consistency

## Objective

Make Harness rollback of a start that provably did not launch a provider restore
the prior lifecycle state without leaving the discarded attempt's packet
binding behind. Provide an audited, compare-and-swap recovery for lifecycle
state produced by the previous rollback implementation, so valid retries can
resume without editing runtime state by hand.

## Scope and acceptance criteria

- AC-001: normal rollback removes exactly the packet binding appended by that
  start, decrements the attempt counter, restores the prior pending/failed
  status and retry authorization, and preserves all earlier attempt bindings
  and history. It records the rollback reason and discarded binding as an
  immutable audit entry in the same atomic state replacement.
- AC-002: rollback refuses unless the task is running under the expected owner,
  the worktree is clean, and the latest binding exactly matches the current
  attempt and active packet revision/fingerprint. Refusal leaves state and
  packet history byte-identical.
- AC-003: an explicit recovery operation repairs only the legacy shape created
  by the old rollback: failed status, a recorded unexecuted-start rollback,
  current attempt count N, valid bindings for prior attempts, and exactly one
  otherwise-valid excess binding for N+1 matching the active packet. It uses a
  CAS over status, attempts, rollback record, feature fingerprint/generation,
  active revision/fingerprint, and the complete binding ledger.
- AC-004: successful recovery removes only that excess binding, preserves the
  failed status, attempt count, prior bindings, and retry/human authorization
  history, and appends a durable actor/reason/timestamp/removed-binding audit
  record atomically with the state replacement. A repeated identical request
  is idempotent; changed, ambiguous, or stale requests fail closed.
- AC-005: after recovery, read-only status/ready succeeds, and ordinary start
  can bind the authorized next attempt to the active packet without a duplicate
  binding. Tests prove that no provider is invoked by rollback or recovery and
  that authorization remains usable exactly as before.

## Boundaries

The rollback API is an orchestrator operation and may be called only after the
orchestrator has established that no provider process was launched. The
recovery operation requires an explicit operator identity and reason; it does
not terminate a running attempt, infer provider state, rewrite immutable packet
revisions, or grant/consume a retry. Existing completion and packet authority
records remain unchanged. Runtime state is changed only through the locked
Harness transition, never by direct file edits.

## Verification

Run `python3.13 tooling/agent-harness/tests/test_harness.py` and
`python3.13 -m py_compile tooling/agent-harness/harness.py`. Exercise legacy
recovery from an exact state fixture, compare all unaffected records and
authorization bytes, then prove normal start succeeds. An independent evaluator
must challenge stale and ambiguous recovery requests and inspect the atomic
state update.

## Risks

- The orchestrator's assertion that the provider did not launch remains a trust
  boundary; this tool cannot prove process launch state outside the host it
  controls.
- Recovery may restore liveness only for the precisely recognized legacy
  rollback shape. Other inconsistent state remains blocked for deliberate
  operator resolution.
