package com.cp.ecommerce.domain.order.usecase;

import java.time.Instant;

import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderAnalyticsProjectionOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

/**
 * Tests for {@link RecordOrderAnalyticsProjectionUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class RecordOrderAnalyticsProjectionUseCaseTest {

    @Mock
    private transient SaveOrderAnalyticsProjectionOutPort saveOrderAnalyticsProjectionOutPort;

    @InjectMocks
    private transient RecordOrderAnalyticsProjectionUseCase recordOrderAnalyticsProjectionUseCase;

    @Test
    void shouldDelegateToOutgoingPort() {

        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                "ORDER-1",
                1L,
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                Instant.ofEpochMilli(Instant.now().toEpochMilli()));

        recordOrderAnalyticsProjectionUseCase.recordProjection(projection);

        then(saveOrderAnalyticsProjectionOutPort).should().save(projection);
    }

}
