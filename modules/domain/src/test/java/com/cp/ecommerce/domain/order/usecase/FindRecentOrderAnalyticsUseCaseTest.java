package com.cp.ecommerce.domain.order.usecase;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.port.outgoing.FindRecentOrderAnalyticsProjectionsOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link FindRecentOrderAnalyticsUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class FindRecentOrderAnalyticsUseCaseTest {

    @Mock
    private transient FindRecentOrderAnalyticsProjectionsOutPort findRecentOrderAnalyticsProjectionsOutPort;

    @InjectMocks
    private transient FindRecentOrderAnalyticsUseCase findRecentOrderAnalyticsUseCase;

    @Test
    void shouldDelegateToOutgoingPort() {

        final List<OrderAnalyticsProjection> expected = List
                .of(new OrderAnalyticsProjection("ORDER-1", 1L, new Date(), new Date()));
        given(findRecentOrderAnalyticsProjectionsOutPort.findRecent(20)).willReturn(expected);

        final List<OrderAnalyticsProjection> result = findRecentOrderAnalyticsUseCase.findRecent(20);

        assertThat(result).isSameAs(expected);
    }

}
