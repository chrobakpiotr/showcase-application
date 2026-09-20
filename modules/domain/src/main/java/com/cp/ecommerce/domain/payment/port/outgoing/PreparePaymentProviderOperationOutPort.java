package com.cp.ecommerce.domain.payment.port.outgoing;

import com.cp.ecommerce.domain.payment.PaymentTransaction;

/**
 * Atomically prepares a local payment capture and its durable provider-operation reconciliation intent.
 */
public interface PreparePaymentProviderOperationOutPort {

    /**
     * Persists the pending payment and its reconciliation intent in one local transaction.
     *
     * @param operationId stable provider operation id
     * @param pendingPayment pending payment snapshot
     * @return persisted pending payment
     */
    PaymentTransaction prepareCapture(String operationId, PaymentTransaction pendingPayment);
}
