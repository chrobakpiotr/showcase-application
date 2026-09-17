package com.cp.ecommerce.adapter.persistence.inventory.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntity;
import com.cp.ecommerce.domain.inventory.StockLevel;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link StockLevel} object into/from entity object.
 */
@Component
public class StockLevelPersistenceMapper implements PersistenceMapper<StockLevel, StockLevelEntity> {

    @Override
    public Optional<StockLevelEntity> mapToEntity(final StockLevel stockLevel) {

        return ofNullable(stockLevel).map(
                domain -> StockLevelEntity.builder()
                        .sku(domain.getSku())
                        .quantityOnHand(domain.getQuantityOnHand())
                        .quantityReserved(domain.getQuantityReserved())
                        .version(domain.getVersion())
                        .build());
    }

    @Override
    public Optional<StockLevel> mapToDomainObject(final StockLevelEntity entity) {

        return ofNullable(entity).map(
                stockLevelEntity -> StockLevel.builder()
                        .sku(stockLevelEntity.getSku())
                        .quantityOnHand(stockLevelEntity.getQuantityOnHand())
                        .quantityReserved(stockLevelEntity.getQuantityReserved())
                        .version(stockLevelEntity.getVersion())
                        .build());
    }

}
