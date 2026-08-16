package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;
import com.cp.ecommerce.domain.order.port.outgoing.SendEmailOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for sending the order confirmation email as an asynchronous, retried saga step.
 */
@RequiredArgsConstructor
@UseCase
public class SendOrderConfirmationEmailUseCase implements SendOrderConfirmationEmailInPort {

    private final SendEmailOutPort sendEmailOutPort;

    @Override
    public void sendConfirmationEmail(final Order order) {

        sendEmailOutPort.send(order);
    }

}
