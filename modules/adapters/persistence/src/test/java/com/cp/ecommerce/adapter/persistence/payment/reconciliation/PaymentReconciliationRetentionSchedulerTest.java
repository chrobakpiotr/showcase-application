package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationRetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    @Mock
    private PaymentReconciliationRetentionManager retentionManager;

    @Test
    void shouldDeleteOneBoundedBatchAtRetentionCutoff() {

        final PaymentReconciliationRetentionScheduler scheduler = scheduler(90, 60, 25);

        scheduler.purgeCompleted();

        verify(retentionManager).purgeCompletedBefore(Instant.parse("2026-06-22T12:00:00Z"), 25);
    }

    @Test
    void shouldRejectNonPositiveRetentionDays() {

        assertThatThrownBy(() -> scheduler(0, 60, 25)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retention days must be positive");
    }

    @Test
    void shouldRejectNonPositiveReplayHorizon() {

        assertThatThrownBy(() -> scheduler(90, 0, 25)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replay horizon days must be positive");
    }

    @Test
    void shouldRejectRetentionShorterThanReplayHorizon() {

        assertThatThrownBy(() -> scheduler(30, 90, 25)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than or equal to replay horizon");
    }

    @Test
    void shouldRejectNonPositiveBatchSize() {

        assertThatThrownBy(() -> scheduler(90, 60, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch size must be positive");
    }

    private PaymentReconciliationRetentionScheduler scheduler(
            final int retentionDays,
            final int replayHorizonDays,
            final int batchSize) {

        return new PaymentReconciliationRetentionScheduler(
                retentionManager,
                Clock.fixed(NOW, ZoneOffset.UTC),
                retentionDays,
                replayHorizonDays,
                batchSize);
    }
}
