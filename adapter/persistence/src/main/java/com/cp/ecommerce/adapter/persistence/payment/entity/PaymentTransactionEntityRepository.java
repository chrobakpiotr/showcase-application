package com.cp.ecommerce.adapter.persistence.payment.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Class at the persistence layer representing {@link PaymentTransactionEntity} database repository.
 */
@Repository
public interface PaymentTransactionEntityRepository extends JpaRepository<PaymentTransactionEntity, String> {

}
