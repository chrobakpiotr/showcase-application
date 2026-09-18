package com.cp.ecommerce.adapter.persistence.payment.entity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

/**
 * Repository for payment transactions.
 */
@Repository
public interface PaymentTransactionEntityRepository extends JpaRepository<PaymentTransactionEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentTransactionEntity payment where payment.orderNumber = :orderNumber")
    Optional<PaymentTransactionEntity> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);
}
