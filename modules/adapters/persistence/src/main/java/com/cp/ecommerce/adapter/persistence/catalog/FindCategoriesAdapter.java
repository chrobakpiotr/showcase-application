package com.cp.ecommerce.adapter.persistence.catalog;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.catalog.entity.CategoryEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.CategoryPersistenceMapper;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindCategoriesOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindCategoriesOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindCategoriesAdapter implements FindCategoriesOutPort {

    private final CategoryEntityRepository categoryEntityRepository;

    private final CategoryPersistenceMapper categoryPersistenceMapper;

    @Override
    public List<Category> findAll() {

        return categoryEntityRepository.findAllByOrderByNameAsc()
                .stream()
                .map(categoryPersistenceMapper::mapToDomainObject)
                .flatMap(Optional::stream)
                .toList();
    }

}
