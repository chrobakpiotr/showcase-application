package com.cp.ecommerce.adapter.persistence.metrics;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.domain.notification.NotificationStatus;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Low-cardinality operational signals for durable recovery state.
 *
 * <p>
 * Order numbers, outbox ids, notification ids and payment operation ids belong in logs/traces rather than metric labels.
 */
@Component
public class RecoveryMetrics {

    static final String PENDING_AGE_METRIC_NAME = "saga.order-placement.pending.age";

    static final String EXPIRED_CLAIMS_METRIC_NAME = "saga.order-placement.claim.expired";

    static final String RETRY_BACKLOG_METRIC_NAME = "saga.order-placement.retry.backlog";

    static final String INCOMPLETE_COMPENSATION_METRIC_NAME = "saga.order-placement.compensation.incomplete";

    static final String PAYMENT_UNKNOWN_METRIC_NAME = "payment.operation.unknown";

    static final String NOTIFICATION_LAG_METRIC_NAME = "notification.delivery.lag";

    private static final List<NotificationStatus> RETRYABLE_NOTIFICATION_STATUSES = List
            .of(NotificationStatus.PENDING, NotificationStatus.FAILED, NotificationStatus.DELIVERING);

    private final transient OutboxEventEntityRepository outboxEventEntityRepository;

    private final transient NotificationEntityRepository notificationEntityRepository;

    private final transient AtomicLong pendingAgeSeconds = new AtomicLong();

    private final transient AtomicLong expiredClaims = new AtomicLong();

    private final transient AtomicLong retryBacklog = new AtomicLong();

    private final transient AtomicLong incompleteCompensations = new AtomicLong();

    private final transient AtomicLong notificationLagSeconds = new AtomicLong();

    private final transient Counter paymentUnknownCounter;

    public RecoveryMetrics(
            final MeterRegistry meterRegistry,
            final OutboxEventEntityRepository outboxEventEntityRepository,
            final NotificationEntityRepository notificationEntityRepository) {

        this.outboxEventEntityRepository = outboxEventEntityRepository;
        this.notificationEntityRepository = notificationEntityRepository;

        Gauge.builder(PENDING_AGE_METRIC_NAME, pendingAgeSeconds, AtomicLong::doubleValue)
                .description("Age in seconds of the oldest pending order-placement outbox event")
                .register(meterRegistry);
        Gauge.builder(EXPIRED_CLAIMS_METRIC_NAME, expiredClaims, AtomicLong::doubleValue)
                .description("Number of expired PROCESSING order-placement claims awaiting recovery")
                .register(meterRegistry);
        Gauge.builder(RETRY_BACKLOG_METRIC_NAME, retryBacklog, AtomicLong::doubleValue)
                .description("Number of pending order-placement events with a failed fulfillment attempt")
                .register(meterRegistry);
        Gauge.builder(INCOMPLETE_COMPENSATION_METRIC_NAME, incompleteCompensations, AtomicLong::doubleValue)
                .description("Number of order-placement events with incomplete compensation")
                .register(meterRegistry);
        Gauge.builder(NOTIFICATION_LAG_METRIC_NAME, notificationLagSeconds, AtomicLong::doubleValue)
                .description("Seconds by which the oldest retryable notification is overdue")
                .register(meterRegistry);
        paymentUnknownCounter = Counter.builder(PAYMENT_UNKNOWN_METRIC_NAME)
                .description("Payment capture calls whose final provider outcome is unknown")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${recovery.metrics.refresh-ms:15000}")
    void refresh() {

        final Date now = new Date();
        pendingAgeSeconds
                .set(ageSeconds(outboxEventEntityRepository.findOldestCreatedDateByStatus(OutboxEventStatus.PENDING), now));
        expiredClaims
                .set(outboxEventEntityRepository.countByStatusAndClaimUntilLessThanEqual(OutboxEventStatus.PROCESSING, now));
        retryBacklog.set(outboxEventEntityRepository.countByStatusAndAttemptsGreaterThan(OutboxEventStatus.PENDING, 0));
        incompleteCompensations.set(outboxEventEntityRepository.countByStatus(OutboxEventStatus.COMPENSATING));
        notificationLagSeconds.set(
                ageSeconds(notificationEntityRepository.findOldestDueAttemptDate(RETRYABLE_NOTIFICATION_STATUSES, now), now));
    }

    public void recordPaymentUnknown() {

        paymentUnknownCounter.increment();
    }

    private static long ageSeconds(final Date timestamp, final Date now) {

        if (timestamp == null) {
            return 0L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(now.getTime() - timestamp.getTime()));
    }
}
