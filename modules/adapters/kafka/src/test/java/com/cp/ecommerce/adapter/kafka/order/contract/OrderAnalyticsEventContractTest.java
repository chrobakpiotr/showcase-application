package com.cp.ecommerce.adapter.kafka.order.contract;

import com.cp.ecommerce.adapter.common.testfixtures.AsyncApiSchema;
import com.cp.ecommerce.adapter.kafka.order.dto.OrderAnalyticsEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight producer-side contract test for the Kafka order-analytics event.
 *
 * <p>
 * Mirrors the AMQP {@code OrderMessageContractTest}: instead of introducing the full operational footprint of Spring Cloud
 * Contract or Pact, this asserts the wire-level JSON schema that analytics consumers depend on directly. The expected field set
 * comes from {@code etc/asyncapi/asyncapi.yml} (via {@link AsyncApiSchema}) rather than a duplicated {@code Set.of(...)}, so
 * the spec and the actual wire format cannot silently drift apart.
 * </p>
 */
class OrderAnalyticsEventContractTest {

    private static final String SCHEMA_VERSION = "schemaVersion";

    private static final String ORDER_NUMBER = "orderNumber";

    private static final String CUSTOMER_ID = "customerId";

    private static final String EVENT_TYPE = "eventType";

    private static final String TIMESTAMP = "timestamp";

    @Test
    void shouldPublishExpectedOrderAnalyticsEventSchema() {

        final OrderAnalyticsEvent event = OrderAnalyticsEvent.builder()
                .orderNumber("ORD-1001")
                .customerId(1001L)
                .eventType("ORDER_PLACED")
                .timestamp("2024-01-01T00:00:00Z")
                .build();

        final String payload = new Gson().toJson(event);
        final JsonObject jsonPayload = JsonParser.parseString(payload).getAsJsonObject();

        assertEquals(AsyncApiSchema.declaredProperties("OrderAnalyticsEvent"), jsonPayload.keySet());
        assertEquals(OrderAnalyticsEvent.SCHEMA_VERSION, jsonPayload.get(SCHEMA_VERSION).getAsString());
        assertEquals("ORD-1001", jsonPayload.get(ORDER_NUMBER).getAsString());
        assertEquals(1001L, jsonPayload.get(CUSTOMER_ID).getAsLong());
        assertEquals("ORDER_PLACED", jsonPayload.get(EVENT_TYPE).getAsString());
        assertTrue(jsonPayload.get(TIMESTAMP).getAsString().startsWith("2024-01-01"));
    }

}
