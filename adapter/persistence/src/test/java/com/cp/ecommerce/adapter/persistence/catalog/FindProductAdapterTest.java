package com.cp.ecommerce.adapter.persistence.catalog;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ProductBuilder;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import static com.cp.ecommerce.adapter.common.utils.ProductBuilder.TEST_PRODUCT_SKU;

/**
 * Test class for {@link FindProductAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindProductAdapterTest {

    @InjectMocks
    private transient FindProductAdapter findProductAdapter;

    @Mock
    private transient ProductEntityRepository productEntityRepository;

    @Mock
    private transient ProductPersistenceMapper productPersistenceMapper;

    @Test
    void shouldFindProductBySku() {

        final var entity = ProductEntityBuilder.mockProductEntity();
        final Product product = ProductBuilder.mockProduct();
        doReturn(entity).when(productEntityRepository).findBySku(TEST_PRODUCT_SKU);
        doReturn(Optional.of(product)).when(productPersistenceMapper).mapToDomainObject(entity);

        final Product result = findProductAdapter.find(TEST_PRODUCT_SKU);

        assertEquals(product, result);
    }

    @Test
    void shouldReturnNullWhenProductNotFound() {

        doReturn(null).when(productEntityRepository).findBySku(TEST_PRODUCT_SKU);
        doReturn(Optional.empty()).when(productPersistenceMapper).mapToDomainObject(null);

        final Product result = findProductAdapter.find(TEST_PRODUCT_SKU);

        assertNull(result);
    }

}
