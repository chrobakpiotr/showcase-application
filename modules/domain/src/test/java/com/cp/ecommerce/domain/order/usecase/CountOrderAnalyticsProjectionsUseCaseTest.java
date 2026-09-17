package com.cp.ecommerce.domain.order.usecase;

import java.util.Date;

import com.cp.ecommerce.domain.order.port.outgoing.CountOrderAnalyticsProjectionsOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CountOrderAnalyticsProjectionsUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CountOrderAnalyticsProjectionsUseCaseTest {

    @Mock
    private transient CountOrderAnalyticsProjectionsOutPort countOrderAnalyticsProjectionsOutPort;

    @InjectMocks
    private transient CountOrderAnalyticsProjectionsUseCase countOrderAnalyticsProjectionsUseCase;

    @Test
    void shouldDelegateRangeAndReturnCount() {

        final Date from = new Date(0);
        final Date to = new Date();
        when(countOrderAnalyticsProjectionsOutPort.countPlacedBetween(from, to)).thenReturn(42L);

        final long actual = countOrderAnalyticsProjectionsUseCase.countPlacedBetween(from, to);

        assertThat(actual).isEqualTo(42L);
        verify(countOrderAnalyticsProjectionsOutPort).countPlacedBetween(from, to);
    }

}
