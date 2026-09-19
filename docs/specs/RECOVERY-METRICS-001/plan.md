# Plan - RECOVERY-METRICS-001

1. Add aggregate queries for durable recovery state.
2. Register low-cardinality Micrometer gauges for outbox and notification recovery.
3. Record unknown capture outcomes at the payment gateway boundary.
4. Document exact metric semantics.
5. Run focused persistence tests and full repository gates.
