package com.cp.ecommerce.domain.recommendation;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Customer-supplied request for AI-powered personalized product recommendations.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class RecommendationRequest extends ValidDomainObject<RecommendationRequest> {

    @NotBlank(message = ValidationConstants.INVALID_EMAIL)
    @Email(message = ValidationConstants.INVALID_EMAIL)
    @Size(max = ValidationConstants.CONTACT_EMAIL_MAX, message = ValidationConstants.INVALID_EMAIL)
    String customerEmail;

    public static RecommendationRequest.RecommendationRequestBuilder builder() {

        return new RecommendationRequest.RecommendationRequestBuilder() {

            @Override
            public RecommendationRequest build() {

                return super.build().validate();
            }
        };
    }

}
