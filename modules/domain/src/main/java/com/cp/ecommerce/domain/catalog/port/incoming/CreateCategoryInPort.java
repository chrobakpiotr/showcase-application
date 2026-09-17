package com.cp.ecommerce.domain.catalog.port.incoming;

import com.cp.ecommerce.domain.catalog.Category;

/**
 * Incoming port for creating a new product category.
 */
public interface CreateCategoryInPort {

    Category createCategory(final Category category);

}
