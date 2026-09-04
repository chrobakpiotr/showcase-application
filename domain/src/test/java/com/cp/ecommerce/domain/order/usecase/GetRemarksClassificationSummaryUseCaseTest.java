package com.cp.ecommerce.domain.order.usecase;

import java.util.Map;

import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GetRemarksClassificationSummaryUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GetRemarksClassificationSummaryUseCaseTest {

    @Mock
    private transient GetRemarksClassificationSummaryOutPort getRemarksClassificationSummaryOutPort;

    @InjectMocks
    private transient GetRemarksClassificationSummaryUseCase getRemarksClassificationSummaryUseCase;

    @Test
    void shouldReturnSummaryFromOutPort() {

        final RemarksClassificationSummary expected = new RemarksClassificationSummary(
                Map.of(
                        RemarksTriageCategory.STANDARD,
                        10L,
                        RemarksTriageCategory.URGENT,
                        2L,
                        RemarksTriageCategory.COMPLAINT,
                        1L,
                        RemarksTriageCategory.SUSPICIOUS,
                        0L));
        when(getRemarksClassificationSummaryOutPort.getSummary()).thenReturn(expected);

        final RemarksClassificationSummary actual = getRemarksClassificationSummaryUseCase.getSummary();

        assertThat(actual).isEqualTo(expected);
        verify(getRemarksClassificationSummaryOutPort).getSummary();
    }

}
