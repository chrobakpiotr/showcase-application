package com.cp.ecommerce.adapter.web.inventory.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing the current stock level of a SKU, returned by {@code GET /api/inventory/{sku}} and every stock mutation
 * endpoint.
 */
@Builder
public record StockLevelResource(@Schema(example = "SKU-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String sku,
        @Schema(example = "100") int quantityOnHand, @Schema(example = "15") int quantityReserved,
        @Schema(example = "85") int quantityAvailable) {

}
