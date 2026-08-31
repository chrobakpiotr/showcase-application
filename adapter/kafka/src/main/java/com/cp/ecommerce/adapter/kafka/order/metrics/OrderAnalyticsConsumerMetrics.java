package com.cp.ecommerce.adapter.kafka.order.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Component recording metrics related to consuming order-analytics events from Kafka, exposed via Micrometer to the configured
 * registries (e.g. Prometheus). Kept local to adapter:kafka rather than reusing adapter:web's {@code OrderMetrics}, since
 * adapters must not depend on one another in this hexagonal layout.
 */
@Component
public class OrderAnalyticsConsumerMetrics {

    private static final String ORDER_ANALYTICS_CONSUMED_METRIC_NAME = "orders.analytics.consumed";

    private final transient Counter orderAnalyticsConsumedCounter;

    public OrderAnalyticsConsumerMetrics(final MeterRegistry meterRegistry) {

        this.orderAnalyticsConsumedCounter = Counter.builder(ORDER_ANALYTICS_CONSUMED_METRIC_NAME)
                .description("Number of order-analytics events consumed from Kafka and recorded into the read model")
                .register(meterRegistry);
    }

    public void recordConsumed() {

        orderAnalyticsConsumedCounter.increment();
    }

}
