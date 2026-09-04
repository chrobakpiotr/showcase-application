package com.cp.ecommerce.domain.review;

import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A customer's review of a product, referenced by {@link #sku} only - exactly like {@code inventory.StockLevel} (ADR 0026) and
 * {@code cart.CartLineItem} (ADR 0027), this bounded context has no dependency on {@code catalog.Product}. There is no
 * persisted customer-account concept anywhere in this codebase (see ADR 0027), so a review is attributed to a free-text
 * {@link #authorName} rather than a customer id - the same constraint that shaped Shopping Cart also shapes this context (see
 * ADR 0028).
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class Review extends ValidDomainObject<Review> {

    // Business identifier, blank until SubmitReviewUseCase assigns one via GenerateReviewIdOutPort on creation.
    @Size(max = ValidationConstants.REVIEW_ID_MAX, message = ValidationConstants.INVALID_REVIEW_ID)
    String reviewId;

    @NotBlank(message = ValidationConstants.INVALID_REVIEW_SKU)
    @Size(max = ValidationConstants.REVIEW_SKU_MAX, message = ValidationConstants.INVALID_REVIEW_SKU)
    String sku;

    @NotBlank(message = ValidationConstants.INVALID_REVIEW_AUTHOR_NAME)
    @Size(max = ValidationConstants.REVIEW_AUTHOR_NAME_MAX, message = ValidationConstants.INVALID_REVIEW_AUTHOR_NAME)
    String authorName;

    @Min(value = 1, message = ValidationConstants.INVALID_REVIEW_RATING)
    @Max(value = 5, message = ValidationConstants.INVALID_REVIEW_RATING)
    int rating;

    @NotBlank(message = ValidationConstants.INVALID_REVIEW_COMMENT)
    @Size(max = ValidationConstants.REVIEW_COMMENT_MAX, message = ValidationConstants.INVALID_REVIEW_COMMENT)
    String comment;

    @NotNull(message = ValidationConstants.INVALID_REVIEW_STATUS)
    @Builder.Default
    ReviewStatus status = ReviewStatus.PENDING;

    Date created;

    public static Review.ReviewBuilder builder() {

        return new Review.ReviewBuilder() {

            @Override
            public Review build() {

                return super.build().validate();
            }
        };
    }

}
