package com.cp.ecommerce.domain.shipment.port.outgoing;

import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentOperation;

/**
 * Outgoing port for persisting shipments.
 */
public interface SaveShipmentOutPort {

    Shipment save(Shipment shipment);

    ShipmentOperation findOperation(String operationId);

    void saveOperation(ShipmentOperation operation);

}
