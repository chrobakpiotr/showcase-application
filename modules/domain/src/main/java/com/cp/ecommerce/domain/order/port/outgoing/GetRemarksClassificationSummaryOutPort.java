package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.RemarksClassificationSummary;

/**
 * Outgoing port for reading a snapshot of remarks-triage classification counts per category.
 */
public interface GetRemarksClassificationSummaryOutPort {

    /**
     * @return the current {@link RemarksClassificationSummary}.
     */
    RemarksClassificationSummary getSummary();

}
