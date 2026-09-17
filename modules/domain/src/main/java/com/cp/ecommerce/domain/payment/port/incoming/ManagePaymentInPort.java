package com.cp.ecommerce.domain.payment.port.incoming;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.exception.PaymentDeclinedException;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

/**
 * Incoming port for capturing and refunding an order's payment.
 */
public interface ManagePaymentInPort {

    /**
     * Charges {@code amount} for {@code orderNumber} via {@code method}. Idempotent: if a
     * {@link com.cp.ecommerce.domain.payment.PaymentStatus#CAPTURED} transaction already exists for this order, the existing
     * transaction is returned unchanged instead of charging a second time - this is what lets the order-placement saga simply
     * re-invoke this method on every poll (see {@code OrderPlacementSagaOrchestrator}) without needing its own
     * already-attempted bookkeeping.
     *
     * @throws PaymentDeclinedException if the gateway declines the charge. The decline itself is still durably recorded (as
     *             {@link com.cp.ecommerce.domain.payment.PaymentStatus#DECLINED}) before the exception propagates.
     */
    PaymentTransaction capturePayment(String orderNumber, BigDecimal amount, PaymentMethod method);

    /**
     * Refunds a previously captured payment for {@code orderNumber}. Idempotent no-op if no
     * {@link com.cp.ecommerce.domain.payment.PaymentStatus#CAPTURED} transaction exists (never captured, already refunded) -
     * mirrors {@code ManageStockInPort#releaseStock}'s "safe to call unconditionally" convention (ADR 0026), which lets both
     * {@code OrderController#cancelOrder} and the saga's compensating transaction call this without a separate existence check.
     */
    PaymentTransaction refundPayment(String orderNumber);

}
