package com.cp.ecommerce.adapter.persistence.inventory.mapper;

import com.cp.ecommerce.adapter.common.utils.StockLevelBuilder;
import com.cp.ecommerce.adapter.persistence.utils.StockLevelEntityBuilder;
import com.cp.ecommerce.domain.inventory.StockLevel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link StockLevelPersistenceMapper}.
 */
class StockLevelPersistenceMapperTest {

    private final transient StockLevelPersistenceMapper stockLevelPersistenceMapper = new StockLevelPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();

        final var result = stockLevelPersistenceMapper.mapToEntity(stockLevel);

        assertTrue(result.isPresent());
        assertEquals(stockLevel.getSku(), result.get().getSku());
        assertEquals(stockLevel.getQuantityOnHand(), result.get().getQuantityOnHand());
        assertEquals(stockLevel.getQuantityReserved(), result.get().getQuantityReserved());
        assertEquals(stockLevel.getVersion(), result.get().getVersion());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = StockLevelEntityBuilder.mockStockLevelEntity();

        final var result = stockLevelPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getSku(), result.get().getSku());
        assertEquals(entity.getQuantityOnHand(), result.get().getQuantityOnHand());
        assertEquals(entity.getQuantityReserved(), result.get().getQuantityReserved());
        assertEquals(entity.getVersion(), result.get().getVersion());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(stockLevelPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(stockLevelPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
