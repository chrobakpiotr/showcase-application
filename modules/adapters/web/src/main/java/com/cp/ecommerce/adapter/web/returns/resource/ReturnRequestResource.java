package com.cp.ecommerce.adapter.web.returns.resource;

import java.math.BigDecimal;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a return request.
 */
@Builder
public record ReturnRequestResource(@Schema(example = "RETURN-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String returnNumber,
        @Schema(example = "ORD-20240907-0001") String orderNumber, @Schema(example = "SKU-1234") String sku,
        @Schema(example = "1") int quantity, @Schema(example = "Damaged on arrival") String reason,
        @Schema(example = "REQUESTED") String status, Date requestedDate, Date decidedDate, BigDecimal refundAmount) {

}
