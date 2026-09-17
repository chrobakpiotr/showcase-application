package com.cp.ecommerce.adapter.common.testfixtures;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link AsyncApiSchema}.
 */
class AsyncApiSchemaTest {

    @Test
    void shouldReturnDeclaredPropertiesForKnownSchema() {

        final Set<String> properties = AsyncApiSchema.declaredProperties("OrderMessage");

        assertEquals(Set.of("schemaVersion", "created", "customerId", "orderNumber"), properties);
    }

    @Test
    void shouldThrowForUnknownSchema() {

        assertThrows(IllegalArgumentException.class, () -> AsyncApiSchema.declaredProperties("NoSuchSchema"));
    }

}
