package com.cp.ecommerce.domain.payment.port.outgoing;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

/**
 * Atomic persistence boundary for refund claim and completion state.
 */
public interface ManagePaymentRefundOutPort {

    PaymentRefundClaim reserve(String refundId, String orderNumber, BigDecimal amount);

    PaymentRefundClaim reserveRemaining(String refundId, String orderNumber);

    PaymentTransaction complete(String refundId);

    boolean hasPending(String orderNumber);
}
