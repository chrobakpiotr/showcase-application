package com.cp.ecommerce.adapter.persistence.inventory.entity;

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
import lombok.Setter;

/**
 * Durable identity record for one workflow-owned SKU reservation.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "STOCK_RESERVATION")
public class StockReservationEntity {

    @Id
    @Column(name = "RESERVATION_KEY", length = 100, nullable = false)
    private String reservationKey;

    @Column(name = "RESERVATION_ID", length = 40, nullable = false)
    private String reservationId;

    @Column(name = "SKU", length = 40, nullable = false)
    private String sku;

    @Column(name = "QUANTITY", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private StockReservationStatus status;
}
