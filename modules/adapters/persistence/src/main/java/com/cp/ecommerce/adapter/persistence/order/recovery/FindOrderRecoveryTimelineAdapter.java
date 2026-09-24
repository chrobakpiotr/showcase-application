package com.cp.ecommerce.adapter.persistence.order.recovery;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrderRecoveryTimelineOutPort;
import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineEntry;
import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineState;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

/**
 * One-query read-only projection over existing durable recovery records.
 *
 * <p>
 * This is deliberately not an event store. Rows are normalized current-state/milestone evidence from the durable tables and use
 * only safe identifiers/statuses/timestamps.
 * </p>
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindOrderRecoveryTimelineAdapter implements FindOrderRecoveryTimelineOutPort {

    static final String TIMELINE_SQL = """
            SELECT 'PLACEMENT' source_name,
                   'SAGA' type_name,
                   CASE
                       WHEN STATUS = 'MANUAL_REVIEW' THEN 'MANUAL_REVIEW'
                       WHEN STATUS IN ('SENT') THEN 'COMPLETED'
                       WHEN STATUS IN ('COMPENSATED', 'CANCELLED') THEN 'REJECTED'
                       ELSE 'PENDING'
                   END normalized_state,
                   CASE
                       WHEN STATUS = 'SENT' THEN COALESCE(SENT_DATE, CREATED_DATE)
                       WHEN STATUS = 'COMPENSATED' THEN COALESCE(COMPENSATED_DATE, CREATED_DATE)
                       ELSE CREATED_DATE
                   END occurred_at,
                   CAST(ID AS VARCHAR(120)) reference_id,
                   CAST(STATUS AS VARCHAR(40)) raw_status
              FROM test_db.OUTBOX_EVENT
             WHERE ORDER_NUMBER = :orderNumber
            UNION ALL
            SELECT 'PAYMENT_RECONCILIATION',
                   CAST(OPERATION_TYPE AS VARCHAR(40)),
                   CASE
                       WHEN STATUS = 'MANUAL_REVIEW' THEN 'MANUAL_REVIEW'
                       WHEN STATUS = 'COMPLETED' THEN 'COMPLETED'
                       WHEN STATUS = 'FAILED' THEN 'UNKNOWN'
                       ELSE 'PENDING'
                   END,
                   COALESCE(COMPLETION_DATE, CREATION_DATE),
                   OPERATION_ID,
                   CAST(STATUS AS VARCHAR(40))
              FROM test_db.PAYMENT_RECONCILIATION_OPERATION
             WHERE ORDER_NUMBER = :orderNumber
            UNION ALL
            SELECT 'PAYMENT',
                   'REFUND',
                   CASE WHEN STATUS = 'COMPLETED' THEN 'COMPLETED' ELSE 'PENDING' END,
                   COALESCE(COMPLETION_DATE, CREATION_DATE),
                   REFUND_ID,
                   CAST(STATUS AS VARCHAR(40))
              FROM test_db.PAYMENT_REFUND
             WHERE ORDER_NUMBER = :orderNumber
            UNION ALL
            SELECT 'FULFILLMENT',
                   'RABBITMQ',
                   'COMPLETED',
                   RECEIVED_DATE,
                   OPERATION_ID,
                   'RECEIVED'
              FROM test_db.ORDER_FULFILLMENT_RECEIPT
             WHERE ORDER_NUMBER = :orderNumber
            UNION ALL
            SELECT 'PLACEMENT_DISPATCH',
                   CAST(DISPATCH_TYPE AS VARCHAR(40)),
                   CASE
                       WHEN STATUS = 'SENT' THEN 'COMPLETED'
                       WHEN STATUS = 'FAILED' THEN 'UNKNOWN'
                       ELSE 'PENDING'
                   END,
                   COALESCE(SENT_DATE, CREATED_DATE),
                   DISPATCH_ID,
                   CAST(STATUS AS VARCHAR(40))
              FROM test_db.ORDER_PLACEMENT_DISPATCH
             WHERE ORDER_NUMBER = :orderNumber
            UNION ALL
            SELECT 'NOTIFICATION',
                   CAST(TYPE AS VARCHAR(40)),
                   CASE
                       WHEN STATUS = 'SENT' THEN 'COMPLETED'
                       WHEN STATUS = 'FAILED' THEN 'UNKNOWN'
                       ELSE 'PENDING'
                   END,
                   COALESCE(SENT_DATE, CREATED_DATE),
                   NOTIFICATION_ID,
                   CAST(STATUS AS VARCHAR(40))
              FROM test_db.NOTIFICATION
             WHERE LEFT(EVENT_KEY, LENGTH(CONCAT('order:', :orderNumber, ':')))
                   = CONCAT('order:', :orderNumber, ':')
            UNION ALL
            SELECT 'SHIPMENT',
                   'CREATED',
                   'ACCEPTED',
                   CREATED_DATE,
                   SHIPMENT_NUMBER,
                   CAST(STATUS AS VARCHAR(40))
              FROM test_db.SHIPMENT
             WHERE ORDER_NUMBER = :orderNumber
            UNION ALL
            SELECT 'SHIPMENT',
                   'DISPATCHED',
                   'COMPLETED',
                   DISPATCHED_DATE,
                   SHIPMENT_NUMBER,
                   CAST(STATUS AS VARCHAR(40))
              FROM test_db.SHIPMENT
             WHERE ORDER_NUMBER = :orderNumber
               AND DISPATCHED_DATE IS NOT NULL
            UNION ALL
            SELECT 'SHIPMENT',
                   'DELIVERED',
                   'COMPLETED',
                   DELIVERED_DATE,
                   SHIPMENT_NUMBER,
                   CAST(STATUS AS VARCHAR(40))
              FROM test_db.SHIPMENT
             WHERE ORDER_NUMBER = :orderNumber
               AND DELIVERED_DATE IS NOT NULL
            ORDER BY occurred_at DESC, source_name ASC, type_name ASC, reference_id ASC
            """;

    private final EntityManager entityManager;

    @Override
    public List<OrderRecoveryTimelineEntry> find(final String orderNumber, final int page, final int size) {

        final Query query = entityManager.createNativeQuery(TIMELINE_SQL);
        query.setParameter("orderNumber", orderNumber);
        query.setFirstResult(page * size);
        query.setMaxResults(size);

        final List<?> rows = query.getResultList();
        return rows.stream().map(Object[].class::cast).map(this::mapRow).toList();
    }

    private OrderRecoveryTimelineEntry mapRow(final Object... row) {

        final String source = requiredText(row, 0);
        final String type = requiredText(row, 1);
        final OrderRecoveryTimelineState state = OrderRecoveryTimelineState.valueOf(requiredText(row, 2));
        final Instant occurredAt = toInstant(row[3]);
        final String referenceId = requiredText(row, 4);
        final String rawStatus = requiredText(row, 5);

        return new OrderRecoveryTimelineEntry(
                source,
                type,
                state,
                occurredAt,
                referenceId,
                source + " " + type + " state " + rawStatus);
    }

    private static String requiredText(final Object[] row, final int index) {

        final Object value = row[index];
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("Recovery timeline projection contains missing safe field at index " + index);
        }
        return value.toString();
    }

    private static Instant toInstant(final Object value) {

        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unsupported recovery timeline timestamp type: " + value);
    }
}
