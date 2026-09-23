package com.cp.ecommerce.adapter.persistence.metrics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntityRepository;
import com.cp.ecommerce.domain.payment.RefundReturnContinuationStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RefundReturnContinuationMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-23T08:00:00Z");

    @Mock
    private RefundReturnContinuationEntityRepository repository;

    @Test
    void shouldExposePendingAgeCompletedRetriesAndParkedWithoutHighCardinalityLabels() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        given(repository.findOldestCreatedDateByStatus(RefundReturnContinuationStatus.PENDING))
                .willReturn(NOW.minusSeconds(45));
        given(repository.countByStatus(RefundReturnContinuationStatus.COMPLETED)).willReturn(8L);
        given(repository.countByStatusAndAttemptsGreaterThan(RefundReturnContinuationStatus.PENDING, 0)).willReturn(9L);
        given(repository.countByStatus(RefundReturnContinuationStatus.MANUAL_REVIEW)).willReturn(10L);

        final RefundReturnContinuationMetrics metrics = new RefundReturnContinuationMetrics(
                Optional.of(registry),
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        metrics.refresh();

        assertThat(registry.get(RefundReturnContinuationMetrics.PENDING_AGE_METRIC_NAME).gauge().value()).isEqualTo(45.0);
        assertThat(registry.get(RefundReturnContinuationMetrics.COMPLETED_METRIC_NAME).gauge().value()).isEqualTo(8.0);
        assertThat(registry.get(RefundReturnContinuationMetrics.RETRY_METRIC_NAME).gauge().value()).isEqualTo(9.0);
        assertThat(registry.get(RefundReturnContinuationMetrics.PARKED_METRIC_NAME).gauge().value()).isEqualTo(10.0);
    }

    @Test
    void shouldExposeZeroPendingAgeWhenNoPendingContinuationExists() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final RefundReturnContinuationMetrics metrics = new RefundReturnContinuationMetrics(
                Optional.of(registry),
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        metrics.refresh();
        assertThat(registry.get(RefundReturnContinuationMetrics.PENDING_AGE_METRIC_NAME).gauge().value()).isZero();
    }

    @Test
    void shouldRemainNoOpWithoutMeterRegistry() {
        final RefundReturnContinuationMetrics metrics = new RefundReturnContinuationMetrics(
                Optional.empty(),
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        metrics.refresh();
        verifyNoInteractions(repository);
    }
}
