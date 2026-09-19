package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Thrown when a return request exceeds the still-returnable quantity of an order line.
 */
public class ReturnQuantityConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReturnQuantityConflictException(final int remainingQuantity) {

        super("Requested quantity exceeds the remaining returnable quantity of " + remainingQuantity);
    }

}
