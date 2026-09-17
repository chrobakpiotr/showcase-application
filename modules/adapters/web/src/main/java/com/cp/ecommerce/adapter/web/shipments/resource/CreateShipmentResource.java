package com.cp.ecommerce.adapter.web.shipments.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource used to create a new shipment.
 */
@Builder
public record CreateShipmentResource(@Schema(example = "ORD-20240907-0001") String orderNumber,
        @Schema(example = "DHL") String carrier) {

}
