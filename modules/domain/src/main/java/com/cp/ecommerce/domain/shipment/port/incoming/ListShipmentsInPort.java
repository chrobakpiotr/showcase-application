package com.cp.ecommerce.domain.shipment.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

/**
 * Incoming port for listing shipments.
 */
public interface ListShipmentsInPort {

    List<Shipment> listShipments();

    List<Shipment> listShipmentsForOrder(String orderNumber);

    List<Shipment> listShipmentsByStatus(ShipmentStatus status);

}
