package com.cp.ecommerce.application.order;

import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationEventKey;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.CancellationCompletionOutcome;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort.CancellationClaim;
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

        return executeCancellation(orderNumber, null).order();
    }

    @Override
    public Order cancelOrder(final String orderNumber, final String claimId) {

        return executeCancellation(orderNumber, claimId).order();
    }

    @Override
    public CancellationRecoveryOutcome recoverCancellation(final String orderNumber, final String claimId) {

        return executeCancellation(orderNumber, claimId).outcome();
    }

    private CancellationExecution executeCancellation(final String orderNumber, final String claimId) {

        final OrderCancellationArbitrator.CancellationStart cancellation = claimId == null
                ? orderCancellationArbitrator.beginCancellation(orderNumber)
                : orderCancellationArbitrator.beginCancellation(orderNumber, claimId);
        final Order order = cancellation.order();
        if (cancellation.claim() == CancellationClaim.BUSY) {

            return new CancellationExecution(order, CancellationRecoveryOutcome.BUSY);
        }
        if (cancellation.claim() == CancellationClaim.LOST_CLAIM) {

            return new CancellationExecution(order, CancellationRecoveryOutcome.LOST_CLAIM);
        }
        if (order == null) {
            if (claimId != null) {

                throw new IllegalStateException("Cancellation recovery lost its order: " + orderNumber);
            }
            return new CancellationExecution(null, CancellationRecoveryOutcome.COMPLETED);
        }
        if (!cancellation.runSideEffects()) {

            return new CancellationExecution(order, CancellationRecoveryOutcome.COMPLETED);
        }

        releaseStock(order);
        if (paymentRecoveryPending(order)) {

            return new CancellationExecution(order, CancellationRecoveryOutcome.WAITING_FOR_REFUND);
        }

        return new CancellationExecution(order, finalizeCancellation(order, claimId));
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
                NotificationEventKey.of("order", order.getOrderNumber(), NotificationType.ORDER_CANCELLED, "cancellation-v1"),
                order.getCustomer().getContact().getEmail(),
                NotificationType.ORDER_CANCELLED,
                "Order " + order.getOrderNumber() + " cancelled",
                "Your order " + order.getOrderNumber() + " was cancelled.");
    }

    private CancellationRecoveryOutcome finalizeCancellation(final Order order, final String claimId) {

        final CancellationCompletionOutcome outcome = orderCancellationArbitrator
                .finalizeCancellation(order.getOrderNumber(), claimId, () -> sendCancellationNotification(order));
        return switch (outcome) {
        case COMPLETED, ALREADY_COMPLETED -> CancellationRecoveryOutcome.COMPLETED;
        case LOST_CLAIM -> CancellationRecoveryOutcome.LOST_CLAIM;
        case CONFLICT ->
            throw new IllegalStateException("Cancellation finalization conflicted for order: " + order.getOrderNumber());
        };
    }

    private static boolean isPendingCreatedPayment(final PaymentTransaction payment) {

        return payment != null && payment.getStatus() == PaymentStatus.PENDING && payment.getCreated() != null;
    }

    private static boolean requiresRefund(final PaymentTransaction payment) {

        return payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED;
    }

    private record CancellationExecution(Order order, CancellationRecoveryOutcome outcome) {
    }

    private static String stockReservationId(final Order order) {

        return order.getStockReservationId() == null ? order.getOrderNumber() : order.getStockReservationId();
    }

}
