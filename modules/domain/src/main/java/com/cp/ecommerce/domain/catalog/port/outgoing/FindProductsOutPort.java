package com.cp.ecommerce.domain.catalog.port.outgoing;

import com.cp.ecommerce.domain.catalog.PagedResult;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.ProductPageQuery;

/**
 * Outgoing port for retrieving catalog products page by page.
 */
public interface FindProductsOutPort {

    PagedResult<Product> findAll(final ProductPageQuery pageQuery);

}
