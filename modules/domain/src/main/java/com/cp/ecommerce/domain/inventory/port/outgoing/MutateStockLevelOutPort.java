package com.cp.ecommerce.domain.inventory.port.outgoing;

import java.util.function.UnaryOperator;

import com.cp.ecommerce.adapter.common.exception.StockLevelConflictException;
import com.cp.ecommerce.domain.inventory.StockLevel;

/**
 * Executes one complete generic stock mutation attempt.
 *
 * <p>
 * The persistence implementation owns a fresh transaction for the read, mutation and optimistic save. A caller may retry after
 * {@link StockLevelConflictException}; every retry must therefore use a fresh persistence context.
 */
public interface MutateStockLevelOutPort {

    StockLevel mutate(String sku, UnaryOperator<StockLevel> mutation);
}
