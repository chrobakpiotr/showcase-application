package com.cp.ecommerce.adapter.persistence.inventory;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.StockLevelBuilder;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.mapper.StockLevelPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.StockLevelEntityBuilder;
import com.cp.ecommerce.domain.inventory.StockLevel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import static com.cp.ecommerce.adapter.common.utils.StockLevelBuilder.TEST_STOCK_SKU;

/**
 * Test class for {@link FindStockLevelAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindStockLevelAdapterTest {

    @InjectMocks
    private transient FindStockLevelAdapter findStockLevelAdapter;

    @Mock
    private transient StockLevelEntityRepository stockLevelEntityRepository;

    @Mock
    private transient StockLevelPersistenceMapper stockLevelPersistenceMapper;

    @Test
    void shouldFindStockLevelBySku() {

        final var entity = StockLevelEntityBuilder.mockStockLevelEntity();
        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        doReturn(Optional.of(entity)).when(stockLevelEntityRepository).findById(TEST_STOCK_SKU);
        doReturn(Optional.of(stockLevel)).when(stockLevelPersistenceMapper).mapToDomainObject(entity);

        final StockLevel result = findStockLevelAdapter.find(TEST_STOCK_SKU);

        assertEquals(stockLevel, result);
    }

    @Test
    void shouldReturnNullWhenStockLevelNotFound() {

        doReturn(Optional.empty()).when(stockLevelEntityRepository).findById(TEST_STOCK_SKU);

        final StockLevel result = findStockLevelAdapter.find(TEST_STOCK_SKU);

        assertNull(result);
    }

}
