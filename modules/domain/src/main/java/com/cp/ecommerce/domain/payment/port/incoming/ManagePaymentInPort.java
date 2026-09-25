package com.cp.ecommerce.domain.payment.port.incoming;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;

/**
 * Incoming port for capturing and refunding an order's payment.
 */
public interface ManagePaymentInPort {

    /**
     * Captures the order amount. CAPTURED, PARTIALLY_REFUNDED and REFUNDED transactions are never charged again.
     *
     * @throws PaymentDeclinedException if the gateway declines the charge.
     */
    PaymentTransaction capturePayment(String orderNumber, BigDecimal amount, PaymentMethod method);

    /**
     * Replays a capture as the current durable reconciliation owner.
     */
    PaymentTransaction recoverCapturePayment(
            String orderNumber,
            BigDecimal amount,
            PaymentMethod method,
            PaymentRecoveryContext recoveryContext);

    /**
     * Replays capture as the durable reconciliation owner but leaves completion to the caller so post-capture durable
     * continuations can finish first.
     */
    PaymentTransaction recoverCapturePaymentPendingCompletion(
            String orderNumber,
            BigDecimal amount,
            PaymentMethod method,
            PaymentRecoveryContext recoveryContext);

    /**
     * Refunds the remaining refundable amount for an order. Safe to retry.
     */
    PaymentTransaction refundPayment(String orderNumber);

    /**
     * Refunds the remaining order amount only when the supplied worker still owns the capture reconciliation.
     *
     * <p>
     * The capture ownership check and durable whole-order refund preparation form one short local transaction; provider I/O
     * happens only after that authorization has been durably transferred to the refund operation.
     */
    PaymentTransaction refundPaymentAfterCaptureRecovery(String orderNumber, PaymentRecoveryContext captureRecoveryContext);

    /**
     * Refunds a specific amount under a durable idempotency identity, e.g. one return/RMA number.
     */
    PaymentTransaction refundPayment(String orderNumber, String refundId, BigDecimal amount);

    /**
     * Replays a specific refund as the current durable reconciliation owner.
     */
    PaymentTransaction recoverRefundPayment(
            String orderNumber,
            String refundId,
            BigDecimal amount,
            PaymentRecoveryContext recoveryContext);

    /**
     * Returns true while at least one durable refund for the order has not reached provider-confirmed completion.
     */
    boolean hasPendingRefunds(String orderNumber);
}
