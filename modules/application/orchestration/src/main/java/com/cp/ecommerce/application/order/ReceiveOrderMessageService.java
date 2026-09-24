package com.cp.ecommerce.application.order;

import com.cp.ecommerce.domain.order.OrderFulfillmentReceiptOutcome;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.port.incoming.ReceiveOrderMessageInPort;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderFulfillmentReceiptOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationBadRequestException;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Validates the RabbitMQ fulfillment envelope before the durable inbox boundary. */
@Service
@RequiredArgsConstructor
public class ReceiveOrderMessageService implements ReceiveOrderMessageInPort {

    private static final int OPERATION_ID_MAX_LENGTH = 120;
    private static final int ORDER_NUMBER_MAX_LENGTH = 40;

    private final SaveOrderFulfillmentReceiptOutPort receiptOutPort;

    @Override
    public OrderFulfillmentReceiptOutcome receive(final OrderMessage message) {

        if (message == null) {
            throw new ApplicationBadRequestException("order fulfillment message is required");
        }
        if (!OrderMessage.SCHEMA_VERSION.equals(message.schemaVersion())) {
            throw new ApplicationBadRequestException("unsupported order fulfillment schemaVersion");
        }
        requireText(message.operationId(), "operationId", OPERATION_ID_MAX_LENGTH);
        requireText(message.orderNumber(), "orderNumber", ORDER_NUMBER_MAX_LENGTH);
        if (message.customerId() == null) {
            throw new ApplicationBadRequestException("customerId is required");
        }
        if (message.created() == null) {
            throw new ApplicationBadRequestException("created is required");
        }
        return receiptOutPort.saveOnce(message);
    }

    private static void requireText(final String value, final String field, final int maxLength) {

        if (value == null || value.isBlank()) {
            throw new ApplicationBadRequestException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new ApplicationBadRequestException(field + " must not exceed " + maxLength + " characters");
        }
    }
}
