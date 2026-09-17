package com.cp.ecommerce.domain.catalog.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.catalog.Category;

/**
 * Outgoing port for retrieving every product category.
 */
public interface FindCategoriesOutPort {

    List<Category> findAll();

}
