package com.cp.ecommerce.adapter.web.order.usecase;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.domain.order.port.outgoing.SendOrderMessageOutPort;
import com.cp.ecommerce.domain.order.usecase.SendMessageUseCase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link SendMessageUseCaseTest}.
 */
@ExtendWith(MockitoExtension.class)
class SendMessageUseCaseTest {

    @Mock
    private transient SendOrderMessageOutPort sendOrderMessageOutPort;

    @InjectMocks
    private transient SendMessageUseCase sendMessageUseCase;

    @Test
    void shouldSendMessage() {

        sendMessageUseCase.sendMessage(OrderBuilder.mockOrder());
        verify(sendOrderMessageOutPort, atLeastOnce()).send(any());
    }

}
