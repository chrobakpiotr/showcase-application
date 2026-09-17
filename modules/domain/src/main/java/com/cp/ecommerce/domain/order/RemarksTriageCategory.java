package com.cp.ecommerce.domain.order;

/**
 * Outcome category of an AI-assisted triage of an order's free-text {@link Order#getRemarks()}, run as a best-effort saga step
 * (see {@code ClassifyOrderRemarksInPort}). Deliberately small and closed: a bounded set of categories keeps the classification
 * prompt simple enough for a small, locally-hosted model to answer reliably, and keeps the Micrometer tag cardinality this
 * produces small and predictable.
 */
public enum RemarksTriageCategory {

    /**
     * No remarks, or remarks that need no special handling. The default/fallback outcome whenever classification is skipped
     * (feature disabled, blank remarks) or fails technically.
     */
    STANDARD,

    /**
     * Remarks indicate a time-sensitive request (e.g. "needed by tomorrow") that a human should prioritize.
     */
    URGENT,

    /**
     * Remarks describe dissatisfaction with a previous order or the service itself, warranting a follow-up.
     */
    COMPLAINT,

    /**
     * Remarks contain patterns associated with abuse (e.g. inducements to ship to a different address than billed, threats, or
     * attempts to manipulate fulfillment) - surfaced to a human reviewer, never acted on automatically.
     */
    SUSPICIOUS

}
