package com.cp.ecommerce.adapter.persistence.order.recovery;

import java.sql.Timestamp;
import java.time.Instant;

import com.cp.ecommerce.application.EcommerceApplication;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrderRecoveryTimelineOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineState;

import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(classes = EcommerceApplication.class)
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "service.mail.enabled=false",
                "service.camel.enabled=false",
                "outbox.publisher.enabled=false",
                "order-placement.dispatch.enabled=false",
                "payment.reconciliation.enabled=false",
                "notification.retry.enabled=false" })
class OrderRecoveryTimelinePostgresIntegrationTest {

    private static final String ORDER_NUMBER = "ORD%_1001";

    private static final String NEAR_MISS_ORDER_NUMBER = "ORDXX1001";

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    private static final String SECRET_ERROR = "provider-secret-stack-fragment";

    private static final String SECRET_RECIPIENT = "private-customer@example.com";

    private static final String SECRET_SUBJECT = "private subject";

    private static final String SECRET_BODY = "private body";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private FindOrderRecoveryTimelineOutPort timeline;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clean() {

        jdbcTemplate.update("DELETE FROM test_db.NOTIFICATION");
        jdbcTemplate.update("DELETE FROM test_db.ORDER_PLACEMENT_DISPATCH");
    }

    @Test
    void exactOrderScopeMustResistSqlWildcardsAndExcludeSensitiveFields() {

        insertDispatch("dispatch-target", ORDER_NUMBER, SECRET_ERROR);
        insertDispatch("dispatch-near-miss", NEAR_MISS_ORDER_NUMBER, "other error");

        insertNotification(
                "notification-target",
                "order:" + ORDER_NUMBER + ":confirmed:v1",
                SECRET_RECIPIENT,
                SECRET_SUBJECT,
                SECRET_BODY);
        insertNotification(
                "notification-near-miss",
                "order:" + NEAR_MISS_ORDER_NUMBER + ":confirmed:v1",
                "other@example.com",
                "other subject",
                "other body");

        final var entries = timeline.find(ORDER_NUMBER, 0, 100);

        assertThat(entries).extracting(entry -> entry.referenceId())
                .containsExactlyInAnyOrder("dispatch-target", "notification-target")
                .doesNotContain("dispatch-near-miss", "notification-near-miss");

        assertThat(entries).extracting(entry -> entry.state()).containsOnly(OrderRecoveryTimelineState.UNKNOWN);

        final String rendered = entries.toString();
        assertThat(rendered).doesNotContain(SECRET_ERROR)
                .doesNotContain(SECRET_RECIPIENT)
                .doesNotContain(SECRET_SUBJECT)
                .doesNotContain(SECRET_BODY);
    }

    private void insertDispatch(final String dispatchId, final String orderNumber, final String lastError) {

        jdbcTemplate.update(
                """
                        INSERT INTO test_db.ORDER_PLACEMENT_DISPATCH (
                            DISPATCH_ID,
                            ORDER_NUMBER,
                            DISPATCH_TYPE,
                            STATUS,
                            CREATED_DATE,
                            ATTEMPTS,
                            NEXT_ATTEMPT_DATE,
                            LAST_ERROR
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                dispatchId,
                orderNumber,
                "CONFIRMATION_EMAIL",
                "FAILED",
                Timestamp.from(NOW),
                1,
                Timestamp.from(NOW),
                lastError);
    }

    private void insertNotification(
            final String notificationId,
            final String eventKey,
            final String recipient,
            final String subject,
            final String body) {

        jdbcTemplate.update(
                """
                        INSERT INTO test_db.NOTIFICATION (
                            NOTIFICATION_ID,
                            EVENT_KEY,
                            RECIPIENT_EMAIL,
                            CHANNEL,
                            TYPE,
                            SUBJECT,
                            BODY,
                            STATUS,
                            CREATED_DATE,
                            DELIVERY_ATTEMPTS,
                            NEXT_ATTEMPT_DATE,
                            LAST_ERROR
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                notificationId,
                eventKey,
                recipient,
                "EMAIL",
                "ORDER_CONFIRMED",
                subject,
                body,
                "FAILED",
                Timestamp.from(NOW),
                1,
                Timestamp.from(NOW),
                SECRET_ERROR);
    }
}
