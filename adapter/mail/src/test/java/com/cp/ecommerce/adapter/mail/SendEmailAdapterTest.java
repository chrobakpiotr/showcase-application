package com.cp.ecommerce.adapter.mail;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.mail.message.EmailMessageFactory;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.SupportedLocale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_EMAIL;

/**
 * Test class for {@link SendEmailAdapter}.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class SendEmailAdapterTest {

    @Value("${spring.mail.default-encoding:}")
    private transient String defaultEmailEncoding;

    @Mock
    private transient EmailMessageFactory emailMessageFactory;

    @Spy
    private transient JavaMailSenderImpl emailSender;

    @Mock
    private transient ResilientExecutor resilientExecutor;

    @InjectMocks
    private transient SendEmailAdapter sendEmailAdapter;

    @Test
    void shouldSendEmailToCustomer() throws Exception {

        final Order order = OrderBuilder.mockOrder();
        final MimeMessage message = createMimeMessage();
        given(emailMessageFactory.createEmailMessage(any(Order.class))).willReturn(message);
        doNothing().when(emailSender).send(any(MimeMessage.class));
        runResilientActionEagerly();

        sendEmailAdapter.send(order, SupportedLocale.ENGLISH);

        verify(emailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void shouldThrowExceptionWhileSendingEmail() throws Exception {

        final Order order = OrderBuilder.mockOrder();
        runResilientActionEagerly();

        assertThrows(MailParseException.class, () -> sendEmailAdapter.send(order, SupportedLocale.ENGLISH));
        verify(emailSender, never()).send(any(MimeMessage.class));
    }

    @SuppressWarnings("unchecked")
    private void runResilientActionEagerly() throws Exception {

        doAnswer(invocation -> {
            final java.util.concurrent.Callable<Object> action = invocation.getArgument(1);
            return action.call();
        }).when(resilientExecutor).callResilient(anyString(), any());
    }

    private MimeMessage createMimeMessage() throws MessagingException {

        final MimeMessage message = emailSender.createMimeMessage();
        final MimeMessageHelper helper = new MimeMessageHelper(message, true, defaultEmailEncoding);
        helper.setFrom(TEST_EMAIL);
        helper.setTo(TEST_EMAIL);
        helper.setSubject("some subject");
        helper.setText("some text");
        return message;
    }

}
