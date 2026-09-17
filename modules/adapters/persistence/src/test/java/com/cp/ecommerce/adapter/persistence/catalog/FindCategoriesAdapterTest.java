package com.cp.ecommerce.adapter.persistence.catalog;

import java.util.List;
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
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link FindCategoriesAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindCategoriesAdapterTest {

    @InjectMocks
    private transient FindCategoriesAdapter findCategoriesAdapter;

    @Mock
    private transient CategoryEntityRepository categoryEntityRepository;

    @Mock
    private transient CategoryPersistenceMapper categoryPersistenceMapper;

    @Test
    void shouldFindAllCategories() {

        final var entity = CategoryEntityBuilder.mockCategoryEntity();
        final Category category = CategoryBuilder.mockCategory();
        doReturn(List.of(entity)).when(categoryEntityRepository).findAllByOrderByNameAsc();
        doReturn(Optional.of(category)).when(categoryPersistenceMapper).mapToDomainObject(entity);

        final List<Category> result = findCategoriesAdapter.findAll();

        assertEquals(List.of(category), result);
    }

}
