package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.RouteOrderNotificationOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Tests for {@link RouteOrderNotificationUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class RouteOrderNotificationUseCaseTest {

    @Mock
    private transient RouteOrderNotificationOutPort routeOrderNotificationOutPort;

    @InjectMocks
    private transient RouteOrderNotificationUseCase routeOrderNotificationUseCase;

    @Test
    void shouldDelegateNotificationRouting() {

        final Order order = TestDomainObjectFactory.validOrder();

        routeOrderNotificationUseCase.routeNotification(order);

        verify(routeOrderNotificationOutPort).route(order);
    }

}
