package com.cp.ecommerce.adapter.web.shipments.resource;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a shipment.
 */
@Builder
public record ShipmentResource(@Schema(example = "SHIP-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String shipmentNumber,
        @Schema(example = "ORD-20240907-0001") String orderNumber, @Schema(example = "DHL") String carrier,
        @Schema(example = "DHL-3F2504E0-4F89-11D3-9A0C-0305E82C3301") String trackingNumber,
        @Schema(example = "PENDING") String status, Instant dispatchedDate, Instant estimatedDeliveryDate,
        Instant deliveredDate, Instant createdDate) {

}
