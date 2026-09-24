# QUALITY-HARDENING-Q01-Q06 — correctness before stronger claims

Status: **IMPLEMENTED + TESTED SLICES / INDEPENDENT VERIFICATION PENDING**

Audited baseline:

```text
ead24024a39bde946289ba8e5aa8eb92d9217391
fix(frontend): patch npm audit vulnerabilities
```

This feature captures the next correctness iteration identified by the 2026-09-20
quality review. Historical N01-N20 completion labels are not acceptance evidence for
these stronger failure-mode guarantees.

## Implementation and evidence snapshot - 2026-09-23

Evidence baseline before this documentation update:

```text
c912a5555122e8719a0ee6436b82340747a7f1b6
ci: harden critical evidence gates
```

The implementation was delivered through direct S22 checkpoints rather than an
executable Agentic SDD `tasks.json`. That history is intentional and must not be
rewritten retroactively. `docs/specs/INVENTORY.md` therefore marks this feature as
document-only and not selected by `harness.py validate-all`.

| Contract area | Current evidence status |
| --- | --- |
| C01 aggregate/evidence | IMPLEMENTED + TESTED; critical PostgreSQL evidence and PIT are fail-closed; required-status ruleset is deployed; independent verification remains pending |
| Q01/Q02 placement/payment | IMPLEMENTED + TESTED; owner-aware recovery, stable provider operation identity and manual-review preservation are covered; independent review remains pending |
| Q03 cancellation | IMPLEMENTED + TESTED; terminal fencing plus notification enqueue are one transaction and WAITING_FOR_REFUND is retryable; independent review remains pending |
| Q04 shipment | IMPLEMENTED + TESTED; immutable operation identity, dispatch rule parity, atomic rollback and UI 409 retry semantics are covered; independent review remains pending |
| Q05 refund entitlement | IMPLEMENTED + TESTED; persisted refund entitlement is conserved and rejected allocation is released exactly; independent review remains pending |
| Q06 notification | CORE + placement dispatch IMPLEMENTED + TESTED; durable event identity/insert-once/replay protection, enqueue/delivery overlap, transactional cancellation rollback, durable AMQP consumer receipts, real-broker republish/redelivery evidence, and durable SMTP/Camel dispatch ownership are covered. SMTP is at-least-once under ambiguous outcome and Camel is a local durable-handoff demo; no provider-level exactly-once SMTP/Camel delivery is claimed |

Key implementation corrections now reflected by the contract:

- Payment recovery carries owner context and rejects stale claim use across takeover.
- Refund-to-return continuation persists refund/order/amount identity, crash recovery,
  retry state, fairness ordering and manual-review parking.
- Cancellation finalization fences the terminal transition together with notification
  enqueue and does not spend the execution failure budget while waiting for refund.
- Shipment operation history is insert-once: a global operation ID cannot be reassigned
  to another shipment, and canonical fingerprint mismatch is a conflict.
- Legacy and operation-aware shipment dispatch share the same CONFIRMED/CAPTURED rule.
- Browser HTTP 409 is a definitive rejection: refresh state and begin a new logical
  attempt; an unknown network outcome retains the original operation identity.
- Refund entitlement uses persisted monetary ownership. Historical allocations are not
  silently recomputed by reordering line items.
- Critical PostgreSQL evidence is manifest-driven at suite and test-method level with
  no retry masking; PIT evidence is fresh, non-empty and bound to source SHA/JDK/config.
- Q06 core evidence now includes PostgreSQL enqueue/delivery overlap: re-enqueue during an active delivery claim/finalize
  preserves one durable row and cannot regress SENT delivery state. Cancellation finalization already proves that terminal
  business state plus notification enqueue roll back together on notification persistence failure.
- S22-08d1 moves placement confirmation email and Camel routing behind durable dispatch rows with stable identity, short owner claims, external I/O outside the claim transaction, and owner-fenced finalization. The SMTP/Camel adapters execute one attempt; durable worker recovery owns retries.
- S22-08d2 makes the external boundary explicit: SMTP is at-least-once when outcome is ambiguous, the stable `Message-ID` is correlation only, and Camel's local `file:` route is showcase handoff evidence rather than a provider-level exactly-once guarantee. See `docs/runbooks/order-placement-external-delivery.md`.
- Q06 AMQP evidence uses real PostgreSQL plus RabbitMQ: a broker-accepted publish can be repeated after placement owner loss under the same `operationId`, and a committed consumer receipt can be redelivered after connection loss before ACK. Both paths converge on one durable fulfillment receipt and preserve captured-payment state; broker acceptance is not consumer business commit.

## Goal

Prove final durable state for money, stock, cancellation and notifications after
lease loss, takeover, provider uncertainty, replay and restart. CI must fail closed
when a required verification job did not actually succeed.

## Non-goals

