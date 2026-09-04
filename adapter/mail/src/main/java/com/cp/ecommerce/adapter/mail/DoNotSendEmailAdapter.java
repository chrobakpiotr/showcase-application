package com.cp.ecommerce.adapter.mail;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.SupportedLocale;
import com.cp.ecommerce.domain.order.port.outgoing.SendEmailOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Representation of Email Service's behavior.
 */
@Slf4j
@RequiredArgsConstructor
@WebAdapter
@ConditionalOnProperty(name = "service.mail.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotSendEmailAdapter implements SendEmailOutPort {

    @Override
    public void send(final Order order, final SupportedLocale locale) {

        log.info("Mailing disabled for order, email will not be sent.");
    }

}
