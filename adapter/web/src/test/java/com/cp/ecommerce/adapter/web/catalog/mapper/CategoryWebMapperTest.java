package com.cp.ecommerce.adapter.web.catalog.mapper;

import com.cp.ecommerce.adapter.common.utils.CategoryBuilder;
import com.cp.ecommerce.adapter.web.catalog.resource.CategoryResource;
import com.cp.ecommerce.adapter.web.utils.CategoryResourceBuilder;
import com.cp.ecommerce.domain.catalog.Category;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link CategoryWebMapper}.
 */
class CategoryWebMapperTest {

    private final transient CategoryWebMapper categoryWebMapper = new CategoryWebMapper();

    @Test
    void shouldMapToDomainObject() {

        final CategoryResource resource = CategoryResourceBuilder.mockCategoryResource();

        final var result = categoryWebMapper.mapToDomainObject(resource);

        assertTrue(result.isPresent());
        assertEquals(resource.name(), result.get().getName());
        assertEquals(resource.slug(), result.get().getSlug());
    }

    @Test
    void shouldMapToResource() {

        final Category category = CategoryBuilder.mockCategory();

        final var result = categoryWebMapper.mapToResource(category);

        assertTrue(result.isPresent());
        assertEquals(category.getName(), result.get().name());
        assertEquals(category.getSlug(), result.get().slug());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(categoryWebMapper.mapToDomainObject(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToResource() {

        assertTrue(categoryWebMapper.mapToResource(null).isEmpty());
    }

}
