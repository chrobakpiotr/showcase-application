package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.SupportedLocale;

/**
 * Send email outgoing port.
 */
public interface SendEmailOutPort {

    /**
     * Sending email.
     *
     * @param order placed order.
     * @param locale the locale to render the email/PDF in (see ADR 0023), as detected by {@link DetectRemarksLanguageOutPort}.
     */
    void send(final Order order, final SupportedLocale locale);

}
