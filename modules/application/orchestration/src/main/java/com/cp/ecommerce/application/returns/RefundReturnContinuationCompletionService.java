package com.cp.ecommerce.application.returns;

import java.util.Objects;

import com.cp.ecommerce.domain.payment.RefundReturnContinuationIntent;
import com.cp.ecommerce.domain.payment.port.incoming.CompleteRefundReturnContinuationInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Resumes provider refund if needed and atomically finishes the linked RMA. */
@Service
@RequiredArgsConstructor
class RefundReturnContinuationCompletionService implements CompleteRefundReturnContinuationInPort {

    private final GetReturnInPort getReturnInPort;
    private final ManagePaymentInPort managePaymentInPort;
    private final ManageRefundReturnContinuationOutPort continuationOutPort;
    private final ReturnStateNotificationTransaction returnStateNotificationTransaction;

    @Override
    public void complete(final String returnNumber) {
        final RefundReturnContinuationIntent intent = continuationOutPort.findByReturnNumber(returnNumber);
        if (intent == null) {
            throw new IllegalStateException("Refund continuation disappeared for return: " + returnNumber);
        }
        final ReturnRequest request = getReturnInPort.getReturn(returnNumber);
        if (request == null) {
            throw new ApplicationNotFoundException("Return request not found");
        }
        validateIntent(intent, request);
        if (intent.refundAmount().signum() > 0) {
            managePaymentInPort.refundPayment(intent.orderNumber(), intent.refundId(), intent.refundAmount());
        }
        returnStateNotificationTransaction.markRefundedAndNotify(returnNumber);
    }

    private static void validateIntent(final RefundReturnContinuationIntent intent, final ReturnRequest request) {
        if (!Objects.equals(intent.returnNumber(), request.getReturnNumber())
                || !Objects.equals(intent.orderNumber(), request.getOrderNumber())
                || intent.refundAmount().compareTo(request.getRefundAmount()) != 0) {
            throw new PaymentRefundConflictException(
                    "Refund continuation " + intent.refundId() + " does not match immutable RMA facts");
        }
    }
}
