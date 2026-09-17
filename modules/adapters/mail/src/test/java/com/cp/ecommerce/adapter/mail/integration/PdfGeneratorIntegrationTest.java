package com.cp.ecommerce.adapter.mail.integration;

import java.util.Date;
import java.util.Map;

import com.cp.ecommerce.adapter.MailTestConfiguration;
import com.cp.ecommerce.adapter.common.utils.CustomerBuilder;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.mail.freemarker.FreeMarkerTemplateProcessor;
import com.cp.ecommerce.adapter.mail.pdf.PdfGenerator;
import com.cp.ecommerce.adapter.mail.pdf.utils.ClasspathResourceResolver;
import com.cp.ecommerce.domain.order.Order;
import com.google.common.collect.ImmutableMap;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

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

    private byte[] generatePdf(final Order request) {

        return new PdfGenerator(fopXml, resourceResolver)
                .generatePdf(freeMarkerTemplateProcessor.processTemplate(TEMPLATE_FILE_NAME, TEST_FILE_PATH, request));
    }

    private Order createOrderWithoutOrderNumber() {

        return Order.builder()
                .orderNumber(null)
                .remarks(TEST_REMARKS)
                .created(new Date())
                .customer(CustomerBuilder.mockCustomer())
                .build();
    }

}
