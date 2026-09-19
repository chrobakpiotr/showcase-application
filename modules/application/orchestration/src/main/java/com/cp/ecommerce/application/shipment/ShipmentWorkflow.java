package com.cp.ecommerce.application.shipment;

import com.cp.ecommerce.domain.shipment.Shipment;

public interface ShipmentWorkflow {

    Shipment createShipment(String orderNumber, String carrier);

    Shipment advanceShipment(String shipmentNumber);
}
