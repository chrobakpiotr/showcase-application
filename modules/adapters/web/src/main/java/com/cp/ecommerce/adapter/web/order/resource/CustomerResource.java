package com.cp.ecommerce.adapter.web.order.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing the customer contact and address details attached to an order.
 */
@Builder
public record CustomerResource(@Schema(example = "Jane Doe") String fullName,
        @Schema(example = "jane.doe@example.com") String email, @Schema(example = "+1 555 123 4567") String phone,
        @Schema(example = "Main Street 1") String street, @Schema(example = "12-345") String postalCode,
        @Schema(example = "Warsaw") String city, @Schema(example = "PL") String countryCode) {

    /** Omits the components, which hold personal data, so that framework debug logs of request bodies do not expose them. */
    @Override
    public String toString() {

        return "CustomerResource[personal data omitted]";
    }

    /** Lombok completes this builder; only its text is written here, because a builder holds personal data too. */
    public static class CustomerResourceBuilder {

        @Override
        public String toString() {

            return "CustomerResourceBuilder[personal data omitted]";
        }
    }
}
