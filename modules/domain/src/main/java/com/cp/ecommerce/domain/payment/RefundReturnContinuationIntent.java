package com.cp.ecommerce.domain.payment;

import java.math.BigDecimal;

/** Immutable durable identity required to resume one refund-to-RMA continuation. */
public record RefundReturnContinuationIntent(String refundId, String returnNumber, String orderNumber,
        BigDecimal refundAmount) {
}
