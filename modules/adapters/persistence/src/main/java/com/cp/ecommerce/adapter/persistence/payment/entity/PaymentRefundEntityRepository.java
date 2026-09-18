package com.cp.ecommerce.adapter.persistence.payment.entity;

import java.math.BigDecimal;
import java.util.Optional;

import com.cp.ecommerce.domain.payment.PaymentRefundStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

/**
 * Repository for durable refund operations.
 */
@Repository
public interface PaymentRefundEntityRepository extends JpaRepository<PaymentRefundEntity, String> {

    @Query("select refund.orderNumber from PaymentRefundEntity refund where refund.refundId = :refundId")
    Optional<String> findOrderNumberByRefundId(@Param("refundId") String refundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refund from PaymentRefundEntity refund where refund.refundId = :refundId")
    Optional<PaymentRefundEntity> findByIdForUpdate(@Param("refundId") String refundId);

    @Query("""
            select sum(refund.amount)
            from PaymentRefundEntity refund
            where refund.orderNumber = :orderNumber and refund.status = :status
            """)
    BigDecimal sumAmountByOrderNumberAndStatus(
            @Param("orderNumber") String orderNumber,
            @Param("status") PaymentRefundStatus status);
}
