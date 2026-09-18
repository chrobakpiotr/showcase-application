package com.cp.ecommerce.domain.payment;

import java.math.BigDecimal;

/**
 * Durable refund claim returned by the persistence arbitration boundary.
 */
public record PaymentRefundClaim(PaymentRefundOutcome outcome, String refundId, String orderNumber, BigDecimal amount,
        String gatewayReference, PaymentTransaction payment) {
}
