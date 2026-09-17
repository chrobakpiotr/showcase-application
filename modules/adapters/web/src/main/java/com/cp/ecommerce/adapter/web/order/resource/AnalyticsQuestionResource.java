package com.cp.ecommerce.adapter.web.order.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Request body for asking the AI ops-analytics assistant a question (see ADR 0021).
 *
 * @param question the operator's free-text question.
 * @param conversationId client-generated identifier correlating this question with earlier turns in the same session, same
 *            pattern as {@code SupportQuestionResource}. Optional: a single, standalone question can omit it.
 */
@Builder
public record AnalyticsQuestionResource(
        @Schema(example = "How many orders were placed between 2024-01-01 and 2024-01-31?") String question,
        @Schema(example = "a343b57f-f1b0-46c4-846c-f8ee538f30f0") String conversationId) {

}
