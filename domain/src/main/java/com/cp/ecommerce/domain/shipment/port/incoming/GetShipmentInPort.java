package com.cp.ecommerce.domain.shipment.port.incoming;

import com.cp.ecommerce.domain.shipment.Shipment;

/**
 * Incoming port for reading one shipment.
 */
public interface GetShipmentInPort {

    Shipment getShipment(String shipmentNumber);

}
