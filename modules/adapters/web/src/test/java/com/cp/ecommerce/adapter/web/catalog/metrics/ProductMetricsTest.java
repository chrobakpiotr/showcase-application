package com.cp.ecommerce.adapter.web.catalog.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class checking that {@link ProductMetrics} records the "catalog.products.created" and "catalog.products.updated"
 * counters correctly.
 */
class ProductMetricsTest {

    private transient MeterRegistry meterRegistry;

    private transient ProductMetrics productMetrics;

    @BeforeEach
    void setUp() {

        meterRegistry = new SimpleMeterRegistry();
        productMetrics = new ProductMetrics(meterRegistry);
    }

    @Test
    void shouldRegisterProductsCreatedCounterWithZeroInitialValue() {

        assertThat(meterRegistry.get("catalog.products.created").counter().count()).isZero();
    }

    @Test
    void shouldIncrementProductsCreatedCounterOnEachRecordedProduct() {

        productMetrics.recordProductCreated();
        productMetrics.recordProductCreated();

        assertThat(meterRegistry.get("catalog.products.created").counter().count()).isEqualTo(2);
    }

    @Test
    void shouldRegisterProductsUpdatedCounterWithZeroInitialValue() {

        assertThat(meterRegistry.get("catalog.products.updated").counter().count()).isZero();
    }

    @Test
    void shouldIncrementProductsUpdatedCounterOnEachRecordedUpdate() {

        productMetrics.recordProductUpdated();

        assertThat(meterRegistry.get("catalog.products.updated").counter().count()).isEqualTo(1);
    }

}
