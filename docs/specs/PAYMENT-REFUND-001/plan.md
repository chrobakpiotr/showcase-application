# Plan - PAYMENT-REFUND-001

1. Add a RED return-controller contract proving RMA approval must not invoke whole-order refund.
2. Record ADR 0039 before broad implementation.
3. Add `refundedAmount`, `PARTIALLY_REFUNDED`, and durable `PAYMENT_REFUND`.
4. Add a payment-refund claim/complete persistence boundary with PostgreSQL row arbitration.
5. Change the gateway contract to amount + stable refund idempotency key.
6. Route return approval through amount-aware refund using `returnNumber`.
7. Keep cancellation's one-argument API, but implement it as refund of the remaining captured amount.
8. Fence placement-saga capture/fulfillment after partial or full refund.
9. Add exhaustive unit tests and PostgreSQL acceptance/concurrency evidence.
10. Run SDD validation, touched-module quality gates, R01/R02 regression, full tests and full build.
