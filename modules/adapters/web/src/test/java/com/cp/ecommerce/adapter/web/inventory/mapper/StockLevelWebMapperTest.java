package com.cp.ecommerce.adapter.web.inventory.mapper;

import com.cp.ecommerce.adapter.common.utils.StockLevelBuilder;
import com.cp.ecommerce.domain.inventory.StockLevel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link StockLevelWebMapper}.
 */
class StockLevelWebMapperTest {

    private final transient StockLevelWebMapper stockLevelWebMapper = new StockLevelWebMapper();

    @Test
    void shouldMapToResource() {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();

        final var result = stockLevelWebMapper.mapToResource(stockLevel);

        assertTrue(result.isPresent());
        assertEquals(stockLevel.getSku(), result.get().sku());
        assertEquals(stockLevel.getQuantityOnHand(), result.get().quantityOnHand());
        assertEquals(stockLevel.getQuantityReserved(), result.get().quantityReserved());
        assertEquals(stockLevel.getQuantityAvailable(), result.get().quantityAvailable());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToResource() {

        assertTrue(stockLevelWebMapper.mapToResource(null).isEmpty());
    }

}
