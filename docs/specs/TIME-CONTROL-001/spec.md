# TIME-CONTROL-001 - controlled operational clocks

## Acceptance criteria
- AC-001: saga lease and terminal timestamps derive from injected `Clock`.
- AC-002: idempotency stale arbitration and persisted timestamps derive from injected `Clock`.
- AC-003: SQS/Kafka order-event timestamps derive from injected `Clock`.
- AC-004: the controlled-time guard rejects direct wall-clock regression in these classes.
- AC-005: focused and full repository tests remain green.
