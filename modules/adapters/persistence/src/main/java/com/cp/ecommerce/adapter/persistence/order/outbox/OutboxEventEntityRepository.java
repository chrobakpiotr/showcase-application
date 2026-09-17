package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link OutboxEventEntity} objects.
 */
@Repository
public interface OutboxEventEntityRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * Find all outbox events by status ordered by creation date.
     *
     * @param status status to search for.
     * @return matching outbox events.
     */
    List<OutboxEventEntity> findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus status);

}
