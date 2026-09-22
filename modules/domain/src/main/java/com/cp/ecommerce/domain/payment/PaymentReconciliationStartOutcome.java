package com.cp.ecommerce.domain.payment;

/**
 * Result of preparing a durable provider-operation reconciliation intent.
 *
 * <p>
 * Only {@link #READY} authorizes automatic provider I/O. Other outcomes preserve existing durable ownership or
 * operational-control state and must not be treated as permission to replay the provider mutation.
 */
public enum PaymentReconciliationStartOutcome {

    READY,
    BUSY,
    MANUAL_REVIEW,
    COMPLETED,
    BLOCKED
}
