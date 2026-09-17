package com.cp.ecommerce.domain.catalog.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.catalog.Category;

/**
 * Incoming port for listing every product category available to browse/filter by.
 */
public interface ListCategoriesInPort {

    List<Category> listCategories();

}
