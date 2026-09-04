package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.port.incoming.GetRemarksClassificationSummaryInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for reading a snapshot of remarks-triage classification counts per category.
 */
@UseCase
@RequiredArgsConstructor
public class GetRemarksClassificationSummaryUseCase implements GetRemarksClassificationSummaryInPort {

    private final GetRemarksClassificationSummaryOutPort getRemarksClassificationSummaryOutPort;

    @Override
    public RemarksClassificationSummary getSummary() {

        return getRemarksClassificationSummaryOutPort.getSummary();
    }

}
