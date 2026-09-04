package com.cp.ecommerce.adapter.ai.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.SupportedLocale;
import com.cp.ecommerce.domain.order.port.outgoing.DetectRemarksLanguageOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link DetectRemarksLanguageOutPort} with AI-assisted language detection disabled (the default: no Ollama
 * instance is assumed to be running). Always renders the confirmation email/PDF in {@link SupportedLocale#ENGLISH}.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotDetectRemarksLanguageAdapter implements DetectRemarksLanguageOutPort {

    @Override
    public SupportedLocale detectLanguage(final String remarks) {

        log.debug("AI remarks-language detection disabled, defaulting to ENGLISH.");
        return SupportedLocale.ENGLISH;
    }

}
