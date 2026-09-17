# 0037. Order cancellation and placement saga share one PostgreSQL arbiter

## Context

Customer cancellation and the order-placement saga currently mutate related state independently.

A placed order leaves a `PENDING` `OUTBOX_EVENT`. Customer cancellation changes `ORDER_.STATUS` to
`CANCELLED`, releases stock, refunds payment and sends a cancellation notification, but it does not
change the pending placement-saga event. The next saga poll can therefore load the cancelled order and
still execute payment capture and fulfillment.

The PostgreSQL reproducer in `ORDER-SAGA-001` demonstrates the concrete failure:

1. place the order,
2. cancel it before the first saga poll,
3. verify payment is still `PENDING`,
4. run one saga poll,
5. observe payment become `CAPTURED`.

A status check alone is insufficient. A poll and a cancellation can read `CONFIRMED` concurrently and
both proceed. Payment also currently treats only `CAPTURED` as an idempotent terminal capture result, so
a later capture invocation can charge a payment that was already `REFUNDED`.

The system therefore needs an explicit arbitration point that serializes the decision to continue the
placement saga versus begin cancellation, without pretending that PostgreSQL and the external payment
gateway participate in one distributed ACID transaction.

## Decision

### `OUTBOX_EVENT` is the arbitration row

The placement outbox row is the durable process record for one order-placement saga and becomes the
single PostgreSQL arbitration point for placement versus customer cancellation.

Both the saga poller and customer cancellation must acquire a pessimistic write lock on the same
`OUTBOX_EVENT` row inside a transaction and re-read its status while holding that lock **before** any
new irreversible placement-saga side effect is started.

The lock is a serialization mechanism, not a claim of exactly-once delivery or distributed
transactions.

### Process states

Extend `OutboxEventStatus` with customer-cancellation states:

- `PENDING` - placement saga may still perform payment/fulfillment work.
- `SENT` - fulfillment succeeded and the placement saga is terminal.
- `COMPENSATED` - placement saga failed and completed its own compensating path.
- `CANCELLING` - a customer cancellation durably won arbitration; placement work must not continue.
- `CANCELLED` - customer-cancellation compensation is complete.

Allowed high-level transitions are:

```text
PENDING -> SENT
PENDING -> COMPENSATED
PENDING -> CANCELLING -> CANCELLED
```

`SENT`, `COMPENSATED` and `CANCELLED` are terminal for placement processing.

### Winner rules

When customer cancellation acquires the arbiter lock while the row is `PENDING`, cancellation wins.
In the same database transaction it records durable cancellation intent (`CANCELLING`) and transitions
the order to `OrderStatus.CANCELLED`. After that commit, no later placement poll may capture payment or
send fulfillment for the order.

When the saga poller acquires the arbiter lock first while the row is `PENDING`, cancellation waits for
that transaction to finish.

- If the poll commits the row as `PENDING` again because fulfillment failed and will be retried,
  cancellation may acquire the lock next, transition to `CANCELLING`, refund any captured payment and
  stop all later placement retries.
- If the poll commits `SENT`, fulfillment has won the race. A later customer cancellation is rejected
  as too late for the placement-cancellation workflow.
- If the poll commits `COMPENSATED`, the order is already cancelled by saga compensation. A repeated
  customer cancellation is idempotent and must not duplicate compensation effects.

This makes the race deterministic: commit order at the shared locked process row defines the winner.

### Polling under the lock

The poller may discover candidate IDs without locks, but processing a candidate must open a transaction,
load that row with `PESSIMISTIC_WRITE`, re-check `status == PENDING`, and only then run
`ensurePaymentCaptured` / fulfillment.

A stale candidate that became `CANCELLING`, `CANCELLED`, `COMPENSATED` or `SENT` between discovery and
processing is a no-op.

The lock is intentionally held across the placement pivot step. This can make cancellation wait for a
slow gateway call, but it prevents the more dangerous outcome where cancellation reports success while
a concurrent poll is still free to start a new charge. This showcase prefers correctness and an
explicit contention point over an uncoordinated race.

### Cancellation intent is durable before compensation

Customer cancellation must not rely only on the HTTP call stack. `CANCELLING` is committed before
retryable compensation is considered complete so a restart cannot make the placement saga eligible
again.

Recovery of a `CANCELLING` row must resume missing compensation steps rather than re-run the whole
request blindly.

Reservation-scoped/idempotent stock release is coordinated with R02 because a crash between individual
line-item releases cannot safely be solved by repeatedly releasing stock by SKU alone. Durable
notification/compensation retry mechanics remain coordinated with R07. R01 establishes the durable
intent and the rule that placement work is permanently fenced once cancellation wins.

### Payment state is a second defensive guard

`ManagePaymentInPort.capturePayment` must never charge a transaction whose persisted status is
`REFUNDED`. The outbox arbiter is the primary concurrency mechanism; the payment guard is
defense-in-depth against a future caller bypassing the placement-saga coordinator.

`CAPTURED` remains idempotent. `REFUNDED` is terminal for capture. A declined payment continues to drive
the existing saga-compensation behavior.

## Consequences

- `place -> cancel before first poll -> poll` cannot capture payment after cancellation wins.
- `capture -> fulfillment retry -> cancel/refund -> poll` cannot re-capture after the cancellation
  transition fences the pending saga.
- A real cancel-versus-poll race has a documented winner determined by the shared PostgreSQL lock and
  transaction commit order.
- Cancellation after a successfully committed `SENT` placement saga is rejected by this workflow;
  downstream post-fulfillment returns remain a separate business process.
- The database transaction can remain open while a payment/fulfillment pivot is in flight. That is an
  intentional correctness trade-off for this showcase and must be covered by concurrency tests.
- This does not introduce 2PC and does not claim exactly-once external side effects.
- Full crash-safe replay of stock-release and notification compensation requires the coordinated R02
  and R07 follow-ups; the R01 fence ensures those retries can never resurrect placement work.
- ADR 0009 and ADR 0030 remain historical records. This ADR supersedes only their assumptions that a
  pending placement event can be processed independently of customer cancellation and that `REFUNDED`
  may fall through the capture path.
