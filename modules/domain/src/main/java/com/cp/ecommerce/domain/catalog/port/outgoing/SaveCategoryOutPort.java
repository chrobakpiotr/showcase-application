package com.cp.ecommerce.domain.catalog.port.outgoing;

import com.cp.ecommerce.domain.catalog.Category;

/**
 * Outgoing port for persisting a product category.
 */
public interface SaveCategoryOutPort {

    Category save(final Category category);

}
