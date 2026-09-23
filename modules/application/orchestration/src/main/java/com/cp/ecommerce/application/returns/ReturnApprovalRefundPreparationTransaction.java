package com.cp.ecommerce.application.returns;

import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Persists APPROVED together with enough durable intent to resume before provider I/O. */
@Service
@RequiredArgsConstructor
public class ReturnApprovalRefundPreparationTransaction {

    private final ReturnModerationInPort returnModerationInPort;
    private final ManageRefundReturnContinuationOutPort continuationOutPort;
    private final ReturnStateNotificationTransaction returnStateNotificationTransaction;

    @Transactional
    ReturnRequest approveAndPrepare(final String returnNumber) {
        final ReturnRequest approved = returnModerationInPort.approveReturn(returnNumber);
        if (approved == null || approved.getStatus() == ReturnStatus.REFUNDED) {
            return approved;
        }
        if (approved.getRefundAmount().signum() == 0) {
            return returnStateNotificationTransaction.markRefundedAndNotify(returnNumber);
        }
        continuationOutPort.start(
                approved.getReturnNumber(),
                approved.getReturnNumber(),
                approved.getOrderNumber(),
                approved.getRefundAmount());
        return approved;
    }
}
