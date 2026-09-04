package com.cp.ecommerce.domain.payment;

/**
 * Lifecycle status of a {@link PaymentTransaction}.
 */
public enum PaymentStatus {

    /**
     * No capture has been attempted yet (or none was found) - the default/placeholder status returned by
     * {@code GetPaymentInPort#getPayment} for an order the saga hasn't reached its payment-capture step for yet, mirroring how
     * {@code StockLevel} represents a never-received SKU as a zero-quantity object rather than {@code null}.
     */
    PENDING,

    /**
     * The gateway accepted the charge; {@link PaymentTransaction#getGatewayReference()} identifies it.
     */
    CAPTURED,

    /**
     * The gateway declined the charge (see {@code PaymentDeclinedException}) - a genuine business outcome, not a technical
     * failure, kept for audit purposes even though the order itself is cancelled as a result.
     */
    DECLINED,

    /**
     * A previously {@link #CAPTURED} payment was refunded, e.g. because the order was later cancelled/compensated.
     */
    REFUNDED

}
