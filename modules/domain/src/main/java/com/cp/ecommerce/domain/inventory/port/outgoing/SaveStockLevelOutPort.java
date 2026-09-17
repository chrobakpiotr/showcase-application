package com.cp.ecommerce.domain.inventory.port.outgoing;

import com.cp.ecommerce.adapter.common.exception.StockLevelConflictException;
import com.cp.ecommerce.domain.inventory.StockLevel;

/**
 * Outgoing port for persisting a SKU's stock level.
 */
public interface SaveStockLevelOutPort {

    /**
     * Persists {@code stockLevel}, using {@link StockLevel#getVersion()} as the optimistic-locking token.
     *
     * @throws StockLevelConflictException if the persisted row's version no longer matches {@code stockLevel.getVersion()} -
     *             i.e. another request updated this SKU's stock concurrently since it was read.
     */
    StockLevel save(StockLevel stockLevel);

}
