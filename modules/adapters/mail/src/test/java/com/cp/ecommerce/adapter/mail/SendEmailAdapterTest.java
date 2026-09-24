package com.cp.ecommerce.adapter.mail;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.mail.message.EmailMessageFactory;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.SupportedLocale;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_EMAIL;

class SendEmailAdapterTest {

    @Test
    void shouldSendOnceWithStableCorrelationHeaders() throws Exception {
        final Order order = OrderBuilder.mockOrder();
        final JavaMailSenderImpl sender = Mockito.spy(new JavaMailSenderImpl());
        final EmailMessageFactory factory = Mockito.mock(EmailMessageFactory.class);
        final MimeMessage message = createMimeMessage(sender);
        given(factory.createEmailMessage(any(Order.class))).willReturn(message);
        doNothing().when(sender).send(message);
        new SendEmailAdapter(sender, factory).send(order, SupportedLocale.ENGLISH);
        Mockito.verify(sender).send(message);
        assertThat(message.getHeader("Message-ID", null)).isEqualTo(SendEmailAdapter.stableMessageId(order));
        assertThat(message.getHeader(SendEmailAdapter.DISPATCH_HEADER, null)).isEqualTo(SendEmailAdapter.dispatchId(order));
    }

    @Test
    void shouldPropagateTransportFailureForDurableRetry() throws Exception {
        final Order order = OrderBuilder.mockOrder();
        final JavaMailSenderImpl sender = Mockito.spy(new JavaMailSenderImpl());
        final EmailMessageFactory factory = Mockito.mock(EmailMessageFactory.class);
        final MimeMessage message = createMimeMessage(sender);
        given(factory.createEmailMessage(order)).willReturn(message);
        doThrow(new MailSendException("smtp outcome unknown")).when(sender).send(message);
        assertThatThrownBy(() -> new SendEmailAdapter(sender, factory).send(order, SupportedLocale.ENGLISH))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("smtp outcome unknown");
    }

    @Test
    void shouldWrapMessageCreationFailure() throws Exception {
        final Order order = OrderBuilder.mockOrder();
        final JavaMailSenderImpl sender = Mockito.mock(JavaMailSenderImpl.class);
        final EmailMessageFactory factory = Mockito.mock(EmailMessageFactory.class);
        given(factory.createEmailMessage(order)).willThrow(new MessagingException("cannot create"));
        assertThatThrownBy(() -> new SendEmailAdapter(sender, factory).send(order, SupportedLocale.POLISH))
                .isInstanceOf(MailParseException.class)
                .hasCauseInstanceOf(MessagingException.class);
    }

    @Test
    void shouldWrapRecipientReadFailureAfterSend() throws Exception {
        final Order order = OrderBuilder.mockOrder();
        final JavaMailSenderImpl sender = Mockito.mock(JavaMailSenderImpl.class);
        final EmailMessageFactory factory = Mockito.mock(EmailMessageFactory.class);
        final MimeMessage message = Mockito.mock(MimeMessage.class);
        given(factory.createEmailMessage(order)).willReturn(message);
        doNothing().when(sender).send(message);
        given(message.getAllRecipients()).willThrow(new MessagingException("cannot read recipients"));
        assertThatThrownBy(() -> new SendEmailAdapter(sender, factory).send(order, SupportedLocale.ENGLISH))
                .isInstanceOf(MailParseException.class)
                .hasCauseInstanceOf(MessagingException.class);
    }

    private static MimeMessage createMimeMessage(final JavaMailSenderImpl sender) throws MessagingException {
        final MimeMessage message = sender.createMimeMessage();
        final MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(TEST_EMAIL);
        helper.setTo(TEST_EMAIL);
        helper.setSubject("subject");
        helper.setText("text");
        return message;
    }
}
