package com.cp.ecommerce.domain.inventory.port.incoming;

import com.cp.ecommerce.domain.inventory.StockLevel;

/**
 * Incoming port for looking up the current stock level of a SKU.
 */
public interface GetStockLevelInPort {

    /**
     * Returns the current stock level for {@code sku}. Never {@code null}: a SKU with no stock row yet (never received) is
     * represented as a zero on-hand, zero-reserved {@link StockLevel} rather than a 404 - whether the SKU denotes a "real"
     * catalog product is out of scope for this bounded context (see ADR 0026).
     */
    StockLevel getStockLevel(String sku);

}
