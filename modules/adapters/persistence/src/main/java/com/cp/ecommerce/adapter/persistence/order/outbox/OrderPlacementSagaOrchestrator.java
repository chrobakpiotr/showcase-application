package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;

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

    private final Clock clock;

    @Value("${outbox.publisher.max-fulfillment-attempts:5}")
    private int maxFulfillmentAttempts = 5;

    @Value("${outbox.publisher.claim-lease-ms:60000}")
    private long claimLeaseMs = 60_000L;

    /**
     * Publish all pending outbox events.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:5000}")
    public void publishPendingEvents() {

        final Date now = Date.from(clock.instant());
        outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING)
                .forEach(this::publishPlacementCandidate);
        outboxEventEntityRepository
                .findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(OutboxEventStatus.PROCESSING, now)
                .forEach(this::publishPlacementCandidate);
        outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING)
                .forEach(this::publishCompensatingCandidate);
    }

    private void publishPlacementCandidate(final OutboxEventEntity candidate) {

        try {
            claimPlacementEvent(candidate).ifPresent(claim -> {
                try {
                    processPlacementClaim(claim);
                } catch (final RuntimeException exception) {
                    releasePlacementClaim(claim, exception.getMessage());
                    throw exception;
                }
            });
        } catch (RuntimeException exception) {
            log.warn("Could not process saga step for order: {}", candidate.getOrderNumber(), exception);
        }
    }

    private Optional<SagaClaim> claimPlacementEvent(final OutboxEventEntity candidate) {

        return transactionOperations.execute(
                status -> outboxEventEntityRepository.findByIdForUpdate(candidate.getId())
                        .filter(
                                event -> event.getStatus() == OutboxEventStatus.PENDING
                                        || event.getStatus() == OutboxEventStatus.PROCESSING
                                                && leaseExpired(event, Date.from(clock.instant())))
                        .map(event -> {
                            final String claimId = UUID.randomUUID().toString();
                            event.setStatus(OutboxEventStatus.PROCESSING);
                            event.setClaimId(claimId);
                            event.setClaimUntil(claimUntil());
                            return new SagaClaim(event.getId(), event.getOrderNumber(), claimId);
                        }));
    }

    private void processPlacementClaim(final SagaClaim claim) {

        final Order order = manageOrderInPort.findOrder(claim.orderNumber());
        if (!ensurePaymentCaptured(order, claim)) {

            return;
        }
        if (notifyFulfillment(order, claim)) {

            runBestEffortStepsConcurrently(order);
            completePlacementClaim(claim);
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
    private boolean ensurePaymentCaptured(final Order order, final SagaClaim claim) {

        final long startNanos = System.nanoTime();
        try {
            final PaymentTransaction payment = managePaymentInPort
                    .capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod());
            if (payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED || payment.getStatus() == PaymentStatus.REFUNDED) {

                sagaMetrics.recordStepDuration("payment-capture", elapsedSince(startNanos), false);
                log.warn("Refusing to continue placement saga for refunded order: {}", order.getOrderNumber());
                releasePlacementClaim(claim, "Payment is already refunded");
                return false;
            }
            sagaMetrics.recordStepDuration("payment-capture", elapsedSince(startNanos), true);
            return true;
        } catch (final PaymentDeclinedException exception) {

            sagaMetrics.recordStepDuration("payment-capture", elapsedSince(startNanos), false);
            log.error(
                    "Payment capture declined for order: {}, compensating by cancelling the order.",
                    order.getOrderNumber(),
                    exception);
            startCompensation(order, claim, exception.getMessage());
            return false;
        }
    }

    // Pivot/compensable saga step: bounded-retry, and once exhausted, compensates instead of retrying forever.
    private boolean notifyFulfillment(final Order order, final SagaClaim claim) {

        final long startNanos = System.nanoTime();
        try {
            sendMessageInPort.sendMessage(order);
            sagaMetrics.recordStepDuration("fulfillment", elapsedSince(startNanos), true);
            return true;
        } catch (RuntimeException exception) {

            sagaMetrics.recordStepDuration("fulfillment", elapsedSince(startNanos), false);
            recordFulfillmentFailure(order, claim, exception);
            return false;
        }
    }

    private void recordFulfillmentFailure(final Order order, final SagaClaim claim, final RuntimeException exception) {

        transactionOperations.executeWithoutResult(
                status -> outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                        .filter(event -> ownsPlacementClaim(event, claim))
                        .ifPresent(event -> {
                            event.setAttempts(event.getAttempts() + 1);
                            event.setLastError(exception.getMessage());
                            if (event.getAttempts() >= maxFulfillmentAttempts) {

                                log.error(
                                        "Fulfillment notification failed for order: {} after {} attempts, compensating by cancelling the order.",
                                        order.getOrderNumber(),
                                        event.getAttempts(),
                                        exception);
                                cancelOrderInPort.cancelOrder(order.getOrderNumber());
                                event.setStatus(OutboxEventStatus.COMPENSATING);
                            } else {

                                log.warn(
                                        "Fulfillment notification failed for order: {} (attempt {}/{}), will retry.",
                                        order.getOrderNumber(),
                                        event.getAttempts(),
                                        maxFulfillmentAttempts,
                                        exception);
                                event.setStatus(OutboxEventStatus.PENDING);
                            }
                            clearClaim(event);
                            outboxEventEntityRepository.save(event);
                        }));
    }

    private void startCompensation(final Order order, final SagaClaim claim, final String error) {

        transactionOperations.executeWithoutResult(
                status -> outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                        .filter(event -> ownsPlacementClaim(event, claim))
                        .ifPresent(event -> {
                            cancelOrderInPort.cancelOrder(order.getOrderNumber());
                            event.setStatus(OutboxEventStatus.COMPENSATING);
                            event.setLastError(error);
                            clearClaim(event);
                            outboxEventEntityRepository.save(event);
                        }));
    }

    private void releasePlacementClaim(final SagaClaim claim, final String error) {

        transactionOperations.executeWithoutResult(
                status -> outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                        .filter(event -> ownsPlacementClaim(event, claim))
                        .ifPresent(event -> {
                            event.setStatus(OutboxEventStatus.PENDING);
                            event.setLastError(error);
                            clearClaim(event);
                        }));
    }

    private void completePlacementClaim(final SagaClaim claim) {

        transactionOperations.executeWithoutResult(
                status -> outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                        .filter(event -> ownsPlacementClaim(event, claim))
                        .ifPresent(event -> {
                            event.setStatus(OutboxEventStatus.SENT);
                            event.setSentDate(Date.from(clock.instant()));
                            clearClaim(event);
                            outboxEventEntityRepository.save(event);
                        }));
    }

    private void publishCompensatingCandidate(final OutboxEventEntity candidate) {

        try {
            claimCompensationEvent(candidate).ifPresent(this::processCompensationClaim);
        } catch (final RuntimeException exception) {
            log.warn("Could not claim compensation for order: {}", candidate.getOrderNumber(), exception);
        }
    }

    private Optional<SagaClaim> claimCompensationEvent(final OutboxEventEntity candidate) {

        return transactionOperations.execute(
                status -> outboxEventEntityRepository.findByIdForUpdate(candidate.getId())
                        .filter(
                                event -> event.getStatus() == OutboxEventStatus.COMPENSATING
                                        && leaseAvailable(event, Date.from(clock.instant())))
                        .map(event -> {
                            final String claimId = UUID.randomUUID().toString();
                            event.setClaimId(claimId);
                            event.setClaimUntil(claimUntil());
                            return new SagaClaim(event.getId(), event.getOrderNumber(), claimId);
                        }));
    }

    private void processCompensationClaim(final SagaClaim claim) {

        try {
            final Order order = manageOrderInPort.findOrder(claim.orderNumber());
            releaseReservedStock(order);
            refundCapturedPayment(order);
            transactionOperations.executeWithoutResult(status -> completeCompensation(claim));
        } catch (final RuntimeException exception) {
            transactionOperations.executeWithoutResult(status -> recordCompensationFailure(claim, exception));
        }
    }

    private void completeCompensation(final SagaClaim claim) {

        outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                .filter(event -> ownsCompensationClaim(event, claim))
                .ifPresent(event -> {
                    event.setStatus(OutboxEventStatus.COMPENSATED);
                    event.setCompensatedDate(Date.from(clock.instant()));
                    event.setLastError(null);
                    clearClaim(event);
                    outboxEventEntityRepository.save(event);
                    sagaMetrics.recordCompensation();
                });
    }

    private void recordCompensationFailure(final SagaClaim claim, final RuntimeException exception) {

        outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                .filter(event -> ownsCompensationClaim(event, claim))
                .ifPresent(event -> {
                    event.setCompensationAttempts(event.getCompensationAttempts() + 1);
                    event.setLastError(exception.getMessage());
                    clearClaim(event);
                    outboxEventEntityRepository.save(event);
                });
    }

    private void refundCapturedPayment(final Order order) {

        managePaymentInPort.refundPayment(order.getOrderNumber());
    }

    private void releaseReservedStock(final Order order) {

        order.getItems().forEach(item -> manageStockInPort.releaseStock(stockReservationId(order), item.getSku()));
    }

    private static String stockReservationId(final Order order) {

        return order.getStockReservationId() == null ? order.getOrderNumber() : order.getStockReservationId();
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

    private boolean ownsPlacementClaim(final OutboxEventEntity event, final SagaClaim claim) {

        return event.getStatus() == OutboxEventStatus.PROCESSING && Objects.equals(event.getClaimId(), claim.claimId());
    }

    private boolean ownsCompensationClaim(final OutboxEventEntity event, final SagaClaim claim) {

        return event.getStatus() == OutboxEventStatus.COMPENSATING && Objects.equals(event.getClaimId(), claim.claimId());
    }

    private static boolean leaseAvailable(final OutboxEventEntity event, final Date now) {

        return event.getClaimUntil() == null || leaseExpired(event, now);
    }

    private static boolean leaseExpired(final OutboxEventEntity event, final Date now) {

        return !event.getClaimUntil().after(now);
    }

    private Date claimUntil() {

        return Date.from(clock.instant().plusMillis(claimLeaseMs));
    }

    private static void clearClaim(final OutboxEventEntity event) {

        event.setClaimId(null);
        event.setClaimUntil(null);
    }

    private record SagaClaim(Long eventId, String orderNumber, String claimId) {
    }

    private static Duration elapsedSince(final long startNanos) {

        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

}
