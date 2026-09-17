package com.cp.ecommerce.domain.shipment.port.incoming;

import com.cp.ecommerce.domain.shipment.Shipment;

/**
 * Incoming port for creating a shipment for an order.
 */
public interface CreateShipmentInPort {

    Shipment createShipment(String orderNumber, String carrier);

}
