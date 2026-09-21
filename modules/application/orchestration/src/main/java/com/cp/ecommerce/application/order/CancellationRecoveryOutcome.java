package com.cp.ecommerce.application.order;

/** Durable outcome of one autonomous cancellation recovery pass. */
public enum CancellationRecoveryOutcome {

    COMPLETED,
    WAITING_FOR_REFUND
}
