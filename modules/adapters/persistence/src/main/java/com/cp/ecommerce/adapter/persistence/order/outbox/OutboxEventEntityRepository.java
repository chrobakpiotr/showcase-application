package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

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

    /**
     * Reload one candidate while holding the shared saga/cancellation arbitration lock.
     *
     * @param id technical outbox identifier.
     * @return locked row if it still exists.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEventEntity event where event.id = :id")
    Optional<OutboxEventEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * Lock the placement process for a customer cancellation request.
     *
     * @param orderNumber order business key.
     * @return locked placement row if present.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEventEntity event where event.orderNumber = :orderNumber")
    Optional<OutboxEventEntity> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

}
