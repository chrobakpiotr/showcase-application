package com.cp.ecommerce.application.returns;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.RequestReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReturnService implements ReturnWorkflow {

    private static final String RETURN_NOT_FOUND = "Return request not found";

    private final RequestReturnInPort requestReturnInPort;

    private final GetReturnInPort getReturnInPort;

    private final ReturnModerationInPort returnModerationInPort;

    private final ManageOrderUseCase manageOrderUseCase;

    private final ManagePaymentInPort managePaymentInPort;

    private final SendNotificationInPort sendNotificationInPort;

    @Override
    public ReturnRequest requestReturn(final String orderNumber, final String sku, final int quantity, final String reason) {

        final Order order = requireOrder(orderNumber);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only CONFIRMED orders can be returned");
        }
        final OrderLineItem item = order.getItems()
                .stream()
                .filter(line -> line.getSku().equals(sku))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order line item not found"));
        final BigDecimal amount = item.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        return requestReturnInPort.requestReturn(orderNumber, sku, quantity, item.getQuantity(), reason, amount);
    }

    @Override
    public ReturnRequest approveReturn(final String returnNumber) {

        final ReturnRequest existing = getReturnInPort.getReturn(returnNumber);
        final ReturnRequest approved = returnModerationInPort.approveReturn(returnNumber);
        if (approved == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, RETURN_NOT_FOUND);
        }
        if (approved.getStatus() != ReturnStatus.REFUNDED) {
            managePaymentInPort
                    .refundPayment(approved.getOrderNumber(), approved.getReturnNumber(), approved.getRefundAmount());
        }
        final ReturnRequest refunded = returnModerationInPort.markRefunded(returnNumber);
        if (refunded == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, RETURN_NOT_FOUND);
        }
        if (existing == null || existing.getStatus() != ReturnStatus.REFUNDED) {
            notify(refunded, NotificationType.RETURN_REFUNDED, "refunded");
        }
        return refunded;
    }

    @Override
    public ReturnRequest rejectReturn(final String returnNumber) {

        final ReturnRequest existing = getReturnInPort.getReturn(returnNumber);
        final ReturnRequest rejected = returnModerationInPort.rejectReturn(returnNumber);
        if (rejected == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, RETURN_NOT_FOUND);
        }
        if (existing == null || existing.getStatus() != ReturnStatus.REJECTED) {
            notify(rejected, NotificationType.RETURN_REJECTED, "rejected");
        }
        return rejected;
    }

    private void notify(final ReturnRequest request, final NotificationType type, final String state) {

        final Order order = requireOrder(request.getOrderNumber());
        sendNotificationInPort.sendNotification(
                order.getCustomer().getContact().getEmail(),
                type,
                "Return " + request.getReturnNumber() + " " + state,
                "Your return request " + request.getReturnNumber() + " was " + state + ".");
    }

    private Order requireOrder(final String orderNumber) {

        final Order order = manageOrderUseCase.findOrder(orderNumber);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order;
    }
}
