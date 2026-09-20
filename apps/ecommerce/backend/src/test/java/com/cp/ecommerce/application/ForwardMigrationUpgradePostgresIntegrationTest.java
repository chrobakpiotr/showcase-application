package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import liquibase.integration.spring.SpringLiquibase;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ForwardMigrationUpgradePostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("upgrade_test")
            .withUsername("sa")
            .withPassword("sa");

    @Test
    void shouldForwardFixDeterministicPaymentStateAndReportAmbiguousStockState() throws Exception {
        final DataSource dataSource = dataSource();
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPreviousSchema(jdbc);

        jdbc.update(
                "insert into test_db.PAYMENT_TRANSACTION "
                        + "(ORDER_NUMBER, AMOUNT, REFUNDED_AMOUNT, METHOD, STATUS) values (?, ?, ?, ?, ?)",
                "ORDER-UPGRADE",
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "CARD",
                "REFUNDED");
        jdbc.update("insert into test_db.STOCK_LEVEL (SKU, QUANTITY_RESERVED) values (?, ?)", "SKU-UPGRADE", 2);

        runForwardFix(dataSource);
        runForwardFix(dataSource);

        assertThat(
                jdbc.queryForObject(
                        "select REFUNDED_AMOUNT from test_db.PAYMENT_TRANSACTION where ORDER_NUMBER = ?",
                        BigDecimal.class,
                        "ORDER-UPGRADE"))
                .isEqualByComparingTo("100.00");
        assertThat(
                jdbc.queryForObject(
                        "select count(*) from test_db.DATA_RECONCILIATION_ISSUE " + "where ISSUE_KEY = 'STOCK:SKU-UPGRADE' "
                                + "and ISSUE_TYPE = 'STOCK_RESERVATION_MISMATCH'",
                        Long.class))
                .isEqualTo(1L);
    }

    private static void runForwardFix(final DataSource dataSource) throws Exception {
        final SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setDefaultSchema("test_db");
        liquibase.setChangeLog("classpath:db/changelog/changelogs/db.changelog-stabilization-n01-n13.xml");
        liquibase.setChangeLogParameters(Map.of("schema_name_full", "test_db", "schema_name_prefix", "\"test_db\"."));
        liquibase.afterPropertiesSet();
    }

    private static DataSource dataSource() {
        final DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static void createPreviousSchema(final JdbcTemplate jdbc) {
        jdbc.execute("create schema test_db");
        jdbc.execute(
                "create table test_db.OUTBOX_EVENT ("
                        + "ID bigint primary key, ORDER_NUMBER varchar(40) not null, STATUS varchar(20) not null, "
                        + "CREATED_DATE timestamp not null, SENT_DATE timestamp, COMPENSATED_DATE timestamp, "
                        + "ATTEMPTS integer not null default 0, LAST_ERROR varchar(500), "
                        + "COMPENSATION_ATTEMPTS integer not null default 0, CLAIM_ID varchar(36), CLAIM_UNTIL timestamp)");
        jdbc.execute(
                "create table test_db.NOTIFICATION ("
                        + "NOTIFICATION_ID varchar(42) primary key, RECIPIENT_EMAIL varchar(255) not null, "
                        + "CHANNEL varchar(20) not null, TYPE varchar(30) not null, SUBJECT varchar(255) not null, "
                        + "BODY varchar(2000) not null, STATUS varchar(20) not null, CREATED_DATE timestamp not null, "
                        + "SENT_DATE timestamp, DELIVERY_ATTEMPTS integer not null default 0, "
                        + "NEXT_ATTEMPT_DATE timestamp not null, LAST_ERROR varchar(500))");
        jdbc.execute("create table test_db.SHIPMENT (SHIPMENT_NUMBER varchar(40) primary key)");
        jdbc.execute(
                "create table test_db.PAYMENT_TRANSACTION ("
                        + "ORDER_NUMBER varchar(40) primary key, AMOUNT numeric(19,2) not null, "
                        + "REFUNDED_AMOUNT numeric(19,2) not null default 0, METHOD varchar(20), "
                        + "STATUS varchar(20) not null, GATEWAY_REFERENCE varchar(80), CREATION_DATE timestamp)");
        jdbc.execute("create table test_db.STOCK_LEVEL (SKU varchar(80) primary key, QUANTITY_RESERVED integer not null)");
        jdbc.execute(
                "create table test_db.STOCK_RESERVATION (" + "RESERVATION_ID varchar(80) not null, SKU varchar(80) not null, "
                        + "QUANTITY integer not null, STATUS varchar(20) not null)");
    }
}
