package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;

/**
 * Incoming port for asking the AI ops-analytics assistant a free-text question (see ADR 0021).
 */
public interface AskAnalyticsQuestionInPort {

    /**
     * Answers the given operator question.
     *
     * @param question the {@link AnalyticsQuestion} to answer.
     * @param conversationId client-supplied identifier correlating this question with earlier turns in the same session, same
     *            pattern as {@code AskSupportQuestionInPort}. May be {@code null} or blank, in which case the question is
     *            answered with no memory of any earlier turn.
     * @return the assistant's {@link AnalyticsAnswer}.
     */
    AnalyticsAnswer askQuestion(AnalyticsQuestion question, String conversationId);

}
