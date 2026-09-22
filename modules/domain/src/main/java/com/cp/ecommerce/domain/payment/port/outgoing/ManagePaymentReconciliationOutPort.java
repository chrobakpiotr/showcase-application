package com.cp.ecommerce.domain.payment.port.outgoing;

import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStartOutcome;

/**
 * Durable boundary for provider-operation reconciliation intents.
 */
public interface ManagePaymentReconciliationOutPort {

    /**
     * Persists a provider operation before the remote mutation is attempted.
     *
     * <p>
     * Replaying the same immutable identity is a no-op. Reusing an operation id with conflicting identity is rejected.
     */
    PaymentReconciliationStartOutcome start(
            String operationId,
            String orderNumber,
            PaymentProviderOperationType type,
            String refundId);

    /**
     * Marks a previously started provider operation as locally completed.
     *
     * <p>
     * Missing rows are tolerated so historical terminal payments/refunds can be replayed safely.
     */
    void complete(String operationId);
}
