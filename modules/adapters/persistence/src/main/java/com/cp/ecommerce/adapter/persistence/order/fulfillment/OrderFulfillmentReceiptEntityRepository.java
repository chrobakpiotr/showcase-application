package com.cp.ecommerce.adapter.persistence.order.fulfillment;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to durable RabbitMQ fulfillment receipts. */
public interface OrderFulfillmentReceiptEntityRepository extends JpaRepository<OrderFulfillmentReceiptEntity, String> {
}
