package com.cp.ecommerce.domain.order.usecase;

import java.time.Instant;

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

        final Instant from = Instant.ofEpochMilli(0);
        final Instant to = Instant.ofEpochMilli(Instant.now().toEpochMilli());
        when(countOrderAnalyticsProjectionsOutPort.countPlacedBetween(from, to)).thenReturn(42L);

        final long actual = countOrderAnalyticsProjectionsUseCase.countPlacedBetween(from, to);

        assertThat(actual).isEqualTo(42L);
        verify(countOrderAnalyticsProjectionsOutPort).countPlacedBetween(from, to);
    }

}
