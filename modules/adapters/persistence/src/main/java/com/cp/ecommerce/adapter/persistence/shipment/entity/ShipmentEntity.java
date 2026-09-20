package com.cp.ecommerce.adapter.persistence.shipment.entity;

import java.time.Instant;

import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link Shipment} in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "SHIPMENT")
public class ShipmentEntity {

    @Id
    @Column(name = "SHIPMENT_NUMBER", length = 41, nullable = false)
    private String shipmentNumber;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "CARRIER", length = 80, nullable = false)
    private String carrier;

    @Column(name = "TRACKING_NUMBER", length = 60, nullable = false)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private ShipmentStatus status;

    @Column(name = "DISPATCHED_DATE")
    private Instant dispatchedDate;

    @Column(name = "ESTIMATED_DELIVERY_DATE")
    private Instant estimatedDeliveryDate;

    @Column(name = "DELIVERED_DATE")
    private Instant deliveredDate;

    @Column(name = "CREATED_DATE", nullable = false)
    private Instant createdDate;

    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

    @Column(name = "LAST_OPERATION_ID", length = 80)
    private String lastOperationId;

}
