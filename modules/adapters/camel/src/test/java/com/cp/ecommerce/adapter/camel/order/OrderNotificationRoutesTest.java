package com.cp.ecommerce.adapter.camel.order;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import com.cp.ecommerce.adapter.camel.configuration.CamelProperties;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.order.Order;
import com.google.gson.Gson;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cp.ecommerce.adapter.camel.order.OrderNotificationRoutes.ORDER_NOTIFICATION_ENDPOINT;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link OrderNotificationRoutes}, exercising the wire-tap audit copy and the domestic/international
 * content-based routing end-to-end through a real (in-memory) {@link CamelContext}.
 */
class OrderNotificationRoutesTest {

    private static final String DOMESTIC_COUNTRY_CODE = "PL";

    @TempDir
    private transient Path tempDir;

    private transient CamelContext camelContext;

    private transient ProducerTemplate producerTemplate;

    @BeforeEach
    void setUp() throws Exception {

        final CamelProperties camelProperties = new CamelProperties(DOMESTIC_COUNTRY_CODE, tempDir.toString());

        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new OrderNotificationRoutes(camelProperties, new Gson()));
        camelContext.start();
        producerTemplate = camelContext.createProducerTemplate();
    }

    @AfterEach
    void tearDown() {

        camelContext.stop();
    }

    @Test
    void shouldRouteDomesticOrderToDomesticChannelAndAudit() throws Exception {

        final Order order = orderShippedTo(DOMESTIC_COUNTRY_CODE);

        producerTemplate.sendBody(ORDER_NOTIFICATION_ENDPOINT, order);

        assertThat(awaitFile("domestic", order)).exists();
        assertThat(awaitFile("audit", order)).exists();
        assertThat(notificationFile("international", order)).doesNotExist();
    }

    @Test
    void shouldRouteInternationalOrderToInternationalChannelAndAudit() throws Exception {

        final Order order = orderShippedTo("XX");

        producerTemplate.sendBody(ORDER_NOTIFICATION_ENDPOINT, order);

        assertThat(awaitFile("international", order)).exists();
        assertThat(awaitFile("audit", order)).exists();
        assertThat(notificationFile("domestic", order)).doesNotExist();
    }

    @Test
    void shouldTreatDomesticCountryCodeComparisonAsCaseInsensitive() throws Exception {

        final Order order = orderShippedTo("pl");

        producerTemplate.sendBody(ORDER_NOTIFICATION_ENDPOINT, order);

        assertThat(awaitFile("domestic", order)).exists();
    }

    private Path notificationFile(final String channel, final Order order) {

        return tempDir.resolve(channel).resolve(order.getOrderNumber() + ".json");
    }

    /**
     * Waits (polling) up to 5 seconds for the wire-tapped/routed notification file to show up, since the wire-tap branch is
     * processed asynchronously by Camel.
     */
    private Path awaitFile(final String channel, final Order order) throws InterruptedException {

        final Path file = notificationFile(channel, order);
        final Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (!Files.exists(file) && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        return file;
    }

    private Order orderShippedTo(final String countryCode) {

        final Order template = mockOrder();
        final Address address = Address.builder()
                .street("Main Street 1")
                .postalCode("12-345")
                .city("Warsaw")
                .countryCode(countryCode)
                .build();
        final Contact contact = Contact.builder().fullName("Jane Doe").email("jane.doe@test.com").phone("").build();
        final Customer customer = Customer.builder().id(1L).contact(contact).address(address).build();
        return Order.builder()
                .remarks(template.getRemarks())
                .orderNumber(template.getOrderNumber() + "-" + countryCode)
                .created(template.getCreated())
                .customer(customer)
                .build();
    }

}
