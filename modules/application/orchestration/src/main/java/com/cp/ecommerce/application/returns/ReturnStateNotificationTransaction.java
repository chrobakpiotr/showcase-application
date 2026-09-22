package com.cp.ecommerce.application.returns;

import com.cp.ecommerce.domain.notification.NotificationEventKey;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Commits a terminal return state and its durable notification enqueue atomically.
 */
@Service
@RequiredArgsConstructor
public class ReturnStateNotificationTransaction {

    private final ReturnModerationInPort returnModerationInPort;

    private final ManageOrderUseCase manageOrderUseCase;

    private final SendNotificationInPort sendNotificationInPort;

    private final ManageRefundReturnContinuationOutPort manageRefundReturnContinuationOutPort;

    @Transactional
    public ReturnRequest markRefundedAndNotify(final String returnNumber) {

        final ReturnRequest refunded = returnModerationInPort.markRefunded(returnNumber);
        if (refunded == null) {
            throw new ApplicationNotFoundException("Return request not found");
        }
        notify(refunded, NotificationType.RETURN_REFUNDED, "refunded");
        manageRefundReturnContinuationOutPort.completeByReturnNumber(returnNumber);
        return refunded;
    }

    @Transactional
    public ReturnRequest rejectAndNotify(final String returnNumber) {

        final ReturnRequest rejected = returnModerationInPort.rejectReturn(returnNumber);
        if (rejected == null) {
            throw new ApplicationNotFoundException("Return request not found");
        }
        notify(rejected, NotificationType.RETURN_REJECTED, "rejected");
        return rejected;
    }

    private void notify(final ReturnRequest request, final NotificationType type, final String state) {

        final Order order = manageOrderUseCase.findOrder(request.getOrderNumber());
        if (order == null) {
            throw new ApplicationNotFoundException("Order not found");
        }
        sendNotificationInPort.sendNotification(
                NotificationEventKey.of("return", request.getReturnNumber(), type, state + "-v1"),
                order.getCustomer().getContact().getEmail(),
                type,
                "Return " + request.getReturnNumber() + " " + state,
                "Your return request " + request.getReturnNumber() + " was " + state + ".");
    }
}
