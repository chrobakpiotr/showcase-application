package com.cp.ecommerce.adapter.persistence.payment.entity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

/**
 * Repository for durable payment-provider reconciliation operations.
 */
@Repository
public interface PaymentReconciliationEntityRepository extends JpaRepository<PaymentReconciliationEntity, String> {

    long countByStatus(PaymentReconciliationStatus status);

    @Query("select min(operation.created) from PaymentReconciliationEntity operation where operation.status = :status")
    Instant findOldestCreatedByStatus(@Param("status") PaymentReconciliationStatus status);

    long deleteByStatusAndCompletedBefore(PaymentReconciliationStatus status, Instant completedBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from PaymentReconciliationEntity operation where operation.operationId = :operationId")
    Optional<PaymentReconciliationEntity> findByIdForUpdate(@Param("operationId") String operationId);

    @Query("select operation.operationId from PaymentReconciliationEntity operation "
            + "where operation.status = :status and operation.nextAttemptDate <= :now "
            + "and (operation.claimUntil is null or operation.claimUntil <= :now) "
            + "order by operation.created asc, operation.operationId asc")
    List<String> findDueAndClaimableOperationIds(
            @Param("status") PaymentReconciliationStatus status,
            @Param("now") Instant now,
            Pageable pageable);
}
