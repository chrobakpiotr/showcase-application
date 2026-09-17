package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.port.outgoing.CancelOrderOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Tests for {@link CancelOrderUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CancelOrderUseCaseTest {

    @Mock
    private transient CancelOrderOutPort cancelOrderOutPort;

    @InjectMocks
    private transient CancelOrderUseCase cancelOrderUseCase;

    @Test
    void shouldDelegateOrderCancellation() {

        cancelOrderUseCase.cancelOrder(TestDomainObjectFactory.TEST_ORDER_NUMBER);

        verify(cancelOrderOutPort).cancel(TestDomainObjectFactory.TEST_ORDER_NUMBER);
    }

}
