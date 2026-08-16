package com.cp.ecommerce.adapter.kafka.order.dto;

import lombok.Builder;

/**
 * Adapter-local DTO representing an order-placed event published to the Kafka analytics topic. This is intentionally kept
 * separate from the domain {@code Order} object, and versioned independently from the RabbitMQ {@code OrderMessage} and SQS
 * {@code OrderAuditEvent} wire formats since it has its own, unrelated consumers (BI/analytics pipelines).
 *
 * @param timestamp ISO-8601 timestamp string, e.g. {@code 2024-01-01T00:00:00Z}.
 */
@Builder
public record OrderAnalyticsEvent(String schemaVersion, String orderNumber, Long customerId, String eventType,
        String timestamp) {

    public static final String SCHEMA_VERSION = "1.0";

    public OrderAnalyticsEvent {

        if (schemaVersion == null) {
            schemaVersion = SCHEMA_VERSION;
        }
    }

}
