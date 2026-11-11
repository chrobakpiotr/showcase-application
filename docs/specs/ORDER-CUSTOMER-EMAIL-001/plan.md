# Implementation plan

The specification in [spec.md](spec.md) owns acceptance.

1. Add `db.changelog-customer-email-not-unique.xml`, included last in the master changelog. On PostgreSQL it runs
   `ALTER TABLE ... DROP CONSTRAINT IF EXISTS customer_email_key`. The rollback re-adds the constraint.
2. Add `AbstractRepeatCustomerOrderIntegrationTest` with sequential and concurrent cases, run by
   `RepeatCustomerOrderPostgresIntegrationTest` (plus the schema check) and `RepeatCustomerOrderH2IntegrationTest`.
   The PostgreSQL class covers AC-001 to AC-003
   and register its three cases in the critical PostgreSQL manifest so the CI gate requires them.
3. Run the k6 smoke again on a fresh Docker Compose stack after hosted CI passes.

## Persistence review

- Transaction boundary: unchanged. Each order still inserts its own CUSTOMER row in the placement transaction.
- Consistency: removing the constraint removes the only cross-order conflict on CUSTOMER. No code reads CUSTOMER by
  email expecting one row; the duplicate-order lookup already expects several.
- Index and query pattern: `IDX_CUSTOMER_EMAIL` from changeset 19 stays and serves the email lookup.
- Concurrency: concurrent placements no longer contend on CUSTOMER. Stock contention keeps its existing bounded retry.
- Migration: a single `ALTER TABLE` on CUSTOMER, verified locally against PostgreSQL 16 for the drop, the repeated
  drop and the rollback on unique data. Hosted CI runs it on PostgreSQL 18.6 through the integration tests.

## Verification limits

The authoring workspace had no Docker, no JDK 25 and no network for Gradle. Java compilation, Spotless and the
integration tests run first in hosted CI.
