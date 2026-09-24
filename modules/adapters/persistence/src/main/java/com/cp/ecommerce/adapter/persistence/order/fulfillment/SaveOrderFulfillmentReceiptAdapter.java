package com.cp.ecommerce.adapter.persistence.order.fulfillment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.OrderFulfillmentReceiptOutcome;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderFulfillmentReceiptOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;

import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** PostgreSQL-backed insert-once inbox for RabbitMQ order fulfillment messages. */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveOrderFulfillmentReceiptAdapter implements SaveOrderFulfillmentReceiptOutPort {

    private final OrderFulfillmentReceiptEntityRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public OrderFulfillmentReceiptOutcome saveOnce(final OrderMessage message) {

        final OrderFulfillmentReceiptEntity candidate = OrderFulfillmentReceiptEntity.builder()
                .operationId(message.operationId())
                .schemaVersion(message.schemaVersion())
                .orderNumber(message.orderNumber())
                .customerId(message.customerId())
                .messageCreated(message.created())
                .receivedDate(Instant.ofEpochMilli(clock.instant().toEpochMilli()))
                .build();

        final int inserted = insertOnce(candidate);
        final OrderFulfillmentReceiptEntity persisted = repository.findById(message.operationId())
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Fulfillment receipt insert-once did not resolve operation id: " + message.operationId()));

        validateReplayPayload(candidate, persisted);
        return inserted == 1 ? OrderFulfillmentReceiptOutcome.RECORDED : OrderFulfillmentReceiptOutcome.REPLAYED;
    }

    private int insertOnce(final OrderFulfillmentReceiptEntity candidate) {

        return entityManager.createNativeQuery("""
                insert into test_db.ORDER_FULFILLMENT_RECEIPT (
                    OPERATION_ID,
                    SCHEMA_VERSION,
                    ORDER_NUMBER,
                    CUSTOMER_ID,
                    MESSAGE_CREATED,
                    RECEIVED_DATE
                ) values (
                    :operationId,
                    :schemaVersion,
                    :orderNumber,
                    :customerId,
                    :messageCreated,
                    :receivedDate
                )
                on conflict do nothing
                """)
                .setParameter("operationId", candidate.getOperationId())
                .setParameter("schemaVersion", candidate.getSchemaVersion())
                .setParameter("orderNumber", candidate.getOrderNumber())
                .setParameter("customerId", candidate.getCustomerId())
                .setParameter("messageCreated", candidate.getMessageCreated())
                .setParameter("receivedDate", candidate.getReceivedDate())
                .executeUpdate();
    }

    static void validateReplayPayload(
            final OrderFulfillmentReceiptEntity candidate,
            final OrderFulfillmentReceiptEntity persisted) {

        final boolean samePayload = Objects.equals(candidate.getSchemaVersion(), persisted.getSchemaVersion())
                && Objects.equals(candidate.getOrderNumber(), persisted.getOrderNumber())
                && Objects.equals(candidate.getCustomerId(), persisted.getCustomerId())
                && Objects.equals(candidate.getMessageCreated(), persisted.getMessageCreated());

        if (!samePayload) {
            throw new ApplicationConflictException(
                    "Fulfillment operation id reused with conflicting immutable payload: " + candidate.getOperationId());
        }
    }
}
