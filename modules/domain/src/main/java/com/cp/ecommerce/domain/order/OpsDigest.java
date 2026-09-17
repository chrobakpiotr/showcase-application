package com.cp.ecommerce.domain.order;

import java.util.Date;

import lombok.Builder;
import lombok.Value;

/**
 * A point-in-time, AI-narrated summary of recent order-placement activity and remarks-triage trends (see ADR 0022).
 *
 * <p>
 * Unlike {@link AnalyticsAnswer} - where the entire response is AI-derived and simply falls back to a fixed message when the
 * model is unavailable - {@link #ordersPlacedLastDay()} and the remarks-triage breakdown it summarizes always come straight
 * from the existing, deterministic analytics ports ({@code CountOrderAnalyticsProjectionsInPort} /
 * {@code GetRemarksClassificationSummaryInPort}): only {@link #narrative()}, the prose wrapped around those figures, can
 * degrade to a generic sentence if the model call fails or the feature is disabled. A plain immutable result object, not a
 * {@code @DomainObject}: system-generated, not user-supplied input needing {@code jakarta.validation} constraints.
 */
@Value
@Builder
public class OpsDigest {

    Date generatedDate;

    long ordersPlacedLastDay;

    RemarksClassificationSummary remarksClassificationSummary;

    /**
     * Plain-English narrative wrapping {@link #ordersPlacedLastDay()} and {@link #remarksClassificationSummary()}, written by
     * the AI ops-digest narrator - or a fixed fallback sentence if that narrator is disabled or unreachable.
     */
    String narrative;

}
