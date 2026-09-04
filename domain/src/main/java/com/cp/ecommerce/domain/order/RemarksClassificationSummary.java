package com.cp.ecommerce.domain.order;

import java.util.Map;

/**
 * Point-in-time snapshot of how many best-effort remarks-triage classifications (see {@link RemarksTriageCategory}) have been
 * recorded per category, over this process's lifetime. Backed by the same Micrometer counter
 * {@code OrderPlacementSagaOrchestrator} already increments for Grafana ({@code saga.order-placement.remarks-classifications}),
 * so this is deliberately a read of an existing observability signal rather than a new, separately-persisted aggregate - see
 * ADR 0021.
 *
 * @param countsByCategory count recorded so far for every {@link RemarksTriageCategory}, including categories that have never
 *            occurred yet (0, not absent - callers should not need to null-check or default missing keys themselves).
 */
public record RemarksClassificationSummary(Map<RemarksTriageCategory, Long> countsByCategory) {

}
