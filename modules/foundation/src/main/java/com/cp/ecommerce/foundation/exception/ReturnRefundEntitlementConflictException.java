package com.cp.ecommerce.foundation.exception;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * Signals that persisted active RMA refund amounts are inconsistent with the immutable line entitlement.
 *
 * <p>
 * Automatic allocation stops instead of hiding historical over-allocation by clamping the remaining amount to zero.
 */
public class ReturnRefundEntitlementConflictException extends ApplicationConflictException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReturnRefundEntitlementConflictException(
            final String orderNumber,
            final String sku,
            final BigDecimal activeRefundAmount,
            final BigDecimal lineEntitlement) {

        super("Persisted active return refunds " + activeRefundAmount.toPlainString() + " exceed line entitlement "
                + lineEntitlement.toPlainString() + " for order " + orderNumber + ", SKU " + sku
                + "; historical data requires manual review");
    }
}
