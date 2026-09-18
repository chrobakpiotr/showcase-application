# INV-TX-001 - optimistic stock retry owns a fresh transaction per attempt

## Intent

Generic inventory mutations use optimistic locking and a bounded retry loop. Today the retry loop lives in
`ManageStockUseCase`, while `FindStockLevelOutPort` and `SaveStockLevelOutPort` can join an ambient caller
transaction.

When a versioned `saveAndFlush` fails inside that ambient transaction:

- the JPA transaction can be marked rollback-only;
- the persistence context can still contain the stale `StockLevelEntity`;
- retrying inside the same transaction does not provide a fresh read;
- a caught `StockLevelConflictException` therefore does not guarantee a usable retry.

R06 makes one optimistic retry attempt the transaction boundary.

## Acceptance criteria

- AC-001: a PostgreSQL RED test reproduces generic stock mutation inside an ambient transaction with a stale
  entity in the outer persistence context.
- AC-002: the RED scenario succeeds after the fix without marking the ambient transaction rollback-only.
- AC-003: each generic mutation attempt executes `read -> business mutation -> saveAndFlush` in a fresh
  `REQUIRES_NEW` transaction.
- AC-004: `ManageStockUseCase` still owns the bounded retry count of 3 and still re-evaluates the mutation on
  fresh state after a conflict.
- AC-005: `reserveStock` and `fulfillStock` business rules are evaluated against the fresh state of that
  attempt.
- AC-006: identity-aware reservation operations from R02 remain unchanged and continue using their
  pessimistic-lock ledger transaction.
- AC-007: optimistic lock failures are still translated to `StockLevelConflictException`.
- AC-008: a missing stock row still behaves as zero stock for the first generic receive operation.
- AC-009: no JPA or Spring transaction type leaks into the domain use case.
- AC-010: unit tests cover retry success, retry exhaustion, fresh-state business rejection, persistence
  mapping failures and optimistic conflicts.
- AC-011: PostgreSQL acceptance proves the outer transaction remains usable and commits its own marker write.
- AC-012: R01, R02 and R04 regression tests remain green.
- AC-013: all available module quality gates and the full repository build are green.

## Transaction ownership

For generic optimistic stock mutations only:

1. `ManageStockUseCase` asks `MutateStockLevelOutPort` to execute one attempt.
2. The persistence adapter starts `REQUIRES_NEW`, suspending any ambient caller transaction.
3. It loads the current stock row in that fresh persistence context.
4. It invokes the domain mutation function.
5. It performs the versioned `saveAndFlush`.
6. On optimistic conflict the attempt rolls back and throws `StockLevelConflictException`.
7. The use case retries by invoking the port again, creating another fresh transaction.

This intentionally means a generic stock mutation is not atomically enlisted in an unrelated ambient
transaction. A caller needing a larger atomic unit must own and retry that larger unit instead of relying
on an inner optimistic retry after its transaction has already failed.

## Out of scope

- changing the R02 identity-aware reservation ledger;
- changing the retry count;
- generalized transaction retry infrastructure for every bounded context;
- R07 durable compensation and notification retry.
