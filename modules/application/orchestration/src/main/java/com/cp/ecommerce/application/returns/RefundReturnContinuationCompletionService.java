package com.cp.ecommerce.application.returns;

import com.cp.ecommerce.domain.payment.port.incoming.CompleteRefundReturnContinuationInPort;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Application continuation invoked by durable payment recovery. */
@Service
@RequiredArgsConstructor
class RefundReturnContinuationCompletionService implements CompleteRefundReturnContinuationInPort {

    private final ReturnStateNotificationTransaction returnStateNotificationTransaction;

    @Override
    public void complete(final String returnNumber) {

        returnStateNotificationTransaction.markRefundedAndNotify(returnNumber);
    }
}
