package com.cp.ecommerce.domain.inventory.port.incoming;

import com.cp.ecommerce.adapter.common.exception.InsufficientStockException;
import com.cp.ecommerce.domain.inventory.StockLevel;

/**
 * Incoming port for every stock-mutating operation on a SKU's {@link StockLevel}.
 */
public interface ManageStockInPort {

    /**
     * Increases on-hand quantity - a restock/goods-receipt event. Creates the stock row if this SKU has never been received
     * before.
     */
    StockLevel receiveStock(String sku, int quantity);

    /**
     * Reserves {@code quantity} units against a SKU's currently available stock (on-hand minus already-reserved), e.g. when a
     * shopping cart checkout or order placement claims stock ahead of shipment.
     *
     * @throws InsufficientStockException if fewer than {@code quantity} units are currently available.
     */
    StockLevel reserveStock(String sku, int quantity);

    /**
     * Releases a previously-made reservation without touching on-hand quantity, e.g. when an order/cart reservation is
     * cancelled before shipment. Safe to call with a quantity larger than what is actually reserved - reserved quantity is
     * simply clamped to zero rather than going negative.
     */
    StockLevel releaseStock(String sku, int quantity);

    /**
     * Reserves stock for a durable workflow identity. Replaying the same reservation identity and SKU is idempotent.
     */
    StockLevel reserveStock(String reservationId, String sku, int quantity);

    /**
     * Releases only the stock owned by the given durable reservation identity and SKU. Replays are idempotent.
     */
    StockLevel releaseStock(String reservationId, String sku);

    /**
     * Fulfills a previously-made reservation: decreases both on-hand and reserved quantity by {@code quantity}, e.g. once an
     * order actually ships.
     *
     * @throws InsufficientStockException if fewer than {@code quantity} units are currently reserved.
     */
    StockLevel fulfillStock(String sku, int quantity);

    StockLevel fulfillStock(String reservationId, String sku);

}
