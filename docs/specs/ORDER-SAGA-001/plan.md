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
