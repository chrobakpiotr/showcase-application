package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges completed payment reconciliation evidence only after the configured local replay-safety horizon.
 */
@Component
@ConditionalOnProperty(
        prefix = "payment.reconciliation.retention",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
class PaymentReconciliationRetentionScheduler {

    private final PaymentReconciliationRetentionManager retentionManager;
    private final Clock clock;
    private final int retentionDays;
    private final int batchSize;

    PaymentReconciliationRetentionScheduler(
            final PaymentReconciliationRetentionManager retentionManager,
            final Clock clock,
            @Value("${payment.reconciliation.retention.days:90}") final int retentionDays,
            @Value("${payment.reconciliation.retention.replay-horizon-days:90}") final int replayHorizonDays,
            @Value("${payment.reconciliation.retention.batch-size:100}") final int batchSize) {

        if (retentionDays <= 0) {
            throw new IllegalArgumentException("payment reconciliation retention days must be positive");
        }
        if (replayHorizonDays <= 0) {
            throw new IllegalArgumentException("payment reconciliation replay horizon days must be positive");
        }
        if (retentionDays < replayHorizonDays) {
            throw new IllegalArgumentException(
                    "payment reconciliation retention days must be greater than or equal to replay horizon days");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("payment reconciliation retention batch size must be positive");
        }

        this.retentionManager = retentionManager;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${payment.reconciliation.retention.cron:0 17 3 * * *}")
    void purgeCompleted() {

        final Instant cutoff = Instant.ofEpochMilli(clock.instant().minus(Duration.ofDays(retentionDays)).toEpochMilli());
        retentionManager.purgeCompletedBefore(cutoff, batchSize);
    }
}
