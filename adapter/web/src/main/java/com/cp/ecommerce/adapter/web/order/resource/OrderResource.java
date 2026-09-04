package com.cp.ecommerce.adapter.web.order.resource;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resources representing order response.
 */
@Builder
public record OrderResource(@Schema(example = "Please leave the package with the concierge.") String remarks,
        @Schema(example = "2024-03-15T10:30:00.000Z") Date created, CustomerResource customer,
        List<OrderLineItemResource> items) {

}
