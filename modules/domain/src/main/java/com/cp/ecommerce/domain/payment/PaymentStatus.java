package com.cp.ecommerce.domain.payment;

/**
 * Lifecycle status of a {@link PaymentTransaction}.
 */
public enum PaymentStatus {

    PENDING,
    CAPTURED,
    PARTIALLY_REFUNDED,
    DECLINED,
    REFUNDED
}
