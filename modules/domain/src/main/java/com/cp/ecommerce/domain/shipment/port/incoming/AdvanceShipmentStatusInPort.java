package com.cp.ecommerce.domain.shipment.port.incoming;

import com.cp.ecommerce.domain.shipment.Shipment;

/**
 * Incoming port for advancing a shipment to its next status.
 */
public interface AdvanceShipmentStatusInPort {

    Shipment advanceShipmentStatus(String shipmentNumber);

}
