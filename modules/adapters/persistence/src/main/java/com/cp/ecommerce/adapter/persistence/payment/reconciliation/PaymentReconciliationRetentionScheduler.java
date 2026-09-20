package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "payment.reconciliation.retention",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
class PaymentReconciliationRetentionScheduler {

    private final PaymentReconciliationEntityRepository repository;
    private final Clock clock;
    private final int retentionDays;

    PaymentReconciliationRetentionScheduler(
            final PaymentReconciliationEntityRepository repository,
            final Clock clock,
            @Value("${payment.reconciliation.retention.days:90}") final int retentionDays) {
        this.repository = repository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${payment.reconciliation.retention.cron:0 17 3 * * *}")
    void purgeCompleted() {
        final Instant cutoff = Instant.ofEpochMilli(clock.instant().minus(Duration.ofDays(retentionDays)).toEpochMilli());
        repository.deleteByStatusAndCompletedBefore(PaymentReconciliationStatus.COMPLETED, cutoff);
    }
}
