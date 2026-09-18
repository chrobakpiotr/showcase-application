# DURABLE-RECOVERY-001 - durable saga compensation and notification retry

## Intent

Two recovery paths currently stop too early after a technical failure.

First, order-placement compensation catches stock-release and payment-refund failures and still marks the outbox event
`COMPENSATED`. That loses durable work: an order can be cancelled while stock remains reserved or payment remains captured.

Second, notification delivery persists `FAILED` and propagates the delivery exception to the business workflow. There is no
durable worker that retries the same notification row.

R07 makes both paths recoverable from persisted state.

## Acceptance criteria

- AC-001: exhausted placement-saga work transitions the outbox event to `COMPENSATING` before release/refund work.
- AC-002: the order is durably marked `CANCELLED` when compensation is claimed.
- AC-003: stock release and payment refund run outside the transaction that claims compensation.
- AC-004: a stock-release or refund failure leaves the event `COMPENSATING`.
- AC-005: a later poll retries `COMPENSATING` work and reaches `COMPENSATED` only after all compensation side effects succeed.
- AC-006: compensation retries are safe because R02 stock release and R04 refund operations are identity-aware/idempotent.
- AC-007: compensation failures persist an independent attempt counter and last error.
- AC-008: a notification delivery failure returns a persisted `FAILED` notification instead of failing the parent workflow.
- AC-009: notification retry reuses the same `NOTIFICATION_ID`; it never creates a second notification row for one failed send.
- AC-010: notification claims use a short database transaction and a `DELIVERING` lease so another worker does not immediately
  deliver the same row.
- AC-011: expired `DELIVERING` leases are retryable after process crash.
- AC-012: notification retries persist attempt count, next-attempt time and last error.
- AC-013: successful retry changes the same row to `SENT` and populates `SENT_DATE`.
- AC-014: a scheduler drives due notification retries, with an off switch for deterministic tests.
- AC-015: PostgreSQL tests prove compensation recovery and same-row notification retry.
- AC-016: R01, R02, R04 and R06 regression tests remain green.
- AC-017: repository quality gates remain green.

## Delivery semantics

Notification delivery is at-least-once. The durable `NOTIFICATION_ID` is passed inside the `Notification` object to the
delivery adapter and is the stable key a real provider should use for idempotency when supported. A provider that accepts a
message and loses the acknowledgement can still cause a duplicate on retry if it offers no idempotency facility.

## Transaction ownership

Saga compensation uses three phases:

1. under the outbox lock, cancel the order and persist `COMPENSATING`;
2. outside that transaction, run reservation-aware stock release and idempotent payment refund;
3. in a short transaction, mark the outbox row `COMPENSATED`, or persist the compensation failure and leave it retryable.

Notification delivery follows the same claim/side-effect/complete shape:

1. lock a due notification and mark it `DELIVERING` with a lease;
2. call the external delivery adapter outside the claim transaction;
3. mark the row `SENT` or `FAILED` in a short transaction.

## Out of scope

- exactly-once delivery when an external notification provider has no idempotency support;
- durable retries for every best-effort order-placement tail step;
- changing R02 reservation identity;
- changing R04 payment-refund identity;
- a dead-letter UI.
