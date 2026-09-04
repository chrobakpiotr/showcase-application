package com.cp.ecommerce.domain.assistant;

import lombok.Builder;
import lombok.Value;

/**
 * The AI support assistant's answer to a {@link SupportQuestion} (see ADR 0020). A plain immutable result object, not a
 * {@code @DomainObject}: like {@link com.cp.ecommerce.domain.order.RemarksTriageResult}, this represents an AI-derived (or
 * fallback) output, not user-supplied input that needs {@code jakarta.validation} constraints enforced on construction.
 */
@Value
@Builder
public class SupportAnswer {

    String answer;

    /**
     * Whether {@link #answer} came from the real assistant (grounded/tool-augmented model response) or is a fixed fallback
     * message, e.g. because the feature is disabled or the model call failed. The frontend widget uses this to decide whether
     * to also surface a "the assistant is currently unavailable" hint alongside {@link #answer}.
     */
    boolean assistantAvailable;

    /**
     * The fixed fallback answer used whenever the feature is disabled ({@code DoNotAnswerSupportQuestionsAdapter}) or the real
     * model call itself fails - never a technical exception bubbling up to a chatting customer.
     */
    public static SupportAnswer unavailable() {

        return SupportAnswer.builder()
                .answer(
                        "Sorry, the AI support assistant is currently unavailable. Please contact our support team directly "
                                + "for help with your question.")
                .assistantAvailable(false)
                .build();
    }

}
