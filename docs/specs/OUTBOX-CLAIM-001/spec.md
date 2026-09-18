# OUTBOX-CLAIM-001 - leased multi-worker order outbox claiming

## Intent

R03 makes order-placement outbox processing safe when more than one application
instance polls the same database.

Before this change, workers first read the same `PENDING` or `COMPENSATING`
candidates and only serialize later on the row lock. Placement processing also keeps
that database transaction open while payment, fulfillment and best-effort external
steps execute.

The new model uses a short durable claim transaction, a lease and a fencing token.
External work runs after the claim transaction commits.

## Acceptance criteria

- AC-001: a `PENDING` placement event is claimed as `PROCESSING` in a short
  transaction before payment or fulfillment is attempted.
- AC-002: every placement claim stores a unique `CLAIM_ID` fencing token and a
  `CLAIM_UNTIL` lease deadline.
- AC-003: another worker cannot process an event while its `PROCESSING` lease is
  active.
- AC-004: an expired `PROCESSING` lease is reclaimable after worker death.
- AC-005: only the worker whose fencing token still matches may transition a claimed
  event to `SENT`, back to `PENDING`, or into `COMPENSATING`.
- AC-006: payment capture, fulfillment notification and best-effort tail work run
  outside the transaction that acquires the placement claim.
- AC-007: a retryable fulfillment failure returns the owned event to `PENDING`,
  increments attempts and clears claim metadata.
- AC-008: a payment decline or exhausted fulfillment retry transitions the owned
  event to `COMPENSATING`, cancels the order in the same transaction and clears the
  placement claim.
- AC-009: customer cancellation may claim `PENDING`; an active `PROCESSING` lease
  means the placement poll won and cancellation is too late; an expired
  `PROCESSING` lease may be stolen by cancellation.
- AC-010: `COMPENSATING` work also uses `CLAIM_ID` and `CLAIM_UNTIL`, so two workers
  do not normally run stock release and payment refund concurrently.
- AC-011: expired compensation claims are retryable, and stale compensation workers
  cannot complete or record failure after another worker owns the row.
- AC-012: the delivery guarantee remains at-least-once for external side effects.
  Lease expiry or process death can replay an external call, so existing payment,
  reservation and refund idempotency remain required.
- AC-013: PostgreSQL tests prove active-lease exclusion and expired-lease recovery
  with two orchestrator instances sharing one database.
- AC-014: R01, R02, R04, R06 and R07 regression tests remain green.
- AC-015: persistence JaCoCo remains at 100 percent instruction coverage and full
  repository gates remain green.

## State machine extension

```text
PENDING -> PROCESSING -> SENT
PENDING -> PROCESSING -> PENDING
PENDING -> PROCESSING -> COMPENSATING -> COMPENSATED

PENDING -> CANCELLING -> CANCELLED

PROCESSING(active lease) -> cancellation rejected
PROCESSING(expired lease) -> CANCELLING -> CANCELLED
PROCESSING(expired lease) -> PROCESSING with a new fencing token
```

`PROCESSING` is not a terminal business state. It is a durable ownership marker.

`COMPENSATING` remains the R07 recovery state. R03 adds lease metadata to it without
adding another compensation status.

## Transaction ownership

Placement claim transaction:

1. lock one outbox row,
2. verify it is `PENDING` or an expired `PROCESSING` row,
3. write `PROCESSING`, `CLAIM_ID` and `CLAIM_UNTIL`,
4. commit.

Payment, fulfillment and the success tail execute after that transaction commits.

Placement completion/failure transaction:

1. lock the same row,
2. require `PROCESSING` and the same `CLAIM_ID`,
3. apply the next durable state,
4. clear claim metadata,
5. commit.

Compensation uses the same short claim and fenced-completion shape while retaining
status `COMPENSATING`.

## Failure and lease semantics

The lease is a recovery mechanism, not an exactly-once guarantee.

If a worker stops after claim, a later poll may reclaim the row after
`CLAIM_UNTIL`. If the original worker is only slow rather than dead, both workers
can perform an external call around lease expiry. The fencing token prevents the
stale worker from overwriting newer database state, while external idempotency
contracts handle replay.

The default lease is 60 seconds and is configurable through
`outbox.publisher.claim-lease-ms`.

## Out of scope

- distributed consensus or exactly-once external delivery;
- changing payment, stock reservation or refund identities;
- adding a heartbeat/lease-renewal protocol;
- changing the R07 notification retry lease;
- changing the public HTTP API.
