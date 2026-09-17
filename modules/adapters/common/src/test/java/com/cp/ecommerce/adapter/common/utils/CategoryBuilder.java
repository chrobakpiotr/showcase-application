package com.cp.ecommerce.adapter.common.utils;

import com.cp.ecommerce.domain.catalog.Category;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link Category} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CategoryBuilder {

    public static final Long TEST_CATEGORY_ID = 1L;

    public static final String TEST_CATEGORY_NAME = "Electronics";

    public static final String TEST_CATEGORY_SLUG = "electronics";

    public static Category mockCategory() {

        return Category.builder().id(TEST_CATEGORY_ID).name(TEST_CATEGORY_NAME).slug(TEST_CATEGORY_SLUG).build();
    }

}
