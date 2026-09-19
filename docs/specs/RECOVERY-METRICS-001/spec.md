# RECOVERY-METRICS-001 - executable durable recovery metrics

## Intent

Turn the R18 recovery metric contract from documentation into low-cardinality runtime
signals backed by durable state.

## Acceptance criteria

- AC-001: pending age, expired claims, retry backlog and incomplete compensation are
  exposed from order outbox state.
- AC-002: overdue retryable notification age is exposed without notification ids as
  labels.
- AC-003: exhausted capture calls increment `payment.operation.unknown`.
- AC-004: metric names match the documented recovery contract.
- AC-005: focused persistence tests and full repository gates remain green.

## Out of scope

- alert thresholds or SLO values without production baseline data;
- high-cardinality identifiers in metric labels;
- automatic destructive redrive.
