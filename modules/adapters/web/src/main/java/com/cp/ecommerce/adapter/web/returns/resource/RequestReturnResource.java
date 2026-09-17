package com.cp.ecommerce.adapter.web.returns.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource used to create a new return request.
 */
@Builder
public record RequestReturnResource(@Schema(example = "ORD-20240907-0001") String orderNumber,
        @Schema(example = "SKU-1234") String sku, @Schema(example = "1") Integer quantity,
        @Schema(example = "Damaged on arrival") String reason) {

}
