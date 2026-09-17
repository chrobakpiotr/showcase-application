package com.cp.ecommerce.domain.shipment.port.outgoing;

import com.cp.ecommerce.domain.shipment.Shipment;

/**
 * Outgoing port for persisting shipments.
 */
public interface SaveShipmentOutPort {

    Shipment save(Shipment shipment);

}
