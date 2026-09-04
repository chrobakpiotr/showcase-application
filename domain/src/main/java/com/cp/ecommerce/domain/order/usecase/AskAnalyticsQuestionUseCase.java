package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;
import com.cp.ecommerce.domain.order.port.incoming.AskAnalyticsQuestionInPort;
import com.cp.ecommerce.domain.order.port.outgoing.AskAnalyticsQuestionOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for asking the AI ops-analytics assistant a free-text question.
 */
@UseCase
@RequiredArgsConstructor
public class AskAnalyticsQuestionUseCase implements AskAnalyticsQuestionInPort {

    private final AskAnalyticsQuestionOutPort askAnalyticsQuestionOutPort;

    @Override
    public AnalyticsAnswer askQuestion(final AnalyticsQuestion question, final String conversationId) {

        return askAnalyticsQuestionOutPort.ask(question, conversationId);
    }

}
