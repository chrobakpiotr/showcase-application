package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    int DEFAULT_POLL_BATCH_SIZE = 50;

    /**
     * Find all outbox events by status ordered by creation date.
     *
     * @param status status to search for.
     * @return matching outbox events.
     */
    default List<OutboxEventEntity> findAllByStatusOrderByCreatedDateAsc(final OutboxEventStatus status) {

        return findAllByStatusOrderByCreatedDateAsc(status, PageRequest.of(0, DEFAULT_POLL_BATCH_SIZE));
    }

    List<OutboxEventEntity> findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus status, Pageable pageable);

    /**
     * Find expired claims for one status ordered by creation date.
     *
     * @param status claimed status.
     * @param claimUntil latest lease deadline that is considered expired.
     * @return expired claimed events.
     */
    default List<OutboxEventEntity> findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
            final OutboxEventStatus status,
            final Instant claimUntil) {

        return findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
                status,
                claimUntil,
                PageRequest.of(0, DEFAULT_POLL_BATCH_SIZE));
    }

    List<OutboxEventEntity> findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
            OutboxEventStatus status,
            Instant claimUntil,
            Pageable pageable);

    @Query("select min(event.createdDate) from OutboxEventEntity event where event.status = :status")
    Instant findOldestCreatedDateByStatus(@Param("status") OutboxEventStatus status);

    long countByStatusAndClaimUntilLessThanEqual(OutboxEventStatus status, Instant claimUntil);

    long countByStatusAndAttemptsGreaterThan(OutboxEventStatus status, int attempts);

    long countByStatus(OutboxEventStatus status);

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
