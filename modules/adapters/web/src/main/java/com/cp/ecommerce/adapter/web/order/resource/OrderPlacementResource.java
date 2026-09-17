package com.cp.ecommerce.adapter.web.order.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Response body returned by a successful (or idempotently replayed) order placement, wrapping the raw order number in a proper
 * JSON resource rather than a bare string, so clients can rely on standard JSON parsing instead of content-type sniffing.
 *
 * @param orderNumber number of the placed (or replayed) order
 */
@Builder
public record OrderPlacementResource(@Schema(example = "a343b57f-f1b0-46c4-846c-f8ee538f30f0-3") String orderNumber) {

}
