package com.cp.ecommerce.adapter.persistence.order.outbox.metrics;

import java.time.Duration;

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
 * confirmation-email, s3-export, sqs-audit, kafka-analytics, camel-routing) and {@code outcome} (success/failure) covers all
 * six saga steps, instead of one bespoke timer per step - this keeps the metric surface small while still letting a
 * Grafana/Prometheus query slice by either dimension (e.g. p99 duration of just the pivot "fulfillment" step, or the failure
 * rate of "sqs-audit" specifically).
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SagaMetrics {

    private static final String STEP_DURATION_METRIC_NAME = "saga.order-placement.step.duration";

    private static final String COMPENSATION_METRIC_NAME = "saga.order-placement.compensations";

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

}
