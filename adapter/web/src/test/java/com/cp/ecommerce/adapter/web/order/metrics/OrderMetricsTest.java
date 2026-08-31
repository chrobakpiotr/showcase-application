package com.cp.ecommerce.adapter.web.order.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class checking that {@link OrderMetrics} records the "orders.placed" and "orders.cancelled" counters correctly.
 */
class OrderMetricsTest {

    private transient MeterRegistry meterRegistry;

    private transient OrderMetrics orderMetrics;

    @BeforeEach
    void setUp() {

        meterRegistry = new SimpleMeterRegistry();
        orderMetrics = new OrderMetrics(meterRegistry);
    }

    @Test
    void shouldRegisterOrdersPlacedCounterWithZeroInitialValue() {

        assertThat(meterRegistry.get("orders.placed").counter().count()).isZero();
    }

    @Test
    void shouldIncrementOrdersPlacedCounterOnEachRecordedOrder() {

        orderMetrics.recordOrderPlaced();
        orderMetrics.recordOrderPlaced();

        assertThat(meterRegistry.get("orders.placed").counter().count()).isEqualTo(2);
    }

    @Test
    void shouldRegisterOrdersCancelledCounterWithZeroInitialValue() {

        assertThat(meterRegistry.get("orders.cancelled").counter().count()).isZero();
    }

    @Test
    void shouldIncrementOrdersCancelledCounterOnEachRecordedCancellation() {

        orderMetrics.recordOrderCancelled();

        assertThat(meterRegistry.get("orders.cancelled").counter().count()).isEqualTo(1);
    }

}
