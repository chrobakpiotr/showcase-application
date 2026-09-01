package com.cp.ecommerce.adapter.amqp.order.contract;

import java.util.Date;

import com.cp.ecommerce.adapter.amqp.order.mapper.OrderMessageMapper;
import com.cp.ecommerce.adapter.common.testfixtures.AsyncApiSchema;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight producer-side contract test for the AMQP order message.
 *
 * <p>
 * This keeps the showcase self-contained by asserting the published JSON schema directly, without introducing stub brokers,
 * stub artifact publishing or other infrastructure required by a full contract-testing framework. The current listener
 * intentionally only logs raw payloads, so the contract remains focused on the producer's wire format. The expected field set
 * itself comes from {@code etc/asyncapi/asyncapi.yml} (via {@link AsyncApiSchema}) rather than being duplicated here, so the
 * spec and the actual wire format cannot silently drift apart.
 * </p>
 */
class OrderMessageContractTest {

    private static final String CREATED = "created";

    private static final String CUSTOMER_ID = "customerId";

    private static final String ORDER_NUMBER = "orderNumber";

    private static final String SCHEMA_VERSION = "schemaVersion";

    private final transient OrderMessageMapper mapper = new OrderMessageMapper();

    @Test
    void shouldPublishExpectedOrderMessageSchema() {

        final Order order = validOrder();

        final String payload = new Gson().toJson(mapper.mapToMessage(order).orElseThrow());
        final JsonObject jsonPayload = JsonParser.parseString(payload).getAsJsonObject();

        assertEquals(AsyncApiSchema.declaredProperties("OrderMessage"), jsonPayload.keySet());
        assertTrue(jsonPayload.get(SCHEMA_VERSION).isJsonPrimitive());
        assertEquals(OrderMessage.SCHEMA_VERSION, jsonPayload.get(SCHEMA_VERSION).getAsString());
        assertTrue(jsonPayload.get(CREATED).isJsonPrimitive());
        assertTrue(jsonPayload.get(CREATED).getAsJsonPrimitive().isString());
        assertTrue(jsonPayload.get(CUSTOMER_ID).isJsonPrimitive());
        assertTrue(jsonPayload.get(CUSTOMER_ID).getAsJsonPrimitive().isNumber());
        assertEquals(1001L, jsonPayload.get(CUSTOMER_ID).getAsLong());
        assertTrue(jsonPayload.get(ORDER_NUMBER).isJsonPrimitive());
        assertEquals("ORD-1001", jsonPayload.get(ORDER_NUMBER).getAsString());
    }

    private Order validOrder() {

        return Order.builder()
                .remarks("remark")
                .orderNumber("ORD-1001")
                .created(new Date(1710000000000L))
                .customer(validCustomer())
                .build();
    }

    private Customer validCustomer() {

        return Customer.builder()
                .id(1001L)
                .contact(Contact.builder().fullName("John Doe").email("john.doe@test.com").phone("+48 123 456 789").build())
                .address(
                        Address.builder().street("Main Street 1").postalCode("12-345").city("Warsaw").countryCode("PL").build())
                .build();
    }

}