- no new bounded context, broker or framework;
- no UI redesign;
- no broad module moves while changing lifecycle semantics;
- no edits to already-applied Liquibase changesets;
- no weakening of PIT, JaCoCo, static analysis, security or audit thresholds;
- no claim of exactly-once when the provider contract does not support it;
- no repository ruleset mutation from implementation scripts.

## Shared protocol invariants

1. A lease controls temporary execution ownership; losing it is not business intent
   to cancel or refund.
2. A fencing token controls whether a worker may mutate workflow control state.
3. A stable provider operation ID controls replay of one external logical effect.
4. External I/O is outside long DB locks/transactions.
5. Every durable nonterminal state has an automatic recovery candidate or an explicit
   manual-review path.
6. Terminal/replayed operations validate immutable parameters instead of silently
   accepting a reused identity with different input.
7. Tests use Clock plus barriers/latches for ordering; sleeps/timeouts are watchdogs,
   not the oracle.

## C01 — fail-closed CI quality gate

### AC-C01-AGGREGATE
The `CI quality gate` succeeds only if all required CI jobs succeeded. A failed,
cancelled, missing or unexpectedly skipped required job makes the aggregate fail.

### AC-C01-DEPENDENCY-REVIEW
On `pull_request`, dependency review must succeed. On non-PR events it may be
`skipped` because the job is intentionally PR-only.

### AC-C01-GUARDS
CI executes both controlled-time guards and validates Bash syntax for repository
operational scripts.

### AC-C01-RULESET
This repository change prepares a stable aggregate status only. Changing GitHub
required-status/ruleset configuration is a separate human-authorized action after
the status name has been observed on a real PR.

## Q01 — placement ownership loss and DECLINED replay

Failure timeline:

```text
worker A owns placement -> capture starts -> A lease expires
-> healthy worker B takes over -> A receives provider success
-> A must not refund merely because its lease was lost
```

### AC-Q01-TAKEOVER
Healthy takeover while the order remains a valid placement flow cannot cause the old
worker to refund the payment needed by the new owner.

### AC-Q01-CANCEL
If a durable cancellation/compensation decision exists, financial compensation is
driven by that durable workflow and uses its stable operation identity.

### AC-Q01-DECLINED
A persisted/replayed `DECLINED` payment can never be treated as successful capture and
can never allow fulfillment publication.

### AC-Q01-BACKOFF
A PENDING candidate is revalidated under lock at claim time, including
`NEXT_ATTEMPT_DATE`; a stale discovery cannot bypass a newer backoff.

### AC-Q01-FULFILLMENT-REPLAY
If a fulfillment command becomes externally visible and the placement lease is lost
before local completion is recorded, takeover by another worker cannot create a
second logical fulfillment. The protocol must prove either a stable fulfillment
operation/message identity consumed idempotently downstream, or an equivalent durable
dispatch state that prevents duplicate logical effect. A post-send claim check by the
old worker alone is not sufficient evidence.

Required RED timeline:

```text
worker A owns placement
-> A renews lease
-> RabbitMQ accepts fulfillment command
-> A loses lease before post-send ownership check
-> worker B takes over
-> B retries placement
-> expected: one logical fulfillment for the order
```

Placement-tail scope note:

- RabbitMQ fulfillment is the critical pivot and belongs to A1/Q01.
- Customer-facing placement notification replay belongs to A3/Q06.
- S3 export, SQS audit and Kafka analytics remain explicit best-effort/at-least-once
  side-channels in this phase; A1 must not claim exactly-once semantics for them.
- AI triage/duplicate detection are read-only human-in-the-loop side effects and are
  outside the durable-effect protocol.

## Q02 — payment reconciliation protocol

Protocol shape:

```text
prepare durable local operation + reconciliation intent in one short transaction
-> remote provider I/O outside DB lock
-> finalize/reconcile durable result
```

The concrete provider capability must be explicit: query/lookup semantics or a proven
idempotent replay contract. Absence of a local result is never proof that a remote
effect did not occur.

### AC-Q02-PREPARE
No provider mutation is attempted unless the payment/refund operation and durable
reconciliation identity are prepared atomically enough that a crash leaves a
recoverable record.

### AC-Q02-UNKNOWN
Provider commit plus lost response eventually converges to one external logical effect
and a consistent local payment state without guessing failure.

### AC-Q02-FENCING
A stale worker cannot overwrite another worker's workflow-control decision or clear
manual-review state. Any accepted late provider success must have an explicit monotonic
rule that makes it safe.

### AC-Q02-IDENTITY
Reusing the same provider operation ID with different immutable capture/refund
parameters is an explicit conflict, including terminal replay paths.

### AC-Q02-REFUND-CONTINUATION
A remotely completed refund has a durable continuation that can finish the associated
RMA state and notification after restart without a second user click.

## Q03 — cancellation ownership covers real finalization

