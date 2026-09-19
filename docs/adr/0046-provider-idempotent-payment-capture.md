# 0046. Payment capture retries use a stable provider idempotency identity

## Context

Order placement captures payment after a leased outbox claim commits.

The local payment row prevents a second capture after a successful local commit, but
there is an unavoidable unknown-outcome window: a payment provider may accept the
charge and the application can then stop before the payment transaction is persisted.
On recovery, the database still looks uncaptured.

A fresh provider request in that window can create a second business charge. Database
claim fencing alone cannot prevent that external replay.

## Decision

Every logical order capture has one stable provider operation id:

`ORDER-CAPTURE:<orderNumber>`

`ChargePaymentOutPort` carries that operation id explicitly. Retries of the same
logical capture must reuse it, and a real provider adapter must send it through the
provider's idempotency mechanism.

The showcase mock gateway models that contract by deriving its gateway reference
deterministically from the operation id. Repeating the same capture therefore
represents the same logical provider operation even across process restarts.

The local `PaymentTransaction` check remains useful: once a captured/partially
refunded/refunded transaction is persisted, the gateway is not called again.

## Consequences

- a crash after provider acceptance but before local persistence no longer requires a
  new logical capture identity;
- payment capture remains at-least-once at the transport/call level but idempotent at
  the provider-operation level;
- a production payment adapter must map `operationId` to the provider's supported
  idempotency-key facility;
- this does not claim distributed exactly-once semantics;
- bounded outbox polling remains a separate R03 follow-up.
