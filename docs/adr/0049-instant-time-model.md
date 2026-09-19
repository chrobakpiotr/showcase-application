# ADR 0049: use Instant as the Java timestamp model

## Status

Accepted.

## Decision

The Java application model uses `java.time.Instant` for timestamps across domain objects, web resources, persistence entities,
ports, adapters and tests.

`java.util.Date` is permitted only inside `LegacyDateInterop`, an explicit adapter-common compatibility bridge for third-party APIs
that still require the legacy type (for example Nimbus JWT and FreeMarker test boundaries).

The migration preserves legacy millisecond precision with `Instant.ofEpochMilli(...)`, configures Hibernate JDBC timestamp
handling explicitly to UTC, and registers ISO-8601 `Instant` support in the shared Gson configuration.

TypeScript/JavaScript `Date` is outside this ADR.
