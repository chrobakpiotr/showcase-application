package com.cp.ecommerce.adapter.persistence.inventory.entity;

/**
 * Durable lifecycle of one order-owned SKU reservation.
 */
public enum StockReservationStatus {

    RESERVED,
    RELEASED,
    FULFILLED
}
