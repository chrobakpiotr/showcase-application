package com.cp.ecommerce.adapter.persistence.payment.entity;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.domain.payment.PaymentRefundStatus;
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
    @Query("""
            select continuation
            from RefundReturnContinuationEntity continuation
            where continuation.returnNumber = :returnNumber
            """)
    Optional<RefundReturnContinuationEntity> findByReturnNumberForUpdate(@Param("returnNumber") String returnNumber);

    @Query("""
            select continuation.returnNumber
            from RefundReturnContinuationEntity continuation, PaymentRefundEntity refund
            where continuation.refundId = refund.refundId
              and continuation.status = :continuationStatus
              and refund.status = :refundStatus
            order by continuation.created asc
            """)
    List<String> findRecoverableReturnNumbers(
            @Param("continuationStatus") RefundReturnContinuationStatus continuationStatus,
            @Param("refundStatus") PaymentRefundStatus refundStatus,
            Pageable pageable);
}
