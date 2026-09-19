package com.cp.ecommerce.domain.shipment.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.shipment.PageQuery;
import com.cp.ecommerce.domain.shipment.PagedResult;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

/**
 * Outgoing port for finding multiple shipments.
 */
public interface FindShipmentsOutPort {

    List<Shipment> findAll();

    List<Shipment> findByOrderNumber(String orderNumber);

    List<Shipment> findByStatus(ShipmentStatus status);

    PagedResult<Shipment> findAll(PageQuery pageQuery);

    PagedResult<Shipment> findByOrderNumber(String orderNumber, PageQuery pageQuery);

    PagedResult<Shipment> findByStatus(ShipmentStatus status, PageQuery pageQuery);

}
