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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link SaveCategoryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveCategoryAdapterTest {

    @InjectMocks
    private transient SaveCategoryAdapter saveCategoryAdapter;

    @Mock
    private transient CategoryEntityRepository categoryEntityRepository;

    @Mock
    private transient CategoryPersistenceMapper categoryPersistenceMapper;

    @Test
    void shouldSaveCategory() {

        final Category category = CategoryBuilder.mockCategory();
        final var mockEntity = CategoryEntityBuilder.mockCategoryEntity();
        doReturn(Optional.of(mockEntity)).when(categoryPersistenceMapper).mapToEntity(eq(category));
        doReturn(mockEntity).when(categoryEntityRepository).save(mockEntity);
        doReturn(Optional.of(category)).when(categoryPersistenceMapper).mapToDomainObject(mockEntity);

        final Category result = saveCategoryAdapter.save(category);

        verify(categoryEntityRepository, times(1)).save(mockEntity);
        assertEquals(category, result);
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final Category category = CategoryBuilder.mockCategory();
        doReturn(Optional.empty()).when(categoryPersistenceMapper).mapToEntity(eq(category));

        assertThrows(IllegalStateException.class, () -> saveCategoryAdapter.save(category));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final Category category = CategoryBuilder.mockCategory();
        final var mockEntity = CategoryEntityBuilder.mockCategoryEntity();
        doReturn(Optional.of(mockEntity)).when(categoryPersistenceMapper).mapToEntity(eq(category));
        doReturn(mockEntity).when(categoryEntityRepository).save(any());
        doReturn(Optional.empty()).when(categoryPersistenceMapper).mapToDomainObject(mockEntity);

        assertThrows(IllegalStateException.class, () -> saveCategoryAdapter.save(category));
    }

}
