package com.cp.ecommerce.adapter.web.assistant.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Request body for asking the AI support assistant a question (see ADR 0020).
 *
 * @param question the customer's free-text question.
 * @param conversationId client-generated identifier correlating this question with earlier turns in the same chat session - the
 *            same "client generates an opaque correlation token" pattern already used for order placement's
 *            {@code Idempotency-Key}. Optional: a single, standalone question can omit it.
 */
@Builder
public record SupportQuestionResource(@Schema(example = "Can I still cancel my order after placing it?") String question,
        @Schema(example = "a343b57f-f1b0-46c4-846c-f8ee538f30f0") String conversationId) {

    /** Omits the components, which hold personal data, so that framework debug logs of request bodies do not expose them. */
    @Override
    public String toString() {

        return "SupportQuestionResource[personal data omitted]";
    }

    /** Lombok completes this builder; only its text is written here, because a builder holds personal data too. */
    public static class SupportQuestionResourceBuilder {

        @Override
        public String toString() {

            return "SupportQuestionResourceBuilder[personal data omitted]";
        }
    }
}
