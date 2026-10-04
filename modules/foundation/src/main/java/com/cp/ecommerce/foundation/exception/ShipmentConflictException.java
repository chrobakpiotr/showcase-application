package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Exception thrown when a shipment operation violates fulfillment state constraints.
 */
public class ShipmentConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Code code;

    public ShipmentConflictException(final String message) {
        this(message, null);
    }

    public ShipmentConflictException(final String message, final Code code) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        SHIPMENT_STALE_STATUS,
        SHIPMENT_OPERATION_FINGERPRINT_CONFLICT
    }
}
