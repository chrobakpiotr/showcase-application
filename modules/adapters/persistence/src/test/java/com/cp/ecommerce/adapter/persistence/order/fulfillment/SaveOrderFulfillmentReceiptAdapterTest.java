package com.cp.ecommerce.adapter.persistence.order.fulfillment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.cp.ecommerce.domain.order.OrderFulfillmentReceiptOutcome;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SaveOrderFulfillmentReceiptAdapterTest {

    private static final Instant CREATED = Instant.parse("2026-09-24T12:00:00Z");
    private static final Instant RECEIVED = Instant.parse("2026-09-24T12:00:01Z");
    private static final String OPERATION_ID = "op-1";
    private static final String ORDER_NUMBER = "ORD-1";

    @Mock
    private OrderFulfillmentReceiptEntityRepository repository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query nativeQuery;

    private SaveOrderFulfillmentReceiptAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SaveOrderFulfillmentReceiptAdapter(repository, entityManager, Clock.fixed(RECEIVED, ZoneOffset.UTC));
    }

    @Test
    void shouldRecordFirstReceipt() {
        final OrderMessage message = message(OPERATION_ID, ORDER_NUMBER);
        final OrderFulfillmentReceiptEntity persisted = entity(message);
        stubInsert(1);
        given(repository.findById(message.operationId())).willReturn(Optional.of(persisted));

        assertThat(adapter.saveOnce(message)).isEqualTo(OrderFulfillmentReceiptOutcome.RECORDED);
    }

    @Test
    void shouldResolveIdenticalReplay() {
        final OrderMessage message = message(OPERATION_ID, ORDER_NUMBER);
        final OrderFulfillmentReceiptEntity persisted = entity(message);
        stubInsert(0);
        given(repository.findById(message.operationId())).willReturn(Optional.of(persisted));

        assertThat(adapter.saveOnce(message)).isEqualTo(OrderFulfillmentReceiptOutcome.REPLAYED);
    }

    @Test
    void shouldFailWhenInsertCannotResolveReceipt() {
        final OrderMessage message = message(OPERATION_ID, ORDER_NUMBER);
        stubInsert(1);
        given(repository.findById(message.operationId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.saveOnce(message)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message.operationId());
    }

    @Test
    void shouldRejectSchemaConflict() {
        final OrderFulfillmentReceiptEntity candidate = entity(message(OPERATION_ID, ORDER_NUMBER));
        final OrderFulfillmentReceiptEntity baseline = entity(message(OPERATION_ID, ORDER_NUMBER));
        final OrderFulfillmentReceiptEntity persisted = copy(
                baseline,
                "2.0",
                baseline.getOrderNumber(),
                baseline.getCustomerId(),
                baseline.getMessageCreated());
        assertConflict(candidate, persisted);
    }

    @Test
    void shouldRejectOrderConflict() {
        assertConflict(entity(message(OPERATION_ID, ORDER_NUMBER)), entity(message(OPERATION_ID, "ORD-2")));
    }

    @Test
    void shouldRejectCustomerConflict() {
        final OrderFulfillmentReceiptEntity candidate = entity(message(OPERATION_ID, ORDER_NUMBER));
        final OrderFulfillmentReceiptEntity persisted = copy(
                candidate,
                candidate.getSchemaVersion(),
                candidate.getOrderNumber(),
                99L,
                candidate.getMessageCreated());
        assertConflict(candidate, persisted);
    }

    @Test
    void shouldRejectCreatedTimestampConflict() {
        final OrderFulfillmentReceiptEntity candidate = entity(message(OPERATION_ID, ORDER_NUMBER));
        final OrderFulfillmentReceiptEntity persisted = copy(
                candidate,
                candidate.getSchemaVersion(),
                candidate.getOrderNumber(),
                candidate.getCustomerId(),
                CREATED.plusSeconds(1));
        assertConflict(candidate, persisted);
    }

    private void stubInsert(final int result) {
        given(entityManager.createNativeQuery(anyString())).willReturn(nativeQuery);
        given(nativeQuery.setParameter(anyString(), nullable(Object.class))).willReturn(nativeQuery);
        given(nativeQuery.executeUpdate()).willReturn(result);
    }

    private static void assertConflict(
            final OrderFulfillmentReceiptEntity candidate,
            final OrderFulfillmentReceiptEntity persisted) {
        assertThatThrownBy(() -> SaveOrderFulfillmentReceiptAdapter.validateReplayPayload(candidate, persisted))
                .isInstanceOf(ApplicationConflictException.class)
                .hasMessageContaining(candidate.getOperationId());
    }

    private static OrderMessage message(final String operationId, final String orderNumber) {
        return new OrderMessage(OrderMessage.SCHEMA_VERSION, operationId, CREATED, 10L, orderNumber);
    }

    private static OrderFulfillmentReceiptEntity entity(final OrderMessage message) {
        return OrderFulfillmentReceiptEntity.builder()
                .operationId(message.operationId())
                .schemaVersion(message.schemaVersion())
                .orderNumber(message.orderNumber())
                .customerId(message.customerId())
                .messageCreated(message.created())
                .receivedDate(RECEIVED)
                .build();
    }

    private static OrderFulfillmentReceiptEntity copy(
            final OrderFulfillmentReceiptEntity source,
            final String schemaVersion,
            final String orderNumber,
            final Long customerId,
            final Instant messageCreated) {
        return OrderFulfillmentReceiptEntity.builder()
                .operationId(source.getOperationId())
                .schemaVersion(schemaVersion)
                .orderNumber(orderNumber)
                .customerId(customerId)
                .messageCreated(messageCreated)
                .receivedDate(source.getReceivedDate())
                .build();
    }
}
