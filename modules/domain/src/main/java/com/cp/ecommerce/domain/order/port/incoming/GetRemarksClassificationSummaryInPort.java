package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.RemarksClassificationSummary;

/**
 * Incoming port for reading a snapshot of remarks-triage classification counts per category.
 */
public interface GetRemarksClassificationSummaryInPort {

    /**
     * @return the current {@link RemarksClassificationSummary}.
     */
    RemarksClassificationSummary getSummary();

}
