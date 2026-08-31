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

}
