package com.cp.ecommerce.adapter.web.catalog.mapper;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.utils.ProductBuilder;
import com.cp.ecommerce.adapter.web.catalog.resource.ProductResource;
import com.cp.ecommerce.adapter.web.utils.ProductResourceBuilder;
import com.cp.ecommerce.domain.catalog.Product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link ProductWebMapper}.
 */
class ProductWebMapperTest {

    private final transient ProductWebMapper productWebMapper = new ProductWebMapper();

    @Test
    void shouldMapToDomainObjectDraftWithoutCategory() {

        final ProductResource resource = ProductResourceBuilder.mockProductResource();

        final var result = productWebMapper.mapToDomainObject(resource);

        assertTrue(result.isPresent());
        assertEquals(resource.name(), result.get().getName());
        assertEquals(resource.description(), result.get().getDescription());
        assertEquals(resource.unitPrice(), result.get().getUnitPrice());
        assertEquals(resource.imageUrl(), result.get().getImageUrl());
        assertEquals(resource.active(), result.get().isActive());
        assertNull(result.get().getCategory());
    }

    @Test
    void shouldExtractCategorySlug() {

        final ProductResource resource = ProductResourceBuilder.mockProductResource();

        assertEquals(ProductResourceBuilder.TEST_CATEGORY_SLUG, productWebMapper.extractCategorySlug(resource));
    }

    @Test
    void shouldReturnNullCategorySlugWhenResourceIsNull() {

        assertNull(productWebMapper.extractCategorySlug(null));
    }

    @Test
    void shouldMapToResource() {

        final Product product = ProductBuilder.mockProduct();

        final var result = productWebMapper.mapToResource(product);

        assertTrue(result.isPresent());
        assertEquals(product.getSku(), result.get().sku());
        assertEquals(product.getName(), result.get().name());
        assertEquals(product.getDescription(), result.get().description());
        assertEquals(product.getCategory().getSlug(), result.get().categorySlug());
        assertEquals(product.getCategory().getName(), result.get().categoryName());
        assertEquals(product.getUnitPrice(), result.get().unitPrice());
        assertEquals(product.getImageUrl(), result.get().imageUrl());
        assertEquals(product.isActive(), result.get().active());
        assertEquals(product.getCreated(), result.get().created());
    }

    @Test
    void shouldMapToResourceWithNullCategoryFieldsWhenProductHasNoCategory() {

        final Product product = Product.builder().name("name").unitPrice(BigDecimal.ONE).build();

        final var result = productWebMapper.mapToResource(product);

        assertTrue(result.isPresent());
        assertNull(result.get().categorySlug());
        assertNull(result.get().categoryName());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(productWebMapper.mapToDomainObject(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToResource() {

        assertTrue(productWebMapper.mapToResource(null).isEmpty());
    }

}
