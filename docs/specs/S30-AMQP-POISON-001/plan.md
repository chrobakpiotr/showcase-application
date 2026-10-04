# S30-AMQP-POISON-001 — architecture plan (draft)

Status: **DRAFT — blocked on spec decisions**

## Bounded context and ownership

The inbound AMQP adapter owns Rabbit delivery mechanics and the message
listener. The domain/application receive port and persistence receipt adapter
own validation/business rejection and the durable receipt commit. The adapter
must not weaken that transaction boundary or infer downstream finance/stock
completion from a receipt. Rabbit resource provisioning ownership is not
currently established by this plan and must be resolved in the spec before
implementation.

Likely code/test seams, subject to task-packet confirmation:

- `modules/adapters/amqp/.../configuration/MessagingConfiguration.java` —
  additive quarantine topology, manual acknowledgement, container lifecycle,
  publisher confirm/return wiring.
- `modules/adapters/amqp/.../order/MessageListener.java` — consume the raw
  broker message, preserve bytes/properties, classify only known permanent
  failures, and coordinate source ACK after the transfer boundary.
- `apps/ecommerce/backend/src/test/.../RabbitMqFulfillmentDeliveryIntegrationTest.java`
  — real broker and production-container scenarios; update the critical Rabbit
  test manifest if required by repository conventions.
- AMQP adapter tests and relevant application configuration only where
  explicitly packeted.

No source paths are authorized by this architecture draft. Do not modify
AsyncAPI or the original topology unless a contract change is found and
recorded; no business event schema change is proposed.

## Proposed component flow

1. The production listener receives a broker `Message` retaining raw body bytes,
   properties, headers, and source routing metadata.
2. Existing receive/receipt behavior runs. Same-payload duplicate receipt is
   successful idempotent completion. A narrowly enumerated permanent
   validation/fingerprint-conflict outcome enters quarantine transfer.
3. Quarantine publisher sends the original body and required metadata to the
   additive destination with persistent delivery, mandatory routing, confirms,
   and returns enabled.
4. The listener ACKs the source only when the publisher has a positive confirm
   and the message was not returned. All other publisher outcomes remain
   unacknowledged and invoke the accepted pause lifecycle.
5. Transient and unknown processing outcomes bypass quarantine and invoke the
   same fail-closed pause lifecycle. No application retry loop or reject/requeue
   loop is introduced in 06a.

This transfer is at-least-once. Broker confirm, mandatory routing, and source
ACK are not one atomic transaction. Crashes/lost ACKs can duplicate a
quarantine copy; ambiguous publication must preserve the source delivery and
may also result in a duplicate if the quarantine publish had actually arrived.

## Decisions and dependencies

The graph below is a dependency DAG, not a claim that unresolved contracts are
already accepted:

```text
D1 topology names + provisioning owner ─┐
D2 error taxonomy + transaction boundary ├─> D5 architecture grill / spec READY
D3 pause + in-flight + restart contract ┤             │
D4 data access + retention policy ──────┘             v
                                             T1 real-container RED tests
                                                       │
                                                       v
                                             T2 additive topology + publisher
                                                       │
                                                       v
                                             T3 raw-message/manual-ACK flow
                                                       │
                                                       v
                                             T4 failure/prefetch/restart tests
                                                       │
                                                       v
                                             T5 observability + config verification
                                                       │
                                                       v
                                             E1 independent messaging/persistence review
                                                       │
                                                       v
                                             E2 independent evaluator + Rabbit gate
```

T1–T5 must not start until D1–D4 close. T2 and T3 may be implemented in
separate bounded tasks only if their file ownership is disjoint; otherwise one
builder owns the listener/configuration seam. E1/E2 cannot be self-review by a
builder. Security review is required because the raw payload crosses into a
new persisted broker destination.

### D1 — topology and declaration mismatch

The current source queue is declared by application bean without DLX arguments
and the container is manually constructed. Boot listener-factory settings do
not implicitly govern it. Decide exact additive exchange/queue/binding names,
which deployable artifact provisions them, how app startup verifies
pre-provisioned resources, mismatch behavior, and whether declarations are
disabled in production. Preserve the original queue declaration exactly.

### D2 — permanent error and commit semantics

Before code, map concrete exceptions from JSON parsing, `ReceiveOrderMessage`
validation, and receipt fingerprint conflict. Confirm which are permanent and
that they occur before/after receipt transaction commit. Treat all unlisted
exceptions and database/network failures as unknown. Do not classify by
exception message text or broad `RuntimeException`.

### D3 — pause, prefetch, and process lifecycle

Select whether to pause the consumer/container or stop it, how prefetched and
in-flight deliveries are treated, how readiness reports the state, and how
operators resume. Specify channel/process restart behavior and demonstrate it
with the production container. A container-only pause is not a durable guard
across application restart; if auto-start recreates a hot loop, 06b's durable
ledger (or a separately accepted persistent circuit state) must precede
release. Do not conceal this limitation with a claim of bounded attempts.

The prototype result is preserved in
[`evidence/container-lifecycle-spike.md`](evidence/container-lifecycle-spike.md):
manual unacked deliveries were requeued/redelivered after container stop and
restart while an old handler remained blocked. Therefore a volatile stop flag
does not satisfy restart-safe admission; 06b or another durable guard must be
sequenced before production enablement if automatic restart is permitted.

### D4 — quarantine data controls

Agree raw body/header classification, smallest authorized readers, encryption,
read/export audit, retention/deletion horizon, backup, and size limits. The
quarantine queue stores sensitive data, not merely diagnostic codes. No fixed
retention period is selected here.

## Verification design

Use the real Rabbit broker/container test seam. Tests must observe source queue
redelivery/ACK and quarantine queue bytes/properties, not only invoke a
listener method. Declare and verify the repository's dedicated critical Rabbit
gate, no retry masking, a fresh XML/report, and zero skipped tests.

Required deterministic scenarios are mapped in `spec.md` AC-06A-* and include
permanent poison, idempotent success, failure between publish confirm and
source ACK, mandatory return, nack/timeout/channel loss, transient persistence
failure, unknown failure, prefetch/in-flight behavior, broker reconnect,
healthy message after poison, and incompatible resource declarations. Tests
must distinguish the expected duplicate-transfer window from data loss and
must not assert exactly-once behavior.

## Later slices

S30-06b owns durable operation-keyed attempt accounting/fencing and bounded
retry policy if accepted. It must specify atomic claim behavior, redelivery and
broker restart semantics, and how database unavailability is handled without
an unbounded hot loop.

S30-06c owns operator authorization, audited replay command identity/reason,
one-shot transitions, and payload correction/conflict workflow. It depends on
the accepted 06a quarantine format and 06b attempt lifecycle; replay must
preserve the original operation id and cannot silently mutate a conflicting
payload.

## Risk tags

`messaging`, `persistence`, `security`, `concurrency`, `observability`,
`integration-test`.

## Decision status

No ADR is proposed yet. If deployment ownership or consumer pause/restart
semantics establish a cross-module policy not already recorded in an ADR,
architecture review should decide whether to add one before implementation.
