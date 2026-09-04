package com.cp.ecommerce.adapter.web.catalog.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a product category, used both as the request payload for creating a category and as the response body
 * when listing categories.
 */
@Builder
public record CategoryResource(@Schema(example = "Electronics") String name, @Schema(example = "electronics") String slug) {

}
