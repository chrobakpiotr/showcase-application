package com.cp.ecommerce.adapter.amqp.order;

import java.time.Instant;

import com.cp.ecommerce.adapter.common.configuration.GsonConfiguration;
import com.cp.ecommerce.domain.order.OrderFulfillmentReceiptOutcome;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.port.incoming.ReceiveOrderMessageInPort;
import com.cp.ecommerce.foundation.exception.ApplicationBadRequestException;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageListenerTest {

    private final Gson gson = new GsonConfiguration().gson();
    private final ReceiveOrderMessageInPort receiveOrderMessageInPort = mock(ReceiveOrderMessageInPort.class);
    private final MessageListener listener = new MessageListener(gson, receiveOrderMessageInPort);

    @Test
    void shouldDeserializeAndDelegateDurableProcessing() {

        final OrderMessage message = new OrderMessage(
                OrderMessage.SCHEMA_VERSION,
                "ORDER-FULFILLMENT:ORD-1",
                Instant.parse("2026-09-24T12:00:00Z"),
                10L,
                "ORD-1");
        given(receiveOrderMessageInPort.receive(message)).willReturn(OrderFulfillmentReceiptOutcome.RECORDED);

        listener.receiveMessage(gson.toJson(message));

        verify(receiveOrderMessageInPort).receive(message);
    }

    @Test
    void shouldRejectMalformedJson() {

        assertThatThrownBy(() -> listener.receiveMessage("{not-json")).isInstanceOf(ApplicationBadRequestException.class)
                .hasMessageContaining("Invalid order fulfillment message JSON")
                .hasCauseInstanceOf(JsonParseException.class);
    }
}