### AC-Q03-TOKEN
The cancellation recovery token (or equivalent ownership generation) is checked at
the durable terminal transition, not only at later bookkeeping.

### AC-Q03-HTTP-SCHEDULER
HTTP cancellation and scheduler recovery share one durable ownership/finalization
protocol; HTTP cannot bypass a live recovery owner.

### AC-Q03-WAITING
Waiting for an external payment/refund operation is represented as waiting/retryable
workflow state and does not consume the same failure budget as an execution error.

### AC-Q03-TAKEOVER
Two workers plus an expired lease permit one takeover; stale terminal success/failure
cannot overwrite the new owner. Claim time is evaluated when each record is actually
claimed, not once for an entire slow batch.

## Q04 — shipment atomicity and operation identity

### AC-Q04-ATOMIC
Legacy and operation-aware shipment advance both use one transactional application
boundary. Failure on a later SKU rolls back shipment state, all stock mutations and
notification enqueue for that command.

### AC-Q04-FINGERPRINT
Durable replay identity includes operation ID plus shipment/command fingerprint
(expected state and immutable command parameters). Same ID + same payload replays;
same ID + different payload conflicts.

### AC-Q04-REPLAY
A replayed operation does not rerun fulfillment side effects. Earlier operation IDs
have a defined replay result after subsequent shipment transitions.

### AC-Q04-CLIENT-RETRY
After a lost browser response, retry uses the same pending operation ID until the
outcome is resolved.

### AC-Q04-DEPRECATION
Legacy compatibility remains explicit. If the `Deprecation` HTTP field is emitted it
uses RFC 9745 date Structured Field syntax; Sunset comes from an agreed compatibility
policy, not an arbitrary fixture.

## Q05 — refund entitlement conservation

### AC-Q05-CONSERVATION
For every accepted sequence of return request/reject/retry, total completed + reserved
refund never exceeds the persisted order entitlement and a complete return equals that
entitlement exactly.

### AC-Q05-REJECT
The sequence A/B/reject-A/C/D for a line with non-even cent allocation releases the
exact monetary allocation owned by A; it cannot lose a cent because quantity offsets
were recomputed.

### AC-Q05-STABLE-ORDER
Refund allocation does not depend on unspecified JPA collection read order. Historical
fingerprints are not changed by globally reordering order items.

### AC-Q05-HISTORY
Already persisted/refunded historical data is handled by forward migration or explicit
reconciliation; it is not silently recalculated under a new allocation order.

## Q06 — atomic notification enqueue and explicit event identity

### AC-Q06-EVENT-KEY
Notification producers provide a stable business event key derived from aggregate,
event type and stable operation/version identity. Human-readable subject/body text is
not the business identity.

### AC-Q06-INSERT-ONCE
Concurrent enqueue uses an atomic insert-once strategy (for PostgreSQL, e.g.
`INSERT ... ON CONFLICT DO NOTHING` plus read) or an equivalent proven transaction
pattern. A duplicate race cannot regress delivery state.

### AC-Q06-PAYLOAD
Replay of the same event key validates immutable recipient/type/payload snapshot or
returns the persisted snapshot according to an explicit contract. Conflicting reuse is
not silently merged.

### AC-Q06-DELIVERY-RACE
Concurrent enqueue and delivery leave one durable record and an already `SENT` record
cannot become `PENDING`.

### AC-Q06-PLACEMENT-REPLAY
If placement loses its lease after the placement tail begins, takeover/replay uses the
same durable dispatch identity for the same order and dispatch type. Only one worker may
own a dispatch attempt at a time, stale owners cannot finalize another worker's claim,
and a durably `SENT` dispatch is not reopened by placement replay.

This local protocol does not upgrade an external provider contract. SMTP has no
provider idempotency/query capability in this repository, so an ambiguous send is
at-least-once and may be externally duplicated; its stable `Message-ID` is correlation
evidence, not exactly-once proof. Camel currently demonstrates a local `file:` handoff
and likewise carries no provider-level exactly-once claim. A1 does not own this change.

### AC-Q06-EXTERNAL-CONTRACT
The adapter performs one external attempt per durable dispatch attempt. Retry ownership
belongs to the durable placement-dispatch worker, not a nested adapter retry loop.
Documentation and operational evidence distinguish accepted/rejected/ambiguous outcomes
and never infer that absence of a local `SENT` record proves the provider did not act.

## Required evidence before this feature can be called complete

- RED and GREEN evidence for each Q01-Q06 counterexample;
- PostgreSQL integration evidence for lease/fencing/insert-once/transaction boundaries;
- stateful fake provider evidence for response-lost-after-commit;
- no skipped critical suites;
- fresh test results for required concurrency suites;
- independent evaluator counterexample review;
- full module/repository gates appropriate to each implementation slice.

A green unit test alone is not completion evidence for an AC that names PostgreSQL,
restart, provider uncertainty or browser-to-backend replay.
