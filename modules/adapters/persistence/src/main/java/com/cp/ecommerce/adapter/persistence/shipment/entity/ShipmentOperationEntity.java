package com.cp.ecommerce.adapter.persistence.shipment.entity;

import java.time.Instant;

import com.cp.ecommerce.domain.shipment.ShipmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "SHIPMENT_OPERATION")
public class ShipmentOperationEntity {

    @Id
    @Column(name = "OPERATION_ID", length = 80, nullable = false)
    private String operationId;

    @Column(name = "SHIPMENT_NUMBER", length = 41, nullable = false)
    private String shipmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "EXPECTED_STATUS", length = 20, nullable = false)
    private ShipmentStatus expectedStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "RESULT_STATUS", length = 20, nullable = false)
    private ShipmentStatus resultStatus;

    @Column(name = "DISPATCHED_DATE")
    private Instant dispatchedDate;

    @Column(name = "ESTIMATED_DELIVERY_DATE")
    private Instant estimatedDeliveryDate;

    @Column(name = "DELIVERED_DATE")
    private Instant deliveredDate;

    @Column(name = "RESULT_VERSION", nullable = false)
    private long resultVersion;
}
