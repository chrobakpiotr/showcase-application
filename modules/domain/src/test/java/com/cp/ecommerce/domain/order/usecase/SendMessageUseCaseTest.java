package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.SendOrderMessageOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SendMessageUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class SendMessageUseCaseTest {

    @Mock
    private transient SendOrderMessageOutPort sendOrderMessageOutPort;

    @InjectMocks
    private transient SendMessageUseCase sendMessageUseCase;

    @Test
    void shouldDelegateSendingMessage() {

        final Order order = TestDomainObjectFactory.validOrder();

        sendMessageUseCase.sendMessage(order);

        verify(sendOrderMessageOutPort).send(order);
    }

}
