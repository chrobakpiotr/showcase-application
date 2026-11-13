package com.cp.ecommerce.adapter.web.order.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Response body returned by the AI ops-analytics assistant (see ADR 0021).
 *
 * @param answer the assistant's answer, or a fixed fallback message when the feature is disabled or unavailable.
 * @param assistantAvailable whether {@code answer} came from the real, model-backed assistant. The frontend uses this to decide
 *            whether to also surface an "assistant unavailable" hint alongside the answer.
 */
@Builder
public record AnalyticsAnswerResource(
        @Schema(example = "14 order(s) were placed between 2024-01-01 and 2024-01-31 (inclusive, UTC).") String answer,
        boolean assistantAvailable) {

    /** Omits the components, which hold personal data, so that framework debug logs of request bodies do not expose them. */
    @Override
    public String toString() {

        return "AnalyticsAnswerResource[personal data omitted]";
    }

    /** Lombok completes this builder; only its text is written here, because a builder holds personal data too. */
    public static class AnalyticsAnswerResourceBuilder {

        @Override
        public String toString() {

            return "AnalyticsAnswerResourceBuilder[personal data omitted]";
        }
    }
}
