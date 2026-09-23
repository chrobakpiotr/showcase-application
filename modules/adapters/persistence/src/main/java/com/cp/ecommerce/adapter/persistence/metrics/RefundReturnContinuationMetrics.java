package com.cp.ecommerce.adapter.persistence.metrics;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntityRepository;
import com.cp.ecommerce.domain.payment.RefundReturnContinuationStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Low-cardinality metrics for the bounded refund-to-RMA continuation queue. */
@Component
class RefundReturnContinuationMetrics {

    static final String PENDING_AGE_METRIC_NAME = "payment.refund-return.pending.age";
    static final String COMPLETED_METRIC_NAME = "payment.refund-return.completed";
    static final String RETRY_METRIC_NAME = "payment.refund-return.retry";
    static final String PARKED_METRIC_NAME = "payment.refund-return.parked";

    private final RefundReturnContinuationEntityRepository repository;
    private final Clock clock;
    private final Optional<MeterRegistry> meterRegistry;
    private final AtomicLong pendingAgeSeconds = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong parked = new AtomicLong();

    @Autowired
    RefundReturnContinuationMetrics(
            final Optional<MeterRegistry> meterRegistry,
            final RefundReturnContinuationEntityRepository repository,
            final Clock clock) {
        this.meterRegistry = meterRegistry;
        this.repository = repository;
        this.clock = clock;
        meterRegistry.ifPresent(this::register);
    }

    @Scheduled(fixedDelayString = "${recovery.metrics.refresh-ms:15000}")
    void refresh() {
        if (meterRegistry.isEmpty()) {
            return;
        }
        final Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        pendingAgeSeconds
                .set(ageSeconds(repository.findOldestCreatedDateByStatus(RefundReturnContinuationStatus.PENDING), now));
        completed.set(repository.countByStatus(RefundReturnContinuationStatus.COMPLETED));
        retries.set(repository.countByStatusAndAttemptsGreaterThan(RefundReturnContinuationStatus.PENDING, 0));
        parked.set(repository.countByStatus(RefundReturnContinuationStatus.MANUAL_REVIEW));
    }

    private void register(final MeterRegistry registry) {
        gauge(
                registry,
                PENDING_AGE_METRIC_NAME,
                pendingAgeSeconds,
                "Age in seconds of the oldest pending refund-to-return continuation");
        gauge(registry, COMPLETED_METRIC_NAME, completed, "Number of completed durable refund-to-return continuations");
        gauge(registry, RETRY_METRIC_NAME, retries, "Number of pending refund-to-return continuations with previous failures");
        gauge(registry, PARKED_METRIC_NAME, parked, "Number of refund-to-return continuations parked for manual review");
    }

    private static void gauge(
            final MeterRegistry registry,
            final String name,
            final AtomicLong value,
            final String description) {
        Gauge.builder(name, value, AtomicLong::doubleValue).description(description).register(registry);
    }

    private static long ageSeconds(final Instant timestamp, final Instant now) {
        if (timestamp == null) {
            return 0L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(now.toEpochMilli() - timestamp.toEpochMilli()));
    }
}
