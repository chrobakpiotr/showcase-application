package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.Date;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAnalyticsEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the order-placement saga: polls pending outbox events and drives each one through its saga steps.
 *
 * <p>
 * {@link #notifyFulfillment} is the saga's pivot/compensable step (fulfillment notification via RabbitMQ): failures are retried
 * with a bounded number of attempts recorded on the outbox event; once {@code outbox.publisher.max-fulfillment-attempts} is
 * reached, the saga runs its compensating transaction and cancels the order via {@link CancelOrderInPort} instead of retrying
 * forever. Only once fulfillment succeeds do the remaining steps run - confirmation email, export, audit, analytics,
 * notification routing - all best-effort: a failure there is logged and does not block the event from being marked
 * {@code SENT}, matching this application's existing eventual-consistency trade-offs (see ADR 0002 and ADR 0008).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderPlacementSagaOrchestrator {

    private final OutboxEventEntityRepository outboxEventEntityRepository;

    private final ManageOrderInPort manageOrderInPort;

    private final SendMessageInPort sendMessageInPort;

    private final SendOrderConfirmationEmailInPort sendOrderConfirmationEmailInPort;

    private final ExportOrderInPort exportOrderInPort;

    private final PublishOrderAuditEventInPort publishOrderAuditEventInPort;

    private final PublishOrderAnalyticsEventInPort publishOrderAnalyticsEventInPort;

    private final RouteOrderNotificationInPort routeOrderNotificationInPort;

    private final CancelOrderInPort cancelOrderInPort;

    private final TransactionOperations transactionOperations;

    @Value("${outbox.publisher.max-fulfillment-attempts:5}")
    private int maxFulfillmentAttempts = 5;

    /**
     * Publish all pending outbox events.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:5000}")
    public void publishPendingEvents() {

        outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING)
                .forEach(this::publishPendingEvent);
    }

    private void publishPendingEvent(final OutboxEventEntity outboxEventEntity) {

        try {
            transactionOperations.executeWithoutResult(status -> processPendingEvent(outboxEventEntity));
        } catch (RuntimeException exception) {
            log.warn("Could not process saga step for order: {}", outboxEventEntity.getOrderNumber(), exception);
        }
    }

    private void processPendingEvent(final OutboxEventEntity outboxEventEntity) {

        final Order order = manageOrderInPort.findOrder(outboxEventEntity.getOrderNumber());
        if (notifyFulfillment(order, outboxEventEntity)) {

            sendConfirmationEmail(order);
            exportOrder(order);
            publishAuditEvent(order);
            publishAnalyticsEvent(order);
            routeNotification(order);
            outboxEventEntity.setStatus(OutboxEventStatus.SENT);
            outboxEventEntity.setSentDate(new Date());
            outboxEventEntityRepository.save(outboxEventEntity);
        }
    }

    // Pivot/compensable saga step: bounded-retry, and once exhausted, compensates instead of retrying forever.
    private boolean notifyFulfillment(final Order order, final OutboxEventEntity outboxEventEntity) {

        try {
            sendMessageInPort.sendMessage(order);
            return true;
        } catch (RuntimeException exception) {

            outboxEventEntity.setAttempts(outboxEventEntity.getAttempts() + 1);
            outboxEventEntity.setLastError(exception.getMessage());
            if (outboxEventEntity.getAttempts() >= maxFulfillmentAttempts) {

                compensate(order, outboxEventEntity, exception);
            } else {

                log.warn(
                        "Fulfillment notification failed for order: {} (attempt {}/{}), will retry.",
                        order.getOrderNumber(),
                        outboxEventEntity.getAttempts(),
                        maxFulfillmentAttempts,
                        exception);
                outboxEventEntityRepository.save(outboxEventEntity);
            }
            return false;
        }
    }

    private void compensate(final Order order, final OutboxEventEntity outboxEventEntity, final RuntimeException exception) {

        log.error(
                "Fulfillment notification failed for order: {} after {} attempts, compensating by cancelling the order.",
                order.getOrderNumber(),
                outboxEventEntity.getAttempts(),
                exception);
        cancelOrderInPort.cancelOrder(order.getOrderNumber());
        outboxEventEntity.setStatus(OutboxEventStatus.COMPENSATED);
        outboxEventEntity.setCompensatedDate(new Date());
        outboxEventEntityRepository.save(outboxEventEntity);
    }

    private void sendConfirmationEmail(final Order order) {

        try {
            sendOrderConfirmationEmailInPort.sendConfirmationEmail(order);
        } catch (RuntimeException exception) {
            log.warn("Could not send order confirmation email (best-effort): {}", order.getOrderNumber(), exception);
        }
    }

    private void exportOrder(final Order order) {

        try {
            exportOrderInPort.exportOrder(order);
        } catch (RuntimeException exception) {
            log.warn("Could not export order to S3 (best-effort): {}", order.getOrderNumber(), exception);
        }
    }

    private void publishAuditEvent(final Order order) {

        try {
            publishOrderAuditEventInPort.publishAuditEvent(order);
        } catch (RuntimeException exception) {
            log.warn("Could not publish SQS audit event (best-effort): {}", order.getOrderNumber(), exception);
        }
    }

    private void publishAnalyticsEvent(final Order order) {

        try {
            publishOrderAnalyticsEventInPort.publishAnalyticsEvent(order);
        } catch (RuntimeException exception) {
            log.warn("Could not publish Kafka analytics event (best-effort): {}", order.getOrderNumber(), exception);
        }
    }

    private void routeNotification(final Order order) {

        try {
            routeOrderNotificationInPort.routeNotification(order);
        } catch (RuntimeException exception) {
            log.warn("Could not route order notification via Camel (best-effort): {}", order.getOrderNumber(), exception);
        }
    }

}
