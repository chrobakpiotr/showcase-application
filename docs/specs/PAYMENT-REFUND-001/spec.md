# PAYMENT-REFUND-001 - partial and idempotent payment refunds

## Intent

R04 replaces whole-order refund semantics in the returns/RMA flow with amount-aware, operation-identified
refunds.

Today approving one return request invokes `refundPayment(orderNumber)`, which refunds the entire captured
payment. A second legitimate return for another unit/SKU is then marked `REFUNDED` even though the payment
is already terminal. The payment aggregate also has no cumulative refunded amount and no durable refund
operation identity.

## Acceptance criteria

- **AC-001** A RED test proves return approval must not use the whole-order refund operation.
- **AC-002** `PaymentTransaction` exposes cumulative `refundedAmount`; status distinguishes
  `CAPTURED`, `PARTIALLY_REFUNDED` and `REFUNDED`.
- **AC-003** return approval refunds exactly `ReturnRequest.refundAmount` using `returnNumber` as the
  durable refund idempotency key.
- **AC-004** repeating a completed refund id does not call the gateway again and does not increase
  `refundedAmount`.
- **AC-005** a retry of a `PENDING` refund reuses the same gateway idempotency key and amount; the local
  refund remains recoverable after a gateway/DB unknown outcome.
- **AC-006** concurrent distinct refund claims for the same payment are serialized in PostgreSQL and cannot
  reserve more than the remaining captured amount.
- **AC-007** cumulative completed refunds can never exceed the captured amount.
- **AC-008** whole-order cancellation remains backward compatible and refunds only the remaining refundable
  amount after any earlier partial refunds.
- **AC-009** capture is terminal/no-op for both `PARTIALLY_REFUNDED` and `REFUNDED` payments; a placement
  saga must not continue fulfillment after either state is observed.
- **AC-010** the mock gateway contract accepts refund amount plus refund idempotency key.
- **AC-011** existing payment rows are forward-migrated with `REFUNDED_AMOUNT = 0`; refund operations are
  stored durably in `PAYMENT_REFUND`.
- **AC-012** PostgreSQL acceptance proves two sequential partial returns, duplicate approval idempotency,
  cancellation-after-partial-refund, and concurrent over-refund prevention without arbitrary sleeps.
- **AC-013** all touched-module and repository quality gates remain green, including 100% instruction
  coverage.

## Refund state machine

Payment status:

```text
PENDING -> CAPTURED
PENDING -> DECLINED
CAPTURED -> PARTIALLY_REFUNDED -> PARTIALLY_REFUNDED -> REFUNDED
CAPTURED -> REFUNDED
```

Refund operation status:

```text
PENDING -> COMPLETED
```

A `PENDING` refund is intentionally durable. Retrying it repeats the external gateway request with the same
`refundId`; the outbound gateway contract requires that key to be idempotent.

## Transaction and concurrency model

`PAYMENT_TRANSACTION` is the arbitration row for refund claims. A claim acquires `PESSIMISTIC_WRITE` on
the payment row before reading/creating a `PAYMENT_REFUND` row.

Available refundable amount is:

`captured amount - completed refundedAmount - sum(PENDING refund claims)`

This prevents two concurrent, distinct refund requests from both claiming the same remaining amount.

The database transaction is not held across the gateway call:

1. claim/reserve refund in a short DB transaction;
2. call gateway with stable `refundId`;
3. complete refund in a second short DB transaction.

This is an idempotent external-call protocol, not a distributed transaction or exactly-once claim.

## Failure semantics

- gateway failure: refund row remains `PENDING`; retry uses the same refund id and amount;
- gateway success + process crash before DB completion: retry sends the same idempotency key and then completes locally;
- completed refund retry: no gateway call;
- over-refund / identity reuse with different order or amount: conflict;
- whole-order refund with no captured/remaining amount: backward-compatible no-op.

## Out of scope

- provider-specific refund settlement/webhook reconciliation;
- chargebacks;
- shipping-restock semantics;
- generalized compensation scheduling (R07).
