package com.cp.ecommerce.adapter.common.utils;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.domain.catalog.Product;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link Product} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductBuilder {

    public static final String TEST_PRODUCT_SKU = "SKU-1234";

    public static final String TEST_PRODUCT_NAME = "Wireless Headphones";

    public static final String TEST_PRODUCT_DESCRIPTION = "Over-ear, noise-cancelling.";

    public static final BigDecimal TEST_PRODUCT_UNIT_PRICE = new BigDecimal("99.99");

    public static final String TEST_PRODUCT_IMAGE_URL = "https://example.com/headphones.png";

    public static Product mockProduct() {

        return Product.builder()
                .sku(TEST_PRODUCT_SKU)
                .name(TEST_PRODUCT_NAME)
                .description(TEST_PRODUCT_DESCRIPTION)
                .category(CategoryBuilder.mockCategory())
                .unitPrice(TEST_PRODUCT_UNIT_PRICE)
                .imageUrl(TEST_PRODUCT_IMAGE_URL)
                .active(true)
                .created(new Date())
                .build();
    }

}
