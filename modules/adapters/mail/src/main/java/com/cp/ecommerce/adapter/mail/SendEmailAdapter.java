package com.cp.ecommerce.adapter.mail;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.mail.message.EmailMessageFactory;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.SupportedLocale;
import com.cp.ecommerce.domain.order.port.outgoing.SendEmailOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@WebAdapter
@ConditionalOnProperty(name = "service.mail.enabled", havingValue = "true")
public class SendEmailAdapter implements SendEmailOutPort {

    static final String DISPATCH_HEADER = "X-Showcase-Dispatch-Id";
    private final JavaMailSender emailSender;
    private final EmailMessageFactory emailMessageFactory;

    @Override
    public void send(final Order order, final SupportedLocale locale) {
        LocaleContextHolder.setLocale(toJavaLocale(locale));
        try {
            createAndSend(order);
            // Recipients are personal data, so the log line carries only the dispatch correlation id.
            log.info("Email sent (dispatchId={})", dispatchId(order));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    private void createAndSend(final Order order) {
        try {
            final MimeMessage message = emailMessageFactory.createEmailMessage(order);
            message.setHeader("Message-ID", stableMessageId(order));
            message.setHeader(DISPATCH_HEADER, dispatchId(order));
            emailSender.send(message);
        } catch (final MessagingException exception) {
            throw new MailParseException(exception);
        }
    }

    static String dispatchId(final Order order) {
        return "ORDER-CONFIRMATION:" + order.getOrderNumber();
    }

    static String stableMessageId(final Order order) {
        final UUID id = UUID.nameUUIDFromBytes(dispatchId(order).getBytes(StandardCharsets.UTF_8));
        return "<" + id + "@showcase.local>";
    }

    private Locale toJavaLocale(final SupportedLocale locale) {
        return switch (locale) {
        case POLISH -> Locale.of("pl");
        case ENGLISH -> Locale.ENGLISH;
        };
    }
}
