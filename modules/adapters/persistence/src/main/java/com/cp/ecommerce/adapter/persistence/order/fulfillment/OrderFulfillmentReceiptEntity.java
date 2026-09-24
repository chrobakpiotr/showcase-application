package com.cp.ecommerce.adapter.persistence.order.fulfillment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable inbox/receipt row for one logical RabbitMQ order-fulfillment command. */
@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ORDER_FULFILLMENT_RECEIPT")
public class OrderFulfillmentReceiptEntity {

    @Id
    @Column(name = "OPERATION_ID", length = 120, nullable = false)
    private String operationId;

    @Column(name = "SCHEMA_VERSION", length = 20, nullable = false)
    private String schemaVersion;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Column(name = "CUSTOMER_ID", nullable = false)
    private Long customerId;

    @Column(name = "MESSAGE_CREATED", nullable = false)
    private Instant messageCreated;

    @Column(name = "RECEIVED_DATE", nullable = false)
    private Instant receivedDate;
}
