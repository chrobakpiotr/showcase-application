# Implementation plan

1. Add a PostgreSQL-backed red reproducer for
   `place -> cancel before first poll -> poll`.
   Disable the scheduled publisher and invoke the poll manually so ordering is
   deterministic. No production code changes are allowed in this step.
2. Record the failing evidence: SHA, exact Gradle command and assertion showing that a
   cancelled order is captured/fulfilled by the pending saga.
3. Define the cancellation/saga state machine and concurrency winner rules. Cover both
   cancel-before-capture and cancel-after-capture/before-fulfillment-retry. Add/supersede
   an ADR if the durable transition changes existing architecture contracts.
4. Implement the smallest atomic/durable transition that makes the reproducer green.
   Do not use a lone status check as the concurrency fix.
5. Add deterministic PostgreSQL tests for:
   - capture -> fulfillment retry -> cancel/refund -> poll,
   - simultaneous cancel vs poll using barriers/latches,
   - restart/retry of incomplete cancellation work.
6. Run focused tests, full application/backend gates, then independent evaluator review.

R02 and R03 must be considered in the design, but their larger persistence models remain
separate follow-up changes unless R01 cannot be made correct without a minimal shared
primitive.

## T-002 decision checkpoint

The R01 implementation must follow ADR 0037:

1. add locked lookup of the placement outbox row by order / id;
2. re-check `PENDING` under `PESSIMISTIC_WRITE` in the poll transaction before payment capture;
3. arbitrate customer cancellation on the same row;
4. introduce `CANCELLING` / `CANCELLED` process states and fence placement work once cancellation wins;
5. make `REFUNDED` non-capturable as defense-in-depth;
6. add deterministic PostgreSQL tests for:
   - cancel before first poll,
   - capture + fulfillment retry + cancel/refund + later poll,
   - simultaneous poll/cancel with barriers/latches and an asserted winner;
7. keep crash-safe per-item stock recovery coordinated with R02 and general durable retry mechanics
   coordinated with R07 rather than hiding either behind unsafe repeated SKU release.
