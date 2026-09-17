package com.cp.ecommerce.domain.order;

import lombok.Builder;
import lombok.Value;

/**
 * Result of checking whether an order is a likely accidental duplicate of another order recently placed by the same customer
 * (see {@code DetectDuplicateOrderOutPort}). A plain immutable result object, not a {@code @DomainObject}: like
 * {@link RemarksTriageResult}, this represents an AI-derived analysis output, not user-supplied input.
 */
@Value
@Builder
public class DuplicateOrderCheckResult {

    boolean duplicate;

    /**
     * Order number of the matching recent order this one was compared against, {@code null} when {@link #duplicate} is
     * {@code false}.
     */
    String matchedOrderNumber;

    /**
     * Similarity score in {@code [0, 1]} between this order and {@link #matchedOrderNumber}, {@code 0} when {@link #duplicate}
     * is {@code false}. Never used for any automated decision - only surfaced alongside the flag for a human reviewer.
     */
    double similarityScore;

    /**
     * Short, human-readable explanation of why the check flagged (or did not flag) a duplicate, logged alongside the other
     * fields - never used for any automated decision.
     */
    String rationale;

    /**
     * The trivial, no-comparison-possible result used whenever there is nothing to compare against (no recent orders from the
     * same customer) or the feature is disabled/unavailable - see {@code DoNotDetectDuplicateOrderAdapter}.
     */
    public static DuplicateOrderCheckResult none() {

        return DuplicateOrderCheckResult.builder().duplicate(false).similarityScore(0).build();
    }

}
