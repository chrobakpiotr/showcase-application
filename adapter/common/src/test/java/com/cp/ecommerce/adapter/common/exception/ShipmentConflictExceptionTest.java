package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ShipmentConflictException}.
 */
class ShipmentConflictExceptionTest {

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final ShipmentConflictException exception = new ShipmentConflictException("shipment conflict");

        assertThat(exception).hasMessage("shipment conflict");
    }
}
