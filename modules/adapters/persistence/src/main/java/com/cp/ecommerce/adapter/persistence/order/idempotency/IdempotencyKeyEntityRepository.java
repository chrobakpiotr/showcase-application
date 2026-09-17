package com.cp.ecommerce.adapter.persistence.order.idempotency;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link IdempotencyKeyEntity} objects.
 */
@Repository
public interface IdempotencyKeyEntityRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

    /**
     * Find a persisted idempotency key record.
     *
     * @param key the client-supplied {@code Idempotency-Key}.
     * @return matching record, if one has been reserved.
     */
    Optional<IdempotencyKeyEntity> findByKey(String key);

}
