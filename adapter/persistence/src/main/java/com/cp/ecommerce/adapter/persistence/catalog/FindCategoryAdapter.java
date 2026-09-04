package com.cp.ecommerce.adapter.persistence.catalog;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.catalog.entity.CategoryEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.CategoryPersistenceMapper;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindCategoryOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindCategoryOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindCategoryAdapter implements FindCategoryOutPort {

    private final CategoryEntityRepository categoryEntityRepository;

    private final CategoryPersistenceMapper categoryPersistenceMapper;

    @Override
    public Category findBySlug(final String slug) {

        return categoryPersistenceMapper.mapToDomainObject(categoryEntityRepository.findBySlug(slug)).orElse(null);
    }

}
