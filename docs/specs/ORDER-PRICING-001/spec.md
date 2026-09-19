# ORDER-PRICING-001 - server-authoritative order pricing

## Intent

Resolve product name and unit price from the catalog for new order placements while preserving idempotent replay.

## Acceptance criteria

- AC-001: the change has a focused automated regression test or deterministic verification.
- AC-002: existing public contracts remain compatible unless the feature explicitly documents a replacement.
- AC-003: repository tests remain green.
- AC-004: full repository quality gates remain green before the series is pushed.

## Out of scope

- unrelated renames or formatting-only rewrites;
- weakening existing coverage, security or static-analysis thresholds.
