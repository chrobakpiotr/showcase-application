package com.cp.ecommerce.domain.order.recovery;

/**
 * Normalized state exposed by the read-only order recovery timeline.
 */
public enum OrderRecoveryTimelineState {
    ACCEPTED,
    PENDING,
    COMPLETED,
    UNKNOWN,
    REJECTED,
    MANUAL_REVIEW
}
