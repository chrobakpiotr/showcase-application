package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.SendEmailOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SendOrderConfirmationEmailUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class SendOrderConfirmationEmailUseCaseTest {

    @Mock
    private transient SendEmailOutPort sendEmailOutPort;

    @InjectMocks
    private transient SendOrderConfirmationEmailUseCase sendOrderConfirmationEmailUseCase;

    @Test
    void shouldDelegateConfirmationEmailSending() {

        final Order order = TestDomainObjectFactory.validOrder();

        sendOrderConfirmationEmailUseCase.sendConfirmationEmail(order);

        verify(sendEmailOutPort).send(order);
    }

}
