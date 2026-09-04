package com.cp.ecommerce.domain.assistant.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;
import com.cp.ecommerce.domain.assistant.port.incoming.AskSupportQuestionInPort;
import com.cp.ecommerce.domain.assistant.port.outgoing.AskSupportQuestionOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for asking the AI support assistant a free-text question.
 */
@UseCase
@RequiredArgsConstructor
public class AskSupportQuestionUseCase implements AskSupportQuestionInPort {

    private final AskSupportQuestionOutPort askSupportQuestionOutPort;

    @Override
    public SupportAnswer askQuestion(final SupportQuestion question, final String conversationId) {

        return askSupportQuestionOutPort.ask(question, conversationId);
    }

}
