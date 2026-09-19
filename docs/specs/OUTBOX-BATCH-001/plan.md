# Plan - OUTBOX-BATCH-001

1. Keep the orchestrator call sites and claim state machine unchanged.
2. Bound repository candidate reads with a first-page limit of 50.
3. Prove the default repository methods always delegate with the expected limit.
4. Re-run the multi-worker PostgreSQL regression.
5. Run full repository gates after the combined roadmap series.
