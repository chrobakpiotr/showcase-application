package com.cp.ecommerce.adapter.mail.message;

import com.cp.ecommerce.adapter.MailTestConfiguration;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import jakarta.mail.MessagingException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test of {@link EmailMessageFactory}.
 */
@SpringBootTest
@Import(MailTestConfiguration.class)
class EmailMessageFactoryTest {

    @Autowired
    private transient EmailMessageFactory emailMessageFactory;

    @Test
    void shouldCreateCustomerMessage() throws MessagingException {

        assertThat(emailMessageFactory.createEmailMessage(OrderBuilder.mockOrder())).isNotNull();
    }

}
