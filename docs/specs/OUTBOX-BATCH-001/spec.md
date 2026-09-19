# OUTBOX-BATCH-001 - bounded order outbox polling

## Intent

Complete the bounded-memory part of R03 without changing leased claim or fencing
semantics.

## Acceptance criteria

- AC-001: each status candidate query materializes at most 50 rows per poll.
- AC-002: pending, expired-processing and compensating reads are all bounded.
- AC-003: existing claim, lease, fencing and cancellation arbitration remain unchanged.
- AC-004: focused persistence tests and the multi-worker PostgreSQL regression pass.
- AC-005: full repository gates remain green.

## Out of scope

- changing the lease protocol;
- changing payment/refund identities;
- changing the public HTTP API.
