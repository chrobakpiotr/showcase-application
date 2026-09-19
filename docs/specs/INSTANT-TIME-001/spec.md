# INSTANT-TIME-001 - global Java Instant migration

## Acceptance criteria

- AC-001: Java application/domain/persistence/web timestamp types use `Instant`.
- AC-002: `java.util.Date` exists only in the explicit `LegacyDateInterop` third-party compatibility bridge.
- AC-003: constructor, epoch and comparison semantics preserve legacy millisecond behavior.
- AC-004: Hibernate JDBC timestamp handling is explicit UTC.
- AC-005: shared Gson round-trips Instant as ISO-8601.
- AC-006: full tests and quality gates remain green.
