# 0043. Return entitlement and moderation use database row locks

## Context

ADR 0033 intentionally allows multiple partial RMAs for one order line while requiring
their non-rejected quantity never to exceed the ordered quantity.

The original implementation enforces that rule with a read in `ReturnController`
followed by a later insert. Concurrent requests can both observe the same remaining
quantity.

Return moderation has the same check-then-save shape. Concurrent approve and reject
operations can both read `REQUESTED` and race to persist incompatible terminal
decisions.

R04 added correct partial-refund identities and accounting, so R05 must preserve the
important boundary where payment refund happens after approval commits and outside
the RMA database transaction.

## Decision

### The persisted order row is the return-entitlement mutex

Creating an RMA locks the `ORDER_` row for its `orderNumber` with
`PESSIMISTIC_WRITE`. While holding that lock, persistence sums all non-rejected
`RETURN_REQUEST` quantities for the requested SKU, validates the remaining
entitlement and inserts the new RMA in the same transaction.

This deliberately serializes all RMA creation for one order. The lock is coarser than
an order+SKU mutex but uses an already durable row and avoids introducing a second
entitlement aggregate whose initialization would itself need concurrency control.

Returns continue to store bare order-number and SKU references. The cross-context
dependency is a persistence locking implementation detail, not a domain object
reference.

### Moderation locks the RMA row

Approve, reject and mark-refunded acquire `PESSIMISTIC_WRITE` on the
`RETURN_REQUEST` row before evaluating the current status.

Reject additionally locks the owning order first. This gives request creation and
entitlement release a single lock ordering:

```text
ORDER_ -> RETURN_REQUEST
```

If approve and reject race, one row lock wins. The loser re-reads the committed state
and receives the existing not-approvable or not-rejectable business conflict.

`RETURN_REQUEST.VERSION` is also added as a defensive optimistic lost-update guard
for any future write path that does not use the explicit moderation lock.

### Rejected entitlement is derived, not decremented

There is no mutable "remaining returns" counter. Active entitlement is the sum of RMA
rows whose status is not `REJECTED`.

Therefore the first `REQUESTED -> REJECTED` transition releases that quantity.
Repeating reject returns the already rejected row and cannot release anything a
second time.

### Payment remains outside the RMA transaction

Approval commits before the controller invokes the R04 partial-refund operation.
`markRefunded` is a second short locked transition after the refund succeeds.

No database lock is held across the payment provider call.

## Consequences

- concurrent last-unit RMA requests cannot both succeed;
- approve and reject cannot overwrite each other;
- rejected quantity becomes returnable again without a separate counter;
- all entitlement creation for the same order is serialized, even across different
  SKUs;
- R04 refund amount, refund ID and provider retry semantics are unchanged;
- direct callers of `RequestReturnInPort` now provide the authoritative ordered
  quantity snapshot already resolved by the composition layer;
- the old generic return save port is removed so moderation cannot accidentally
  return to an unsafe find-then-save path.
