package com.cp.ecommerce.domain.order.recovery;

import java.time.Instant;

/**
 * One safe read-only projection row from an existing durable recovery record.
 *
 * @param source durable workflow family.
 * @param type source-specific operation/event type.
 * @param state normalized recovery state.
 * @param occurredAt best durable timestamp available for the projected state.
 * @param referenceId stable safe business/recovery reference.
 * @param summary concise safe summary; never contains claim tokens or provider error details.
 */
public record OrderRecoveryTimelineEntry(String source, String type, OrderRecoveryTimelineState state, Instant occurredAt,
        String referenceId, String summary) {
}
