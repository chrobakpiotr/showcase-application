package com.cp.ecommerce.adapter.kafka.order.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class checking that {@link OrderAnalyticsConsumerMetrics} records the "orders.analytics.consumed" counter correctly.
 */
class OrderAnalyticsConsumerMetricsTest {

    private transient MeterRegistry meterRegistry;

    private transient OrderAnalyticsConsumerMetrics orderAnalyticsConsumerMetrics;

    @BeforeEach
    void setUp() {

        meterRegistry = new SimpleMeterRegistry();
        orderAnalyticsConsumerMetrics = new OrderAnalyticsConsumerMetrics(meterRegistry);
    }

    @Test
    void shouldRegisterCounterWithZeroInitialValue() {

        assertThat(meterRegistry.get("orders.analytics.consumed").counter().count()).isZero();
    }

    @Test
    void shouldIncrementCounterOnEachRecordedConsumption() {

        orderAnalyticsConsumerMetrics.recordConsumed();
        orderAnalyticsConsumerMetrics.recordConsumed();

        assertThat(meterRegistry.get("orders.analytics.consumed").counter().count()).isEqualTo(2);
    }

}
