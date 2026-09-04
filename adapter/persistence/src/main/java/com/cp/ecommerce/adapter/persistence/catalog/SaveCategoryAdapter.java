package com.cp.ecommerce.adapter.persistence.catalog;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.catalog.entity.CategoryEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.CategoryPersistenceMapper;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.port.outgoing.SaveCategoryOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link Category} persistence functionality.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveCategoryAdapter implements SaveCategoryOutPort {

    private final CategoryEntityRepository categoryEntityRepository;

    private final CategoryPersistenceMapper categoryPersistenceMapper;

    @Override
    public Category save(final Category category) {

        final var savedEntity = categoryEntityRepository.save(
                categoryPersistenceMapper.mapToEntity(category)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Failed to map category domain object to entity for slug: " + category.getSlug())));
        return categoryPersistenceMapper.mapToDomainObject(savedEntity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map category entity to domain object for slug: " + category.getSlug()));
    }

}
