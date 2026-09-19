package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Exception thrown when a shipment operation violates fulfillment state constraints.
 */
public class ShipmentConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ShipmentConflictException(final String message) {

        super(message);
    }
}
