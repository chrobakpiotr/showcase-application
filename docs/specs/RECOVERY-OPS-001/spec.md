# RECOVERY-OPS-001 - operationalize durable recovery

## Intent

Document safe redrive and expose a low-cardinality recovery metric contract for backlog, leases and compensation.

## Acceptance criteria

- AC-001: the change has a focused automated regression test or deterministic verification.
- AC-002: existing public contracts remain compatible unless the feature explicitly documents a replacement.
- AC-003: repository tests remain green.
- AC-004: full repository quality gates remain green before the series is pushed.

## Out of scope

- unrelated renames or formatting-only rewrites;
- weakening existing coverage, security or static-analysis thresholds.
