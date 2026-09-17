package com.cp.ecommerce.adapter.persistence.catalog;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntity;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.ProductPersistenceMapper;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.outgoing.SaveProductOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link Product} persistence functionality.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveProductAdapter implements SaveProductOutPort {

    private final ProductEntityRepository productEntityRepository;

    private final ProductPersistenceMapper productPersistenceMapper;

    @Override
    public Product save(final Product product) {

        final ProductEntity entityToSave = productPersistenceMapper.mapToEntity(product)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map product domain object to entity for SKU: " + product.getSku()));
        // The domain Product carries no database id (sku is its business key, mirroring Order.orderNumber), so on an update
        // (ManageProductUseCase.updateProduct) the freshly mapped entity would otherwise have a null id and be re-inserted
        // instead of updated, violating the SKU unique constraint - look up any existing row by sku first and reuse its id.
        final ProductEntity existingEntity = productEntityRepository.findBySku(product.getSku());
        if (existingEntity != null) {

            entityToSave.setId(existingEntity.getId());
        }
        final var savedEntity = productEntityRepository.save(entityToSave);
        return productPersistenceMapper.mapToDomainObject(savedEntity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map product entity to domain object for SKU: " + product.getSku()));
    }

}
