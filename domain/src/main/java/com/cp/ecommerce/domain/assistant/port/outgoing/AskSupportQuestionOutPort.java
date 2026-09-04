package com.cp.ecommerce.domain.assistant.port.outgoing;

import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;

/**
 * Outgoing port for an AI-assisted answer to a customer's support question. Implementations may call a locally-hosted or hosted
 * LLM (optionally grounded via retrieval-augmented generation and/or tool-calling into existing order-lookup use cases), or -
 * when the feature is disabled/unavailable - a no-op adapter that always returns {@link SupportAnswer#unavailable()} without
 * making any external call.
 */
public interface AskSupportQuestionOutPort {

    /**
     * Answers the given customer question.
     *
     * @param question the {@link SupportQuestion} to answer.
     * @param conversationId client-supplied identifier correlating this question with earlier turns in the same chat session.
     *            May be {@code null} or blank.
     * @return the assistant's {@link SupportAnswer}.
     */
    SupportAnswer ask(SupportQuestion question, String conversationId);

}
