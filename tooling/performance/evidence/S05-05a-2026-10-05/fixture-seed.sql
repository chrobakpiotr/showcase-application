
\pset pager off
\pset format unaligned
\pset tuples_only on
CREATE UNLOGGED TABLE parked_sparse (
    dispatch_id varchar(100) PRIMARY KEY,
    order_number varchar(40) NOT NULL,
    dispatch_type varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    created_date timestamptz NOT NULL,
    sent_date timestamptz,
    attempts integer NOT NULL,
    next_attempt_date timestamptz NOT NULL,
    last_error varchar(500), claim_id varchar(36), claim_until timestamptz,
    UNIQUE(order_number, dispatch_type)
);
CREATE INDEX idx_parked_sparse_due ON parked_sparse(status, next_attempt_date, created_date);
CREATE UNLOGGED TABLE parked_dense (LIKE parked_sparse INCLUDING ALL);
CREATE INDEX idx_parked_dense_due ON parked_dense(status, next_attempt_date, created_date);
INSERT INTO parked_sparse
SELECT 'D-' || id, 'O-' || id, 'AUDIT',
       CASE WHEN id <= 1000 THEN 'PARKED' WHEN id <= 100000 THEN 'PENDING' ELSE 'SENT' END,
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second',
       CASE WHEN id > 100000 THEN timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second' END,
       (id % 8)::integer,
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second', NULL, NULL, NULL
FROM generate_series(1, 1000000) id;
INSERT INTO parked_dense
SELECT 'D-' || id, 'O-' || id, 'AUDIT',
       CASE WHEN id <= 100000 THEN 'PARKED' WHEN id <= 200000 THEN 'PENDING' ELSE 'SENT' END,
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second',
       CASE WHEN id > 200000 THEN timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second' END,
       (id % 8)::integer,
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second', NULL, NULL, NULL
FROM generate_series(1, 1000000) id;
ANALYZE parked_sparse;
ANALYZE parked_dense;
CREATE SCHEMA test_db;
CREATE TABLE test_db.OUTBOX_EVENT (ID varchar(120), ORDER_NUMBER varchar(40), STATUS varchar(30), SENT_DATE timestamptz, CREATED_DATE timestamptz, COMPENSATED_DATE timestamptz);
CREATE TABLE test_db.PAYMENT_RECONCILIATION_OPERATION (OPERATION_TYPE varchar(40), STATUS varchar(30), COMPLETION_DATE timestamptz, CREATION_DATE timestamptz, OPERATION_ID varchar(120), ORDER_NUMBER varchar(40));
CREATE TABLE test_db.PAYMENT_REFUND (STATUS varchar(30), COMPLETION_DATE timestamptz, CREATION_DATE timestamptz, REFUND_ID varchar(120), ORDER_NUMBER varchar(40));
CREATE TABLE test_db.ORDER_FULFILLMENT_RECEIPT (ORDER_NUMBER varchar(40), RECEIVED_DATE timestamptz, OPERATION_ID varchar(120));
CREATE TABLE test_db.ORDER_PLACEMENT_DISPATCH (DISPATCH_TYPE varchar(30), STATUS varchar(20), SENT_DATE timestamptz, CREATED_DATE timestamptz, DISPATCH_ID varchar(100), ORDER_NUMBER varchar(40));
CREATE TABLE test_db.NOTIFICATION (TYPE varchar(40), STATUS varchar(20), SENT_DATE timestamptz, CREATED_DATE timestamptz, NOTIFICATION_ID varchar(100), EVENT_KEY varchar(200) NOT NULL UNIQUE);
CREATE TABLE test_db.SHIPMENT (ORDER_NUMBER varchar(40) NOT NULL UNIQUE, CREATED_DATE timestamptz NOT NULL, SHIPMENT_NUMBER varchar(41) NOT NULL, STATUS varchar(30) NOT NULL, DISPATCHED_DATE timestamptz, DELIVERED_DATE timestamptz);
INSERT INTO test_db.OUTBOX_EVENT VALUES ('SPIKE-OUTBOX', 'SPIKE-ORDER', 'SENT', now(), now(), NULL);
INSERT INTO test_db.PAYMENT_RECONCILIATION_OPERATION VALUES ('CAPTURE', 'COMPLETED', now(), now(), 'SPIKE-PAYMENT', 'SPIKE-ORDER');
INSERT INTO test_db.PAYMENT_REFUND VALUES ('COMPLETED', now(), now(), 'SPIKE-REFUND', 'SPIKE-ORDER');
INSERT INTO test_db.ORDER_FULFILLMENT_RECEIPT VALUES ('SPIKE-ORDER', now(), 'SPIKE-RECEIPT');
INSERT INTO test_db.ORDER_PLACEMENT_DISPATCH VALUES ('AUDIT', 'SENT', now(), now(), 'SPIKE-DISPATCH', 'SPIKE-ORDER');
INSERT INTO test_db.NOTIFICATION
SELECT 'ORDER', 'SENT', now(), now(), 'N-' || id,
       CASE WHEN id <= 200 THEN 'order:SPIKE-ORDER:' || id ELSE 'order:OTHER-' || id || ':1' END
FROM generate_series(1, 1000000) id;
INSERT INTO test_db.SHIPMENT
SELECT CASE WHEN id = 1 THEN 'SPIKE-ORDER' ELSE 'OTHER-' || id END,
       now() - id * interval '1 second', 'S-' || id, 'CREATED', NULL, NULL
FROM generate_series(1, 100000) id;
ANALYZE test_db.OUTBOX_EVENT;
ANALYZE test_db.PAYMENT_RECONCILIATION_OPERATION;
ANALYZE test_db.PAYMENT_REFUND;
ANALYZE test_db.ORDER_FULFILLMENT_RECEIPT;
ANALYZE test_db.ORDER_PLACEMENT_DISPATCH;
ANALYZE test_db.NOTIFICATION;
ANALYZE test_db.SHIPMENT;
SELECT jsonb_build_object(
  'postgres', version(), 'database', current_database(), 'startedAt', pg_postmaster_start_time(),
  'sharedBuffers', current_setting('shared_buffers'), 'effectiveCacheSize', current_setting('effective_cache_size'),
  'workMem', current_setting('work_mem'), 'parkedSparseRows', (SELECT count(*) FROM parked_sparse),
  'parkedSparseCount', (SELECT count(*) FROM parked_sparse WHERE status = 'PARKED'),
  'parkedDenseRows', (SELECT count(*) FROM parked_dense),
  'parkedDenseCount', (SELECT count(*) FROM parked_dense WHERE status = 'PARKED'),
  'notificationRows', (SELECT count(*) FROM test_db.NOTIFICATION),
  'notificationMatches', (SELECT count(*) FROM test_db.NOTIFICATION WHERE LEFT(EVENT_KEY, LENGTH('order:SPIKE-ORDER:')) = 'order:SPIKE-ORDER:'),
  'shipmentRows', (SELECT count(*) FROM test_db.SHIPMENT)
);
