package com.cp.ecommerce.adapter.persistence.catalog;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ProductBuilder;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntity;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.ProductPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ProductEntityBuilder;
import com.cp.ecommerce.domain.catalog.PagedResult;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.ProductPageQuery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test class for {@link FindProductsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindProductsAdapterTest {

    private static final String TEST_CATEGORY_SLUG = "electronics";

    @InjectMocks
    private transient FindProductsAdapter findProductsAdapter;

    @Mock
    private transient ProductEntityRepository productEntityRepository;

    @Mock
    private transient ProductPersistenceMapper productPersistenceMapper;

    @Test
    void shouldFindAllByCategoryAndActiveWhenBothFiltersSet() {

        final ProductEntity entity = ProductEntityBuilder.mockProductEntity();
        final Product product = ProductBuilder.mockProduct();
        final Page<ProductEntity> page = new PageImpl<>(List.of(entity));
        doReturn(page).when(productEntityRepository).findAllByCategory_SlugAndActive(anyString(), anyBoolean(), any());
        doReturn(Optional.of(product)).when(productPersistenceMapper).mapToDomainObject(entity);

        final PagedResult<Product> result = findProductsAdapter.findAll(new ProductPageQuery(0, 20, TEST_CATEGORY_SLUG, true));

        assertEquals(List.of(product), result.content());
        verify(productEntityRepository).findAllByCategory_SlugAndActive(TEST_CATEGORY_SLUG, true, buildPageable());
        verifyNoMoreInteractions(productEntityRepository);
    }

    @Test
    void shouldFindAllByCategoryOnlyWhenActiveOnlyIsFalse() {

        final ProductEntity entity = ProductEntityBuilder.mockProductEntity();
        final Product product = ProductBuilder.mockProduct();
        final Page<ProductEntity> page = new PageImpl<>(List.of(entity));
        doReturn(page).when(productEntityRepository).findAllByCategory_Slug(anyString(), any());
        doReturn(Optional.of(product)).when(productPersistenceMapper).mapToDomainObject(entity);

        final PagedResult<Product> result = findProductsAdapter.findAll(new ProductPageQuery(0, 20, TEST_CATEGORY_SLUG, false));

        assertEquals(List.of(product), result.content());
        verify(productEntityRepository).findAllByCategory_Slug(TEST_CATEGORY_SLUG, buildPageable());
        verifyNoMoreInteractions(productEntityRepository);
    }

    @Test
    void shouldFindAllByActiveOnlyWhenNoCategoryFilterSet() {

        final ProductEntity entity = ProductEntityBuilder.mockProductEntity();
        final Product product = ProductBuilder.mockProduct();
        final Page<ProductEntity> page = new PageImpl<>(List.of(entity));
        doReturn(page).when(productEntityRepository).findAllByActive(anyBoolean(), any());
        doReturn(Optional.of(product)).when(productPersistenceMapper).mapToDomainObject(entity);

        final PagedResult<Product> result = findProductsAdapter.findAll(new ProductPageQuery(0, 20, null, true));

        assertEquals(List.of(product), result.content());
        verify(productEntityRepository).findAllByActive(true, buildPageable());
        verifyNoMoreInteractions(productEntityRepository);
    }

    @Test
    void shouldFindAllWhenNoFiltersSet() {

        final ProductEntity entity = ProductEntityBuilder.mockProductEntity();
        final Product product = ProductBuilder.mockProduct();
        final Page<ProductEntity> page = new PageImpl<>(List.of(entity));
        doReturn(page).when(productEntityRepository).findAll(any(Pageable.class));
        doReturn(Optional.of(product)).when(productPersistenceMapper).mapToDomainObject(entity);

        final PagedResult<Product> result = findProductsAdapter.findAll(new ProductPageQuery(0, 20, "", false));

        assertEquals(List.of(product), result.content());
        verify(productEntityRepository).findAll(buildPageable());
        verifyNoMoreInteractions(productEntityRepository);
    }

    private static Pageable buildPageable() {

        return PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
    }

}
