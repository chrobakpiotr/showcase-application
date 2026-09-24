package com.cp.ecommerce.application;

import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import liquibase.integration.spring.SpringLiquibase;

import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers(disabledWithoutDocker = true)
class OperatorRecoveryToolingPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("operator_tooling")
            .withUsername("sa")
            .withPassword("sa");

    @Test
    void shouldExecuteReadOnlyReconciliationQueriesAgainstCurrentSchema() throws Exception {
        final DataSource dataSource = dataSource();
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        runCurrentMaster(dataSource);

        assertThatCode(
                () -> jdbc.queryForList(
                        "select ISSUE_KEY, ISSUE_TYPE, DETAILS, CREATED_DATE " + "from test_db.DATA_RECONCILIATION_ISSUE "
                                + "order by CREATED_DATE, ISSUE_KEY"))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> jdbc.queryForList(
                        "select OPERATION_ID, ORDER_NUMBER, OPERATION_TYPE, REFUND_ID, ATTEMPTS, LAST_ERROR, CREATION_DATE "
                                + "from test_db.PAYMENT_RECONCILIATION_OPERATION " + "where STATUS = 'MANUAL_REVIEW' "
                                + "order by CREATION_DATE, OPERATION_ID"))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> jdbc.queryForList(
                        "select ID, ORDER_NUMBER, STATUS, LAST_ERROR, CANCELLATION_ATTEMPTS, "
                                + "CANCELLATION_LAST_ERROR, CREATED_DATE " + "from test_db.OUTBOX_EVENT "
                                + "where STATUS = 'MANUAL_REVIEW' " + "order by CREATED_DATE, ID"))
                .doesNotThrowAnyException();
    }

    private static void runCurrentMaster(final DataSource dataSource) throws Exception {
        final SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
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
}
