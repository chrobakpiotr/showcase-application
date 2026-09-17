package com.cp.ecommerce.domain.catalog.port.incoming;

import com.cp.ecommerce.domain.catalog.PagedResult;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.ProductPageQuery;

/**
 * Incoming port for listing catalog products page by page, optionally filtered by category.
 */
public interface ListProductsInPort {

    PagedResult<Product> listProducts(final ProductPageQuery pageQuery);

}
