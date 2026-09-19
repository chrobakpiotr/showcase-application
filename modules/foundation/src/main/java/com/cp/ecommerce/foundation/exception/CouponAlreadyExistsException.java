package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Exception thrown when a new coupon is created with a business code that already exists.
 */
public class CouponAlreadyExistsException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CouponAlreadyExistsException(final String code) {

        super("Coupon " + code + " already exists");
    }

}
