package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.SupportedLocale;
import com.cp.ecommerce.domain.order.port.outgoing.DetectRemarksLanguageOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.SendEmailOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SendOrderConfirmationEmailUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class SendOrderConfirmationEmailUseCaseTest {

    @Mock
    private transient SendEmailOutPort sendEmailOutPort;

    @Mock
    private transient DetectRemarksLanguageOutPort detectRemarksLanguageOutPort;

    @InjectMocks
    private transient SendOrderConfirmationEmailUseCase sendOrderConfirmationEmailUseCase;

    @Test
    void shouldDetectLanguageAndDelegateConfirmationEmailSending() {

        final Order order = TestDomainObjectFactory.validOrder();
        given(detectRemarksLanguageOutPort.detectLanguage(order.getRemarks())).willReturn(SupportedLocale.POLISH);

        sendOrderConfirmationEmailUseCase.sendConfirmationEmail(order);

        verify(sendEmailOutPort).send(order, SupportedLocale.POLISH);
    }

}
