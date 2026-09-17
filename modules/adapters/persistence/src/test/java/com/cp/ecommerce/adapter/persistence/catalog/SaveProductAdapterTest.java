package com.cp.ecommerce.adapter.persistence.catalog;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ProductBuilder;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntity;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.ProductPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ProductEntityBuilder;
import com.cp.ecommerce.domain.catalog.Product;

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

import static com.cp.ecommerce.adapter.common.utils.ProductBuilder.TEST_PRODUCT_SKU;

/**
 * Test class for {@link SaveProductAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveProductAdapterTest {

    @InjectMocks
    private transient SaveProductAdapter saveProductAdapter;

    @Mock
    private transient ProductEntityRepository productEntityRepository;

    @Mock
    private transient ProductPersistenceMapper productPersistenceMapper;

    @Test
    void shouldInsertNewProductWhenNoneExistsYet() {

        final Product product = ProductBuilder.mockProduct();
        final ProductEntity mappedEntity = ProductEntityBuilder.mockProductEntity();
        doReturn(Optional.of(mappedEntity)).when(productPersistenceMapper).mapToEntity(eq(product));
        doReturn(null).when(productEntityRepository).findBySku(TEST_PRODUCT_SKU);
        doReturn(mappedEntity).when(productEntityRepository).save(mappedEntity);
        doReturn(Optional.of(product)).when(productPersistenceMapper).mapToDomainObject(mappedEntity);

        final Product result = saveProductAdapter.save(product);

        verify(productEntityRepository, times(1)).save(mappedEntity);
        assertEquals(product, result);
    }

    @Test
    void shouldReuseExistingEntityIdOnUpdate() {

        final Product product = ProductBuilder.mockProduct();
        final ProductEntity mappedEntity = ProductEntityBuilder.mockProductEntity();
        final ProductEntity existingEntity = ProductEntityBuilder.mockProductEntity();
        existingEntity.setId(42L);
        doReturn(Optional.of(mappedEntity)).when(productPersistenceMapper).mapToEntity(eq(product));
        doReturn(existingEntity).when(productEntityRepository).findBySku(TEST_PRODUCT_SKU);
        doReturn(mappedEntity).when(productEntityRepository).save(any());
        doReturn(Optional.of(product)).when(productPersistenceMapper).mapToDomainObject(mappedEntity);

        saveProductAdapter.save(product);

        assertEquals(42L, mappedEntity.getId());
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final Product product = ProductBuilder.mockProduct();
        doReturn(Optional.empty()).when(productPersistenceMapper).mapToEntity(eq(product));

        assertThrows(IllegalStateException.class, () -> saveProductAdapter.save(product));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final Product product = ProductBuilder.mockProduct();
        final ProductEntity mappedEntity = ProductEntityBuilder.mockProductEntity();
        doReturn(Optional.of(mappedEntity)).when(productPersistenceMapper).mapToEntity(eq(product));
        doReturn(null).when(productEntityRepository).findBySku(TEST_PRODUCT_SKU);
        doReturn(mappedEntity).when(productEntityRepository).save(any());
        doReturn(Optional.empty()).when(productPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> saveProductAdapter.save(product));
    }

}
