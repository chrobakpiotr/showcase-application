package com.cp.ecommerce.domain.payment.port.outgoing;

import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStartOutcome;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;

/**
 * Durable boundary for provider-operation reconciliation intents.
 */
public interface ManagePaymentReconciliationOutPort {

    /**
     * Persists a provider operation before the remote mutation is attempted.
     */
    PaymentReconciliationStartOutcome start(
            String operationId,
            String orderNumber,
            PaymentProviderOperationType type,
            String refundId);

    /**
     * Checks that a recovery worker still owns the exact durable provider operation.
     */
    PaymentReconciliationStartOutcome startOwned(
            String operationId,
            String orderNumber,
            PaymentProviderOperationType type,
            String refundId,
            PaymentRecoveryContext recoveryContext);

    /**
     * Marks an unclaimed provider operation as locally completed.
     */
    void complete(String operationId);

    /**
     * Marks the provider operation complete only if the supplied claim still owns it.
     */
    void completeOwned(PaymentRecoveryContext recoveryContext);
}
