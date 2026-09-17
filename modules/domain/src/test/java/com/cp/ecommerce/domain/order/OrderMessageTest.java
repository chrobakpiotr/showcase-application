package com.cp.ecommerce.domain.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link OrderMessage}.
 */
class OrderMessageTest {

    @Test
    void shouldUseCurrentSchemaVersionByDefault() {

        final OrderMessage message = OrderMessage.builder().build();

        assertEquals(OrderMessage.SCHEMA_VERSION, message.schemaVersion());
    }

}
