package com.cp.ecommerce.adapter.kafka.order.metrics;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Test class checking that {@link OrderAnalyticsConsumerMetrics} records the "orders.analytics.consumed" and
 * "orders.analytics.dead_lettered" counters correctly, and behaves as expected as a {@code RetryListener}.
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

    @Test
    void shouldRegisterDeadLetteredCounterWithZeroInitialValue() {

        assertThat(meterRegistry.get("orders.analytics.dead_lettered").counter().count()).isZero();
    }

    @Test
    void shouldNotIncrementDeadLetteredCounterOnFailedDeliveryAlone() {

        orderAnalyticsConsumerMetrics.failedDelivery(consumerRecord(), new IllegalStateException("boom"), 1);

        assertThat(meterRegistry.get("orders.analytics.dead_lettered").counter().count()).isZero();
    }

    @Test
    void shouldIncrementDeadLetteredCounterWhenRecordIsRecovered() {

        orderAnalyticsConsumerMetrics.recovered(consumerRecord(), new IllegalStateException("boom"));

        assertThat(meterRegistry.get("orders.analytics.dead_lettered").counter().count()).isEqualTo(1);
    }

    @Test
    void shouldNotThrowWhenRecoveryItselfFails() {

        assertThatCode(
                () -> orderAnalyticsConsumerMetrics.recoveryFailed(
                        consumerRecord(),
                        new IllegalStateException("original"),
                        new IllegalStateException("dlt publish failed")))
                .doesNotThrowAnyException();
    }

    private ConsumerRecord<Object, Object> consumerRecord() {

        return new ConsumerRecord<>("com.cp.e.topic.order.analytics", 0, 42L, "key", "value");
    }

}
