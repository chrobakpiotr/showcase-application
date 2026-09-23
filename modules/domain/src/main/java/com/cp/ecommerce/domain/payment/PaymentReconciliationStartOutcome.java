package com.cp.ecommerce.domain.payment;

/**
 * Result of preparing a durable provider-operation reconciliation intent.
 *
 * <p>
 * {@link #READY} authorizes an unclaimed client/provider operation and {@link #CURRENT_OWNER} authorizes the currently fenced
 * reconciliation worker. Other outcomes do not authorize new provider I/O.
 */
public enum PaymentReconciliationStartOutcome {

    READY,
    CURRENT_OWNER,
    BUSY,
    LOST_CLAIM,
    MANUAL_REVIEW,
    COMPLETED,
    BLOCKED
}
