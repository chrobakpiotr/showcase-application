package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a stock reservation or fulfillment request ({@code ManageStockInPort.reserveStock}/
 * {@code fulfillStock}) cannot be satisfied because not enough stock is currently available/reserved for the requested SKU.
 * This is an expected, recoverable outcome (another concurrent request may have already claimed the stock) rather than a
 * programming error, so it is mapped to {@code 409 Conflict} rather than a validation ({@code 400}) or server ({@code 500})
 * error.
 */
public class InsufficientStockException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InsufficientStockException(final String message) {

        super(message);
    }

}
