package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.RemarksTriageResult;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ClassifyOrderRemarksInPort;
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
 * notification routing, AI-assisted remarks triage - all best-effort: a failure there is logged and does not block the event
 * from being marked {@code SENT}, matching this application's existing eventual-consistency trade-offs (see ADR 0002 and ADR
 * 0008).
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

    private final ClassifyOrderRemarksInPort classifyOrderRemarksInPort;

    private final CancelOrderInPort cancelOrderInPort;

    private final TransactionOperations transactionOperations;

    private final SagaMetrics sagaMetrics;

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

            runBestEffortStepsConcurrently(order);
            outboxEventEntity.setStatus(OutboxEventStatus.SENT);
            outboxEventEntity.setSentDate(new Date());
            outboxEventEntityRepository.save(outboxEventEntity);
        }
    }

    /**
     * Runs the saga's best-effort tail steps - confirmation email, S3 export, SQS audit, Kafka analytics, Camel notification
     * routing, AI-assisted remarks triage - concurrently instead of one after another. They are mutually independent (each only
     * reads the already-loaded {@code order}; none depends on another's outcome), so this is a textbook fan-out: tail latency
     * drops to that of the single slowest step instead of their sum.
     *
     * <p>
     * Uses the stable {@link Executors#newVirtualThreadPerTaskExecutor()} (available since JDK 21) rather than
     * {@code StructuredTaskScope}, the API purpose-built for exactly this kind of fan-out/join: as of JDK 25,
     * {@code StructuredTaskScope} is still a preview API (JEP 505, its fifth preview), which would force every build and
     * deployment of this application onto {@code --enable-preview} - not an acceptable trade-off for a showcase meant to
     * demonstrate production-grade engineering rather than bleeding-edge previews. See ADR 0013.
     *
     * <p>
     * {@code ExecutorService#close()} (JDK 19+) blocks until every task submitted before it was called has finished, giving the
     * same "wait for all steps" semantics the previous sequential code had, just executed in parallel. Each step already
     * catches and logs its own {@link RuntimeException} (see below), so a failure in one never affects the others, and the
     * event is still marked {@code SENT} once all six have at least been attempted - unchanged from before this change.
     */
    private void runBestEffortStepsConcurrently(final Order order) {

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            executor.execute(() -> sendConfirmationEmail(order));
            executor.execute(() -> exportOrder(order));
            executor.execute(() -> publishAuditEvent(order));
            executor.execute(() -> publishAnalyticsEvent(order));
            executor.execute(() -> routeNotification(order));
            executor.execute(() -> classifyRemarks(order));
        }
    }

    // Pivot/compensable saga step: bounded-retry, and once exhausted, compensates instead of retrying forever.
    private boolean notifyFulfillment(final Order order, final OutboxEventEntity outboxEventEntity) {

        final long startNanos = System.nanoTime();
        try {
            sendMessageInPort.sendMessage(order);
            sagaMetrics.recordStepDuration("fulfillment", elapsedSince(startNanos), true);
            return true;
        } catch (RuntimeException exception) {

            sagaMetrics.recordStepDuration("fulfillment", elapsedSince(startNanos), false);
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
        sagaMetrics.recordCompensation();
        outboxEventEntity.setStatus(OutboxEventStatus.COMPENSATED);
        outboxEventEntity.setCompensatedDate(new Date());
        outboxEventEntityRepository.save(outboxEventEntity);
    }

    private void sendConfirmationEmail(final Order order) {

        runBestEffortStep(
                "confirmation-email",
                order,
                sendOrderConfirmationEmailInPort::sendConfirmationEmail,
                "Could not send order confirmation email (best-effort): {}");
    }

    private void exportOrder(final Order order) {

        runBestEffortStep("s3-export", order, exportOrderInPort::exportOrder, "Could not export order to S3 (best-effort): {}");
    }

    private void publishAuditEvent(final Order order) {

        runBestEffortStep(
                "sqs-audit",
                order,
                publishOrderAuditEventInPort::publishAuditEvent,
                "Could not publish SQS audit event (best-effort): {}");
    }

    private void publishAnalyticsEvent(final Order order) {

        runBestEffortStep(
                "kafka-analytics",
                order,
                publishOrderAnalyticsEventInPort::publishAnalyticsEvent,
                "Could not publish Kafka analytics event (best-effort): {}");
    }

    private void routeNotification(final Order order) {

        runBestEffortStep(
                "camel-routing",
                order,
                routeOrderNotificationInPort::routeNotification,
                "Could not route order notification via Camel (best-effort): {}");
    }

    // Custom (not runBestEffortStep-based) step: unlike the other five, this one needs the *result* of its in-port call
    // (the triage category/rationale), not just a success/failure outcome, to log a targeted warning for SUSPICIOUS orders
    // and to tag the SagaMetrics counter by category - see ClassifyOrderRemarksOutPort's javadoc for why this is
    // deliberately never used to automatically act on the order (human-in-the-loop only).
    private void classifyRemarks(final Order order) {

        final long startNanos = System.nanoTime();
        try {
            final RemarksTriageResult result = classifyOrderRemarksInPort.classifyRemarks(order);
            sagaMetrics.recordStepDuration("ai-remarks-triage", elapsedSince(startNanos), true);
            sagaMetrics.recordRemarksClassification(result.getCategory());
            if (result.getCategory() == RemarksTriageCategory.SUSPICIOUS) {
                log.warn(
                        "Order remarks flagged as SUSPICIOUS by AI triage (human review recommended): orderNumber={}, rationale={}",
                        order.getOrderNumber(),
                        result.getRationale());
            }
        } catch (RuntimeException exception) {
            sagaMetrics.recordStepDuration("ai-remarks-triage", elapsedSince(startNanos), false);
            log.warn("Could not classify order remarks (best-effort): {}", order.getOrderNumber(), exception);
        }
    }

    // Shared execute-time-catch-log wrapper for the saga's independent, best-effort tail steps that only need a
    // success/failure outcome (unlike classifyRemarks above, which needs its result value too): each already had
    // identical structure before metrics were added (call the port, catch RuntimeException, log a warning), so timing
    // is added here once instead of duplicated across all five step methods above.
    private void runBestEffortStep(
            final String step,
            final Order order,
            final Consumer<Order> action,
            final String failureLogMessage) {

        final long startNanos = System.nanoTime();
        try {
            action.accept(order);
            sagaMetrics.recordStepDuration(step, elapsedSince(startNanos), true);
        } catch (RuntimeException exception) {
            sagaMetrics.recordStepDuration(step, elapsedSince(startNanos), false);
            log.warn(failureLogMessage, order.getOrderNumber(), exception);
        }
    }

    private static Duration elapsedSince(final long startNanos) {

        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

}
