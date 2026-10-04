package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

public class OrderPlacementDispatchRedriveConflictException extends ApplicationConflictException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OrderPlacementDispatchRedriveConflictException(final String message) {
        super(message);
    }
}
