package com.cp.ecommerce.adapter.web.order.resource;

import java.time.Instant;

import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineState;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Safe read-only recovery timeline row.
 */
@Builder
public record OrderRecoveryTimelineEntryResource(@Schema(example = "PLACEMENT_DISPATCH") String source,
        @Schema(example = "CONFIRMATION_EMAIL") String type, OrderRecoveryTimelineState state, Instant occurredAt,
        @Schema(example = "ORDER-CONFIRMATION:ORD-1001") String referenceId,
        @Schema(example = "PLACEMENT_DISPATCH CONFIRMATION_EMAIL state SENT") String summary) {
}
