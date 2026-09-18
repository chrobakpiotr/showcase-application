package com.cp.ecommerce.domain.payment;

/**
 * Result of trying to claim a durable refund operation.
 */
public enum PaymentRefundOutcome {

    RESERVED,
    RETRY,
    COMPLETED,
    NOTHING_TO_REFUND
}
