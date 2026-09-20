package com.cp.ecommerce.application.returns;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.RequestReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Application workflow for RMA creation and moderation.
 */
@Service
@RequiredArgsConstructor
public class ReturnService implements ReturnWorkflow {

    private static final String RETURN_NOT_FOUND = "Return request not found";

    private final RequestReturnInPort requestReturnInPort;

    private final ReturnModerationInPort returnModerationInPort;

    private final ManageOrderUseCase manageOrderUseCase;

    private final ManagePaymentInPort managePaymentInPort;

    private final RefundEntitlementCalculator refundEntitlementCalculator;

    private final ReturnStateNotificationTransaction returnStateNotificationTransaction;

    @Override
    public ReturnRequest requestReturn(final String orderNumber, final String sku, final int quantity, final String reason) {

        final Order order = requireOrder(orderNumber);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ApplicationConflictException("Only CONFIRMED orders can be returned");
        }
        final OrderLineItem item = order.getItems()
                .stream()
                .filter(line -> line.getSku().equals(sku))
                .findFirst()
                .orElseThrow(() -> new ApplicationNotFoundException("Order line item not found"));
        final var lineEntitlement = refundEntitlementCalculator.lineEntitlement(order, sku);
        return requestReturnInPort
                .requestReturnFromLineEntitlement(orderNumber, sku, quantity, item.getQuantity(), reason, lineEntitlement);
    }

    @Override
    public ReturnRequest approveReturn(final String returnNumber) {

        final ReturnRequest approved = returnModerationInPort.approveReturn(returnNumber);
        if (approved == null) {
            throw new ApplicationNotFoundException(RETURN_NOT_FOUND);
        }
        if (approved.getStatus() != ReturnStatus.REFUNDED && approved.getRefundAmount().signum() > 0) {
            managePaymentInPort
                    .refundPayment(approved.getOrderNumber(), approved.getReturnNumber(), approved.getRefundAmount());
        }
        return returnStateNotificationTransaction.markRefundedAndNotify(returnNumber);
    }

    @Override
    public ReturnRequest rejectReturn(final String returnNumber) {

        return returnStateNotificationTransaction.rejectAndNotify(returnNumber);
    }

    private Order requireOrder(final String orderNumber) {

        final Order order = manageOrderUseCase.findOrder(orderNumber);
        if (order == null) {
            throw new ApplicationNotFoundException("Order not found");
        }
        return order;
    }
}
