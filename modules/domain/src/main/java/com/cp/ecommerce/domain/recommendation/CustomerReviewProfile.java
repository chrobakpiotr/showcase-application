package com.cp.ecommerce.domain.recommendation;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

/**
 * Lightweight review-history signal exposed to the recommendation use case.
 */
@Value
@Builder
public class CustomerReviewProfile {

    String sku;

    int rating;

    @ToString.Exclude
    String comment;

    /** Lombok completes this builder; only its text is written here, because a builder holds personal data too. */
    public static class CustomerReviewProfileBuilder {

        @Override
        public String toString() {

            return "CustomerReviewProfileBuilder[personal data omitted]";
        }
    }
}
