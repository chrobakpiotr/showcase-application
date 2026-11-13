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

    /** Omits the components, which hold personal data, so that framework debug logs of request bodies do not expose them. */
    @Override
    public String toString() {

        return "OrderResource[personal data omitted]";
    }

    /** Lombok completes this builder; only its text is written here, because a builder holds personal data too. */
    public static class OrderResourceBuilder {

        @Override
        public String toString() {

            return "OrderResourceBuilder[personal data omitted]";
        }
    }
}
