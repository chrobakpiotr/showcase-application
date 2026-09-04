package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a stock level update loses an optimistic-locking race after exhausting its bounded number of retry
 * attempts (see {@code ManageStockUseCase}) - two concurrent requests tried to mutate the same SKU's stock level at once, and
 * this one kept losing. Mapped to {@code 409 Conflict}: the client's request itself was valid, but the underlying resource
 * changed concurrently, so a retry is the appropriate remedy.
 */
public class StockLevelConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StockLevelConflictException(final String sku, final Throwable cause) {

        super("Concurrent stock modification detected for SKU " + sku + ", please retry", cause);
    }

}
