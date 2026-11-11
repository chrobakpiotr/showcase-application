package com.cp.ecommerce.application;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Repeat-customer orders on PostgreSQL, plus the migrated CUSTOMER.EMAIL schema. */
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
class RepeatCustomerOrderPostgresIntegrationTest extends AbstractRepeatCustomerOrderIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
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

}
