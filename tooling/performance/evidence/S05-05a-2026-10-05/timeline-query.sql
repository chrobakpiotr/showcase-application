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
