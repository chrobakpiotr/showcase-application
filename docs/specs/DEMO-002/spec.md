# DEMO-002: safe order attempts and repeatable demo navigation

## Intent and scope

Deliver the five agreed hardening improvements as one reviewable commit. Preserve
existing API paths, role checks and coverage thresholds. Include the pending
Returns follow-up if it is not yet on main. No push or deployment.

## Acceptance

- AC-001: a keyed replay returns its original order number without coupon
  revalidation, stock mutation, another order or another outbox record.
- AC-002: a failed new placement rolls back key, stock, order and outbox together;
  concurrent same-key attempts cannot both place an order, including stale keys.
- AC-003: request fingerprint includes payment, coupon, all accepted request customer and
  item fields, and an unambiguous timestamp/encoding. Server-derived discount, response-only subtotal and generated customer ID are
  excluded. Changed client content is a conflict. Existing legacy fingerprints
  fail closed (409); do not silently reinterpret or delete stored keys.
- AC-004: bearer tokens attach only to the configured API origin and exact path
  or its descendants. Query strings, external URLs and sibling paths cannot opt in.
- AC-005: an order attempt retains one key and snapshot through a manual retry.
  Unknown outcomes block editing/new attempts until the same attempt is resolved;
  no automatic POST retry. Success exposes an explicit new-order action.
- AC-006: catalog links prefill an inventory SKU without writing stock. Successful
  orders link to order history. E2E fixtures use a unique SKU; mobile checks wait
  for mounted routes and representative response content, including error/empty.

## Risks, assumptions and boundaries

Order placement retains the existing controller transaction. All callers of the
idempotency persistence adapter must join a transaction. Database arbitration must
work on H2 and PostgreSQL, not depend on catching a poisoned transaction. The
existing asynchronous saga/event schema remains unchanged. A stock optimistic
conflict may fail the complete transaction; no new whole-transaction retry is
promised. Existing reservations are not purged. Legacy fingerprint replays need
manual order lookup after rollout; preserve data and document this limitation.
The UI attempt is scoped to one mounted component, not durable across reloads.
Authorization remains server-enforced; no new reset endpoint or privilege.

## Test seams

Use modules/domain/mock controller tests for ordering, real database transaction tests for
reservation arbitration and rollback, delayed Angular HTTP tests for token and
attempt behavior, and Playwright for demo fixtures/navigation/rendered states.
Run available tests; record unavailable Java/Docker gates explicitly. Do not claim
unexecuted integration tests passed.
