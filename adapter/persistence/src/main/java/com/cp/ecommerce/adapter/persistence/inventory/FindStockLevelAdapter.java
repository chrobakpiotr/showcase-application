package com.cp.ecommerce.adapter.persistence.inventory;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.mapper.StockLevelPersistenceMapper;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.outgoing.FindStockLevelOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindStockLevelOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindStockLevelAdapter implements FindStockLevelOutPort {

    private final StockLevelEntityRepository stockLevelEntityRepository;

    private final StockLevelPersistenceMapper stockLevelPersistenceMapper;

    @Override
    public StockLevel find(final String sku) {

        return stockLevelEntityRepository.findById(sku).flatMap(stockLevelPersistenceMapper::mapToDomainObject).orElse(null);
    }

}
