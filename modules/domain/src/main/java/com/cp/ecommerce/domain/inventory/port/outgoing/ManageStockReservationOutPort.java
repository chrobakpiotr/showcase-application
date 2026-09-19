package com.cp.ecommerce.domain.inventory.port.outgoing;

import com.cp.ecommerce.domain.inventory.StockLevel;

/**
 * Atomic persistence boundary for identity-aware stock reservations.
 */
public interface ManageStockReservationOutPort {

    StockLevel reserveStock(String reservationId, String sku, int quantity);

    StockLevel releaseStock(String reservationId, String sku);

    StockLevel fulfillStock(String reservationId, String sku);

}
