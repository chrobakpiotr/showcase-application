package com.cp.ecommerce.adapter.mail.message;

import com.cp.ecommerce.domain.order.Order;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Strategy for creating email message.
 */
interface MessageCreationStrategy {

    /**
     * Create message.
     *
     * @param order placed order.
     * @return message ready to be sent.
     */
    MimeMessage createMessage(Order order) throws MessagingException;

}
