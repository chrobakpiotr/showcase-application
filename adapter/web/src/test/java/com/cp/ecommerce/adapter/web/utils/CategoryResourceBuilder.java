package com.cp.ecommerce.adapter.web.utils;

import com.cp.ecommerce.adapter.web.catalog.resource.CategoryResource;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link CategoryResource}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CategoryResourceBuilder {

    public static final String TEST_CATEGORY_NAME = "Electronics";

    public static final String TEST_CATEGORY_SLUG = "electronics";

    public static CategoryResource mockCategoryResource() {

        return CategoryResource.builder().name(TEST_CATEGORY_NAME).slug(TEST_CATEGORY_SLUG).build();
    }

}
