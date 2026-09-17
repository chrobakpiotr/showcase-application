package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.cp.ecommerce.adapter.common.exception.PaymentDeclinedException;
import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.RemarksTriageResult;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ClassifyOrderRemarksInPort;
import com.cp.ecommerce.domain.order.port.incoming.DetectDuplicateOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAnalyticsEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;

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
 * {@link #ensurePaymentCaptured} is the saga's first pivot/compensable step (see ADR 0030): it charges the customer via
 * {@link ManagePaymentInPort#capturePayment}, and only once that succeeds does {@link #notifyFulfillment} - the second
 * pivot/compensable step (fulfillment notification via RabbitMQ) - run. Fulfillment failures are retried with a bounded number
 * of attempts recorded on the outbox event; once {@code outbox.publisher.max-fulfillment-attempts} is reached, or the payment
 * is declined, the saga runs its compensating transaction and cancels the order via {@link CancelOrderInPort} instead of
 * retrying forever. Only once fulfillment succeeds do the remaining steps run - confirmation email, export, audit, analytics,
 * notification routing, AI-assisted remarks triage, AI-assisted duplicate-order detection - all best-effort: a failure there is
 * logged and does not block the event from being marked {@code SENT}, matching this application's existing eventual-consistency
 * trade-offs (see ADR 0002 and ADR 0008).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
@SuppressWarnings("PMD.CouplingBetweenObjects")
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

    private final DetectDuplicateOrderInPort detectDuplicateOrderInPort;

    private final CancelOrderInPort cancelOrderInPort;

    private final ManageStockInPort manageStockInPort;

    private final ManagePaymentInPort managePaymentInPort;

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
        if (!ensurePaymentCaptured(order, outboxEventEntity)) {

            return;
        }
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
            executor.execute(() -> detectDuplicateOrder(order));
        }
    }

    // Pivot/compensable saga step, run before notifyFulfillment: charges the customer for the order's total. Idempotent
    // via ManagePaymentInPort#capturePayment itself (see its javadoc), so simply re-invoking this on every poll is safe
    // and needs no attempts-counting of its own, unlike notifyFulfillment below. A decline is a genuine, deterministic
    // business outcome (see PaymentDeclinedException) that would never succeed on a later poll, so it compensates
    // immediately on the first attempt rather than being retried.
    private boolean ensurePaymentCaptured(final Order order, final OutboxEventEntity outboxEventEntity) {

        final long startNanos = System.nanoTime();
        try {
            managePaymentInPort.capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod());
            sagaMetrics.recordStepDuration("payment-capture", elapsedSince(startNanos), true);
            return true;
        } catch (final PaymentDeclinedException exception) {

            sagaMetrics.recordStepDuration("payment-capture", elapsedSince(startNanos), false);
            log.error(
                    "Payment capture declined for order: {}, compensating by cancelling the order.",
                    order.getOrderNumber(),
                    exception);
            runCompensation(order, outboxEventEntity);
            return false;
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

                log.error(
                        "Fulfillment notification failed for order: {} after {} attempts, compensating by cancelling the order.",
                        order.getOrderNumber(),
                        outboxEventEntity.getAttempts(),
                        exception);
                runCompensation(order, outboxEventEntity);
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

    // Shared compensating-transaction actions for both pivot steps above: cancel the order, release its reserved
    // stock, refund its payment (all best-effort where relevant/idempotent), record the compensation, and mark the
    // outbox event COMPENSATED so it is never re-processed by a later poll.
    private void runCompensation(final Order order, final OutboxEventEntity outboxEventEntity) {

        cancelOrderInPort.cancelOrder(order.getOrderNumber());
        releaseReservedStock(order);
        refundCapturedPayment(order);
        sagaMetrics.recordCompensation();
        outboxEventEntity.setStatus(OutboxEventStatus.COMPENSATED);
        outboxEventEntity.setCompensatedDate(new Date());
        outboxEventEntityRepository.save(outboxEventEntity);
    }

    // Refunds this order's payment as part of compensation - a no-op via ManagePaymentInPort#refundPayment's own
    // idempotency if payment was never captured (e.g. compensating a payment decline itself) or already refunded, and
    // an actual refund if fulfillment failed after payment had already been captured. Best-effort like
    // releaseReservedStock above: a failure here must not prevent the order from being marked COMPENSATED.
    private void refundCapturedPayment(final Order order) {

        try {
            managePaymentInPort.refundPayment(order.getOrderNumber());
        } catch (final RuntimeException exception) {
            log.warn(
                    "Could not refund payment for order: {} (best-effort): {}",
                    order.getOrderNumber(),
                    exception.getMessage());
        }
    }

    // Releases the stock reserved for this order at placement time (see OrderController#reserveStockFor), as part of
    // the saga's compensating transaction. Best-effort like the tail steps below: a failure here must not prevent the
    // order from being marked COMPENSATED, since the order itself is already cancelled either way - it would only leave
    // stock over-reserved until manually corrected, which is preferable to silently swallowing the cancellation.
    private void releaseReservedStock(final Order order) {

        order.getItems().forEach(item -> {
            try {
                manageStockInPort.releaseStock(item.getSku(), item.getQuantity());
            } catch (final RuntimeException exception) {
                log.warn(
                        "Could not release reserved stock for order: {}, sku: {} (best-effort): {}",
                        order.getOrderNumber(),
                        item.getSku(),
                        exception.getMessage());
            }
        });
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

    // Custom (not runBestEffortStep-based) step, for the same reason as classifyRemarks above: needs the check's result
    // (matched order number/similarity score), not just a success/failure outcome, to log a targeted warning and tag the
    // SagaMetrics counter - see DetectDuplicateOrderOutPort's javadoc for why this is deliberately never used to
    // automatically act on the order (human-in-the-loop only, same as the remarks triage).
    private void detectDuplicateOrder(final Order order) {

        final long startNanos = System.nanoTime();
        try {
            final DuplicateOrderCheckResult result = detectDuplicateOrderInPort.detectDuplicate(order);
            sagaMetrics.recordStepDuration("ai-duplicate-order-detection", elapsedSince(startNanos), true);
            sagaMetrics.recordDuplicateOrderDetection(result.isDuplicate());
            if (result.isDuplicate()) {
                log.warn(
                        "Order flagged as a likely duplicate by AI similarity check (human review recommended): "
                                + "orderNumber={}, matchedOrderNumber={}, similarityScore={}, rationale={}",
                        order.getOrderNumber(),
                        result.getMatchedOrderNumber(),
                        result.getSimilarityScore(),
                        result.getRationale());
            }
        } catch (RuntimeException exception) {
            sagaMetrics.recordStepDuration("ai-duplicate-order-detection", elapsedSince(startNanos), false);
            log.warn("Could not run AI duplicate-order detection (best-effort): {}", order.getOrderNumber(), exception);
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
