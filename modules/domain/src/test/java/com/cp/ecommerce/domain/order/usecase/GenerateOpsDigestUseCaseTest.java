package com.cp.ecommerce.domain.order.usecase;

import java.time.Duration;
import java.util.Date;
import java.util.Map;

import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.port.incoming.CountOrderAnalyticsProjectionsInPort;
import com.cp.ecommerce.domain.order.port.incoming.GetRemarksClassificationSummaryInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GenerateOpsDigestNarrativeOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOpsDigestOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GenerateOpsDigestUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GenerateOpsDigestUseCaseTest {

    private static final String NARRATIVE = "Business as usual: 7 orders placed, mostly standard remarks.";

    @Mock
    private transient CountOrderAnalyticsProjectionsInPort countOrderAnalyticsProjectionsInPort;

    @Mock
    private transient GetRemarksClassificationSummaryInPort getRemarksClassificationSummaryInPort;

    @Mock
    private transient GenerateOpsDigestNarrativeOutPort generateOpsDigestNarrativeOutPort;

    @Mock
    private transient SaveOpsDigestOutPort saveOpsDigestOutPort;

    @Captor
    private transient ArgumentCaptor<OpsDigest> opsDigestCaptor;

    @Test
    void shouldGenerateAndPersistDigestFromCurrentFigures() {

        final RemarksClassificationSummary summary = new RemarksClassificationSummary(
                Map.of(
                        RemarksTriageCategory.STANDARD,
                        6L,
                        RemarksTriageCategory.URGENT,
                        1L,
                        RemarksTriageCategory.COMPLAINT,
                        0L,
                        RemarksTriageCategory.SUSPICIOUS,
                        0L));
        when(countOrderAnalyticsProjectionsInPort.countPlacedBetween(any(Date.class), any(Date.class))).thenReturn(7L);
        when(getRemarksClassificationSummaryInPort.getSummary()).thenReturn(summary);
        when(generateOpsDigestNarrativeOutPort.generateNarrative(eq(7L), eq(summary))).thenReturn(NARRATIVE);

        final GenerateOpsDigestUseCase useCase = new GenerateOpsDigestUseCase(
                countOrderAnalyticsProjectionsInPort,
                getRemarksClassificationSummaryInPort,
                generateOpsDigestNarrativeOutPort,
                saveOpsDigestOutPort);

        final OpsDigest actual = useCase.generateDigest();

        assertThat(actual.getOrdersPlacedLastDay()).isEqualTo(7L);
        assertThat(actual.getRemarksClassificationSummary()).isEqualTo(summary);
        assertThat(actual.getNarrative()).isEqualTo(NARRATIVE);
        assertThat(actual.getGeneratedDate()).isNotNull();

        verify(saveOpsDigestOutPort).save(opsDigestCaptor.capture());
        assertThat(opsDigestCaptor.getValue()).isEqualTo(actual);
    }

    @Test
    void shouldQueryLast24HoursOnly() {

        when(countOrderAnalyticsProjectionsInPort.countPlacedBetween(any(Date.class), any(Date.class))).thenReturn(0L);
        when(getRemarksClassificationSummaryInPort.getSummary()).thenReturn(new RemarksClassificationSummary(Map.of()));
        when(generateOpsDigestNarrativeOutPort.generateNarrative(anyLong(), any())).thenReturn(NARRATIVE);

        final GenerateOpsDigestUseCase useCase = new GenerateOpsDigestUseCase(
                countOrderAnalyticsProjectionsInPort,
                getRemarksClassificationSummaryInPort,
                generateOpsDigestNarrativeOutPort,
                saveOpsDigestOutPort);

        useCase.generateDigest();

        final ArgumentCaptor<Date> fromCaptor = ArgumentCaptor.forClass(Date.class);
        final ArgumentCaptor<Date> toCaptor = ArgumentCaptor.forClass(Date.class);
        verify(countOrderAnalyticsProjectionsInPort).countPlacedBetween(fromCaptor.capture(), toCaptor.capture());

        final long windowMillis = toCaptor.getValue().getTime() - fromCaptor.getValue().getTime();
        assertThat(windowMillis).isEqualTo(Duration.ofDays(1).toMillis());
    }

}
