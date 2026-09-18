# RMA-CONCURRENCY-001 - concurrent return entitlement and moderation

## Intent

R05 closes two races in Returns/RMA.

Creating a return currently performs:

1. list prior returns,
2. calculate remaining quantity in the web controller,
3. insert a new return later.

Two concurrent requests can therefore both observe the same remaining quantity.

Moderation currently performs `find -> domain transition -> save`. Concurrent approve
and reject operations can both read `REQUESTED` and overwrite each other.

R05 makes both operations atomic at the PostgreSQL boundary without moving payment
refund into a database transaction.

## Acceptance criteria

- AC-001: creating an RMA locks the persisted order row before checking the active
  returned quantity for the requested SKU.
- AC-002: while the order row is locked, active quantity is calculated from
  `RETURN_REQUEST` rows whose status is not `REJECTED`.
- AC-003: the new return is inserted in the same transaction as the entitlement
  check.
- AC-004: two concurrent requests for the last returnable unit result in at most one
  successful RMA.
- AC-005: exceeding remaining entitlement produces a business conflict and HTTP 409,
  not a raw database or optimistic-lock error.
- AC-006: the web controller still validates authoritative order existence, order
  status, SKU membership, requested quantity and refund snapshot, but no longer
  performs the non-atomic prior-return sum.
- AC-007: approve, reject and mark-refunded acquire a row lock on the RMA before
  changing state.
- AC-008: reject acquires the order mutex before the RMA row lock so releasing
  entitlement is serialized with new RMA creation.
- AC-009: concurrent approve versus reject yields one legal winner. The loser sees
  the committed state and returns the existing conflict contract.
- AC-010: repeated reject is idempotent. Because entitlement is derived from RMA
  status, a rejected request releases its quantity exactly once.
- AC-011: after one RMA is rejected, one replacement request for the released
  quantity succeeds, while an additional request exceeding the remaining quantity
  is rejected.
- AC-012: `RETURN_REQUEST` has a persistence version column as a defensive lost-update
  guard in addition to explicit pessimistic moderation locks.
- AC-013: approval transaction ends before `ManagePaymentInPort.refundPayment`.
  R04 refund IDs, amount accounting and unknown-outcome semantics are unchanged.
- AC-014: PostgreSQL integration tests prove concurrent last-unit requests,
  approve-vs-reject arbitration and rejected-entitlement reuse.
- AC-015: R04 partial refund integration tests remain green.
- AC-016: the R03 compensation-claim logging branch is covered so persistence
  JaCoCo returns to 100 percent.
- AC-017: full repository tests, static analysis and build remain green.

## Lock ordering

### Create return

```text
lock ORDER_ by orderNumber
  -> sum active RETURN_REQUEST quantity for orderNumber + sku
  -> validate remaining quantity
  -> insert RETURN_REQUEST
  -> commit
```

The order row is used only as a stable transaction mutex for one order. Returns still
store bare order-number/SKU references and do not add a domain dependency on Order.

### Reject return

```text
read RMA snapshot to resolve orderNumber
  -> lock ORDER_ by orderNumber
  -> lock RETURN_REQUEST by returnNumber
  -> re-check current status
  -> REQUESTED -> REJECTED
  -> commit
```

The re-read under the RMA row lock is authoritative. If approve won first, reject
observes `APPROVED` and fails with the existing not-rejectable conflict.

### Approve and mark refunded

These operations lock only the RMA row because they do not release entitlement.

## Payment boundary

The database transaction for `REQUESTED -> APPROVED` commits before the payment
refund starts. The controller then invokes the existing R04 refund operation using
`returnNumber` as the stable refund ID and finally calls the atomic
`APPROVED -> REFUNDED` transition.

No R05 transaction is held across a payment gateway call.

## Delivery semantics and retries

Repeated same-state moderation stays idempotent:

- approving `APPROVED` or `REFUNDED` returns the existing state;
- rejecting `REJECTED` returns the existing state;
- marking `REFUNDED` again returns the existing state.

Cross-state illegal transitions preserve the existing business exceptions and 409
HTTP mapping.

## Out of scope

- changing R04 refund amounts, IDs or provider behavior;
- customer self-service returns;
- shipment-based return eligibility from R12;
- moving all Return orchestration out of the web adapter;
- changing frontend request payloads.
