package com.cp.ecommerce.adapter.web.utils;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.web.catalog.resource.ProductResource;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link ProductResource}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductResourceBuilder {

    public static final String TEST_PRODUCT_NAME = "Wireless Headphones";

    public static final String TEST_PRODUCT_DESCRIPTION = "Over-ear, noise-cancelling.";

    public static final String TEST_CATEGORY_SLUG = "electronics";

    public static final BigDecimal TEST_PRODUCT_UNIT_PRICE = new BigDecimal("99.99");

    public static final String TEST_PRODUCT_IMAGE_URL = "https://example.com/headphones.png";

    public static ProductResource mockProductResource() {

        return ProductResource.builder()
                .name(TEST_PRODUCT_NAME)
                .description(TEST_PRODUCT_DESCRIPTION)
                .categorySlug(TEST_CATEGORY_SLUG)
                .unitPrice(TEST_PRODUCT_UNIT_PRICE)
                .imageUrl(TEST_PRODUCT_IMAGE_URL)
                .active(true)
                .build();
    }

}
