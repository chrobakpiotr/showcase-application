package com.cp.ecommerce.adapter.persistence.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link GenerateSkuAdapter}.
 */
class GenerateSkuAdapterTest {

    private final transient GenerateSkuAdapter generateSkuAdapter = new GenerateSkuAdapter();

    @Test
    void shouldGenerateSkuWithExpectedPrefix() {

        final String sku = generateSkuAdapter.generate();

        assertTrue(sku.startsWith("SKU-"));
    }

    @Test
    void shouldGenerateUniqueSkuOnEachCall() {

        assertNotEquals(generateSkuAdapter.generate(), generateSkuAdapter.generate());
    }

}
