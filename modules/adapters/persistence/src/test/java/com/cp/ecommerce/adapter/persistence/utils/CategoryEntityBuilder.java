package com.cp.ecommerce.adapter.persistence.utils;

import com.cp.ecommerce.adapter.persistence.catalog.entity.CategoryEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.CategoryBuilder.TEST_CATEGORY_ID;
import static com.cp.ecommerce.adapter.common.utils.CategoryBuilder.TEST_CATEGORY_NAME;
import static com.cp.ecommerce.adapter.common.utils.CategoryBuilder.TEST_CATEGORY_SLUG;

/**
 * Builder class for {@link CategoryEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CategoryEntityBuilder {

    public static CategoryEntity mockCategoryEntity() {

        return CategoryEntity.builder().id(TEST_CATEGORY_ID).name(TEST_CATEGORY_NAME).slug(TEST_CATEGORY_SLUG).build();
    }

}
