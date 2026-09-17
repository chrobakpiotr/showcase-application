package com.cp.ecommerce.application.order;

import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.RequestOrderCancellationInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;

import org.springframework.stereotype.Service;

/**
 * Coordinates the existing customer-requested order cancellation side effects.
 *
 * <p>
 * This class intentionally preserves the pre-existing sequence. Durable recovery, concurrency and saga/cancellation race
 * semantics are handled by follow-up features.
 */
@Service
public class CancelOrderService implements CancelOrderWorkflow {

    private final OrderCancellationArbitrator orderCancellationArbitrator;

    private final ManageStockInPort manageStockInPort;

    private final ManagePaymentInPort managePaymentInPort;

    private final SendNotificationInPort sendNotificationInPort;

    public CancelOrderService(
            final OrderCancellationArbitrator orderCancellationArbitrator,
            final ManageStockInPort manageStockInPort,
            final ManagePaymentInPort managePaymentInPort,
            final SendNotificationInPort sendNotificationInPort) {

        this.orderCancellationArbitrator = orderCancellationArbitrator;
        this.manageStockInPort = manageStockInPort;
        this.managePaymentInPort = managePaymentInPort;
        this.sendNotificationInPort = sendNotificationInPort;
    }

    @Override
    public Order cancelOrder(final String orderNumber) {

        final OrderCancellationArbitrator.CancellationStart cancellation = orderCancellationArbitrator
                .beginCancellation(orderNumber);
        final Order order = cancellation.order();
        if (order == null) {

            return null;
        }
        if (!cancellation.runSideEffects()) {

            return order;
        }
        order.getItems().forEach(item -> manageStockInPort.releaseStock(item.getSku(), item.getQuantity()));
        managePaymentInPort.refundPayment(order.getOrderNumber());
        sendNotificationInPort.sendNotification(
                order.getCustomer().getContact().getEmail(),
                NotificationType.ORDER_CANCELLED,
                "Order " + order.getOrderNumber() + " cancelled",
                "Your order " + order.getOrderNumber() + " was cancelled.");
        orderCancellationArbitrator.completeCancellation(orderNumber);
        return order;
    }

}
