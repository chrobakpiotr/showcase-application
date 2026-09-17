package com.cp.ecommerce.adapter.web.review.resource;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a single review, returned by the customer-facing list endpoint and the moderation queue.
 */
@Builder
public record ReviewResource(@Schema(example = "REVIEW-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String reviewId,
        @Schema(example = "SKU-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String sku,
        @Schema(example = "Jane Smith") String authorName, @Schema(example = "5") int rating,
        @Schema(example = "Works great, very happy with it.") String comment, @Schema(example = "APPROVED") String status,
        Date created) {

}
