package com.cp.ecommerce.adapter.persistence.catalog.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CategoryBuilder;
import com.cp.ecommerce.adapter.common.utils.ProductBuilder;
import com.cp.ecommerce.adapter.persistence.utils.CategoryEntityBuilder;
import com.cp.ecommerce.adapter.persistence.utils.ProductEntityBuilder;
import com.cp.ecommerce.domain.catalog.Product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link ProductPersistenceMapper}.
 */
@ExtendWith(MockitoExtension.class)
class ProductPersistenceMapperTest {

    @Mock
    private transient CategoryPersistenceMapper categoryPersistenceMapper;

    private transient ProductPersistenceMapper productPersistenceMapper;

    @BeforeEach
    void setUp() {

        productPersistenceMapper = new ProductPersistenceMapper(categoryPersistenceMapper);
    }

    @Test
    void shouldMapToEntity() {

        final Product product = ProductBuilder.mockProduct();
        doReturn(Optional.of(CategoryEntityBuilder.mockCategoryEntity())).when(categoryPersistenceMapper).mapToEntity(any());

        final var result = productPersistenceMapper.mapToEntity(product);

        assertTrue(result.isPresent());
        assertEquals(product.getSku(), result.get().getSku());
        assertEquals(product.getName(), result.get().getName());
        assertEquals(product.getDescription(), result.get().getDescription());
        assertEquals(product.getUnitPrice(), result.get().getUnitPrice());
        assertEquals(product.getImageUrl(), result.get().getImageUrl());
        assertEquals(product.isActive(), result.get().isActive());
        assertEquals(product.getCreated(), result.get().getCreated());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = ProductEntityBuilder.mockProductEntity();
        doReturn(Optional.of(CategoryBuilder.mockCategory())).when(categoryPersistenceMapper).mapToDomainObject(any());

        final var result = productPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getSku(), result.get().getSku());
        assertEquals(entity.getName(), result.get().getName());
        assertEquals(entity.getDescription(), result.get().getDescription());
        assertEquals(entity.getUnitPrice(), result.get().getUnitPrice());
        assertEquals(entity.getImageUrl(), result.get().getImageUrl());
        assertEquals(entity.isActive(), result.get().isActive());
        assertEquals(entity.getCreated(), result.get().getCreated());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(productPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(productPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
