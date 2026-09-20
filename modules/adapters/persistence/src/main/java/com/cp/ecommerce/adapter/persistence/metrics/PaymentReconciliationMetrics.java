package com.cp.ecommerce.adapter.persistence.metrics;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
class PaymentReconciliationMetrics {

    static final String PENDING_METRIC_NAME = "payment.reconciliation.pending";
    static final String PENDING_AGE_METRIC_NAME = "payment.reconciliation.pending.age";
    static final String MANUAL_REVIEW_METRIC_NAME = "payment.reconciliation.manual-review";

    private final PaymentReconciliationEntityRepository repository;
    private final Clock clock;
    private final boolean enabled;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong pendingAgeSeconds = new AtomicLong();
    private final AtomicLong manualReview = new AtomicLong();

    PaymentReconciliationMetrics(
            final Optional<MeterRegistry> meterRegistry,
            final PaymentReconciliationEntityRepository repository,
            final Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.enabled = meterRegistry.isPresent();
        meterRegistry.ifPresent(this::register);
    }

    @Scheduled(fixedDelayString = "${recovery.metrics.refresh-ms:15000}")
    void refresh() {
        if (!enabled) {
            return;
        }
        final Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        pending.set(repository.countByStatus(PaymentReconciliationStatus.PENDING));
        manualReview.set(repository.countByStatus(PaymentReconciliationStatus.MANUAL_REVIEW));
        final Instant oldest = repository.findOldestCreatedByStatus(PaymentReconciliationStatus.PENDING);
        pendingAgeSeconds.set(
                oldest == null
                        ? 0L
                        : Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(now.toEpochMilli() - oldest.toEpochMilli())));
    }

    private void register(final MeterRegistry registry) {
        Gauge.builder(PENDING_METRIC_NAME, pending, AtomicLong::doubleValue).register(registry);
        Gauge.builder(PENDING_AGE_METRIC_NAME, pendingAgeSeconds, AtomicLong::doubleValue).register(registry);
        Gauge.builder(MANUAL_REVIEW_METRIC_NAME, manualReview, AtomicLong::doubleValue).register(registry);
    }
}
