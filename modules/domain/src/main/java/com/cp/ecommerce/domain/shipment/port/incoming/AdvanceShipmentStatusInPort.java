package com.cp.ecommerce.domain.shipment.port.incoming;

import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

public interface AdvanceShipmentStatusInPort {

    Shipment advanceShipmentStatus(String shipmentNumber);

    Shipment advanceShipmentStatus(String shipmentNumber, String operationId, ShipmentStatus expectedStatus);
}
