package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a coupon redemption loses an optimistic-locking race after exhausting its bounded number of retry
 * attempts.
 */
public class CouponConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CouponConflictException(final String code, final Throwable cause) {

        super("Concurrent coupon modification detected for code " + code + ", please retry", cause);
    }

}
