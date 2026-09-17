package com.cp.ecommerce.adapter.ai.analytics;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.port.incoming.CountOrderAnalyticsProjectionsInPort;
import com.cp.ecommerce.domain.order.port.incoming.GetRemarksClassificationSummaryInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderAnalyticsTool}.
 */
@ExtendWith(MockitoExtension.class)
class OrderAnalyticsToolTest {

    @Mock
    transient CountOrderAnalyticsProjectionsInPort countOrderAnalyticsProjectionsInPort;

    @Mock
    transient GetRemarksClassificationSummaryInPort getRemarksClassificationSummaryInPort;

    private static final String SINGLE_DAY = "2024-01-01";

    @Test
    void shouldCountOrdersPlacedBetweenTheGivenInclusiveDateRange() {

        when(countOrderAnalyticsProjectionsInPort.countPlacedBetween(any(Date.class), any(Date.class))).thenReturn(7L);

        final String result = newTool().countOrdersPlacedBetween(SINGLE_DAY, "2024-01-31");

        assertThat(result).contains("7").contains(SINGLE_DAY).contains("2024-01-31");
    }

    @Test
    void shouldPassAnInclusiveWholeDayRangeToTheOutPort() {

        when(countOrderAnalyticsProjectionsInPort.countPlacedBetween(any(Date.class), any(Date.class))).thenReturn(0L);
        final ArgumentCaptor<Date> fromCaptor = ArgumentCaptor.forClass(Date.class);
        final ArgumentCaptor<Date> toCaptor = ArgumentCaptor.forClass(Date.class);

        newTool().countOrdersPlacedBetween(SINGLE_DAY, SINGLE_DAY);

        verify(countOrderAnalyticsProjectionsInPort).countPlacedBetween(fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(Date.from(Instant.parse("2024-01-01T00:00:00.000Z")));
        assertThat(toCaptor.getValue()).isEqualTo(Date.from(Instant.parse("2024-01-01T23:59:59.999Z")));
    }

    @Test
    void shouldReportAnUnparseableDateInsteadOfThrowing() {

        final String result = newTool().countOrdersPlacedBetween("not-a-date", "2024-01-31");

        assertThat(result).contains("Could not parse");
    }

    @Test
    void shouldSummariseRemarksClassificationCountsPerCategory() {

        when(getRemarksClassificationSummaryInPort.getSummary()).thenReturn(
                new RemarksClassificationSummary(
                        Map.of(
                                RemarksTriageCategory.STANDARD,
                                10L,
                                RemarksTriageCategory.URGENT,
                                2L,
                                RemarksTriageCategory.COMPLAINT,
                                0L,
                                RemarksTriageCategory.SUSPICIOUS,
                                1L)));

        final String result = newTool().remarksClassificationBreakdown();

        assertThat(result).contains("STANDARD: 10").contains("URGENT: 2").contains("COMPLAINT: 0").contains("SUSPICIOUS: 1");
    }

    private OrderAnalyticsTool newTool() {

        return new OrderAnalyticsTool(countOrderAnalyticsProjectionsInPort, getRemarksClassificationSummaryInPort);
    }

}
