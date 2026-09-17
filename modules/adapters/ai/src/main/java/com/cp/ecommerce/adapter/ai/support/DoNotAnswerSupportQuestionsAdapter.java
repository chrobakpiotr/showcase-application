package com.cp.ecommerce.adapter.ai.support;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;
import com.cp.ecommerce.domain.assistant.port.outgoing.AskSupportQuestionOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link AskSupportQuestionOutPort} with the AI support assistant disabled (the default: no Ollama instance
 * is assumed to be running).
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotAnswerSupportQuestionsAdapter implements AskSupportQuestionOutPort {

    @Override
    public SupportAnswer ask(final SupportQuestion question, final String conversationId) {

        log.debug("AI support assistant disabled, returning fallback answer.");
        return SupportAnswer.unavailable();
    }

}
