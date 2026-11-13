package com.cp.ecommerce.adapter.web.assistant.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Response body returned by the AI support assistant (see ADR 0020).
 *
 * @param answer the assistant's answer, or a fixed fallback message when the feature is disabled or unavailable.
 * @param assistantAvailable whether {@code answer} came from the real, model-backed assistant. The frontend widget uses this to
 *            decide whether to also surface an "assistant unavailable" hint alongside the answer.
 */
@Builder
public record SupportAnswerResource(@Schema(
        example = "Yes, you can cancel your order any time before it ships, from the order details page.") String answer,
        boolean assistantAvailable) {

    /** Omits the components, which hold personal data, so that framework debug logs of request bodies do not expose them. */
    @Override
    public String toString() {

        return "SupportAnswerResource[personal data omitted]";
    }

    /** Lombok completes this builder; only its text is written here, because a builder holds personal data too. */
    public static class SupportAnswerResourceBuilder {

        @Override
        public String toString() {

            return "SupportAnswerResourceBuilder[personal data omitted]";
        }
    }
}
