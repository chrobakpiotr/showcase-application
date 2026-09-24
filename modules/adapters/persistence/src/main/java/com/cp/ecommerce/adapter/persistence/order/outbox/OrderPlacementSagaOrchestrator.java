package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessagePublishOutcome;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;
import com.cp.ecommerce.foundation.function.RuntimeFailureBoundary;

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

    private static final String PAYMENT_CAPTURE_STEP = "payment-capture";

    private static final String FULFILLMENT_STEP = "fulfillment";

    private final OutboxEventEntityRepository outboxEventEntityRepository;

    private final ManageOrderInPort manageOrderInPort;

    private final SendMessageInPort sendMessageInPort;

    private final OrderPlacementBestEffortTail bestEffortTail;

    private final CancelOrderInPort cancelOrderInPort;

    private final ManageStockInPort manageStockInPort;

    private final ManagePaymentInPort managePaymentInPort;

    private final TransactionOperations transactionOperations;

    private final SagaMetrics sagaMetrics;

    private final Clock clock;

    @Value("${outbox.publisher.retry-backoff-ms:5000}")
    private long retryBackoffMillis = 5_000L;

    @Value("${outbox.publisher.max-processing-attempts:10}")
    private int maxProcessingAttempts = 10;

    @Value("${outbox.publisher.max-compensation-attempts:10}")
    private int maxCompensationAttempts = 10;

    @Value("${outbox.publisher.max-fulfillment-attempts:5}")
    private int maxFulfillmentAttempts = 5;

    @Value("${outbox.publisher.claim-lease-ms:60000}")
    private long claimLeaseMs = 60_000L;

    /**
     * Publish all pending outbox events.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:5000}")
    public void publishPendingEvents() {

        final Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        outboxEventEntityRepository
                .findDueByStatus(
                        OutboxEventStatus.PENDING,
                        now,
                        org.springframework.data.domain.PageRequest.of(0, OutboxEventEntityRepository.DEFAULT_POLL_BATCH_SIZE))
                .forEach(this::publishPlacementCandidate);
        outboxEventEntityRepository
                .findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(OutboxEventStatus.PROCESSING, now)
                .forEach(this::publishPlacementCandidate);

        final Instant compensationNow = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        outboxEventEntityRepository
                .findDueAndClaimableByStatus(
                        OutboxEventStatus.COMPENSATING,
                        compensationNow,
                        org.springframework.data.domain.PageRequest.of(0, OutboxEventEntityRepository.DEFAULT_POLL_BATCH_SIZE))
                .forEach(this::publishCompensatingCandidate);
    }

    private void publishPlacementCandidate(final OutboxEventEntity candidate) {

        RuntimeFailureBoundary.run(
                () -> claimPlacementEvent(candidate)
                        .ifPresent(claim -> RuntimeFailureBoundary.run(() -> processPlacementClaim(claim), exception -> {
                            releasePlacementClaim(claim, exception.getMessage());
                            throw exception;
                        })),
                exception -> log.warn("Could not process saga step for order: {}", candidate.getOrderNumber(), exception));
    }

    private Optional<SagaClaim> claimPlacementEvent(final OutboxEventEntity candidate) {

        return transactionOperations
                .execute(status -> outboxEventEntityRepository.findByIdForUpdate(candidate.getId()).filter(event -> {
                    final Instant claimNow = Instant.ofEpochMilli(clock.instant().toEpochMilli());
                    return event.getStatus() == OutboxEventStatus.PENDING
                            && (event.getNextAttemptDate() == null || !event.getNextAttemptDate().isAfter(claimNow))
                            || event.getStatus() == OutboxEventStatus.PROCESSING && leaseExpired(event, claimNow);
                }).map(event -> {
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

            bestEffortTail.run(order);
            completePlacementClaim(claim);
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
            if (!ownsPlacementClaim(claim)) {
                sagaMetrics.recordStepDuration(PAYMENT_CAPTURE_STEP, elapsedSince(startNanos), false);
                return false;
            }

            final PaymentTransaction payment = managePaymentInPort
                    .capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod());

            if (!ownsPlacementClaim(claim)) {
                sagaMetrics.recordStepDuration(PAYMENT_CAPTURE_STEP, elapsedSince(startNanos), false);
                log.warn("Placement claim was lost during payment capture for order: {}", order.getOrderNumber());
                compensateLateCaptureIfCancellationWon(order, claim, payment);
                return false;
            }

            if (payment.getStatus() == PaymentStatus.DECLINED) {
                sagaMetrics.recordStepDuration(PAYMENT_CAPTURE_STEP, elapsedSince(startNanos), false);
                startCompensation(order, claim, "Payment is declined");
                return false;
            }

            if (payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED || payment.getStatus() == PaymentStatus.REFUNDED) {
                sagaMetrics.recordStepDuration(PAYMENT_CAPTURE_STEP, elapsedSince(startNanos), false);
                log.warn("Refusing to continue placement saga for refunded order: {}", order.getOrderNumber());
                releasePlacementClaim(claim, "Payment is already refunded");
                return false;
            }

            sagaMetrics.recordStepDuration(PAYMENT_CAPTURE_STEP, elapsedSince(startNanos), true);
            return true;
        } catch (final PaymentDeclinedException exception) {
            sagaMetrics.recordStepDuration(PAYMENT_CAPTURE_STEP, elapsedSince(startNanos), false);
            log.error(
                    "Payment capture declined for order: {}, compensating by cancelling the order.",
                    order.getOrderNumber(),
                    exception);
            startCompensation(order, claim, exception.getMessage());
            return false;
        }
    }

    private boolean ownsPlacementClaim(final SagaClaim claim) {

        final Boolean owned = transactionOperations.execute(
                status -> outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                        .map(event -> ownsPlacementClaim(event, claim))
                        .orElse(false));
        return Boolean.TRUE.equals(owned);
    }

    private boolean renewPlacementClaim(final SagaClaim claim) {

        final Boolean renewed = transactionOperations.execute(
                status -> outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                        .filter(event -> ownsPlacementClaim(event, claim))
                        .map(event -> {
                            event.setClaimUntil(claimUntil());
                            return true;
                        })
                        .orElse(false));
        return Boolean.TRUE.equals(renewed);
    }

    private void compensateLateCaptureIfCancellationWon(
            final Order order,
            final SagaClaim claim,
            final PaymentTransaction payment) {

        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            return;
        }

        final Boolean cancellationWon = transactionOperations.execute(
                status -> outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                        .map(event -> cancellationOrCompensationWon(event.getStatus()))
                        .orElse(false));

        if (Boolean.TRUE.equals(cancellationWon)) {
            log.warn(
                    "Compensating late payment capture after durable cancellation/compensation won for order: {}",
                    order.getOrderNumber());
            managePaymentInPort.refundPayment(order.getOrderNumber());
        }
    }

    private static boolean cancellationOrCompensationWon(final OutboxEventStatus status) {

        return switch (status) {
        case CANCELLING, CANCELLED, COMPENSATING, COMPENSATED -> true;
        default -> false;
        };
    }

    // Pivot/compensable saga step: bounded-retry, and once exhausted, compensates instead of retrying forever.
    private boolean notifyFulfillment(final Order order, final SagaClaim claim) {

        final long startNanos = System.nanoTime();
        return RuntimeFailureBoundary.call(() -> {
            if (!renewPlacementClaim(claim)) {
                sagaMetrics.recordStepDuration(FULFILLMENT_STEP, elapsedSince(startNanos), false);
                return false;
            }
            final OrderMessagePublishOutcome publishOutcome = sendMessageInPort.sendMessage(order);
            if (publishOutcome == OrderMessagePublishOutcome.UNKNOWN) {
                sagaMetrics.recordStepDuration(FULFILLMENT_STEP, elapsedSince(startNanos), false);
                releasePlacementClaim(claim, "Fulfillment publish outcome is unknown");
                return false;
            }
            if (publishOutcome == OrderMessagePublishOutcome.REJECTED) {
                throw new IllegalStateException("RabbitMQ rejected fulfillment publish");
            }
            if (!ownsPlacementClaim(claim)) {
                sagaMetrics.recordStepDuration(FULFILLMENT_STEP, elapsedSince(startNanos), false);
                return false;
            }
            sagaMetrics.recordStepDuration(FULFILLMENT_STEP, elapsedSince(startNanos), true);
            return true;
        }, exception -> {
            sagaMetrics.recordStepDuration(FULFILLMENT_STEP, elapsedSince(startNanos), false);
            recordFulfillmentFailure(order, claim, exception);
            return false;
        });
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
                            event.setNextAttemptDate(Instant.ofEpochMilli(clock.instant().toEpochMilli() + retryBackoffMillis));
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
                            event.setNextAttemptDate(Instant.ofEpochMilli(clock.instant().toEpochMilli()));
                            clearClaim(event);
                            outboxEventEntityRepository.save(event);
                        }));
    }

    private void releasePlacementClaim(final SagaClaim claim, final String error) {

        transactionOperations.executeWithoutResult(
                status -> outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                        .filter(event -> ownsPlacementClaim(event, claim))
                        .ifPresent(event -> {
                            event.setProcessingAttempts(event.getProcessingAttempts() + 1);
                            event.setStatus(
                                    event.getProcessingAttempts() >= maxProcessingAttempts
                                            ? OutboxEventStatus.MANUAL_REVIEW
                                            : OutboxEventStatus.PENDING);
                            event.setLastError(error);
                            event.setNextAttemptDate(Instant.ofEpochMilli(clock.instant().toEpochMilli() + retryBackoffMillis));
                            clearClaim(event);
                            outboxEventEntityRepository.save(event);
                        }));
    }

    private void completePlacementClaim(final SagaClaim claim) {

        transactionOperations.executeWithoutResult(
                status -> outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                        .filter(event -> ownsPlacementClaim(event, claim))
                        .ifPresent(event -> {
                            event.setStatus(OutboxEventStatus.SENT);
                            event.setSentDate(Instant.ofEpochMilli(clock.instant().toEpochMilli()));
                            clearClaim(event);
                            outboxEventEntityRepository.save(event);
                        }));
    }

    private void publishCompensatingCandidate(final OutboxEventEntity candidate) {

        RuntimeFailureBoundary.run(
                () -> claimCompensationEvent(candidate).ifPresent(this::processCompensationClaim),
                exception -> log.warn("Could not claim compensation for order: {}", candidate.getOrderNumber(), exception));
    }

    private Optional<SagaClaim> claimCompensationEvent(final OutboxEventEntity candidate) {

        return transactionOperations.execute(
                status -> outboxEventEntityRepository.findByIdForUpdate(candidate.getId())
                        .filter(
                                event -> event.getStatus() == OutboxEventStatus.COMPENSATING
                                        && leaseAvailable(event, Instant.ofEpochMilli(clock.instant().toEpochMilli())))
                        .map(event -> {
                            final String claimId = UUID.randomUUID().toString();
                            event.setClaimId(claimId);
                            event.setClaimUntil(claimUntil());
                            return new SagaClaim(event.getId(), event.getOrderNumber(), claimId);
                        }));
    }

    private void processCompensationClaim(final SagaClaim claim) {

        RuntimeFailureBoundary.run(() -> {
            final Order order = manageOrderInPort.findOrder(claim.orderNumber());
            releaseReservedStock(order);
            refundCapturedPayment(order);
            transactionOperations.executeWithoutResult(status -> completeCompensation(claim));
        }, exception -> transactionOperations.executeWithoutResult(status -> recordCompensationFailure(claim, exception)));
    }

    private void completeCompensation(final SagaClaim claim) {

        outboxEventEntityRepository.findByIdForUpdate(claim.eventId())
                .filter(event -> ownsCompensationClaim(event, claim))
                .ifPresent(event -> {
                    event.setStatus(OutboxEventStatus.COMPENSATED);
                    event.setCompensatedDate(Instant.ofEpochMilli(clock.instant().toEpochMilli()));
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
                    event.setNextAttemptDate(Instant.ofEpochMilli(clock.instant().toEpochMilli() + retryBackoffMillis));
                    if (event.getCompensationAttempts() >= maxCompensationAttempts) {
                        event.setStatus(OutboxEventStatus.MANUAL_REVIEW);
                    }
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

    private boolean ownsPlacementClaim(final OutboxEventEntity event, final SagaClaim claim) {

        return event.getStatus() == OutboxEventStatus.PROCESSING && Objects.equals(event.getClaimId(), claim.claimId());
    }

    private boolean ownsCompensationClaim(final OutboxEventEntity event, final SagaClaim claim) {

        return event.getStatus() == OutboxEventStatus.COMPENSATING && Objects.equals(event.getClaimId(), claim.claimId());
    }

    private static boolean leaseAvailable(final OutboxEventEntity event, final Instant now) {

        return event.getClaimUntil() == null || leaseExpired(event, now);
    }

    private static boolean leaseExpired(final OutboxEventEntity event, final Instant now) {

        return !event.getClaimUntil().isAfter(now);
    }

    private Instant claimUntil() {

        return Instant.ofEpochMilli(clock.instant().plusMillis(claimLeaseMs).toEpochMilli());
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
