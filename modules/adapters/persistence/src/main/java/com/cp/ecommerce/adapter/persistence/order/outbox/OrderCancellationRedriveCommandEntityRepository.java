package com.cp.ecommerce.adapter.persistence.order.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for durable cancellation-redrive audit commands. */
@Repository
public interface OrderCancellationRedriveCommandEntityRepository
        extends JpaRepository<OrderCancellationRedriveCommandEntity, String> {
}
