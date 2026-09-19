package com.cp.ecommerce.adapter.persistence.inventory;

import java.util.function.UnaryOperator;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntity;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.mapper.StockLevelPersistenceMapper;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.outgoing.MutateStockLevelOutPort;
import com.cp.ecommerce.foundation.exception.StockLevelConflictException;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Executes one complete generic stock mutation attempt in a fresh transaction.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class MutateStockLevelAdapter implements MutateStockLevelOutPort {

    private final StockLevelEntityRepository stockLevelEntityRepository;

    private final StockLevelPersistenceMapper stockLevelPersistenceMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockLevel mutate(final String sku, final UnaryOperator<StockLevel> mutation) {

        final StockLevel current = stockLevelEntityRepository.findById(sku).map(this::toDomain).orElseGet(() -> zeroStock(sku));
        final StockLevel mutated = mutation.apply(current);
        mutated.assertValidationsEmpty();

        final StockLevelEntity entity = stockLevelPersistenceMapper.mapToEntity(mutated)
                .orElseThrow(() -> new IllegalStateException("Failed to map stock level for SKU: " + sku));

        try {

            final StockLevelEntity saved = stockLevelEntityRepository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (final OptimisticLockingFailureException conflict) {

            throw new StockLevelConflictException(sku, conflict);
        }
    }

    private StockLevel toDomain(final StockLevelEntity entity) {

        return stockLevelPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map stock level entity to domain object for SKU: " + entity.getSku()));
    }

    private static StockLevel zeroStock(final String sku) {

        return StockLevel.builder().sku(sku).quantityOnHand(0).quantityReserved(0).version(0).build();
    }
}
