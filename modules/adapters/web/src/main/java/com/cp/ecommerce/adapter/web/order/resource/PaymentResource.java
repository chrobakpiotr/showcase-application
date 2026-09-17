package com.cp.ecommerce.adapter.web.order.resource;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing the payment transaction recorded for an order (see ADR 0030), nested inside
 * {@link OrderDetailsResource} - composed at the web layer from {@code GetPaymentInPort}, a separate bounded context from
 * {@code Order} itself (see {@code OrderController}).
 */
@Builder
public record PaymentResource(@Schema(example = "CAPTURED") PaymentStatus status,
        @Schema(example = "CARD") PaymentMethod method, @Schema(example = "59.98") BigDecimal amount,
        @Schema(example = "mock-gw-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String gatewayReference) {

}
