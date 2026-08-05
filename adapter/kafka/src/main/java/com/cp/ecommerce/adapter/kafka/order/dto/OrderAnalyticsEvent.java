package com.cp.ecommerce.adapter.kafka.order.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Adapter-local DTO representing an order-placed event published to the Kafka analytics topic. This is intentionally kept
 * separate from the domain {@code Order} object, and versioned independently from the RabbitMQ {@code OrderMessage} and SQS
 * {@code OrderAuditEvent} wire formats since it has its own, unrelated consumers (BI/analytics pipelines).
 */
@Value
@Builder
public class OrderAnalyticsEvent {

    public static final String SCHEMA_VERSION = "1.0";

    @Builder.Default
    String schemaVersion = SCHEMA_VERSION;

    String orderNumber;

    Long customerId;

    String eventType;

    /** ISO-8601 timestamp string, e.g. {@code 2024-01-01T00:00:00Z}. */
    String timestamp;

}
