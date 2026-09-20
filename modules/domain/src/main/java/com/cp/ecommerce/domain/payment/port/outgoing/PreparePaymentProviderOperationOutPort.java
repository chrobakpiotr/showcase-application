package com.cp.ecommerce.domain.payment.port.outgoing;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
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

    /**
     * Atomically reserves a refund and creates/reuses its reconciliation intent.
     *
     * @param refundId stable provider refund identity
     * @param orderNumber order being refunded
     * @param amount requested amount; {@code null} means all currently refundable funds
     * @return durable refund claim
     */
    PaymentRefundClaim prepareRefund(String refundId, String orderNumber, BigDecimal amount);

}
