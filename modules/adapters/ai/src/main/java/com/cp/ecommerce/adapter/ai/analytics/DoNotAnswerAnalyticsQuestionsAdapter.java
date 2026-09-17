package com.cp.ecommerce.adapter.ai.analytics;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;
import com.cp.ecommerce.domain.order.port.outgoing.AskAnalyticsQuestionOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link AskAnalyticsQuestionOutPort} with the AI ops-analytics assistant disabled (the default: no Ollama
 * instance is assumed to be running).
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotAnswerAnalyticsQuestionsAdapter implements AskAnalyticsQuestionOutPort {

    @Override
    public AnalyticsAnswer ask(final AnalyticsQuestion question, final String conversationId) {

        log.debug("AI ops-analytics assistant disabled, returning fallback answer.");
        return AnalyticsAnswer.unavailable();
    }

}
