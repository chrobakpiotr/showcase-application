package com.cp.ecommerce.domain.shipment.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.shipment.PageQuery;
import com.cp.ecommerce.domain.shipment.PagedResult;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

/**
 * Incoming port for listing shipments.
 */
public interface ListShipmentsInPort {

    List<Shipment> listShipments();

    List<Shipment> listShipmentsForOrder(String orderNumber);

    List<Shipment> listShipmentsByStatus(ShipmentStatus status);

    PagedResult<Shipment> listShipments(PageQuery pageQuery);

    PagedResult<Shipment> listShipmentsForOrder(String orderNumber, PageQuery pageQuery);

    PagedResult<Shipment> listShipmentsByStatus(ShipmentStatus status, PageQuery pageQuery);

}
