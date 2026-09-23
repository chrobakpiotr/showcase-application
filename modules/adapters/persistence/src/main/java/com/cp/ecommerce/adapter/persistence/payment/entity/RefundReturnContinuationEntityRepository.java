package com.cp.ecommerce.adapter.persistence.payment.entity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.domain.payment.RefundReturnContinuationStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** Persistence queries for refund-to-return continuation work. */
public interface RefundReturnContinuationEntityRepository extends JpaRepository<RefundReturnContinuationEntity, String> {

    Optional<RefundReturnContinuationEntity> findByReturnNumber(String returnNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RefundReturnContinuationEntity c where c.returnNumber = :returnNumber")
    Optional<RefundReturnContinuationEntity> findByReturnNumberForUpdate(@Param("returnNumber") String returnNumber);

    @Query("""
            select c.returnNumber
            from RefundReturnContinuationEntity c
            where c.status = :status
              and c.nextAttemptDate <= :now
            order by c.nextAttemptDate asc, c.created asc, c.refundId asc
            """)
    List<String> findRecoverableReturnNumbers(
            @Param("status") RefundReturnContinuationStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @Query("select min(c.created) from RefundReturnContinuationEntity c where c.status = :status")
    Instant findOldestCreatedDateByStatus(@Param("status") RefundReturnContinuationStatus status);

    long countByStatus(RefundReturnContinuationStatus status);

    long countByStatusAndAttemptsGreaterThan(RefundReturnContinuationStatus status, int attempts);
}
