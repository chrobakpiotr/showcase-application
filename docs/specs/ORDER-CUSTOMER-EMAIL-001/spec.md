# ORDER-CUSTOMER-EMAIL-001 - repeat customer orders

Status: IMPLEMENTED - real-stack k6 smoke PASS; hosted CI PASS at 84066c0

The owner requested the root-cause fix on 2026-11-11 after the first real-stack k6 smoke failed.

## Problem

Every order owns its own CUSTOMER row through `OrderEntity#customer` with `CascadeType.ALL`.
`FindRecentOrdersByCustomerAdapter` relies on that model and matches a repeat customer's orders by email.
The baseline changelog, however, declares `CUSTOMER.EMAIL` as `unique`. The second order with an email that
already exists fails with PostgreSQL error 23505 on `customer_email_key`, which the API returns as HTTP 500.
The k6 smoke placed two concurrent orders with one email: one returned 201 and the other 500.
The UI form and the integration fixtures had avoided the defect by generating a fresh email per order.

## Acceptance

- AC-001. Two sequential orders with the same customer email both succeed on PostgreSQL and create two CUSTOMER rows.
- AC-002. Two concurrent orders with the same customer email both succeed on PostgreSQL.
- AC-003. After migration, `CUSTOMER.EMAIL` has no unique constraint and keeps the non-unique `IDX_CUSTOMER_EMAIL` lookup index.
- AC-004. The migration is idempotent on databases where the constraint is already absent and runs after the existing changelogs.
- AC-005. No API, event or authorization contract changes. Order placement keeps its documented responses.

## Boundaries

- PostgreSQL drops `customer_email_key` by name. H2 generated the inline constraint name, so its changeset looks the
  name up in `INFORMATION_SCHEMA` and runs the drop with `EXECUTE IMMEDIATE`, with a no-op fallback when it is absent.
  Both were checked locally: PostgreSQL 16 and H2 2.4.240 built from source. `RepeatCustomerOrderH2IntegrationTest`
  runs the sequential and concurrent cases on H2 through the shared abstract test.
- Rollback restores the constraint only while no email repeats. Once repeat-customer rows exist, use a forward fix.
- Mapping other database integrity violations to 409 is out of scope; it would define a new API response contract.
