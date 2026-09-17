package com.cp.ecommerce.domain.order;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A free-text question an operator asks the AI ops-analytics assistant (see ADR 0021), e.g. "how many orders were placed from
 * Germany this week?". Genuine user-supplied input - not an AI-derived output - so it is a self-validating
 * {@code @DomainObject} like {@link com.cp.ecommerce.domain.assistant.SupportQuestion}, its customer-facing sibling.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class AnalyticsQuestion extends ValidDomainObject<AnalyticsQuestion> {

    @NotBlank(message = ValidationConstants.INVALID_ANALYTICS_QUESTION)
    @Size(max = ValidationConstants.ANALYTICS_QUESTION_MAX, message = ValidationConstants.INVALID_ANALYTICS_QUESTION)
    String question;

    public static AnalyticsQuestion.AnalyticsQuestionBuilder builder() {

        return new AnalyticsQuestion.AnalyticsQuestionBuilder() {

            @Override
            public AnalyticsQuestion build() {

                return super.build().validate();
            }
        };
    }

}
