# Backlog

Open work for this repository, newest first. Remove an item when it is done; Git history keeps it.

The [roadmap](improvement-roadmap.md) summarizes completed execution, qualification and external dependencies.


- Owner decision: accept or change the client-data unique-key allowlist that maps to 409 in [API-DATA-CONFLICT-001](specs/API-DATA-CONFLICT-001/spec.md).
- Exception messages outside the web error log (security review 2026-11-11, [report](reviews/log-hygiene-2026-11-11/review.md)): persisted last-error columns (`OrderPlacementSagaOrchestrator`, `OrderPlacementDispatchManager`, `PaymentReconciliationScheduler`, `ManageNotificationUseCase`, `OrderCancellationRecoveryScheduler`), messages returned to clients by `DispatchRedriveController`, `ParkedDispatchController` and `ProductController`, and raw messages in `OrderAnalyticsConsumerMetrics` logs. Check each for database messages that echo personal data and apply the `DatabaseFailures` redaction where they can.

- **Standing rule (owner, 2026-11-06):** after every commit that changes this project's code, update `docs/handover/AGENT_HANDOVER.md`. See "Handover file" in `AGENTS.md`.
