package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentRefundStatus;
import com.cp.ecommerce.domain.payment.RefundReturnContinuationIntent;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.incoming.AdvanceShipmentStatusInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.GetShipmentInPort;
import com.cp.ecommerce.foundation.exception.ShipmentConflictException;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import liquibase.integration.spring.SpringLiquibase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Testcontainers(disabledWithoutDocker = true)
class HistoricalUpgradePostgresIntegrationTest {

    private static final String BASELINE_SHA = "d702f9cafd397dbea976bc6b39cdbcd3210cd22a";
    private static final String FROZEN_MASTER = "classpath:db/changelog/db.changelog-master.xml";
    private static final String CURRENT_MASTER = "classpath:db/changelog/db.changelog-master.xml";

    private static final String EXACT_ORDER = "S22-07A-ORDER-EXACT";
    private static final String EXACT_RETURN = "S22-07A-RMA-EXACT";
    private static final BigDecimal EXACT_AMOUNT = new BigDecimal("10.00");

    private static final String PRE_REFUND_ORDER = "S22-07A-ORDER-PRE";
    private static final String PRE_REFUND_RETURN = "S22-07A-RMA-PRE";
    private static final BigDecimal PRE_REFUND_AMOUNT = new BigDecimal("15.00");

    private static final String REVIEW_ORDER = "S22-07A-ORDER-REVIEW";
    private static final String REVIEW_RETURN = "S22-07A-RMA-REVIEW";
    private static final String OTHER_REFUND = "S22-07A-OTHER-REFUND";
    private static final BigDecimal REVIEW_AMOUNT = new BigDecimal("12.00");

    private static final String SHIPMENT_NUMBER = "S22-07A-SHIP-HIST";
    private static final String LEGACY_OPERATION = "S22-07A-LEGACY-OP";

