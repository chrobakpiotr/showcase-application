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
