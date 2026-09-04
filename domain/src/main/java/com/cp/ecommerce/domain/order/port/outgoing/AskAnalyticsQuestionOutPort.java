package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;

/**
 * Outgoing port for an AI-assisted answer to an operator's ops-analytics question. Implementations may call a locally-hosted or
 * hosted LLM augmented with tool-calling into the existing order-analytics/remarks-classification query use cases, or - when
 * the feature is disabled/unavailable - a no-op adapter that always returns {@link AnalyticsAnswer#unavailable()} without
 * making any external call.
 */
public interface AskAnalyticsQuestionOutPort {

    /**
     * Answers the given operator question.
     *
     * @param question the {@link AnalyticsQuestion} to answer.
     * @param conversationId client-supplied identifier correlating this question with earlier turns in the same session. May be
     *            {@code null} or blank.
     * @return the assistant's {@link AnalyticsAnswer}.
     */
    AnalyticsAnswer ask(AnalyticsQuestion question, String conversationId);

}
