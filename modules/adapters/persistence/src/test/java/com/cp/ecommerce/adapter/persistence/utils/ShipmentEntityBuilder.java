package com.cp.ecommerce.adapter.persistence.utils;

import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.ShipmentBuilder.TEST_CARRIER;
import static com.cp.ecommerce.adapter.common.utils.ShipmentBuilder.TEST_CREATED_DATE;
import static com.cp.ecommerce.adapter.common.utils.ShipmentBuilder.TEST_DELIVERED_DATE;
import static com.cp.ecommerce.adapter.common.utils.ShipmentBuilder.TEST_DISPATCHED_DATE;
import static com.cp.ecommerce.adapter.common.utils.ShipmentBuilder.TEST_ESTIMATED_DELIVERY_DATE;
import static com.cp.ecommerce.adapter.common.utils.ShipmentBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.ShipmentBuilder.TEST_SHIPMENT_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.ShipmentBuilder.TEST_STATUS;
import static com.cp.ecommerce.adapter.common.utils.ShipmentBuilder.TEST_TRACKING_NUMBER;

/**
 * Builder class for {@link ShipmentEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ShipmentEntityBuilder {

    public static ShipmentEntity mockShipmentEntity() {

        return ShipmentEntity.builder()
                .shipmentNumber(TEST_SHIPMENT_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .carrier(TEST_CARRIER)
                .trackingNumber(TEST_TRACKING_NUMBER)
                .status(TEST_STATUS)
                .dispatchedDate(TEST_DISPATCHED_DATE)
                .estimatedDeliveryDate(TEST_ESTIMATED_DELIVERY_DATE)
                .deliveredDate(TEST_DELIVERED_DATE)
                .createdDate(TEST_CREATED_DATE)
                .build();
    }

}
