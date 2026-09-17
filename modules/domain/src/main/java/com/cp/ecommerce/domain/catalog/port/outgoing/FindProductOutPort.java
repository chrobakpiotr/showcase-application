package com.cp.ecommerce.domain.catalog.port.outgoing;

import com.cp.ecommerce.domain.catalog.Product;

/**
 * Outgoing port for retrieving a single catalog product by its SKU.
 */
public interface FindProductOutPort {

    Product find(final String sku);

}
