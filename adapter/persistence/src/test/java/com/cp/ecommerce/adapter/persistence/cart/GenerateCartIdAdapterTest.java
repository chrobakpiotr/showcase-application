package com.cp.ecommerce.adapter.persistence.cart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link GenerateCartIdAdapter}.
 */
class GenerateCartIdAdapterTest {

    private final transient GenerateCartIdAdapter generateCartIdAdapter = new GenerateCartIdAdapter();

    @Test
    void shouldGenerateCartIdWithExpectedPrefix() {

        final String cartId = generateCartIdAdapter.generate();

        assertTrue(cartId.startsWith("CART-"));
    }

    @Test
    void shouldGenerateUniqueCartIdOnEachCall() {

        assertNotEquals(generateCartIdAdapter.generate(), generateCartIdAdapter.generate());
    }

}
