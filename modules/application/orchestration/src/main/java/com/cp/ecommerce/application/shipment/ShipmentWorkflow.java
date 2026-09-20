package com.cp.ecommerce.application.shipment;

import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

public interface ShipmentWorkflow {

    Shipment createShipment(String orderNumber, String carrier);

    Shipment advanceShipment(String shipmentNumber);

    Shipment advanceShipment(String shipmentNumber, String operationId, ShipmentStatus expectedStatus);
}
