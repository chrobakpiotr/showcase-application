package com.cp.ecommerce.adapter.web.order.resource;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Payment state nested in an order response.
 */
@Builder
public record PaymentResource(@Schema(example = "PARTIALLY_REFUNDED") PaymentStatus status,
        @Schema(example = "CARD") PaymentMethod method, @Schema(example = "59.98") BigDecimal amount,
        @Schema(example = "29.99") BigDecimal refundedAmount,
        @Schema(example = "mock-gw-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String gatewayReference) {
}
