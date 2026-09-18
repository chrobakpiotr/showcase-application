# Plan - DURABLE-RECOVERY-001

1. Record the recovery state machines and transaction ownership.
2. Add `COMPENSATING` to placement outbox recovery.
3. Retry compensation until stock release and refund both succeed.
4. Add durable notification delivery metadata and row claiming.
5. Make notification delivery failure non-fatal to the parent business workflow.
6. Add a scheduled retry worker.
7. Prove both recovery paths against PostgreSQL.
8. Run previous priority regressions and full repository gates.
