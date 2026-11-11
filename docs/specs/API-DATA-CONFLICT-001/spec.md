# API-DATA-CONFLICT-001 - unique-key violations as conflicts

Status: IMPLEMENTED - hosted CI PASS at 787a6f0; owner acceptance of the allowlist pending

The owner approved this change on 2026-11-11 as a follow-up to ORDER-CUSTOMER-EMAIL-001.

## Problem

A unique-key violation that reaches a controller, for example from a race that a domain check cannot prevent,
falls into the `RuntimeException` fallback and returns HTTP 500 `urn:problem-type:internal-error`. The request
conflicts with existing data; it is not a server failure. Until commit `99041bc` the 500 log also carried the database message,
which echoes the rejected value.

## Acceptance

- AC-001. A failure whose cause chain holds an `SQLException` with SQLState `23505` and a Hibernate
  `ConstraintViolationException` naming one of the client-data unique keys returns HTTP 409
  `application/problem+json` with type `urn:problem-type:data-conflict`, title `Conflict` and the fixed detail
  `The request conflicts with existing data`. This holds however Spring wrapped it, including commit-time wrappers.
- AC-002. The response never contains the database message, constraint name or rejected value.
- AC-003. Every other failure, including violations of server-generated unique keys and unnamed violations, returns
  the existing HTTP 500 `urn:problem-type:internal-error` response.
- AC-004. The log line keeps `errorId` and `traceId` and records only the SQLState and the simple class names of the
  cause chain; it omits the message when the chain is too deep to inspect.
- AC-005. Existing domain conflict handlers and their problem types are unchanged.

## Boundaries

- SQLState `23505` is the SQL standard unique-violation code that PostgreSQL and H2 both use.
- Client-data unique keys: `UQ_PRODUCT_SKU`, `UQ_CATEGORY_SLUG`, `UK_IDEMPOTENCY_KEY`, `UK_SHIPMENT_ORDER_NUMBER` and
  `UK_PAYMENT_REFUND_RETURN_CONTINUATION_RETURN`. They guard values or one-per-resource actions a client chooses.
  Server-generated keys (order number, dispatch, stock reservation, notification event, analytics projection) stay
  500 because a violation there is a server defect. The independent review raised this choice for the owner; the
  allowlist is the agent's decision while the owner was away and awaits owner acceptance.
- The mapping lives in the `RuntimeException` fallback so that Spring wrappers whose type would otherwise select the
  fallback are still inspected.
- Not-null, foreign-key and check violations stay 500: they indicate a server defect, not a client conflict.
- No endpoint gains a new success path. Clients that already treat 409 as a conflict need no change.
