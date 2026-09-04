package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.SupportedLocale;

/**
 * Outgoing port for detecting which of the application's {@link SupportedLocale}s a customer's free-text order remarks are
 * written in, so the order-confirmation email/PDF can be rendered in that language instead of always defaulting to English (see
 * ADR 0023).
 */
public interface DetectRemarksLanguageOutPort {

    /**
     * Detects the language of the given remarks.
     *
     * @param remarks the customer's free-text order remarks, possibly {@code null} or blank.
     * @return the detected {@link SupportedLocale}, defaulting to {@link SupportedLocale#ENGLISH} for blank remarks, remarks in
     *         an unsupported language, or when detection is disabled/unavailable.
     */
    SupportedLocale detectLanguage(String remarks);

}
