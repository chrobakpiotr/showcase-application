package com.cp.ecommerce.application.order;

import java.time.Instant;

import com.cp.ecommerce.domain.order.OrderFulfillmentReceiptOutcome;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderFulfillmentReceiptOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationBadRequestException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReceiveOrderMessageServiceTest {

    private static final Instant CREATED = Instant.parse("2026-09-24T12:00:00Z");
    private static final String OPERATION_ID = "op-1";
    private static final String ORDER_NUMBER = "ORD-1";

    @Mock
    private SaveOrderFulfillmentReceiptOutPort receiptOutPort;

    @Test
    void shouldDelegateValidMessage() {

        final OrderMessage message = validMessage();
        given(receiptOutPort.saveOnce(message)).willReturn(OrderFulfillmentReceiptOutcome.RECORDED);

        assertThat(new ReceiveOrderMessageService(receiptOutPort).receive(message))
                .isEqualTo(OrderFulfillmentReceiptOutcome.RECORDED);
    }

    @Test
    void shouldRejectMissingMessage() {
        assertInvalid(null, "message is required");
    }

    @Test
    void shouldRejectUnsupportedSchemaVersion() {
        assertInvalid(message("2.0", OPERATION_ID, ORDER_NUMBER, 10L, CREATED), "schemaVersion");
    }

    @Test
    void shouldRejectBlankOperationId() {
        assertInvalid(message(OrderMessage.SCHEMA_VERSION, " ", ORDER_NUMBER, 10L, CREATED), "operationId is required");
    }

    @Test
    void shouldRejectOversizedOperationId() {
        assertInvalid(message(OrderMessage.SCHEMA_VERSION, "x".repeat(121), ORDER_NUMBER, 10L, CREATED), "120");
    }

    @Test
    void shouldRejectBlankOrderNumber() {
        assertInvalid(message(OrderMessage.SCHEMA_VERSION, OPERATION_ID, " ", 10L, CREATED), "orderNumber is required");
    }

    @Test
    void shouldRejectOversizedOrderNumber() {
        assertInvalid(message(OrderMessage.SCHEMA_VERSION, OPERATION_ID, "x".repeat(41), 10L, CREATED), "40");
    }

    @Test
    void shouldRejectMissingCustomerId() {
        assertInvalid(message(OrderMessage.SCHEMA_VERSION, OPERATION_ID, ORDER_NUMBER, null, CREATED), "customerId");
    }

    @Test
    void shouldRejectMissingCreatedTimestamp() {
        assertInvalid(message(OrderMessage.SCHEMA_VERSION, OPERATION_ID, ORDER_NUMBER, 10L, null), "created");
    }

    private void assertInvalid(final OrderMessage message, final String expectedMessage) {

        final ReceiveOrderMessageService service = new ReceiveOrderMessageService(receiptOutPort);
        assertThatThrownBy(() -> service.receive(message)).isInstanceOf(ApplicationBadRequestException.class)
                .hasMessageContaining(expectedMessage);
        verifyNoInteractions(receiptOutPort);
    }

    private static OrderMessage validMessage() {
        return message(OrderMessage.SCHEMA_VERSION, "ORDER-FULFILLMENT:ORD-1", "ORD-1", 10L, CREATED);
    }

    private static OrderMessage message(
            final String schemaVersion,
            final String operationId,
            final String orderNumber,
            final Long customerId,
            final Instant created) {
        return new OrderMessage(schemaVersion, operationId, created, customerId, orderNumber);
    }
}
