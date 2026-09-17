# ORDER-SAGA-001: cancellation vs pending order-placement saga

## Intent

Resolve R01: a customer/operator cancellation and the asynchronous order-placement saga
must have one coherent, durable state-machine contract.

The first implementation step is intentionally test-only: capture the confirmed
counterexample on PostgreSQL before changing production semantics.

## Confirmed current risk

A successful cancellation persists `OrderStatus.CANCELLED`, releases reserved stock,
attempts a payment refund and sends the cancellation notification. It does not make the
pending order-placement outbox work terminal.

`OrderPlacementSagaOrchestrator` currently loads the order for a pending event and
continues directly into payment capture without first arbitrating against cancellation.
`ManagePaymentUseCase.capturePayment` only treats `CAPTURED` as already complete; a
`PENDING` or previously `REFUNDED` payment can therefore be captured by a later poll.

## Acceptance criteria

- AC-001: PostgreSQL reproduces `place -> cancel before first poll -> poll`
  deterministically, with no scheduler timing dependency.
- AC-002: once cancellation has completed successfully, a later saga poll must not
  capture payment for that order.
- AC-003: once cancellation has completed successfully, a later saga poll must not send
  the order to fulfillment or run the best-effort success tail.
- AC-004: `capture -> fulfillment retry pending -> cancel/refund -> poll` must not
  re-capture a refunded payment.
- AC-005: the poll/cancel race has an explicitly documented winner/state transition and
  is tested on PostgreSQL with barriers/latches rather than arbitrary sleeps.
- AC-006: cancellation intent and required compensation work are durable across failure
  and restart; retry continues missing work rather than silently abandoning it.
- AC-007: repeated commands/events do not duplicate externally meaningful effects under
  the contracts owned by this feature. Reservation-operation identity itself remains
  R02 and durable notification/compensation retry mechanics remain coordinated with R07.

## First red slice

The first test covers only AC-001 through AC-003:

1. receive one unit of stock,
2. place an order and persist its PENDING outbox row,
3. cancel the order before any saga poll,
4. prove payment is still PENDING after cancellation,
5. manually invoke one saga poll,
6. require payment to remain PENDING and fulfillment to have no interaction.

The test disables the scheduled publisher and manually constructs the orchestrator with
real PostgreSQL-backed order/outbox/payment/inventory collaborators plus local mocks for
outbound success-tail integrations. This makes ordering deterministic and avoids sleeps.

This PostgreSQL test is a required correctness reproducer: absence of Docker must fail the
test run rather than silently skip it. The test also asserts that the placed order has a
PENDING outbox row both before and after cancellation, so an empty poll cannot produce a
false green result.

The test is expected to fail on the current implementation. Production code must not be
changed in the same red-reproducer step.

## Design constraints for the subsequent fix

- Do not solve R01 with only a non-atomic `if (order.status == CANCELLED)` check.
- Define cancel/capture/fulfillment arbitration and durable transitions before changing
  behavior.
- Preserve transaction evidence on PostgreSQL.
- Do not claim exactly-once external effects.
- Do not introduce R02 stock reservation identity or R03 multi-worker claiming inside
  the first R01 fix unless a concrete dependency makes a smaller change impossible.
- Recovery must remain compatible with the later R07 durable compensation work.

## Out of scope for the red slice

- Production state-machine changes.
- Schema migrations.
- New outbox statuses.
- R02 reservation identity.
- R03 multi-worker claiming/leases.
- R04 partial refund semantics.
- R07 notification retry implementation.
