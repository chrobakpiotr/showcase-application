package com.cp.ecommerce.domain.payment.port.outgoing;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.payment.RefundReturnContinuationIntent;

/** Durable mapping and bounded recovery queue for a refund that must complete one RMA. */
public interface ManageRefundReturnContinuationOutPort {

    void start(String refundId, String returnNumber, String orderNumber, BigDecimal refundAmount);

    List<String> findRecoverableReturnNumbers(int limit);

    RefundReturnContinuationIntent findByReturnNumber(String returnNumber);

    void completeByReturnNumber(String returnNumber);

    void recordFailure(String returnNumber, String error);
}
