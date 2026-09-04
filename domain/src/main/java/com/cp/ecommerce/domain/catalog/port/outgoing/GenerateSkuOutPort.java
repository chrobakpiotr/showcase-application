package com.cp.ecommerce.domain.catalog.port.outgoing;

/**
 * Outgoing port for generating a new, unique product SKU on creation.
 */
public interface GenerateSkuOutPort {

    String generate();

}
