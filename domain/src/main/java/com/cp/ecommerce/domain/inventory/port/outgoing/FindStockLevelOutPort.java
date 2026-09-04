package com.cp.ecommerce.domain.inventory.port.outgoing;

import com.cp.ecommerce.domain.inventory.StockLevel;

/**
 * Outgoing port for looking up a SKU's persisted stock level.
 */
public interface FindStockLevelOutPort {

    /**
     * @return the persisted stock level for {@code sku}, or {@code null} if this SKU has never been received/stocked.
     */
    StockLevel find(String sku);

}
