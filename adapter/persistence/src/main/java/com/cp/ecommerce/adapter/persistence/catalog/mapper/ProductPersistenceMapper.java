package com.cp.ecommerce.adapter.persistence.catalog.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntity;
import com.cp.ecommerce.domain.catalog.Product;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link Product} object into/from entity object.
 */
@Component
@RequiredArgsConstructor
public class ProductPersistenceMapper implements PersistenceMapper<Product, ProductEntity> {

    private final CategoryPersistenceMapper categoryPersistenceMapper;

    @Override
    public Optional<ProductEntity> mapToEntity(final Product product) {

        return ofNullable(product).map(
                domain -> ProductEntity.builder()
                        .sku(domain.getSku())
                        .name(domain.getName())
                        .description(domain.getDescription())
                        .category(categoryPersistenceMapper.mapToEntity(domain.getCategory()).orElse(null))
                        .unitPrice(domain.getUnitPrice())
                        .imageUrl(domain.getImageUrl())
                        .active(domain.isActive())
                        .created(domain.getCreated())
                        .build());
    }

    @Override
    public Optional<Product> mapToDomainObject(final ProductEntity product) {

        return ofNullable(product).map(
                entity -> Product.builder()
                        .sku(entity.getSku())
                        .name(entity.getName())
                        .description(entity.getDescription())
                        .category(categoryPersistenceMapper.mapToDomainObject(entity.getCategory()).orElse(null))
                        .unitPrice(entity.getUnitPrice())
                        .imageUrl(entity.getImageUrl())
                        .active(entity.isActive())
                        .created(entity.getCreated())
                        .build());
    }

}
