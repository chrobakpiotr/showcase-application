package com.cp.ecommerce.adapter.persistence.inventory;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.common.exception.StockLevelConflictException;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntity;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.mapper.StockLevelPersistenceMapper;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.outgoing.SaveStockLevelOutPort;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveStockLevelOutPort}.
 *
 * <p>
 * Uses {@code saveAndFlush} rather than plain {@code save}: it forces Hibernate to immediately issue (and this method to
 * immediately observe the outcome of) the version-checked {@code UPDATE} statement, instead of deferring it to whenever the
 * surrounding transaction happens to flush - which could be well after this method, and this class, has returned. Without the
 * forced flush, {@link OptimisticLockingFailureException} could escape from a completely unrelated call site.
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
class SaveStockLevelAdapter implements SaveStockLevelOutPort {

    private final StockLevelEntityRepository stockLevelEntityRepository;

    private final StockLevelPersistenceMapper stockLevelPersistenceMapper;

    @Override
    public StockLevel save(final StockLevel stockLevel) {

        final StockLevelEntity entityToSave = stockLevelPersistenceMapper.mapToEntity(stockLevel)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map stock level domain object to entity for SKU: " + stockLevel.getSku()));
        try {

            final StockLevelEntity saved = stockLevelEntityRepository.saveAndFlush(entityToSave);
            return stockLevelPersistenceMapper.mapToDomainObject(saved)
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "Failed to map stock level entity to domain object for SKU: " + stockLevel.getSku()));
        } catch (final OptimisticLockingFailureException conflict) {

            throw new StockLevelConflictException(stockLevel.getSku(), conflict);
        }
    }

}
