package com.cp.ecommerce.adapter.common.utils;

import java.util.Date;

import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link Shipment} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ShipmentBuilder {

    public static final String TEST_SHIPMENT_NUMBER = "SHIP-1234";
    public static final String TEST_ORDER_NUMBER = "ORD-1001";
    public static final String TEST_CARRIER = "DHL";
    public static final String TEST_TRACKING_NUMBER = "DHL-TRACK-1234";
    public static final ShipmentStatus TEST_STATUS = ShipmentStatus.PENDING;
    public static final Date TEST_CREATED_DATE = new Date(1710000000000L);
    public static final Date TEST_DISPATCHED_DATE = new Date(1710086400000L);
    public static final Date TEST_ESTIMATED_DELIVERY_DATE = new Date(1710518400000L);
    public static final Date TEST_DELIVERED_DATE = new Date(1710345600000L);

    public static Shipment mockShipment() {

        return Shipment.builder()
                .shipmentNumber(TEST_SHIPMENT_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .carrier(TEST_CARRIER)
                .trackingNumber(TEST_TRACKING_NUMBER)
                .status(TEST_STATUS)
                .createdDate(TEST_CREATED_DATE)
                .build();
    }

}
