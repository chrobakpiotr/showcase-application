package com.cp.ecommerce.adapter.web.order.resource;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.order.PaymentMethod;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resources representing order response.
 */
@Builder
public record OrderResource(@Schema(example = "Please leave the package with the concierge.") String remarks,
        @Schema(example = "2024-03-15T10:30:00.000Z") Instant created, CustomerResource customer,
        List<OrderLineItemResource> items, @Schema(example = "CARD") PaymentMethod paymentMethod,
        @Schema(example = "SAVE10") String couponCode) {

}
