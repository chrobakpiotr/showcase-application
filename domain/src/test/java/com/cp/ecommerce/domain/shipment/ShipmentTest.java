package com.cp.ecommerce.domain.shipment;

import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Shipment}.
 */
class ShipmentTest {

    @Test
    void shouldPassValidationForValidShipment() {

        final Shipment shipment = TestDomainObjectFactory.validShipment();

        assertDoesNotThrow(shipment::assertValidationsEmpty);
    }

    @Test
    void shouldDefaultStatusToPending() {

        final Shipment shipment = Shipment.builder()
                .orderNumber("ORD-1")
                .carrier("DHL")
                .trackingNumber("DHL-123")
                .createdDate(TestDomainObjectFactory.TEST_CREATED)
                .build();

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PENDING);
    }

    @Test
    void shouldFailValidationWhenCarrierIsBlank() {

        final Shipment shipment = Shipment.builder()
                .orderNumber("ORD-1")
                .carrier(" ")
                .trackingNumber("DHL-123")
                .createdDate(TestDomainObjectFactory.TEST_CREATED)
                .build();

        assertThrows(DomainObjectValidationException.class, shipment::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenTrackingNumberIsTooLong() {

        final Shipment shipment = Shipment.builder()
                .orderNumber("ORD-1")
                .carrier("DHL")
                .trackingNumber("x".repeat(ValidationConstants.SHIPMENT_TRACKING_NUMBER_MAX + 1))
                .createdDate(TestDomainObjectFactory.TEST_CREATED)
                .build();

        assertThrows(DomainObjectValidationException.class, shipment::assertValidationsEmpty);
    }

}
