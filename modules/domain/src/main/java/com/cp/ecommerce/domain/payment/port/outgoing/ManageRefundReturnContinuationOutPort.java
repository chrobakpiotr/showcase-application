package com.cp.ecommerce.domain.payment.port.outgoing;

import java.util.List;

/** Durable mapping and recovery queue for a refund that must complete one RMA. */
public interface ManageRefundReturnContinuationOutPort {

    void start(String refundId, String returnNumber);

    List<String> findRecoverableReturnNumbers(int limit);

    void completeByReturnNumber(String returnNumber);
}
