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

    /** Omits the components, which hold personal data, so that framework debug logs of request bodies do not expose them. */
    @Override
    public String toString() {

        return "RequestReturnResource[personal data omitted]";
    }

    /** Lombok completes this builder; only its text is written here, because a builder holds personal data too. */
    public static class RequestReturnResourceBuilder {

        @Override
        public String toString() {

            return "RequestReturnResourceBuilder[personal data omitted]";
        }
    }
}
