package com.cp.ecommerce.domain.returns;

import java.math.BigDecimal;

/**
 * Immutable input for creating a return request.
 *
 * @param orderNumber order being returned
 * @param sku returned product SKU
 * @param quantity requested return quantity
 * @param orderedQuantity quantity originally ordered for the line
 * @param reason customer-provided return reason
 * @param refundAmount refund amount or immutable line entitlement, depending on the entry point
 */
public record ReturnRequestCommand(String orderNumber, String sku, int quantity, int orderedQuantity, String reason,
        BigDecimal refundAmount) {
}
