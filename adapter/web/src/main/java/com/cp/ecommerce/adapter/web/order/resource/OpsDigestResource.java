package com.cp.ecommerce.adapter.web.order.resource;

import java.util.Date;
import java.util.Map;

import com.cp.ecommerce.domain.order.RemarksTriageCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing the latest AI-generated ops digest (see ADR 0022), returned by {@code GET
 * /api/order/analytics/digest}.
 *
 * @param generatedDate when this digest was generated.
 * @param ordersPlacedLastDay orders placed in the 24 hours before {@code generatedDate}.
 * @param remarksClassificationCounts remarks-triage classification counts (since this application instance started).
 * @param narrative plain-English narrative summarizing the figures above, or a fixed fallback sentence if the AI narrator is
 *            disabled or was unavailable when this digest was generated.
 */
@Builder
public record OpsDigestResource(@Schema(example = "2024-03-15T06:00:00.000Z") Date generatedDate,
        @Schema(example = "7") long ordersPlacedLastDay, Map<RemarksTriageCategory, Long> remarksClassificationCounts,
        @Schema(
                example = "7 orders were placed in the last 24 hours, all routine. No urgent or suspicious remarks to "
                        + "review.") String narrative) {

}
