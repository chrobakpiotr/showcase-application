package com.cp.ecommerce.adapter.persistence.shipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link GenerateShipmentNumberAdapter}.
 */
class GenerateShipmentNumberAdapterTest {

    private final transient GenerateShipmentNumberAdapter generateShipmentNumberAdapter = new GenerateShipmentNumberAdapter();

    @Test
    void shouldGenerateShipmentNumberWithExpectedPrefix() {

        assertTrue(generateShipmentNumberAdapter.generate().startsWith("SHIP-"));
    }

    @Test
    void shouldGenerateUniqueShipmentNumberOnEachCall() {

        assertNotEquals(generateShipmentNumberAdapter.generate(), generateShipmentNumberAdapter.generate());
    }

}
