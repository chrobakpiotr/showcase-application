package com.cp.ecommerce.adapter.mail.message;

import com.cp.ecommerce.adapter.MailTestConfiguration;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.mail.pdf.utils.ClasspathResourceResolver;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CustomerMessageCreator} tests.
 */
@SpringBootTest
@Import(MailTestConfiguration.class)
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
class CustomerMessageCreatorTest {

    @Autowired
    transient MessageSource messageSource;

    @Autowired
    transient ClasspathResourceResolver resourceResolver;

    @Autowired
    private transient CustomerMessageCreator customerMessageCreator;

    @Test
    void shouldNotPrepareRecipient() {

        assertEquals("test@test.com", customerMessageCreator.prepareRecipient(OrderBuilder.mockOrder()));

    }

    @Test
    void shouldThrowExceptionWhenOrderIsEmptyWhenCreateSubject() {

        assertThrows(NullPointerException.class, () -> customerMessageCreator.createSubject(null));
    }

    @Test
    void shouldCallGeneratePdfForCustomer() {

        assertTrue(customerMessageCreator.generatePdfAttachment(OrderBuilder.mockOrder()).isPresent());
    }

}
