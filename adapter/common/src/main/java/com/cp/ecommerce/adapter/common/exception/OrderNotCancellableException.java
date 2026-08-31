package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a customer attempts to cancel an order (see {@code RequestOrderCancellationInPort}) that either does
 * not exist, or is no longer in a cancellable state (e.g. it was already cancelled).
 */
public class OrderNotCancellableException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OrderNotCancellableException(final String message) {

        super(message);
    }

}
