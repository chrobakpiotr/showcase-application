package com.cp.ecommerce.domain.shipment.port.outgoing;

import com.cp.ecommerce.domain.shipment.Shipment;

/**
 * Outgoing port for finding a single shipment.
 */
public interface FindShipmentOutPort {

    Shipment findByShipmentNumber(String shipmentNumber);

    Shipment findByOrderNumber(String orderNumber);

}
