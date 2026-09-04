package com.cp.ecommerce.adapter.persistence.inventory;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.StockLevelConflictException;
import com.cp.ecommerce.adapter.common.utils.StockLevelBuilder;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntity;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.mapper.StockLevelPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.StockLevelEntityBuilder;
import com.cp.ecommerce.domain.inventory.StockLevel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Test class for {@link SaveStockLevelAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveStockLevelAdapterTest {

    @InjectMocks
    private transient SaveStockLevelAdapter saveStockLevelAdapter;

    @Mock
    private transient StockLevelEntityRepository stockLevelEntityRepository;

    @Mock
    private transient StockLevelPersistenceMapper stockLevelPersistenceMapper;

    @Test
    void shouldSaveAndReturnMappedStockLevel() {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        final StockLevelEntity mappedEntity = StockLevelEntityBuilder.mockStockLevelEntity();
        doReturn(Optional.of(mappedEntity)).when(stockLevelPersistenceMapper).mapToEntity(eq(stockLevel));
        doReturn(mappedEntity).when(stockLevelEntityRepository).saveAndFlush(mappedEntity);
        doReturn(Optional.of(stockLevel)).when(stockLevelPersistenceMapper).mapToDomainObject(mappedEntity);

        final StockLevel result = saveStockLevelAdapter.save(stockLevel);

        assertEquals(stockLevel, result);
    }

    @Test
    void shouldThrowStockLevelConflictExceptionWhenOptimisticLockFails() {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        final StockLevelEntity mappedEntity = StockLevelEntityBuilder.mockStockLevelEntity();
        doReturn(Optional.of(mappedEntity)).when(stockLevelPersistenceMapper).mapToEntity(eq(stockLevel));
        doThrow(new OptimisticLockingFailureException("conflict")).when(stockLevelEntityRepository).saveAndFlush(mappedEntity);

        assertThrows(StockLevelConflictException.class, () -> saveStockLevelAdapter.save(stockLevel));
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        doReturn(Optional.empty()).when(stockLevelPersistenceMapper).mapToEntity(eq(stockLevel));

        assertThrows(IllegalStateException.class, () -> saveStockLevelAdapter.save(stockLevel));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        final StockLevelEntity mappedEntity = StockLevelEntityBuilder.mockStockLevelEntity();
        doReturn(Optional.of(mappedEntity)).when(stockLevelPersistenceMapper).mapToEntity(eq(stockLevel));
        doReturn(mappedEntity).when(stockLevelEntityRepository).saveAndFlush(mappedEntity);
        doReturn(Optional.empty()).when(stockLevelPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> saveStockLevelAdapter.save(stockLevel));
    }

}
