package com.cp.ecommerce.adapter.persistence.metrics;

import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RecoveryMetricsTest {

    @Mock
    private transient OutboxEventEntityRepository outboxEventEntityRepository;

    @Mock
    private transient NotificationEntityRepository notificationEntityRepository;

    private transient SimpleMeterRegistry meterRegistry;

    private transient RecoveryMetrics recoveryMetrics;

    @BeforeEach
    void setUp() {

        meterRegistry = new SimpleMeterRegistry();
        recoveryMetrics = new RecoveryMetrics(
                Optional.of(meterRegistry),
                outboxEventEntityRepository,
                notificationEntityRepository);
    }

    @Test
    void shouldRefreshDurableRecoveryBacklogMetrics() {

        final long now = System.currentTimeMillis();

        given(outboxEventEntityRepository.findOldestCreatedDateByStatus(OutboxEventStatus.PENDING))
                .willReturn(new Date(now - 60_000L));
        given(
                outboxEventEntityRepository
                        .countByStatusAndClaimUntilLessThanEqual(eq(OutboxEventStatus.PROCESSING), any(Date.class)))
                .willReturn(2L);
        given(outboxEventEntityRepository.countByStatusAndAttemptsGreaterThan(OutboxEventStatus.PENDING, 0)).willReturn(3L);
        given(outboxEventEntityRepository.countByStatus(OutboxEventStatus.COMPENSATING)).willReturn(4L);
        given(notificationEntityRepository.findOldestDueAttemptDate(anyList(), any(Date.class)))
                .willReturn(new Date(now - 30_000L));

        recoveryMetrics.refresh();

        assertThat(gauge(RecoveryMetrics.PENDING_AGE_METRIC_NAME)).isGreaterThanOrEqualTo(60.0);
        assertThat(gauge(RecoveryMetrics.EXPIRED_CLAIMS_METRIC_NAME)).isEqualTo(2.0);
        assertThat(gauge(RecoveryMetrics.RETRY_BACKLOG_METRIC_NAME)).isEqualTo(3.0);
        assertThat(gauge(RecoveryMetrics.INCOMPLETE_COMPENSATION_METRIC_NAME)).isEqualTo(4.0);
        assertThat(gauge(RecoveryMetrics.NOTIFICATION_LAG_METRIC_NAME)).isGreaterThanOrEqualTo(30.0);
    }

    @Test
    void shouldExposeZeroAgeWhenNoPendingRecoveryWorkExists() {

        given(outboxEventEntityRepository.findOldestCreatedDateByStatus(OutboxEventStatus.PENDING)).willReturn(null);
        given(notificationEntityRepository.findOldestDueAttemptDate(anyList(), any(Date.class))).willReturn(null);

        recoveryMetrics.refresh();

        assertThat(gauge(RecoveryMetrics.PENDING_AGE_METRIC_NAME)).isZero();
        assertThat(gauge(RecoveryMetrics.NOTIFICATION_LAG_METRIC_NAME)).isZero();
    }

    @Test
    void shouldCountUnknownPaymentOutcomes() {

        recoveryMetrics.recordPaymentUnknown();

        assertThat(meterRegistry.get(RecoveryMetrics.PAYMENT_UNKNOWN_METRIC_NAME).counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldRemainNoOpWhenMeterRegistryIsUnavailable() {

        final RecoveryMetrics metricsWithoutRegistry = new RecoveryMetrics(
                Optional.empty(),
                outboxEventEntityRepository,
                notificationEntityRepository);

        metricsWithoutRegistry.refresh();
        metricsWithoutRegistry.recordPaymentUnknown();

        verifyNoInteractions(outboxEventEntityRepository, notificationEntityRepository);
    }

    private double gauge(final String name) {

        return meterRegistry.get(name).gauge().value();
    }
}
