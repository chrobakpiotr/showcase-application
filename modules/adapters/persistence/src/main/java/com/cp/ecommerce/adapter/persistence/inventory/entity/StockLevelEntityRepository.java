package com.cp.ecommerce.adapter.persistence.inventory.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Class at the persistence layer representing {@link StockLevelEntity} database repository.
 */
@Repository
public interface StockLevelEntityRepository extends JpaRepository<StockLevelEntity, String> {

}
