package com.cp.ecommerce.adapter.persistence.inventory.entity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

/**
 * Class at the persistence layer representing {@link StockLevelEntity} database repository.
 */
@Repository
public interface StockLevelEntityRepository extends JpaRepository<StockLevelEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select stock from StockLevelEntity stock where stock.sku = :sku")
    Optional<StockLevelEntity> findBySkuForUpdate(@Param("sku") String sku);
}
