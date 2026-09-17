package com.cp.ecommerce.adapter.persistence.order.idempotency;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

/** Locks are held by the caller's placement transaction, including commit or rollback. */
@Repository
public interface IdempotencyLockRepository extends JpaRepository<IdempotencyLockEntity, Integer> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdempotencyLockEntity> findById(Integer id);

}
