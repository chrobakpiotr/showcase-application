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

        return cancelOrder(orderNumber, null);
    }

    @Override
    public Order cancelOrder(final String orderNumber, final String claimId) {

        final OrderCancellationArbitrator.CancellationStart cancellation = orderCancellationArbitrator
                .beginCancellation(orderNumber);
        final Order order = cancellation.order();
        if (order == null || !cancellation.runSideEffects()) {

            return order;
        }

        releaseStock(order);
        if (paymentRecoveryPending(order)) {

            return order;
        }

        sendCancellationNotification(order);
        completeCancellation(orderNumber, claimId);
        return order;
    }

    private void releaseStock(final Order order) {

        final String reservationId = stockReservationId(order);
        order.getItems().forEach(item -> manageStockInPort.releaseStock(reservationId, item.getSku()));
    }

    private boolean paymentRecoveryPending(final Order order) {

        PaymentTransaction payment = managePaymentInPort.refundPayment(order.getOrderNumber());
        if (isPendingCreatedPayment(payment)) {

            payment = recoverPendingPayment(order);
        }
        return isPendingCreatedPayment(payment) || managePaymentInPort.hasPendingRefunds(order.getOrderNumber());
    }

    private PaymentTransaction recoverPendingPayment(final Order order) {

        PaymentTransaction payment = managePaymentInPort
                .capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod());
        if (requiresRefund(payment)) {

            payment = managePaymentInPort.refundPayment(order.getOrderNumber());
        }
        return payment;
    }

    private void sendCancellationNotification(final Order order) {

        sendNotificationInPort.sendNotification(
                order.getCustomer().getContact().getEmail(),
                NotificationType.ORDER_CANCELLED,
                "Order " + order.getOrderNumber() + " cancelled",
                "Your order " + order.getOrderNumber() + " was cancelled.");
    }

    private void completeCancellation(final String orderNumber, final String claimId) {

        if (claimId == null) {

            orderCancellationArbitrator.completeCancellation(orderNumber);
            return;
        }
        orderCancellationArbitrator.completeCancellation(orderNumber, claimId);
    }

    private static boolean isPendingCreatedPayment(final PaymentTransaction payment) {

        return payment != null && payment.getStatus() == PaymentStatus.PENDING && payment.getCreated() != null;
    }

    private static boolean requiresRefund(final PaymentTransaction payment) {

        return payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED;
    }

    private static String stockReservationId(final Order order) {

        return order.getStockReservationId() == null ? order.getOrderNumber() : order.getStockReservationId();
    }

}
