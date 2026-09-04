package com.cp.ecommerce.adapter.persistence.utils;

import java.util.Date;

import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.ProductBuilder.TEST_PRODUCT_DESCRIPTION;
import static com.cp.ecommerce.adapter.common.utils.ProductBuilder.TEST_PRODUCT_IMAGE_URL;
import static com.cp.ecommerce.adapter.common.utils.ProductBuilder.TEST_PRODUCT_NAME;
import static com.cp.ecommerce.adapter.common.utils.ProductBuilder.TEST_PRODUCT_SKU;
import static com.cp.ecommerce.adapter.common.utils.ProductBuilder.TEST_PRODUCT_UNIT_PRICE;

/**
 * Builder class for {@link ProductEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductEntityBuilder {

    public static ProductEntity mockProductEntity() {

        return ProductEntity.builder()
                .sku(TEST_PRODUCT_SKU)
                .name(TEST_PRODUCT_NAME)
                .description(TEST_PRODUCT_DESCRIPTION)
                .category(CategoryEntityBuilder.mockCategoryEntity())
                .unitPrice(TEST_PRODUCT_UNIT_PRICE)
                .imageUrl(TEST_PRODUCT_IMAGE_URL)
                .active(true)
                .created(new Date())
                .build();
    }

}
