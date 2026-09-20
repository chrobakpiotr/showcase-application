package com.cp.ecommerce.adapter.persistence.metrics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentRefundStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RecoveryMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OutboxEventEntityRepository outboxEventEntityRepository;
    @Mock
    private NotificationEntityRepository notificationEntityRepository;
    @Mock
    private PaymentRefundEntityRepository paymentRefundEntityRepository;

    private SimpleMeterRegistry meterRegistry;
    private RecoveryMetrics recoveryMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        recoveryMetrics = new RecoveryMetrics(
                Optional.of(meterRegistry),
                outboxEventEntityRepository,
                notificationEntityRepository,
                Optional.of(paymentRefundEntityRepository),
                Optional.of(CLOCK));
    }

    @Test
    void shouldRefreshDurableRecoveryBacklogMetricsAtInjectedClock() {
        given(outboxEventEntityRepository.findOldestCreatedDateByStatus(OutboxEventStatus.PENDING))
                .willReturn(NOW.minusSeconds(60));
        given(outboxEventEntityRepository.countByStatusAndClaimUntilLessThanEqual(OutboxEventStatus.PROCESSING, NOW))
                .willReturn(2L);
        given(outboxEventEntityRepository.countByStatusAndAttemptsGreaterThan(OutboxEventStatus.PENDING, 0)).willReturn(3L);
        given(outboxEventEntityRepository.countByStatus(OutboxEventStatus.COMPENSATING)).willReturn(4L);
        given(outboxEventEntityRepository.countByStatus(OutboxEventStatus.CANCELLING)).willReturn(5L);
        given(paymentRefundEntityRepository.countByStatus(PaymentRefundStatus.PENDING)).willReturn(6L);
        given(outboxEventEntityRepository.countByStatus(OutboxEventStatus.MANUAL_REVIEW)).willReturn(7L);
        given(notificationEntityRepository.findOldestDueAttemptDate(anyList(), eq(NOW))).willReturn(NOW.minusSeconds(30));

        recoveryMetrics.refresh();

        assertThat(gauge(RecoveryMetrics.PENDING_AGE_METRIC_NAME)).isEqualTo(60.0);
        assertThat(gauge(RecoveryMetrics.EXPIRED_CLAIMS_METRIC_NAME)).isEqualTo(2.0);
        assertThat(gauge(RecoveryMetrics.RETRY_BACKLOG_METRIC_NAME)).isEqualTo(3.0);
        assertThat(gauge(RecoveryMetrics.INCOMPLETE_COMPENSATION_METRIC_NAME)).isEqualTo(4.0);
        assertThat(gauge(RecoveryMetrics.CANCELLING_METRIC_NAME)).isEqualTo(5.0);
        assertThat(gauge(RecoveryMetrics.PENDING_REFUNDS_METRIC_NAME)).isEqualTo(6.0);
        assertThat(gauge(RecoveryMetrics.MANUAL_REVIEW_METRIC_NAME)).isEqualTo(7.0);
        assertThat(gauge(RecoveryMetrics.NOTIFICATION_LAG_METRIC_NAME)).isEqualTo(30.0);
    }

    @Test
    void shouldExposeZeroAgeWhenNoPendingRecoveryWorkExists() {
        given(outboxEventEntityRepository.findOldestCreatedDateByStatus(OutboxEventStatus.PENDING)).willReturn(null);
        given(notificationEntityRepository.findOldestDueAttemptDate(anyList(), eq(NOW))).willReturn(null);

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
                notificationEntityRepository,
                Optional.of(paymentRefundEntityRepository),
                Optional.of(CLOCK));

        metricsWithoutRegistry.refresh();
        metricsWithoutRegistry.recordPaymentUnknown();

        verifyNoInteractions(outboxEventEntityRepository, notificationEntityRepository, paymentRefundEntityRepository);
    }

    private double gauge(final String name) {
        return meterRegistry.get(name).gauge().value();
    }

    @Test
    void shouldRequireApplicationClock() {

        assertThatThrownBy(
                () -> new RecoveryMetrics(
                        Optional.empty(),
                        outboxEventEntityRepository,
                        notificationEntityRepository,
                        Optional.empty(),
                        Optional.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Clock");
    }

    @Test
    void shouldExposeZeroPendingRefundsWhenRefundRepositoryIsUnavailable() {

        final RecoveryMetrics metricsWithoutRefundRepository = new RecoveryMetrics(
                Optional.of(meterRegistry),
                outboxEventEntityRepository,
                notificationEntityRepository,
                Optional.empty(),
                Optional.of(CLOCK));

        metricsWithoutRefundRepository.refresh();

        assertThat(meterRegistry.get(RecoveryMetrics.PENDING_REFUNDS_METRIC_NAME).gauge().value()).isZero();
    }

}
