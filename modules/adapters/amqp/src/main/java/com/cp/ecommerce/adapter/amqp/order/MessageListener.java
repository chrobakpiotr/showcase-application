package com.cp.ecommerce.adapter.amqp.order;

import com.cp.ecommerce.domain.order.OrderFulfillmentReceiptOutcome;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.port.incoming.ReceiveOrderMessageInPort;
import com.cp.ecommerce.foundation.exception.ApplicationBadRequestException;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** AMQP consumer that delegates each fulfillment command to the durable inbox boundary. */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageListener {

    private final Gson gson;
    private final ReceiveOrderMessageInPort receiveOrderMessageInPort;

    public void receiveMessage(final String message) {

        final OrderMessage orderMessage;
        try {
            orderMessage = gson.fromJson(message, OrderMessage.class);
        } catch (JsonParseException exception) {
            final ApplicationBadRequestException badRequestException = new ApplicationBadRequestException(
                    "Invalid order fulfillment message JSON");
            badRequestException.initCause(exception);
            throw badRequestException;
        }

        final OrderFulfillmentReceiptOutcome outcome = receiveOrderMessageInPort.receive(orderMessage);
        log.info(
                "Processed fulfillment message operationId={} orderNumber={} outcome={}",
                orderMessage.operationId(),
                orderMessage.orderNumber(),
                outcome);
    }
}
