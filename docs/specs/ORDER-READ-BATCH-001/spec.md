# ORDER-READ-BATCH-001 - batch payment enrichment for order pages

## Intent

Complete the order-list N+1 portion of R13 while preserving the existing HAL HTTP
contract.

## Acceptance criteria

- AC-001: listing one page of orders performs one batch payment read for the page,
  not one payment persistence lookup per order.
- AC-002: an order without a persisted payment still receives the existing PENDING
  placeholder semantics.
- AC-003: the single-order endpoint preserves its current behavior.
- AC-004: focused domain, persistence and web tests pass.
- AC-005: full repository gates remain green.

## Out of scope

- changing page size or public response shape;
- adding pagination to returns, notifications or shipments;
- speculative indexes without query-plan evidence.
