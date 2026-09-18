# 0039. Partial payment refunds use durable refund identities

## Context

ADR 0030 introduced one payment transaction per order and a whole-order `refundPayment(orderNumber)`.
Returns/RMA later reused that operation. Consequently, approving a return for one unit can refund the
entire order, and a second valid return has no payment left to refund.

R01 also made cancellation retryable. A payment refund therefore needs its own durable operation identity:
a retry after a gateway success but before local persistence must not create a second provider refund.

## Decision

### Payment tracks cumulative refund state

`PaymentTransaction` gains `refundedAmount`, initially zero. `PaymentStatus` gains
`PARTIALLY_REFUNDED`.

A payment is:

- `CAPTURED` while `refundedAmount == 0`;
- `PARTIALLY_REFUNDED` while `0 < refundedAmount < amount`;
- `REFUNDED` when `refundedAmount == amount`.

### Every refund has a durable id

`PAYMENT_REFUND` stores `refundId`, `orderNumber`, `amount`, status (`PENDING`/`COMPLETED`) and timestamps.

For a return, `returnNumber` is the refund id. Whole-order cancellation uses
`ORDER-REFUND:<orderNumber>`.

The same refund id cannot be reused for another order or amount.

### Claim before gateway, complete after gateway

A payment row is locked `PESSIMISTIC_WRITE` while a refund claim is created. Pending claims reserve their
amount, so concurrent refund operations cannot collectively exceed the unrefunded capture.

The database lock is released before calling the external gateway. The gateway receives the stable
`refundId` as an idempotency key. A `PENDING` retry calls the gateway again with that same id; a
`COMPLETED` retry skips the gateway.

After gateway success, a second short transaction locks the payment row, increments `refundedAmount`,
derives `PARTIALLY_REFUNDED`/`REFUNDED`, and marks the refund row `COMPLETED`.

### Whole-order cancellation means remaining amount

The existing `refundPayment(orderNumber)` port remains for cancellation/saga compatibility. It claims and
refunds only the remaining amount after completed and pending partial refunds.

### Placement is fenced after any refund

`capturePayment` treats `PARTIALLY_REFUNDED` and `REFUNDED` as terminal. The placement saga refuses to
continue fulfillment when capture returns either state.

## Consequences

- multiple approved returns can refund one captured payment incrementally;
- retrying a return approval is provider-idempotent and locally idempotent;
- concurrent claims cannot over-refund the payment;
- cancellation after a partial return refunds only the remainder;
- the mock gateway models the provider requirement by accepting an amount and refund idempotency key;
- migration is additive: one column on `PAYMENT_TRANSACTION` and a new `PAYMENT_REFUND` table.
