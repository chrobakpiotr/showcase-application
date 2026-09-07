package com.cp.ecommerce.adapter.persistence.returns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link GenerateReturnNumberAdapter}.
 */
class GenerateReturnNumberAdapterTest {

    private final transient GenerateReturnNumberAdapter generateReturnNumberAdapter = new GenerateReturnNumberAdapter();

    @Test
    void shouldGenerateReturnNumberWithExpectedPrefix() {

        assertTrue(generateReturnNumberAdapter.generate().startsWith("RETURN-"));
    }

    @Test
    void shouldGenerateUniqueReturnNumberOnEachCall() {

        assertNotEquals(generateReturnNumberAdapter.generate(), generateReturnNumberAdapter.generate());
    }

}
