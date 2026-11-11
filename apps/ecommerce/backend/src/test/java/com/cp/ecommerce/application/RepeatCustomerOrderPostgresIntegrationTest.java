package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/** ORDER-CUSTOMER-EMAIL-001: a repeat customer places several orders with one email, also concurrently. */
@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000" })
class RepeatCustomerOrderPostgresIntegrationTest {

    private static final String CUSTOMER_TABLE = "test_db.customer";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksSummary;

    @Autowired
    private OrderController controller;

    @Autowired
    private CatalogProductFixture catalogProductFixture;

    @Autowired
    private ManageStockInPort inventory;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldPlaceSequentialOrdersForTheSameCustomerEmail() {

        final String sku = preparedSku(2);
        final String email = email();

        final String first = controller.placeOrder(request(sku, email), null).orderNumber();
        final String second = controller.placeOrder(request(sku, email), null).orderNumber();

        assertThat(second).isNotEqualTo(first);
        assertThat(customerRows(email)).isEqualTo(2);
        assertThat(reserved(sku)).isEqualTo(2);
    }

    @Test
    void shouldPlaceConcurrentOrdersForTheSameCustomerEmail() throws Exception {

        final String sku = preparedSku(2);
        final String email = email();
        final CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var first = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return controller.placeOrder(request(sku, email), null).orderNumber();
            });
            final var second = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return controller.placeOrder(request(sku, email), null).orderNumber();
            });
            start.countDown();
            final String firstNumber = first.get(20, TimeUnit.SECONDS);
            final String secondNumber = second.get(20, TimeUnit.SECONDS);
            assertThat(secondNumber).isNotEqualTo(firstNumber);
        }
        assertThat(customerRows(email)).isEqualTo(2);
        assertThat(reserved(sku)).isEqualTo(2);
    }

    @Test
    void shouldKeepTheEmailLookupIndexWithoutUniqueness() {

        final String uniqueConstraints = """
                select count(*) from information_schema.table_constraints tc
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_schema = tc.constraint_schema and ccu.constraint_name = tc.constraint_name
                where tc.table_schema = 'test_db' and tc.table_name = 'customer'
                  and tc.constraint_type = 'UNIQUE' and ccu.column_name = 'email'
                """;
        final String lookupIndexes = """
                select count(*) from pg_indexes
                where schemaname = 'test_db' and tablename = 'customer'
                  and lower(indexname) = 'idx_customer_email' and indexdef not like 'CREATE UNIQUE%'
                """;

        assertThat(jdbc.queryForObject(uniqueConstraints, Integer.class)).isZero();
        assertThat(jdbc.queryForObject(lookupIndexes, Integer.class)).isEqualTo(1);
    }

    private String preparedSku(final int quantity) {

        final String sku = "REPEAT-" + UUID.randomUUID();
        catalogProductFixture.ensureActiveProduct(sku, "Integration fixture", BigDecimal.TEN);
        inventory.receiveStock(sku, quantity);
        return sku;
    }

    private Integer customerRows(final String email) {

        return jdbc.queryForObject("select count(*) from " + CUSTOMER_TABLE + " where email = ?", Integer.class, email);
    }

    private Integer reserved(final String sku) {

        return jdbc.queryForObject("select quantity_reserved from test_db.stock_level where sku = ?", Integer.class, sku);
    }

    private static String email() {

        return "repeat-" + UUID.randomUUID() + "@example.com";
    }

    private static OrderResource request(final String sku, final String email) {

        final var customer = new CustomerResource("Repeat Buyer", email, "123", "Street 1", "00-001", "Warsaw", "PL");
        final OrderLineItemResource line = new OrderLineItemResource(sku, "Integration fixture", BigDecimal.TEN, 1, null);
        return new OrderResource(
                "Repeat customer " + UUID.randomUUID(),
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                customer,
                List.of(line),
                PaymentMethod.CARD,
                null);
    }

}
