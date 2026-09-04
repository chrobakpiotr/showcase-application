package com.cp.ecommerce.adapter.mail.integration;

import java.util.Locale;

import com.cp.ecommerce.adapter.MailTestConfiguration;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.mail.SendEmailAdapter;
import com.cp.ecommerce.domain.order.SupportedLocale;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;

import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests cases for {@link SendEmailAdapter}. The locale is passed per call (not derived from the JVM-wide default),
 * proving each order is rendered independently of any other order's language - the actual bug this feature fixes: previously
 * emails were always rendered in whatever locale the JVM happened to default to, with no per-order control at all.
 */
@SpringBootTest
@Import(MailTestConfiguration.class)
public class EmailIntegrationTest {

    @RegisterExtension
    private static final GreenMailExtension MAIL_SERVER = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("test", "secret"))
            .withPerMethodLifecycle(false);

    @Autowired
    private transient SendEmailAdapter sendEmailAdapter;

    @BeforeEach
    void purgeMailbox() throws Exception {

        // The mailbox lifecycle is shared across the whole class (withPerMethodLifecycle(false)), so each test purges it
        // upfront rather than relying on message-array indices that would otherwise depend on test execution order.
        MAIL_SERVER.purgeEmailFromAllMailboxes();
    }

    @Test
    void shouldSendEmailWithCorrectPayloadInPolish() throws Exception {

        this.sendEmailAdapter.send(OrderBuilder.mockOrder(), SupportedLocale.POLISH);

        final MimeMessage receivedMessage = MAIL_SERVER.getReceivedMessages()[0];
        assertThat(receivedMessage).isNotNull();
        assertThat(GreenMailUtil.getBody(receivedMessage)).contains("Pozdrawiamy");
        assertEquals(1, receivedMessage.getAllRecipients().length);
        assertEquals("test@test.com", receivedMessage.getAllRecipients()[0].toString());
    }

    @Test
    void shouldSendEmailWithCorrectPayloadInEnglish() throws Exception {

        this.sendEmailAdapter.send(OrderBuilder.mockOrder(), SupportedLocale.ENGLISH);

        final MimeMessage receivedMessage = MAIL_SERVER.getReceivedMessages()[0];
        assertThat(receivedMessage).isNotNull();
        assertThat(GreenMailUtil.getBody(receivedMessage)).contains("Best regards");
    }

    @Test
    void shouldResetLocaleContextAfterSendingSoItDoesNotLeakIntoUnrelatedCode() throws Exception {

        this.sendEmailAdapter.send(OrderBuilder.mockOrder(), SupportedLocale.POLISH);

        assertThat(LocaleContextHolder.getLocale()).isNotEqualTo(Locale.of("pl"));
    }

}
