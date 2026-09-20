package com.cp.ecommerce.application.order;

import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
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
        final String reservationId = stockReservationId(order);
        order.getItems().forEach(item -> manageStockInPort.releaseStock(reservationId, item.getSku()));
        PaymentTransaction payment = managePaymentInPort.refundPayment(order.getOrderNumber());
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING && payment.getCreated() != null) {
            payment = managePaymentInPort.capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod());
            if (payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
                payment = managePaymentInPort.refundPayment(order.getOrderNumber());
            }
        }
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING && payment.getCreated() != null) {
            return order;
        }
        if (managePaymentInPort.hasPendingRefunds(order.getOrderNumber())) {
            return order;
        }

        sendNotificationInPort.sendNotification(
                order.getCustomer().getContact().getEmail(),
                NotificationType.ORDER_CANCELLED,
                "Order " + order.getOrderNumber() + " cancelled",
                "Your order " + order.getOrderNumber() + " was cancelled.");
        orderCancellationArbitrator.completeCancellation(orderNumber);
        return order;
    }

    private static String stockReservationId(final Order order) {

        return order.getStockReservationId() == null ? order.getOrderNumber() : order.getStockReservationId();
    }

}
