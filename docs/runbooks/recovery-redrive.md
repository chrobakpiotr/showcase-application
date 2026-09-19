# Recovery redrive runbook

## Safety invariants

- Never mutate outbox, payment, stock-reservation or notification rows by hand before inspecting their durable identity.
- Re-drive uses the existing worker paths so claim fencing and idempotency remain active.
- Do not delete idempotency keys or reservation rows inside the declared replay window.
- Keep order number, event id and operation id in logs, not metric labels.

## Triage

1. Inspect outbox status, attempts, claim owner and claim expiry.
2. Inspect payment state and refund ledger.
3. Inspect stock reservation state for the order-owned reservation id.
4. Inspect notification status, attempts and next-attempt time.
5. Confirm the external side effect state when its provider offers an idempotency lookup.

## Redrive

1. Stop only the affected worker if controlled isolation is required.
2. Clear no durable deduplication identities.
3. Make the row eligible using the supported retry state transition.
4. Restart or invoke the normal worker.
5. Verify the same durable identity reaches its terminal state.
6. Record operator, reason, before/after state and timestamp.

## Retention

Retention jobs must support dry-run. Rows referenced by an active replay or idempotency window are not eligible for deletion.
