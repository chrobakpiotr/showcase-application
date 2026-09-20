package com.cp.ecommerce.domain.payment;

/**
 * Durable local state of a provider operation that may need reconciliation.
 */
public enum PaymentReconciliationStatus {

    PENDING,
    COMPLETED,
    FAILED,
    MANUAL_REVIEW
}
