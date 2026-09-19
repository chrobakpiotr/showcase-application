# GRADLE-PERF-001 - cache-aware Gradle verification

## Acceptance criteria

- AC-001: existing Gradle parallel execution, build cache and configuration cache remain enabled.
- AC-002: setup-gradle Enhanced Caching remains the GitHub Actions cache mechanism.
- AC-003: optional `GRADLE_ENCRYPTION_KEY` enables encrypted configuration-cache persistence.
- AC-004: backend CI no longer discards reusable outputs with `clean`.
- AC-005: critical Postgres tests execute fresh without globally rerunning dependency tasks.
- AC-006: Testcontainers/concurrency suites keep conservative test-worker parallelism.
- AC-007: critical Postgres gate and full build remain green.
