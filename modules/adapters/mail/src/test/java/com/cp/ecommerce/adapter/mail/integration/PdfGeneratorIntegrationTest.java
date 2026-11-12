package com.cp.ecommerce.adapter.mail.integration;

import java.time.Instant;
import java.util.Map;

import com.cp.ecommerce.adapter.MailTestConfiguration;
import com.cp.ecommerce.adapter.common.utils.CustomerBuilder;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.mail.freemarker.FreeMarkerTemplateProcessor;
import com.cp.ecommerce.adapter.mail.pdf.PdfGenerator;
import com.cp.ecommerce.adapter.mail.pdf.utils.ClasspathResourceResolver;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.order.Order;
import com.google.common.collect.ImmutableMap;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_EMAIL;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_PHONE_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_REMARKS;

/**
 * Integration tests cases for {@link PdfGenerator} and {@link FreeMarkerTemplateProcessor} classes - representing the whole
 * process of FreeMarker template processing.
 */
@SpringBootTest
@Import(MailTestConfiguration.class)
public class PdfGeneratorIntegrationTest {

    private static final String TEMPLATE_FILE_NAME = "order-confirmation-attachment.fo.ftl";

    private static final String TEST_TEMPLATE_FILE_NAME = "pdf-template.fo.ftl";

    private static final String TEST_FILE_PATH = "pdf";

    private static final String EMAIL_TEMPLATE_FILE_NAME = "order-confirmation.ftl.txt";

    private static final String EMAIL_PATH = "email";

    private static final String INJECTED_NAME = "Smith & Co <fo:block>x</fo:block>";

    private static final String INJECTED_REMARKS = "Ship <fo:basic-link external-destination=\"url('x')\">here</fo:basic-link>";

    @Autowired
    private transient FreeMarkerTemplateProcessor freeMarkerTemplateProcessor;

    @Autowired
    private transient ClasspathResourceResolver resourceResolver;

    @Value("classpath:/fop.xml")
    private transient Resource fopXml;

    @Test
    public void shouldGeneratePdfOutOfEmptyEnglishOrderRequest() {

        final byte[] actual = generatePdf(OrderBuilder.mockOrder());
        assertThat(actual).isNotNull();
    }

    @Test
    public void shouldGeneratePdfOutOfEmptyOrderNumber() {

        final byte[] actual = generatePdf(createOrderWithoutOrderNumber());
        assertThat(actual).isNotNull();
    }

    @Test
    public void shouldGeneratePdfOutOfSimpleMap() {

        final Map<String, String> model = ImmutableMap.of("message", "PDF Generation");
        final byte[] actual = new PdfGenerator(fopXml, resourceResolver)
                .generatePdf(freeMarkerTemplateProcessor.processTemplate(TEST_TEMPLATE_FILE_NAME, TEST_FILE_PATH, model));
        assertThat(actual).isNotNull();
    }

    @Test
    public void shouldEscapeMarkupInCustomerDataOfThePdf() {

        final Order order = orderWithMarkup(INJECTED_REMARKS);

        final String document = freeMarkerTemplateProcessor.processTemplate(TEMPLATE_FILE_NAME, TEST_FILE_PATH, order);

        assertThat(document).contains("Smith &amp; Co &lt;fo:block&gt;", "&lt;fo:basic-link external-destination=")
                .doesNotContain("<fo:basic-link", "Smith & Co");
        assertThat(new PdfGenerator(fopXml, resourceResolver).generatePdf(document)).isNotEmpty();
    }

    @Test
    public void shouldEscapeMarkupInCustomerDataOfTheEmail() {

        final Order order = orderWithMarkup(null);

        final String body = freeMarkerTemplateProcessor.processTemplate(EMAIL_TEMPLATE_FILE_NAME, EMAIL_PATH, order);

        assertThat(body).contains("Smith &amp; Co &lt;fo:block&gt;").doesNotContain("<fo:block>");
    }

    @Test
    public void shouldGeneratePdfWithoutRemarks() {

        assertThat(generatePdf(orderWithMarkup(null))).isNotEmpty();
    }

    private Order orderWithMarkup(final String remarks) {

        final Contact contact = Contact.builder().fullName(INJECTED_NAME).email(TEST_EMAIL).phone(TEST_PHONE_NUMBER).build();
        return Order.builder()
                .orderNumber(TEST_ORDER_NUMBER)
                .remarks(remarks)
                .created(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .customer(Customer.builder().contact(contact).address(CustomerBuilder.mockAddress()).build())
                .build();
    }

    private byte[] generatePdf(final Order request) {

        return new PdfGenerator(fopXml, resourceResolver)
                .generatePdf(freeMarkerTemplateProcessor.processTemplate(TEMPLATE_FILE_NAME, TEST_FILE_PATH, request));
    }

    private Order createOrderWithoutOrderNumber() {

        return Order.builder()
                .orderNumber(null)
                .remarks(TEST_REMARKS)
                .created(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .customer(CustomerBuilder.mockCustomer())
                .build();
    }

}
