package com.cp.ecommerce.adapter.web.catalog.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Component recording business metrics related to the catalog, exposed via Micrometer to the configured registries (e.g.
 * Prometheus).
 */
@Component
public class ProductMetrics {

    private static final String PRODUCTS_CREATED_METRIC_NAME = "catalog.products.created";

    private static final String PRODUCTS_UPDATED_METRIC_NAME = "catalog.products.updated";

    private final transient Counter productsCreatedCounter;

    private final transient Counter productsUpdatedCounter;

    public ProductMetrics(final MeterRegistry meterRegistry) {

        this.productsCreatedCounter = Counter.builder(PRODUCTS_CREATED_METRIC_NAME)
                .description("Number of catalog products successfully created")
                .register(meterRegistry);
        this.productsUpdatedCounter = Counter.builder(PRODUCTS_UPDATED_METRIC_NAME)
                .description("Number of catalog products successfully updated")
                .register(meterRegistry);
    }

    public void recordProductCreated() {

        productsCreatedCounter.increment();
    }

    public void recordProductUpdated() {

        productsUpdatedCounter.increment();
    }

}
