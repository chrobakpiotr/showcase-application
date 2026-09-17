package com.cp.ecommerce.adapter.persistence.shipment.entity;

import java.util.List;

import com.cp.ecommerce.domain.shipment.ShipmentStatus;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ShipmentEntity}.
 */
public interface ShipmentEntityRepository extends JpaRepository<ShipmentEntity, String> {

    ShipmentEntity findByShipmentNumber(String shipmentNumber);

    ShipmentEntity findByOrderNumber(String orderNumber);

    List<ShipmentEntity> findAllByOrderByCreatedDateDesc();

    List<ShipmentEntity> findByOrderNumberOrderByCreatedDateDesc(String orderNumber);

    List<ShipmentEntity> findByStatusOrderByCreatedDateDesc(ShipmentStatus status);

}
