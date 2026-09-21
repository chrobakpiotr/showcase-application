package com.cp.ecommerce.adapter.mail;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.adapter.mail.message.EmailMessageFactory;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.SupportedLocale;
import com.cp.ecommerce.domain.order.port.outgoing.SendEmailOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Representation of {@link SendEmailAdapter} behavior.
 */
@Slf4j
@RequiredArgsConstructor
@WebAdapter
@ConditionalOnProperty(name = "service.mail.enabled", havingValue = "true")
public class SendEmailAdapter implements SendEmailOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "sendEmail";

    private final JavaMailSender emailSender;

    private final EmailMessageFactory emailMessageFactory;

    private final ResilientExecutor resilientExecutor;

    @Override
    public void send(final Order order, final SupportedLocale locale) {

        // LocaleContextHolder is thread-local: this saga step already runs on its own dedicated virtual thread (see
        // OrderPlacementSagaOrchestrator's fan-out), so there's no risk of leaking one order's locale into another's -
        // resetting afterwards is still done for correctness/hygiene rather than relying on that implementation detail.
        // ResilientExecutor#callResilient runs retries synchronously on the calling thread, so the locale set here stays in
        // effect across every retry attempt, not just the first.
        LocaleContextHolder.setLocale(toJavaLocale(locale));
        try {
            final MimeMessage messageToBeSent = resilientExecutor
                    .callResilientOrElse(RESILIENCE_INSTANCE_NAME, () -> createAndSend(order), exception -> {
                        log.error("Error while creating and sending emails for order: {}", order.getOrderNumber());
                        if (exception instanceof MailParseException mailParseException) {
                            throw mailParseException;
                        }
                        throw new MailParseException(exception);
                    });
            log.info("Email with order request confirmation was send to: {}", recipients(messageToBeSent));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    private String recipients(final MimeMessage message) {

        try {
            return Arrays.stream(message.getAllRecipients()).map(Address::toString).collect(Collectors.joining(", "));
        } catch (final MessagingException exception) {
            throw new MailParseException(exception);
        }
    }

    private MimeMessage createAndSend(final Order order) {

        try {
            final MimeMessage messageToBeSent = emailMessageFactory.createEmailMessage(order);
            emailSender.send(messageToBeSent);
            return messageToBeSent;
        } catch (final MessagingException exception) {
            throw new MailParseException(exception);
        }
    }

    private Locale toJavaLocale(final SupportedLocale locale) {

        return switch (locale) {
        case POLISH -> Locale.of("pl");
        case ENGLISH -> Locale.ENGLISH;
        };
    }

}
