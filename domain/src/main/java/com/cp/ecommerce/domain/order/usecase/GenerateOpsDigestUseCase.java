package com.cp.ecommerce.domain.order.usecase;

import java.time.Duration;
import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.port.incoming.CountOrderAnalyticsProjectionsInPort;
import com.cp.ecommerce.domain.order.port.incoming.GenerateOpsDigestInPort;
import com.cp.ecommerce.domain.order.port.incoming.GetRemarksClassificationSummaryInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GenerateOpsDigestNarrativeOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOpsDigestOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case generating a fresh {@link OpsDigest}, reusing the two existing analytics query use cases (see ADR 0021) as its data
 * source rather than any new, separately-maintained aggregate.
 */
@UseCase
@RequiredArgsConstructor
public class GenerateOpsDigestUseCase implements GenerateOpsDigestInPort {

    private static final Duration LOOKBACK_WINDOW = Duration.ofDays(1);

    private final CountOrderAnalyticsProjectionsInPort countOrderAnalyticsProjectionsInPort;

    private final GetRemarksClassificationSummaryInPort getRemarksClassificationSummaryInPort;

    private final GenerateOpsDigestNarrativeOutPort generateOpsDigestNarrativeOutPort;

    private final SaveOpsDigestOutPort saveOpsDigestOutPort;

    @Override
    public OpsDigest generateDigest() {

        final Date now = new Date();
        final Date lookbackStart = Date.from(now.toInstant().minus(LOOKBACK_WINDOW));
        final long ordersPlacedLastDay = countOrderAnalyticsProjectionsInPort.countPlacedBetween(lookbackStart, now);
        final RemarksClassificationSummary remarksClassificationSummary = getRemarksClassificationSummaryInPort.getSummary();
        final String narrative = generateOpsDigestNarrativeOutPort
                .generateNarrative(ordersPlacedLastDay, remarksClassificationSummary);

        final OpsDigest opsDigest = OpsDigest.builder()
                .generatedDate(now)
                .ordersPlacedLastDay(ordersPlacedLastDay)
                .remarksClassificationSummary(remarksClassificationSummary)
                .narrative(narrative)
                .build();
        saveOpsDigestOutPort.save(opsDigest);
        return opsDigest;
    }

}
