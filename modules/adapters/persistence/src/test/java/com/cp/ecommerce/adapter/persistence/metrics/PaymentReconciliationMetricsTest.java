package com.cp.ecommerce.adapter.persistence.metrics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private PaymentReconciliationEntityRepository repository;

    @Test
    void shouldExposeBacklogAgeAndManualReview() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByStatus(PaymentReconciliationStatus.PENDING)).willReturn(3L);
        given(repository.countByStatus(PaymentReconciliationStatus.MANUAL_REVIEW)).willReturn(2L);
        given(repository.findOldestCreatedByStatus(PaymentReconciliationStatus.PENDING)).willReturn(NOW.minusSeconds(40));

        final PaymentReconciliationMetrics metrics = new PaymentReconciliationMetrics(Optional.of(registry), repository, CLOCK);
        metrics.refresh();

        assertThat(registry.get(PaymentReconciliationMetrics.PENDING_METRIC_NAME).gauge().value()).isEqualTo(3.0);
        assertThat(registry.get(PaymentReconciliationMetrics.PENDING_AGE_METRIC_NAME).gauge().value()).isEqualTo(40.0);
        assertThat(registry.get(PaymentReconciliationMetrics.MANUAL_REVIEW_METRIC_NAME).gauge().value()).isEqualTo(2.0);
    }

    @Test
    void shouldRemainNoOpWithoutMetricsRegistry() {
        new PaymentReconciliationMetrics(Optional.empty(), repository, CLOCK).refresh();
        verifyNoInteractions(repository);
    }

    @Test
    void shouldExposeZeroPendingAgeWhenBacklogHasNoOldestRow() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        given(repository.findOldestCreatedByStatus(PaymentReconciliationStatus.PENDING)).willReturn(null);

        final PaymentReconciliationMetrics metrics = new PaymentReconciliationMetrics(Optional.of(registry), repository, CLOCK);
        metrics.refresh();

        assertThat(registry.get(PaymentReconciliationMetrics.PENDING_AGE_METRIC_NAME).gauge().value()).isZero();
    }
}
