package com.cp.ecommerce.domain.order;

import lombok.Builder;
import lombok.Value;

/**
 * Result of classifying an order's free-text remarks (see {@link RemarksTriageCategory}). A plain immutable result object, not
 * a {@code @DomainObject}: unlike {@link Order} itself, this represents an AI-derived analysis output, not user-supplied input
 * that needs {@code jakarta.validation} constraints enforced on construction.
 */
@Value
@Builder
public class RemarksTriageResult {

    RemarksTriageCategory category;

    /**
     * Short, human-readable explanation of why the model chose {@link #category}, logged alongside the category for a human
     * reviewer - never used for any automated decision.
     */
    String rationale;

    /**
     * The trivial, no-model-call-needed result used whenever there is nothing to classify (blank remarks) or the feature is
     * disabled/unavailable - see {@code DoNotClassifyOrderRemarksAdapter}.
     */
    public static RemarksTriageResult standard(final String rationale) {

        return RemarksTriageResult.builder().category(RemarksTriageCategory.STANDARD).rationale(rationale).build();
    }

}
