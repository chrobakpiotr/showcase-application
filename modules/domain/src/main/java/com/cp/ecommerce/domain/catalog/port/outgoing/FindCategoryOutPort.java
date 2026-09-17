package com.cp.ecommerce.domain.catalog.port.outgoing;

import com.cp.ecommerce.domain.catalog.Category;

/**
 * Outgoing port for retrieving a single product category by its slug.
 */
public interface FindCategoryOutPort {

    Category findBySlug(final String slug);

}
