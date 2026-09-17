package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a coupon cannot be applied to the requested order total at the current time.
 */
public class CouponNotApplicableException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CouponNotApplicableException(final String code, final String reason) {

        super("Coupon " + code + " cannot be applied: " + reason);
    }

}
