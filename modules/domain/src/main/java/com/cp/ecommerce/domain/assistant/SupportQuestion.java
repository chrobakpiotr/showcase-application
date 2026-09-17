package com.cp.ecommerce.domain.assistant;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A free-text question a customer asks the AI support assistant (see ADR 0020). Unlike
 * {@link com.cp.ecommerce.domain.order.RemarksTriageResult}, this is genuine user-supplied input - not an AI-derived output -
 * so it is a self-validating {@code @DomainObject} like {@link com.cp.ecommerce.domain.order.Order}, not a plain value object.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class SupportQuestion extends ValidDomainObject<SupportQuestion> {

    @NotBlank(message = ValidationConstants.INVALID_SUPPORT_QUESTION)
    @Size(max = ValidationConstants.SUPPORT_QUESTION_MAX, message = ValidationConstants.INVALID_SUPPORT_QUESTION)
    String question;

    public static SupportQuestion.SupportQuestionBuilder builder() {

        return new SupportQuestion.SupportQuestionBuilder() {

            @Override
            public SupportQuestion build() {

                return super.build().validate();
            }
        };
    }

}
