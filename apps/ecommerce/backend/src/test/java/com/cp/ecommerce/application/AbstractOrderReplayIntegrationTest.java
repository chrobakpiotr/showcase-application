package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import com.cp.ecommerce.adapter.common.exception.InsufficientStockException;
import com.cp.ecommerce.adapter.persistence.order.idempotency.IdempotencyKeyAdapter;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.IdempotencyReservation;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises actual controller proxies, persistence and commit boundaries. No test-level transaction. */
@SpringBootTest
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000" })
abstract class AbstractOrderReplayIntegrationTest {

    // Disabling the saga also disables its metrics read adapter. Analytics is
    // outside this transaction test; order/stock/outbox/key adapters remain real.
    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksSummary;

    @Autowired
    private OrderController controller;

    @Autowired
    private ManageStockInPort inventory;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IdempotencyKeyAdapter keys;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldReplayAfterStockExhaustionWithoutAdditionalWrites() {

        final String sku = sku();
        final String key = UUID.randomUUID().toString();
        inventory.receiveStock(sku, 1);
        final OrderResource request = request(sku, List.of(line(sku)));
        final String first = controller.placeOrder(request, key).orderNumber();
        assertThat(controller.placeOrder(request, key).orderNumber()).isEqualTo(first);
        assertPlacement(first, sku, 1);
    }

    @Test
    void shouldRollbackPartialStockAndKeyThenPermitImmediateRetry() {

        final String firstSku = sku();
        final String secondSku = sku();
        final String key = UUID.randomUUID().toString();
        inventory.receiveStock(firstSku, 1);
        final OrderResource request = request(key, List.of(line(firstSku), line(secondSku)));
        assertThrows(InsufficientStockException.class, () -> controller.placeOrder(request, key));
        assertThat(
                jdbc.queryForObject("select quantity_reserved from test_db.stock_level where sku = ?", Integer.class, firstSku))
                .isZero();
        assertThat(
                jdbc.queryForObject(
                        "select count(*) from test_db.idempotency_key where idempotency_key = ?",
                        Integer.class,
                        key))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from test_db.order_ where remark = ?", Integer.class, key)).isZero();
        inventory.receiveStock(secondSku, 1);
        final String orderNumber = controller.placeOrder(request, key).orderNumber();
        assertPlacement(orderNumber, firstSku, 1);
        assertPlacement(orderNumber, secondSku, 1);
    }

    @Test
    void shouldSerializeConcurrentSameKeyPlacements() throws Exception {

        final String sku = sku();
        final String key = UUID.randomUUID().toString();
        inventory.receiveStock(sku, 1);
        final OrderResource request = request(sku, List.of(line(sku)));
        final CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var first = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return controller.placeOrder(request, key).orderNumber();
            });
            final var second = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return controller.placeOrder(request, key).orderNumber();
            });
            start.countDown();
            final String number = first.get(20, TimeUnit.SECONDS);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(number);
            assertPlacement(number, sku, 1);
        }
    }

    @Test
    void shouldRejectReservationOutsideAnAmbientTransaction() {

        assertThrows(IllegalTransactionStateException.class, () -> keys.reserve(UUID.randomUUID().toString(), "fingerprint"));
    }

    @Test
    void shouldLetAWaitingAttemptProceedAfterRollback() throws Exception {

        contend(false);
    }

    @Test
    void shouldSerializeStaleTakeoverUntilCompletionCommits() throws Exception {

        contend(true);
    }

    private void contend(final boolean stale) throws Exception {

        final String key = UUID.randomUUID().toString();
        if (stale) {
            transaction().executeWithoutResult(status -> keys.reserve(key, "same-request"));
            jdbc.update("update test_db.idempotency_key set created_date = ? where idempotency_key = ?", new Date(0), key);
        }
        final CountDownLatch holding = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch trying = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var first = executor.submit(() -> transaction().execute(status -> {
                final IdempotencyReservation result = keys.reserve(key, "same-request");
                holding.countDown();
                await(release);
                if (stale) {
                    keys.complete(key, "completed-once");
                } else {
                    status.setRollbackOnly();
                }
                return result.outcome();
            }));
            try {
                assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
                final var second = executor.submit(() -> transaction().execute(status -> {
                    trying.countDown();
                    return keys.reserve(key, "same-request");
                }));
                assertThat(trying.await(10, TimeUnit.SECONDS)).isTrue();
                assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
                release.countDown();
                assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(IdempotencyReservation.Outcome.RESERVED);
                final IdempotencyReservation result = second.get(20, TimeUnit.SECONDS);
                assertThat(result.outcome())
                        .isEqualTo(stale ? IdempotencyReservation.Outcome.DUPLICATE : IdempotencyReservation.Outcome.RESERVED);
                if (stale) {
                    assertThat(result.existingOrderNumber()).isEqualTo("completed-once");
                }
            } finally {
                release.countDown();
            }
        }
    }

    private TransactionTemplate transaction() {

        final TransactionTemplate template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }

    private static void await(final CountDownLatch latch) {

        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void shouldUpgradeAWrappedSequenceBeyondRetainedIds() throws Exception {

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            final boolean h2 = "H2".equals(connection.getMetaData().getDatabaseProductName());
            final String name = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
            final String schema = h2 ? name.toUpperCase(Locale.ROOT) : name;
            statement.execute("CREATE SCHEMA " + schema);
            try {
                statement.execute(
                        "CREATE SEQUENCE " + schema + ".SEQ_IDEMPOTENCY_KEY MINVALUE 1 MAXVALUE 999 START WITH 2 CYCLE");
                statement.execute("CREATE TABLE " + schema + ".IDEMPOTENCY_KEY (ID NUMERIC(13) PRIMARY KEY)");
                statement.execute("INSERT INTO " + schema + ".IDEMPOTENCY_KEY (ID) VALUES (2), (999)");
                final Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                database.setDefaultSchemaName(schema);
                final Liquibase migration = new Liquibase(
                        "db/changelog/changelogs/db.changelog-idempotency-locks.xml",
                        new ClassLoaderResourceAccessor(),
                        database);
                migration.setChangeLogParameter("schema_name_full", schema);
                migration.update(new Contexts(), new LabelExpression());
                final String query = h2
                        ? "SELECT NEXT VALUE FOR " + schema + ".SEQ_IDEMPOTENCY_KEY"
                        : "SELECT nextval('" + schema + ".seq_idempotency_key')";
                try (ResultSet result = statement.executeQuery(query)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isEqualTo(1000);
                }
                try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + schema + ".IDEMPOTENCY_LOCK")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(64);
                }
            } finally {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private void assertPlacement(final String orderNumber, final String sku, final int reserved) {

        assertThat(
                jdbc.queryForObject("select count(*) from test_db.order_ where order_number = ?", Integer.class, orderNumber))
                .isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                        "select count(*) from test_db.outbox_event where order_number = ?",
                        Integer.class,
                        orderNumber))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity_reserved from test_db.stock_level where sku = ?", Integer.class, sku))
                .isEqualTo(reserved);
    }

    private static String sku() {

        return "IT-" + UUID.randomUUID();
    }

    private static OrderLineItemResource line(final String sku) {

        return new OrderLineItemResource(sku, "Integration fixture", BigDecimal.TEN, 1, null);
    }

    private static OrderResource request(final String remarks, final List<OrderLineItemResource> items) {

        return new OrderResource(
                remarks,
                new Date(),
                new CustomerResource(
                        "Integration Buyer",
                        UUID.randomUUID() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                items,
                PaymentMethod.CARD,
                null);
    }

}
