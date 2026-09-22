package com.cp.ecommerce.domain.payment.port.incoming;

/** Completes local RMA state after a durable provider refund has completed. */
public interface CompleteRefundReturnContinuationInPort {

    void complete(String returnNumber);
}
