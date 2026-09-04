package com.cp.ecommerce.adapter.persistence.catalog;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CategoryBuilder;
import com.cp.ecommerce.adapter.persistence.catalog.entity.CategoryEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.CategoryPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.CategoryEntityBuilder;
import com.cp.ecommerce.domain.catalog.Category;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import static com.cp.ecommerce.adapter.common.utils.CategoryBuilder.TEST_CATEGORY_SLUG;

/**
 * Test class for {@link FindCategoryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindCategoryAdapterTest {

    @InjectMocks
    private transient FindCategoryAdapter findCategoryAdapter;

    @Mock
    private transient CategoryEntityRepository categoryEntityRepository;

    @Mock
    private transient CategoryPersistenceMapper categoryPersistenceMapper;

    @Test
    void shouldFindCategoryBySlug() {

        final var entity = CategoryEntityBuilder.mockCategoryEntity();
        final Category category = CategoryBuilder.mockCategory();
        doReturn(entity).when(categoryEntityRepository).findBySlug(TEST_CATEGORY_SLUG);
        doReturn(Optional.of(category)).when(categoryPersistenceMapper).mapToDomainObject(entity);

        final Category result = findCategoryAdapter.findBySlug(TEST_CATEGORY_SLUG);

        assertEquals(category, result);
    }

    @Test
    void shouldReturnNullWhenCategoryNotFound() {

        doReturn(null).when(categoryEntityRepository).findBySlug(TEST_CATEGORY_SLUG);
        doReturn(Optional.empty()).when(categoryPersistenceMapper).mapToDomainObject(null);

        final Category result = findCategoryAdapter.findBySlug(TEST_CATEGORY_SLUG);

        assertNull(result);
    }

}
