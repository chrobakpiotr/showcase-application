# RECOVERY-TIMELINE-001 — read-only order recovery timeline

Status: **CLOSED**

S22 package: **S22-09**

Baseline:

```text
41ef69f0b1a820a9abfa78f10f5a1bd69cc078ca
docs(order): define external delivery guarantees
```

## Independent closure — 2026-09-25

Fresh independent evaluation of `1d5314fd6d2f009d851161356b2382fcf83db6cf` returned **PASS** for S22-09:
authorization, exact order scoping, sensitive-field exclusion, deterministic ordering,
bounded pagination, one-query page loading, state semantics and read-only UI behavior
all retained fresh evidence.

The closure authority is [`docs/reviews/S22-final-independent-rereview-1d5314fd-2026-09-25.md`](../../reviews/S22-final-independent-rereview-1d5314fd-2026-09-25.md).
The accepted limitation remains unchanged: this is a current-state recovery projection,
not an event store or complete historical audit log.

## Goal

Expose one operator-facing, read-only recovery timeline for an order by projecting
the durable workflow records that already exist in PostgreSQL.

This is a **current-state recovery projection**, not an event store and not event
sourcing. It must not imply that every historical transition is retained.

## Endpoint

```text
GET /api/order/{orderNumber}/recovery-timeline?page=0&size=50
```

The endpoint remains under `/api/order/**`, so the existing `ORDER_READ` rule
authorizes it. No new role or write permission is introduced.

## Sources

The projection may read only existing durable records:

- order-placement outbox / cancellation recovery;
- payment reconciliation;
- payment refund;
- RabbitMQ fulfillment receipt;
- durable placement dispatch (SMTP/Camel);
- order-owned durable notifications whose formal event key starts with
  `order:<orderNumber>:`;
- shipment durable timestamps.

No new recovery table, audit event store, or migration is introduced.

## Normalized states

External/UI state is deliberately small:

- `ACCEPTED` — durable work was accepted/created;
- `PENDING` — retryable/in-progress durable work;
- `COMPLETED` — durable success evidence exists;
- `UNKNOWN` — durable record says failure/ambiguity but does not prove provider rejection;
- `REJECTED` — durable cancellation/compensation evidence;
- `MANUAL_REVIEW` — automatic recovery is intentionally parked.

Provider ambiguity is never rewritten as rejection.

## Data minimization

Timeline responses may expose stable business/recovery references and safe state
metadata. They must not expose:

- lease/claim tokens;
- `LAST_ERROR` contents or stack traces;
- notification recipient/body/subject;
- payment secrets;
- shipment tracking number;
- credentials or provider secrets.

## Query budget and ordering

One timeline page is loaded by exactly one bounded read-only SQL query using a
`UNION ALL` projection over the durable tables.

Ordering is deterministic:

```text
occurredAt DESC, source ASC, type ASC, referenceId ASC
```

Pagination uses `page` and `size`; `size` is capped at 100. The number of SQL
queries does not grow with timeline length (no N+1).

`occurredAt` is the best durable timestamp available for the projected state.
For states whose exact transition timestamp was never persisted, it is a snapshot
timestamp and must not be described as an exact event time.

## Frontend showcase

After a successful order placement, the Order page fetches the recovery timeline
and renders it read-only. Operators may refresh the projection. There are no
redrive/retry/write controls in S22-09.

## Acceptance criteria

### AC-S22-09-READ
`GET /api/order/{orderNumber}/recovery-timeline` returns only entries for that
order and uses the existing `ORDER_READ` security boundary.

### AC-S22-09-STATE
The response distinguishes pending/completed/unknown/rejected/manual-review
semantics and does not infer provider rejection from a generic durable failure.

### AC-S22-09-SAFE
Claim tokens, last-error text, notification contents, recipient identity and
tracking numbers are absent from the query and response model.

### AC-S22-09-QUERY
A page executes one native SQL query, with stable ordering and bounded
pagination. Adding more timeline rows cannot create N+1 query growth.

### AC-S22-09-SHOWCASE
The successful-placement UI renders and refreshes the timeline without adding
any mutation or redrive action.

## Non-goals

- no event-sourcing claim;
- no complete historical audit log;
- no provider-level exactly-once claim;
- no new persistence table or migration;
- no recovery redrive button;
- no new authorization role;
- no exposure of internal claims/errors/secrets.

## Completion posture

Builder/test evidence can advance this slice to **IMPLEMENTED + TESTED** only.
`REVIEWED` / `CLOSED` require independent review/evaluator evidence.
