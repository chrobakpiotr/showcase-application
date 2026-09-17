package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.SupportedLocale;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;
import com.cp.ecommerce.domain.order.port.outgoing.DetectRemarksLanguageOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.SendEmailOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for sending the order confirmation email as an asynchronous, retried saga step.
 *
 * <p>
 * Detects the customer's language from their free-text remarks (see ADR 0023) before delegating to {@link SendEmailOutPort}, so
 * the confirmation email/PDF is rendered in that language instead of always defaulting to English.
 */
@RequiredArgsConstructor
@UseCase
public class SendOrderConfirmationEmailUseCase implements SendOrderConfirmationEmailInPort {

    private final SendEmailOutPort sendEmailOutPort;

    private final DetectRemarksLanguageOutPort detectRemarksLanguageOutPort;

    @Override
    public void sendConfirmationEmail(final Order order) {

        final SupportedLocale locale = detectRemarksLanguageOutPort.detectLanguage(order.getRemarks());
        sendEmailOutPort.send(order, locale);
    }

}
