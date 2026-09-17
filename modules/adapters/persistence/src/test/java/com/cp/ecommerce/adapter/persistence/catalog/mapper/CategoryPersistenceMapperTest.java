package com.cp.ecommerce.adapter.persistence.catalog.mapper;

import com.cp.ecommerce.adapter.common.utils.CategoryBuilder;
import com.cp.ecommerce.adapter.persistence.utils.CategoryEntityBuilder;
import com.cp.ecommerce.domain.catalog.Category;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link CategoryPersistenceMapper}.
 */
class CategoryPersistenceMapperTest {

    private final transient CategoryPersistenceMapper categoryPersistenceMapper = new CategoryPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final Category category = CategoryBuilder.mockCategory();

        final var result = categoryPersistenceMapper.mapToEntity(category);

        assertTrue(result.isPresent());
        assertEquals(category.getId(), result.get().getId());
        assertEquals(category.getName(), result.get().getName());
        assertEquals(category.getSlug(), result.get().getSlug());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = CategoryEntityBuilder.mockCategoryEntity();

        final var result = categoryPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getId(), result.get().getId());
        assertEquals(entity.getName(), result.get().getName());
        assertEquals(entity.getSlug(), result.get().getSlug());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(categoryPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(categoryPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
