package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.RemarksClassificationSummary;

/**
 * Outgoing port for turning the raw ops-digest figures into a short, plain-English narrative (see ADR 0022).
 *
 * <p>
 * Deliberately narrower than {@code AskAnalyticsQuestionOutPort}: this is a single-shot, stateless generation call (no chat
 * memory, no tool-calling) - the figures are already known and passed in directly, so the model has nothing left to look up.
 */
public interface GenerateOpsDigestNarrativeOutPort {

    /**
     * @param ordersPlacedLastDay orders placed in the last 24 hours.
     * @param remarksClassificationSummary current remarks-triage classification counts.
     * @return a short, plain-English narrative summarizing the given figures, or a fixed fallback sentence if the narrator is
     *         disabled or unreachable - never a technical exception.
     */
    String generateNarrative(long ordersPlacedLastDay, RemarksClassificationSummary remarksClassificationSummary);

}
