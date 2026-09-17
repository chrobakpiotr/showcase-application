package com.cp.ecommerce.domain.order;

import lombok.Builder;
import lombok.Value;

/**
 * The AI ops-analytics assistant's answer to an {@link AnalyticsQuestion} (see ADR 0021). A plain immutable result object, not
 * a {@code @DomainObject}: like {@link com.cp.ecommerce.domain.assistant.SupportAnswer}, this represents an AI-derived (or
 * fallback) output, not user-supplied input that needs {@code jakarta.validation} constraints enforced on construction.
 */
@Value
@Builder
public class AnalyticsAnswer {

    String answer;

    /**
     * Whether {@link #answer} came from the real assistant or is a fixed fallback message, e.g. because the feature is disabled
     * or the model call failed. The frontend uses this to decide whether to also surface an "assistant currently unavailable"
     * hint alongside {@link #answer}.
     */
    boolean assistantAvailable;

    /**
     * The fixed fallback answer used whenever the feature is disabled ({@code DoNotAnswerAnalyticsQuestionsAdapter}) or the
     * real model call itself fails - never a technical exception bubbling up to an operator mid-conversation.
     */
    public static AnalyticsAnswer unavailable() {

        return AnalyticsAnswer.builder()
                .answer(
                        "Sorry, the AI analytics assistant is currently unavailable. Please query the "
                                + "/api/order/analytics/recent endpoint or Grafana dashboards directly.")
                .assistantAvailable(false)
                .build();
    }

}
