package com.cp.ecommerce.adapter.persistence.inventory.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for durable stock-reservation identities.
 */
@Repository
public interface StockReservationEntityRepository extends JpaRepository<StockReservationEntity, String> {
}
