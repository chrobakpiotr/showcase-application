# 0047. Order outbox polling uses bounded candidate batches

## Context

ADR 0042 introduced short leased claims and fencing for multi-worker outbox
processing. Candidate discovery still materialized every matching `PENDING`,
expired `PROCESSING` or `COMPENSATING` row before individual claims were attempted.

A large backlog could therefore turn one scheduler tick into an unbounded database
read and in-memory list.

## Decision

Each candidate query reads only the first 50 rows in creation order.

The claim transaction, fencing token and recovery state machine remain unchanged.
The fixed bound is deliberately small and deterministic for the showcase; later
throughput tuning can make it configurable if measurement justifies that complexity.

## Consequences

- one scheduler tick has bounded candidate memory and query result size;
- backlog drains over repeated polls rather than being fully materialized;
- multiple workers may read overlapping candidate batches, but ADR 0042 fencing
  continues to determine ownership;
- this does not change the at-least-once external-effect guarantee.
