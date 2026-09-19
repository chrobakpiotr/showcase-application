package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.order.port.outgoing.SendOrderMessageOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for sending message to queue.
 */
@RequiredArgsConstructor
@UseCase
public class SendMessageUseCase implements SendMessageInPort {

    private final SendOrderMessageOutPort sendOrderMessageOutPort;

    @Override
    public void sendMessage(final Order order) {

        sendOrderMessageOutPort.send(order);
    }
}