    private static final Instant CREATED = Instant.parse("2026-09-20T10:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("upgrade_test")
            .withUsername("sa")
            .withPassword("sa");

    @Test
    void shouldUpgradeFrozenD702f9cDataAndExposeSafeRecoveryContract() throws Exception {
        final DataSource dataSource = dataSource();
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        resetDatabase(jdbc);
        assertFixtureProvenance();

        runFrozenMaster(dataSource);
        assertThat(tableExists(jdbc, "payment_refund_return_continuation")).isFalse();
        assertThat(tableExists(jdbc, "shipment_operation")).isFalse();

        seedHistoricalData(jdbc);
        final int baselineChangeCount = changelogCount(jdbc);

        runCurrentMaster(dataSource);
        final int upgradedChangeCount = changelogCount(jdbc);
        assertThat(upgradedChangeCount).isGreaterThan(baselineChangeCount);

        final String reviewStatus = jdbc.queryForObject(
                "select STATUS from test_db.PAYMENT_REFUND_RETURN_CONTINUATION where RETURN_NUMBER = ?",
                String.class,
                REVIEW_RETURN);
        if (!"MANUAL_REVIEW".equals(reviewStatus)) {
            throw new AssertionError(
                    "S22-07a ambiguous historical refund identity must be parked for manual review; actual=" + reviewStatus);
        }

        assertThat(
                jdbc.queryForObject(
                        "select count(*) from test_db.DATA_RECONCILIATION_ISSUE "
                                + "where ISSUE_KEY = ? and ISSUE_TYPE = 'REFUND_RETURN_IDENTITY_REVIEW'",
                        Long.class,
                        "REFUND_RETURN:" + REVIEW_RETURN))
                .isEqualTo(1L);
        assertThat(
                jdbc.queryForObject(
                        "select count(*) from test_db.DATA_RECONCILIATION_ISSUE "
                                + "where ISSUE_KEY = ? and ISSUE_TYPE = 'SHIPMENT_OPERATION_HISTORY_UNAVAILABLE'",
                        Long.class,
                        "SHIPMENT_OPERATION:" + SHIPMENT_NUMBER))
                .isEqualTo(1L);

        runCurrentMaster(dataSource);
        assertThat(changelogCount(jdbc)).isEqualTo(upgradedChangeCount);

        verifyRuntimeRecoveryAgainstMigratedData(dataSource, jdbc);

        resetDatabase(jdbc);
        runCurrentMaster(dataSource);
        final int freshInstallChangeCount = changelogCount(jdbc);
        assertThat(tableExists(jdbc, "payment_refund_return_continuation")).isTrue();
        assertThat(tableExists(jdbc, "shipment_operation")).isTrue();
        runCurrentMaster(dataSource);
        assertThat(changelogCount(jdbc)).isEqualTo(freshInstallChangeCount);
    }

    private static void verifyRuntimeRecoveryAgainstMigratedData(final DataSource dataSource, final JdbcTemplate jdbc) {
        assertThat(
                jdbc.queryForList(
                        "select RETURN_NUMBER from test_db.PAYMENT_REFUND_RETURN_CONTINUATION "
                                + "where STATUS = 'PENDING' order by RETURN_NUMBER",
                        String.class))
                .contains(EXACT_RETURN, PRE_REFUND_RETURN)
                .doesNotContain(REVIEW_RETURN);
        assertThat(
                jdbc.queryForObject(
                        "select count(*) from test_db.PAYMENT_REFUND_RETURN_CONTINUATION " + "where STATUS = 'PENDING' "
                                + "and NEXT_ATTEMPT_DATE <= timestamp '2100-01-01 00:00:00'",
                        Long.class))
                .isEqualTo(2L);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EcommerceApplication.class)
                .initializers(HistoricalUpgradePostgresIntegrationTest::registerRuntimeOverrides)
                .profiles("test-postgres")
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.liquibase.enabled=false",
                        "outbox.publisher.enabled=false",
                        "payment.reconciliation.enabled=false",
                        "payment.reconciliation.retention.enabled=false",
                        "notification.retry.enabled=false",
                        "management.health.rabbit.enabled=false",
                        "service.rabbitmq.enabled=false",
                        "service.mail.enabled=false")
                .run()) {

            final ManageRefundReturnContinuationOutPort continuation = context
                    .getBean(ManageRefundReturnContinuationOutPort.class);
            final ManagePaymentInPort payments = context.getBean(ManagePaymentInPort.class);
            final RefundPaymentOutPort provider = context.getBean("historicalRefundPaymentOutPort", RefundPaymentOutPort.class);

            final RefundReturnContinuationIntent exact = continuation.findByReturnNumber(EXACT_RETURN);
            assertThat(exact).isNotNull();
            assertThat(exact.refundId()).isEqualTo(EXACT_RETURN);
            assertThat(exact.orderNumber()).isEqualTo(EXACT_ORDER);
            assertThat(exact.refundAmount()).isEqualByComparingTo(EXACT_AMOUNT);

            final RefundReturnContinuationIntent preRefund = continuation.findByReturnNumber(PRE_REFUND_RETURN);
            assertThat(preRefund).isNotNull();
            assertThat(preRefund.refundId()).isEqualTo(PRE_REFUND_RETURN);

            assertThat(continuation.findRecoverableReturnNumbers(100)).contains(EXACT_RETURN, PRE_REFUND_RETURN)
                    .doesNotContain(REVIEW_RETURN);

            payments.refundPayment(EXACT_ORDER, exact.refundId(), exact.refundAmount());
            verifyNoInteractions(provider);

            payments.refundPayment(PRE_REFUND_ORDER, preRefund.refundId(), preRefund.refundAmount());
            verify(provider).refund(PRE_REFUND_ORDER, "GW-PRE", PRE_REFUND_RETURN, PRE_REFUND_AMOUNT);
            assertThat(
                    jdbc.queryForObject(
                            "select STATUS from test_db.PAYMENT_REFUND where REFUND_ID = ?",
                            String.class,
                            PRE_REFUND_RETURN))
                    .isEqualTo(PaymentRefundStatus.COMPLETED.name());

            final GetShipmentInPort getShipment = context.getBean(GetShipmentInPort.class);
            final AdvanceShipmentStatusInPort advanceShipment = context.getBean(AdvanceShipmentStatusInPort.class);

            assertThat(getShipment.getShipment(SHIPMENT_NUMBER).getLastOperationId()).isEqualTo(LEGACY_OPERATION);
            assertThat(
                    jdbc.queryForObject(
                            "select count(*) from test_db.SHIPMENT_OPERATION where OPERATION_ID = ?",
                            Long.class,
                            LEGACY_OPERATION))
                    .isZero();

            assertThatThrownBy(
                    () -> advanceShipment
                            .advanceShipmentStatusWithResult(SHIPMENT_NUMBER, LEGACY_OPERATION, ShipmentStatus.PENDING))
                    .isInstanceOf(ShipmentConflictException.class)
                    .hasMessageContaining("expected PENDING but is DISPATCHED");
        }
    }

    private static void seedHistoricalData(final JdbcTemplate jdbc) {
        insertPayment(jdbc, EXACT_ORDER, new BigDecimal("100.00"), EXACT_AMOUNT, "PARTIALLY_REFUNDED", "GW-EXACT");
        insertReturn(jdbc, EXACT_RETURN, EXACT_ORDER, EXACT_AMOUNT);
        insertRefund(jdbc, EXACT_RETURN, EXACT_ORDER, EXACT_AMOUNT);

        insertPayment(jdbc, PRE_REFUND_ORDER, new BigDecimal("100.00"), BigDecimal.ZERO, "CAPTURED", "GW-PRE");
        insertReturn(jdbc, PRE_REFUND_RETURN, PRE_REFUND_ORDER, PRE_REFUND_AMOUNT);

        insertPayment(jdbc, REVIEW_ORDER, new BigDecimal("100.00"), REVIEW_AMOUNT, "PARTIALLY_REFUNDED", "GW-REVIEW");
        insertReturn(jdbc, REVIEW_RETURN, REVIEW_ORDER, REVIEW_AMOUNT);
        insertRefund(jdbc, OTHER_REFUND, REVIEW_ORDER, REVIEW_AMOUNT);

        jdbc.update(
                "insert into test_db.SHIPMENT " + "(SHIPMENT_NUMBER, ORDER_NUMBER, CARRIER, TRACKING_NUMBER, STATUS, "
                        + "DISPATCHED_DATE, ESTIMATED_DELIVERY_DATE, DELIVERED_DATE, CREATED_DATE, VERSION, LAST_OPERATION_ID) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SHIPMENT_NUMBER,
                "S22-07A-ORDER-SHIP",
                "HISTORICAL-CARRIER",
                "TRACK-S22-07A",
                "DISPATCHED",
                Timestamp.from(CREATED.plusSeconds(60)),
                Timestamp.from(CREATED.plusSeconds(432000)),
                null,
                Timestamp.from(CREATED),
                1L,
                LEGACY_OPERATION);
    }

    private static void insertPayment(
            final JdbcTemplate jdbc,
            final String orderNumber,
            final BigDecimal amount,
            final BigDecimal refunded,
            final String status,
            final String gatewayReference) {
        jdbc.update(
                "insert into test_db.PAYMENT_TRANSACTION "
                        + "(ORDER_NUMBER, AMOUNT, REFUNDED_AMOUNT, METHOD, STATUS, GATEWAY_REFERENCE, CREATION_DATE) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                orderNumber,
                amount,
                refunded,
                "CARD",
                status,
                gatewayReference,
                Timestamp.from(CREATED));
    }

    private static void insertRefund(
            final JdbcTemplate jdbc,
            final String refundId,
            final String orderNumber,
            final BigDecimal amount) {
        jdbc.update(
                "insert into test_db.PAYMENT_REFUND "
                        + "(REFUND_ID, ORDER_NUMBER, AMOUNT, STATUS, CREATION_DATE, COMPLETION_DATE) "
                        + "values (?, ?, ?, 'COMPLETED', ?, ?)",
                refundId,
                orderNumber,
                amount,
                Timestamp.from(CREATED.plusSeconds(120)),
                Timestamp.from(CREATED.plusSeconds(180)));
    }

    private static void insertReturn(
            final JdbcTemplate jdbc,
            final String returnNumber,
            final String orderNumber,
            final BigDecimal amount) {
        jdbc.update(
                "insert into test_db.RETURN_REQUEST " + "(RETURN_NUMBER, ORDER_NUMBER, SKU, QUANTITY, REASON, STATUS, "
                        + "REQUESTED_DATE, DECIDED_DATE, REFUND_AMOUNT, VERSION) "
                        + "values (?, ?, ?, ?, ?, 'APPROVED', ?, ?, ?, ?)",
                returnNumber,
                orderNumber,
                "SKU-S22-07A",
                1,
                "historical upgrade fixture",
                Timestamp.from(CREATED),
                Timestamp.from(CREATED.plusSeconds(30)),
                amount,
                0L);
    }

    private static void runFrozenMaster(final DataSource dataSource) throws Exception {
        final URL frozenMaster = Objects.requireNonNull(
                HistoricalUpgradePostgresIntegrationTest.class.getClassLoader()
                        .getResource("db/upgrade/d702f9c/db/changelog/db.changelog-master.xml"));
        final Path fixtureRoot = Path.of(frozenMaster.toURI()).getParent().getParent().getParent();
        try (URLClassLoader loader = new URLClassLoader(new URL[] { fixtureRoot.toUri().toURL() }, null)) {
            runLiquibase(dataSource, FROZEN_MASTER, new DefaultResourceLoader(loader));
        }
    }

    private static void runCurrentMaster(final DataSource dataSource) throws Exception {
        runLiquibase(dataSource, CURRENT_MASTER, null);
    }

    private static void runLiquibase(final DataSource dataSource, final String changeLog, final ResourceLoader resourceLoader)
            throws Exception {
        final SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.setChangeLogParameters(Map.of("schema_name_full", "test_db", "schema_name_prefix", "\"test_db\"."));
        if (resourceLoader != null) {
            liquibase.setResourceLoader(resourceLoader);
        }
        liquibase.afterPropertiesSet();
    }

    private static void assertFixtureProvenance() throws Exception {
        final String provenance;
        try (var input = Objects.requireNonNull(
                HistoricalUpgradePostgresIntegrationTest.class.getClassLoader()
                        .getResourceAsStream("db/upgrade/d702f9c/PROVENANCE.txt"))) {
            provenance = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(provenance).contains("SOURCE_COMMIT=" + BASELINE_SHA);
        assertThat(provenance).contains("CAPTURE_METHOD=git archive from local immutable Git object");

        final String frozenMaster;
        try (var input = Objects.requireNonNull(
                HistoricalUpgradePostgresIntegrationTest.class.getClassLoader()
                        .getResourceAsStream("db/upgrade/d702f9c/db/changelog/db.changelog-master.xml"))) {
            frozenMaster = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(frozenMaster).doesNotContain("db.changelog-refund-return-continuation.xml");
        assertThat(frozenMaster).doesNotContain("db.changelog-shipment-operation-history.xml");
    }

    private static boolean tableExists(final JdbcTemplate jdbc, final String tableName) {
        return jdbc.queryForObject(
                "select count(*) from information_schema.tables " + "where table_schema = 'test_db' and table_name = ?",
                Long.class,
                tableName) > 0;
    }

    private static int changelogCount(final JdbcTemplate jdbc) {
        return Objects.requireNonNull(jdbc.queryForObject("select count(*) from databasechangelog", Integer.class));
    }

    private static void resetDatabase(final JdbcTemplate jdbc) {
        jdbc.execute("drop schema if exists test_db cascade");
        jdbc.execute("drop table if exists public.databasechangeloglock");
        jdbc.execute("drop table if exists public.databasechangelog");
    }

    private static DataSource dataSource() {
        final DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static void registerRuntimeOverrides(final ConfigurableApplicationContext context) {
        final GenericApplicationContext genericContext = (GenericApplicationContext) context;
        genericContext.registerBean(
                "historicalRemarksClassificationSummaryOutPort",
                GetRemarksClassificationSummaryOutPort.class,
                () -> mock(GetRemarksClassificationSummaryOutPort.class));
        genericContext.registerBean(
                "historicalUpgradeClock",
                Clock.class,
                () -> Clock.fixed(Instant.parse("2100-01-01T00:00:00Z"), ZoneOffset.UTC),
                beanDefinition -> beanDefinition.setPrimary(true));
        genericContext.registerBean(
                "historicalRefundPaymentOutPort",
                RefundPaymentOutPort.class,
                () -> mock(RefundPaymentOutPort.class),
                beanDefinition -> beanDefinition.setPrimary(true));
    }
}
