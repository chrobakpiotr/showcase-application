# Plan - TIME-CONTROL-001
1. Inject `Clock` into saga/idempotency paths.
2. Inject `Clock` into SQS/Kafka publishers.
3. Keep legacy `Date` boundaries via conversion.
4. Add a narrow regression guard.
5. Run focused and full repository gates.
