# API-DATA-CONFLICT-001 - unique-key violations as conflicts

Status: IMPLEMENTED - unit tests; hosted CI verification pending

The owner approved this change on 2026-11-11 as a follow-up to ORDER-CUSTOMER-EMAIL-001.

## Problem

A unique-key violation that reaches a controller, for example from a race that a domain check cannot prevent,
falls into the `RuntimeException` fallback and returns HTTP 500 `urn:problem-type:internal-error`. The request
conflicts with existing data; it is not a server failure. Until commit `99041bc` the 500 log also carried the database message,
which echoes the rejected value.

## Acceptance

- AC-001. A `DataIntegrityViolationException` or Hibernate `JDBCException` whose cause chain holds an `SQLException`
  with SQLState `23505` returns HTTP 409 `application/problem+json` with type `urn:problem-type:data-conflict`, title
  `Conflict` and the fixed detail `The request conflicts with existing data`.
- AC-002. The response never contains the database message, constraint name or rejected value.
- AC-003. Other `DataIntegrityViolationException` and `JDBCException` failures return the existing HTTP 500
  `urn:problem-type:internal-error` response.
- AC-004. The log line keeps `errorId` and `traceId` and records only the SQLState of the database failure.
- AC-005. Existing domain conflict handlers and their problem types are unchanged.

## Boundaries

- SQLState `23505` is the SQL standard unique-violation code that PostgreSQL and H2 both use.
- Not-null, foreign-key and check violations stay 500: they indicate a server defect, not a client conflict.
- No endpoint gains a new success path. Clients that already treat 409 as a conflict need no change.
