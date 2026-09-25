package com.cp.ecommerce.domain.payment.port.outgoing;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

/**
 * Atomically prepares a local payment capture and its durable provider-operation reconciliation intent.
 */
public interface PreparePaymentProviderOperationOutPort {

    /**
     * Persists the pending payment and its reconciliation intent in one local transaction.
     */
    PaymentTransaction prepareCapture(String operationId, PaymentTransaction pendingPayment);

    /**
     * Reuses the current owner's reconciliation claim while preparing a capture replay.
     */
    PaymentTransaction prepareCapture(
            String operationId,
            PaymentTransaction pendingPayment,
            PaymentRecoveryContext recoveryContext);

    /**
     * Atomically reserves a refund and creates/reuses its reconciliation intent.
     */
    PaymentRefundClaim prepareRefund(String refundId, String orderNumber, BigDecimal amount);

    /**
     * Atomically verifies capture-recovery ownership and prepares a new whole-order refund operation.
     */
    PaymentRefundClaim prepareRefundAfterCaptureRecovery(
            String refundId,
            String orderNumber,
            PaymentRecoveryContext captureRecoveryContext);

    /**
     * Reuses the current owner's reconciliation claim while preparing a refund replay.
     */
    PaymentRefundClaim prepareRefund(
            String refundId,
            String orderNumber,
            BigDecimal amount,
            PaymentRecoveryContext recoveryContext);
}
