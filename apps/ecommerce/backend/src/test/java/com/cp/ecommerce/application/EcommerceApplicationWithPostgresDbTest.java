package com.cp.ecommerce.application;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class that checks whether spring context of the app has started properly with Postgres database.
 */
@ActiveProfiles("postgres-local")
@ContextConfiguration(initializers = { EcommerceApplicationWithPostgresDbTest.Initializer.class })
@Testcontainers(disabledWithoutDocker = true)
class EcommerceApplicationWithPostgresDbTest extends AbstractEcommerceApplicationTest {

    private static final String IMAGE_VERSION = "postgres:18.6";

    private static final String DB_NAME = "test_db";

    private static final String DB_USER = "sa";

    private static final String DB_PASS = "sa";

    @Container
    private static final PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(IMAGE_VERSION)
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USER)
            .withPassword(DB_PASS);

    @Test
    void postgresContainerShouldBeRunning() {

        assertTrue(postgreSQLContainer.isRunning());
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {

            TestPropertyValues
                    .of(
                            "spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                            "spring.datasource.username=" + postgreSQLContainer.getUsername(),
                            "spring.datasource.password=" + postgreSQLContainer.getPassword())
                    .applyTo(configurableApplicationContext.getEnvironment());
        }
    }

}
