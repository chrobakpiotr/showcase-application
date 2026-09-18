# 0040. Generic stock optimistic retries own one transaction per attempt

## Context

ADR 0026 put the retry loop in `ManageStockUseCase` and used `saveAndFlush` so optimistic conflicts become
visible at the retry boundary. That works when the stock persistence calls own their transaction lifecycle.

It is not safe when a caller already has a transaction. The inventory repository calls then join that
transaction. Once Hibernate detects a stale version during flush, the transaction can be rollback-only and
its first-level cache can still contain the stale stock entity. Catching the translated domain exception
does not repair either condition.

## Decision

Generic stock mutations use a new outbound port, `MutateStockLevelOutPort`.

One call to that port represents exactly one optimistic attempt. Its persistence adapter runs with
`Propagation.REQUIRES_NEW` and performs the complete attempt:

1. load current stock or synthesize zero stock for a missing SKU;
2. map to the domain object;
3. apply the domain-supplied mutation;
4. validate the mutated object;
5. map and `saveAndFlush`;
6. translate optimistic locking failures to `StockLevelConflictException`.

`ManageStockUseCase` keeps the three-attempt retry loop. A conflict therefore causes a second call through
the Spring proxy and a second fresh transaction.

The identity-aware R02 reservation path remains separate and unchanged. It already owns an atomic
pessimistic-lock transaction for stock plus its reservation ledger.

## Consequences

- a failed optimistic attempt cannot poison the next attempt;
- retries re-read from a fresh persistence context;
- business rules are re-evaluated on the state read by the new attempt;
- ambient transactions are suspended while a generic stock attempt runs;
- generic stock mutation commit/rollback is independent of the ambient caller transaction;
- callers that require a larger atomic transaction must retry that larger transaction as one unit;
- the old split `SaveStockLevelOutPort` persistence mutation path is removed.
