package com.cp.ecommerce.adapter.web.review.resource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for {@code POST /api/reviews}: the SKU, author name, rating (1-5), and comment a customer submits. There is
 * no field to set the status - every new review starts {@code PENDING}, only an operator can change that (see
 * {@code ReviewModerationController}).
 */
public record SubmitReviewResource(@Schema(example = "SKU-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String sku,
        @Schema(example = "Jane Smith") String authorName, @Schema(example = "5") Integer rating,
        @Schema(example = "Works great, very happy with it.") String comment) {

}
