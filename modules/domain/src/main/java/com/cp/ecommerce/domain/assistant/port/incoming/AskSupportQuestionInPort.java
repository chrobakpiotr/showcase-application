package com.cp.ecommerce.domain.assistant.port.incoming;

import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;

/**
 * Incoming port for asking the AI support assistant a free-text question (see ADR 0020).
 */
public interface AskSupportQuestionInPort {

    /**
     * Answers the given customer question.
     *
     * @param question the {@link SupportQuestion} to answer.
     * @param conversationId client-supplied identifier correlating this question with earlier turns in the same chat session,
     *            mirroring how {@code PlaceOrderInPort}'s {@code idempotencyKey} is a client-supplied correlation token rather
     *            than part of the domain object itself. May be {@code null} or blank, in which case the question is answered
     *            with no memory of any earlier turn.
     * @return the assistant's {@link SupportAnswer}.
     */
    SupportAnswer askQuestion(SupportQuestion question, String conversationId);

}
