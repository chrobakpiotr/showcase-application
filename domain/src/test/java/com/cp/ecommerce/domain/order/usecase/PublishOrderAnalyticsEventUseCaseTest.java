package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.PublishOrderAnalyticsEventOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Tests for {@link PublishOrderAnalyticsEventUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class PublishOrderAnalyticsEventUseCaseTest {

    @Mock
    private transient PublishOrderAnalyticsEventOutPort publishOrderAnalyticsEventOutPort;

    @InjectMocks
    private transient PublishOrderAnalyticsEventUseCase publishOrderAnalyticsEventUseCase;

    @Test
    void shouldDelegateAnalyticsEventPublishing() {

        final Order order = TestDomainObjectFactory.validOrder();

        publishOrderAnalyticsEventUseCase.publishAnalyticsEvent(order);

        verify(publishOrderAnalyticsEventOutPort).publish(order);
    }

}
