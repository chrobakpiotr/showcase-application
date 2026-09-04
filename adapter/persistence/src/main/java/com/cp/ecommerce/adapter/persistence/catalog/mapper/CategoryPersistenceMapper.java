package com.cp.ecommerce.adapter.persistence.catalog.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.catalog.entity.CategoryEntity;
import com.cp.ecommerce.domain.catalog.Category;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link Category} object into/from entity object.
 */
@Component
public class CategoryPersistenceMapper implements PersistenceMapper<Category, CategoryEntity> {

    @Override
    public Optional<CategoryEntity> mapToEntity(final Category category) {

        return ofNullable(category).map(
                domain -> CategoryEntity.builder().id(domain.getId()).name(domain.getName()).slug(domain.getSlug()).build());
    }

    @Override
    public Optional<Category> mapToDomainObject(final CategoryEntity category) {

        return ofNullable(category)
                .map(entity -> Category.builder().id(entity.getId()).name(entity.getName()).slug(entity.getSlug()).build());
    }

}
