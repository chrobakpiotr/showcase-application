package com.cp.ecommerce.adapter.ai.order;

import java.util.Locale;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.SupportedLocale;
import com.cp.ecommerce.domain.order.port.outgoing.DetectRemarksLanguageOutPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link DetectRemarksLanguageOutPort} backed by the same locally-hosted Ollama model as the other AI
 * features (see ADR 0023) - the fifth and simplest distinct shape yet: a single-word classification of free text into one of
 * exactly two labels, with no chat memory or tool-calling.
 *
 * <p>
 * Blank remarks are a trivial fast-path: no model call is made at all, mirroring {@code OrderRemarksClassifierAdapter}'s own
 * blank-remarks short-circuit. Unlike that adapter though, a failed or unreachable model call here is <strong>not</strong> left
 * to propagate: this is called synchronously from within the "send confirmation email" saga step, and letting a
 * language-detection failure abort the whole email would throw away a perfectly good English rendering for no reason - so any
 * failure (technical or an unparseable/unrecognised response) defaults to {@link SupportedLocale#ENGLISH}, the same safe
 * default used for blank remarks.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "true")
public class RemarksLanguageDetectorAdapter implements DetectRemarksLanguageOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "detectRemarksLanguage";

    private static final String SYSTEM_PROMPT = """
            You detect the language of a short piece of customer-submitted free text for an e-commerce platform. Respond with
            exactly one word identifying the language: either ENGLISH or POLISH. If the text is empty, ambiguous, or written \
            in any other language, respond with ENGLISH. Never respond with anything other than ENGLISH or POLISH.""";

    private final ChatClient chatClient;

    private final ResilientExecutor resilientExecutor;

    public RemarksLanguageDetectorAdapter(
            final ChatClient.Builder chatClientBuilder,
            final ResilientExecutor resilientExecutor) {

        this.chatClient = chatClientBuilder.build();
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public SupportedLocale detectLanguage(final String remarks) {

        if (remarks == null || remarks.isBlank()) {
            return SupportedLocale.ENGLISH;
        }

        try {
            return resilientExecutor.callResilient(RESILIENCE_INSTANCE_NAME, () -> detectWithModel(remarks));
        } catch (Exception exception) {
            log.warn("Could not detect remarks language via Ollama, defaulting to ENGLISH.", exception);
            return SupportedLocale.ENGLISH;
        }
    }

    private SupportedLocale detectWithModel(final String remarks) {

        final LanguageDetectionResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(remarks)
                .call()
                .entity(LanguageDetectionResponse.class);

        return parseLocale(response.language());
    }

    private SupportedLocale parseLocale(final String rawLanguage) {

        try {
            return SupportedLocale.valueOf(rawLanguage.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            log.warn("Model returned an unrecognised language '{}', defaulting to ENGLISH.", rawLanguage);
            return SupportedLocale.ENGLISH;
        }
    }

}
