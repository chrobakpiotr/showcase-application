package com.cp.ecommerce.domain.order;

/** Result of the fenced terminal transition of one durable order cancellation. */
public enum CancellationCompletionOutcome {
    COMPLETED,
    ALREADY_COMPLETED,
    LOST_CLAIM,
    CONFLICT
}
