# ADR 0048: controlled operational time

## Status
Accepted.

## Context
R19 is incremental. A repository-wide `Date` to `Instant` migration would create broad persistence and API churn.
The highest-risk direct wall-clock calls are operational decisions: saga leases, idempotency stale takeover, and
timestamps emitted to SQS/Kafka.

## Decision
Inject the application `Clock` into those critical paths. Existing `Date` persistence fields remain compatible via
`Date.from(clock.instant())`. A narrow guard script rejects regression to direct wall-clock calls in these classes.

## Consequences
Critical time-sensitive behavior is deterministic and testable while legacy temporal field migration remains an
independent future modernization rather than a hardening-roadmap blocker.
