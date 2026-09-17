package com.cp.ecommerce.domain.shipment.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

/**
 * Outgoing port for finding multiple shipments.
 */
public interface FindShipmentsOutPort {

    List<Shipment> findAll();

    List<Shipment> findByOrderNumber(String orderNumber);

    List<Shipment> findByStatus(ShipmentStatus status);

}
