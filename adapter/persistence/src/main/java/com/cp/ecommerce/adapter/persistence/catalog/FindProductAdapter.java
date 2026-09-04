package com.cp.ecommerce.adapter.persistence.catalog;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.ProductPersistenceMapper;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindProductOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindProductOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindProductAdapter implements FindProductOutPort {

    private final ProductEntityRepository productEntityRepository;

    private final ProductPersistenceMapper productPersistenceMapper;

    @Override
    public Product find(final String sku) {

        return productPersistenceMapper.mapToDomainObject(productEntityRepository.findBySku(sku)).orElse(null);
    }

}
