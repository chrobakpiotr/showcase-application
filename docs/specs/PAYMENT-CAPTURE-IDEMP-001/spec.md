# PAYMENT-CAPTURE-IDEMP-001 - provider-idempotent payment capture

## Intent

Close the remaining R03 unknown-outcome gap between a successful external payment
capture and persistence of the local payment transaction.

## Acceptance criteria

- AC-001: every capture attempt for one order uses the stable operation id
  `ORDER-CAPTURE:<orderNumber>`.
- AC-002: retrying the same logical capture reuses the same provider idempotency
  identity.
- AC-003: the mock gateway returns the same logical gateway reference for repeated
  calls with the same capture operation id.
- AC-004: already captured, partially refunded and refunded payments still avoid a
  new gateway call.
- AC-005: payment-domain and payment-adapter tests pass.
- AC-006: full repository tests and build remain green.

## Failure semantics

A provider call may succeed while local persistence fails. A later retry is allowed
to call the provider again, but it must use the same operation identity so the
provider can replay the original logical result instead of creating a second charge.

This is provider idempotency, not distributed exactly-once delivery.

## Out of scope

- bounded outbox candidate queries;
- payment-provider reconciliation APIs;
- changing refund identity semantics;
- changing public HTTP contracts.
