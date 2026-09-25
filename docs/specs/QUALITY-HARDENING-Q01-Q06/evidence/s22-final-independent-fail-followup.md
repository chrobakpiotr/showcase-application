# S22 final independent evaluator FAIL follow-up

Baseline reviewed by the independent evaluator:

`1ee1e89ca64588c31d7e4a28792cdd606a6d54b9`

Status: **FIXES IMPLEMENTED + TESTED / INDEPENDENT RE-REVIEW PENDING**

The independent evaluator reported three reproducible violations:

- F1 HIGH — cancellation could become terminal before a stale placement worker
  prepared/captured payment, leaving CANCELLED + CAPTURED.
- F2 MEDIUM — two concurrent identical shipment commands could yield one canonical
  result plus one optimistic-lock exception instead of two canonical replays.
- F3 MEDIUM — an unresolved browser shipment operation identity lived only inside
  the component and was lost on navigation/component recreation.

This follow-up stays inside the accepted master-plan scope. It does not mark the
feature REVIEWED or CLOSED.

## Corrections

- F1: after a placement worker loses ownership after capture, it compensates the
  capture only when the durable saga row proves cancellation/compensation won.
  Healthy placement takeover remains non-refunding.
- F2: operation-aware shipment commands acquire a PostgreSQL pessimistic row lock
  before reading state/operation history. Concurrent identical commands therefore
  serialize and the second observes/replays the canonical operation.
- F3: pending browser shipment operation identity is held by root
  `ShipmentsService`, surviving component recreation. Success or HTTP 409 clears
  it; ambiguous transport failure retains it.

## Regression evidence

- exact F1 PostgreSQL interleaving is a critical manifest case;
- healthy takeover regression remains a critical manifest case;
- exact F2 concurrent-identical PostgreSQL replay is a critical manifest case;
- global conflicting operation-ID collision remains covered;
- component recreation retains operation ID/expected status;
- 409 still clears identity and retries from refreshed state;
- full critical PostgreSQL/RabbitMQ, frontend, PIT and repository gates are run
  before this follow-up may be committed.

- historical-upgrade runtime verification now invokes the operation-aware shipment
  command through transactional `ShipmentWorkflow`, matching the production
  application boundary required by the PostgreSQL pessimistic row lock.

- cancellation multi-worker PostgreSQL fixture is isolated from the live scheduler
  using a future durable due-time plus synthetic worker claim-time; the fixture clock
  is normalized to millisecond precision before the PostgreSQL timestamp round-trip
  so database precision cannot make an equal due-time appear slightly future-due;
  no sleep, retry, timeout inflation, or scheduler disablement is used.

A fresh independent evaluator must review the new checkpoint before any
REVIEWED/CLOSED status change.

## Independent re-review RF1 follow-up

The 2026-09-25 fresh independent re-review at
`4a21132104a37a1dbd21e3ea1d80e2c84108b85e` returned **FAIL** with RF1 HIGH.
Capture reconciliation could close after recovering a durably persisted capture without carrying the already-won cancellation into a durable refund continuation.

The RF1 correction keeps the capture reconciliation owned and pending while a recovered `CAPTURED`/`PARTIALLY_REFUNDED` payment is checked against the durable order state. If the order is `CANCELLED`, remaining refundable value is refunded before the capture reconciliation is completed. If that continuation fails, reconciliation failure bookkeeping remains durable and retryable instead of silently closing.

The exact RF1 PostgreSQL interleaving is a strict critical PostgreSQL manifest case. Status remains **IMPLEMENTED + TESTED / INDEPENDENT RE-REVIEW PENDING**.

The RF1 PostgreSQL regression constructs its reconciliation scheduler without Spring bean post-processing
before invoking the real scheduler method. This prevents `@Scheduled` registration from racing the test's
explicit invocation while preserving the real scheduler implementation, repositories, arbitration, payment
ports and PostgreSQL state. No sleep, retry, lease inflation or production scheduler change is used.

RF1 quality follow-up: the capture use case was decomposed into small completion helpers to restore the repository NPath contract without changing recovery semantics. Scheduler tests use canonical payment constants and explicitly cover PARTIALLY_REFUNDED and non-refundable recovered capture outcomes so persistence instruction coverage remains 100%.

Terminal owner-aware capture replay revalidates the canonical fingerprint after prepare, so a recovery owner cannot accept a changed amount/method returned by canonical preparation. Deferred provider decline is also explicitly covered and remains reconciliation-pending until the scheduler owns final completion.
