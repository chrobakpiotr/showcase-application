package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "payment.reconciliation.enabled=false"
        })
class PaymentOperationPreparationPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @MockitoSpyBean
    private ChargePaymentOutPort chargePaymentOutPort;

    @MockitoSpyBean
    private ManagePaymentReconciliationOutPort reconciliationOutPort;

    @Autowired
    private ManagePaymentInPort managePaymentInPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldRollbackPendingPaymentWhenReconciliationIntentPreparationFails() {

        final String orderNumber = "A1-" + UUID.randomUUID().toString().substring(0, 24);
        final String operationId = "ORDER-CAPTURE:" + orderNumber;

        doThrow(new IllegalStateException("intent write failed"))
                .when(reconciliationOutPort)
                .start(operationId, orderNumber, PaymentProviderOperationType.CAPTURE, null);

        assertThatThrownBy(
                        () -> managePaymentInPort.capturePayment(
                                orderNumber,
                                new BigDecimal("10.00"),
                                PaymentMethod.CARD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("intent write failed");

        final Integer paymentRows = jdbcTemplate.queryForObject(
                "select count(*) from test_db.PAYMENT_TRANSACTION where ORDER_NUMBER = ?",
                Integer.class,
                orderNumber);
        final Integer reconciliationRows = jdbcTemplate.queryForObject(
                "select count(*) from test_db.PAYMENT_RECONCILIATION_OPERATION where OPERATION_ID = ?",
                Integer.class,
                operationId);

        assertThat(paymentRows).isZero();
        assertThat(reconciliationRows).isZero();
        verifyNoInteractions(chargePaymentOutPort);
    }
}
