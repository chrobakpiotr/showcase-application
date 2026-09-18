# 0042. Order outbox workers use leased claims with fencing tokens

## Context

The order-placement scheduler can run in more than one application instance.

R01 added a shared PostgreSQL row lock for cancellation versus placement. R07 made
compensation durable. The scheduler still reads the same candidate rows on every
instance, and placement processing keeps the row-lock transaction open while payment,
fulfillment and other external work run.

That shape serializes workers, but it couples database lock duration to external
latency and gives no durable ownership record after the transaction ends.

## Decision

Placement work uses `PROCESSING` as a durable non-terminal ownership state.

Claiming a `PENDING` event is a short transaction that locks the row and writes:

- `STATUS = PROCESSING`
- a unique `CLAIM_ID`
- `CLAIM_UNTIL`

Payment capture, fulfillment and best-effort tail work then execute outside the claim
transaction.

Every transition after external work locks the row again and requires both
`PROCESSING` and the original `CLAIM_ID`. A stale worker therefore cannot write
`SENT`, restore `PENDING`, or start compensation after another worker or cancellation
has taken ownership.

An expired `PROCESSING` lease may be reclaimed by another worker. Customer
cancellation may also take an expired placement lease and transition it to
`CANCELLING`. An active placement lease means the placement poll won arbitration and
cancellation is rejected as too late.

`COMPENSATING` keeps its R07 state but now uses the same claim metadata. Only one
active compensation lease normally runs stock release/refund work, and completion or
failure recording is fenced by `CLAIM_ID`.

The default claim lease is 60 seconds and is configurable with
`outbox.publisher.claim-lease-ms`.

## Consequences

- database transactions no longer stay open across placement external calls;
- multiple scheduler instances normally perform one active attempt per outbox row;
- crashed workers are recoverable after lease expiry;
- stale workers cannot overwrite a newer durable owner;
- cancellation remains serialized with placement ownership;
- external effects remain at-least-once, because a lease can expire while a slow
  worker is still alive;
- existing payment, stock-release and refund idempotency remain part of correctness;
- the design does not claim exactly-once delivery.
