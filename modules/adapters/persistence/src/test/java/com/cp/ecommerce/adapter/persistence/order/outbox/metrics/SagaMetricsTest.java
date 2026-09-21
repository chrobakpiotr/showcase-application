package com.cp.ecommerce.adapter.persistence.order.outbox.metrics;

import java.time.Duration;

import com.cp.ecommerce.domain.order.RemarksTriageCategory;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class SagaMetricsTest {

    @Test
    void shouldRecordStepOutcomeAndCompensation() {

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final SagaMetrics metrics = new SagaMetrics(registry);

        metrics.recordStepDuration("fulfillment", Duration.ofMillis(25), true);
        metrics.recordStepDuration("fulfillment", Duration.ofMillis(40), false);
        metrics.recordCompensation();

        assertThat(
                registry.get("saga.order-placement.step.duration")
                        .tag("step", "fulfillment")
                        .tag("outcome", "success")
                        .timer()
                        .count())
                .isEqualTo(1L);
        assertThat(
                registry.get("saga.order-placement.step.duration")
                        .tag("step", "fulfillment")
                        .tag("outcome", "failure")
                        .timer()
                        .count())
                .isEqualTo(1L);
        assertThat(registry.get("saga.order-placement.compensations").counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordEveryRemarksClassificationCategory() {

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final SagaMetrics metrics = new SagaMetrics(registry);

        for (final RemarksTriageCategory category : RemarksTriageCategory.values()) {
            metrics.recordRemarksClassification(category);

            assertThat(
                    registry.get("saga.order-placement.remarks-classifications")
                            .tag("category", category.name())
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }
    }

    @Test
    void shouldRecordBothDuplicateDetectionOutcomes() {

        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final SagaMetrics metrics = new SagaMetrics(registry);

        metrics.recordDuplicateOrderDetection(true);
        metrics.recordDuplicateOrderDetection(false);

        assertThat(registry.get("saga.order-placement.duplicate-order-detections").tag("duplicate", "true").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("saga.order-placement.duplicate-order-detections").tag("duplicate", "false").counter().count())
                .isEqualTo(1.0);
    }
}
