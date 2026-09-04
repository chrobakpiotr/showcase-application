package com.cp.ecommerce.adapter.persistence.order.outbox.metrics;

import java.time.Duration;

import com.cp.ecommerce.domain.order.RemarksTriageCategory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Records Micrometer metrics for each step of the order-placement saga orchestrated by {@code OrderPlacementSagaOrchestrator},
 * exposed to the configured registries (e.g. Prometheus) the same way {@code OrderMetrics} exposes order-level business
 * metrics.
 *
 * <p>
 * A single dimensional timer ({@code saga.order-placement.step.duration}) tagged by {@code step} (fulfillment,
 * confirmation-email, s3-export, sqs-audit, kafka-analytics, camel-routing, ai-remarks-triage) and {@code outcome}
 * (success/failure) covers all seven saga steps, instead of one bespoke timer per step - this keeps the metric surface small
 * while still letting a Grafana/Prometheus query slice by either dimension (e.g. p99 duration of just the pivot "fulfillment"
 * step, or the failure rate of "sqs-audit" specifically).
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SagaMetrics {

    private static final String STEP_DURATION_METRIC_NAME = "saga.order-placement.step.duration";

    private static final String COMPENSATION_METRIC_NAME = "saga.order-placement.compensations";

    private static final String REMARKS_CLASSIFICATION_METRIC_NAME = "saga.order-placement.remarks-classifications";

    private final transient MeterRegistry meterRegistry;

    private final transient Counter compensationCounter;

    public SagaMetrics(final MeterRegistry meterRegistry) {

        this.meterRegistry = meterRegistry;
        this.compensationCounter = Counter.builder(COMPENSATION_METRIC_NAME)
                .description(
                        "Number of order-placement sagas that exhausted fulfillment retries and ran their compensating "
                                + "transaction (order cancellation)")
                .register(meterRegistry);
    }

    /**
     * Records how long a single saga step took, tagged with its outcome.
     */
    public void recordStepDuration(final String step, final Duration duration, final boolean success) {

        Timer.builder(STEP_DURATION_METRIC_NAME)
                .description("Duration of each order-placement saga step")
                .tag("step", step)
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * Records that the saga's compensating transaction ran (fulfillment retries exhausted, order cancelled).
     */
    public void recordCompensation() {

        compensationCounter.increment();
    }

    /**
     * Records the outcome category of an AI-assisted best-effort remarks triage, tagged by {@code category} (see
     * {@link RemarksTriageCategory}) - never used to automatically act on the order, only to give a human reviewer a
     * Grafana-visible signal of how many orders per category are coming in (e.g. an unexpected spike in {@code SUSPICIOUS}).
     */
    public void recordRemarksClassification(final RemarksTriageCategory category) {

        Counter.builder(REMARKS_CLASSIFICATION_METRIC_NAME)
                .description("Outcome categories of the AI-assisted order-remarks triage saga step")
                .tag("category", category.name())
                .register(meterRegistry)
                .increment();
    }

}
