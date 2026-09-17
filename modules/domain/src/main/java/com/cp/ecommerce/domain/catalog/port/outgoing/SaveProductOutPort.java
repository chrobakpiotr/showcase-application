package com.cp.ecommerce.domain.catalog.port.outgoing;

import com.cp.ecommerce.domain.catalog.Product;

/**
 * Outgoing port for persisting a catalog product.
 */
public interface SaveProductOutPort {

    Product save(final Product product);

}
