package com.cp.ecommerce.domain.payment;

/**
 * Provider-side payment mutation that may require durable reconciliation.
 */
public enum PaymentProviderOperationType {

    CAPTURE,
    REFUND
}
