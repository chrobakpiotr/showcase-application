package com.cp.ecommerce.adapter.mail.message;

import com.cp.ecommerce.domain.order.Order;

import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

/**
 * Factory to create email message.
 */
@Component
@RequiredArgsConstructor
public class EmailMessageFactory {

    private final CustomerMessageCreator customerMessageCreator;

    public MimeMessage createEmailMessage(final Order order) throws MessagingException {

        return customerMessageCreator.createMessage(order);
    }

}
