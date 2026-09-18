# Plan - INV-TX-001

1. Add a PostgreSQL RED test that caches a stale stock entity in an ambient transaction, commits a
   concurrent `REQUIRES_NEW` update, then invokes the generic inventory mutation.
2. Record ADR 0040.
3. Replace the split find/save mutation path with a transaction-owning `MutateStockLevelOutPort`.
4. Keep retry policy and business mutation logic in `ManageStockUseCase`.
5. Add persistence and domain unit coverage.
6. Run PostgreSQL acceptance, R01/R02/R04 regression, SDD validation and full repository gates.
