package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.application.EcommerceApplication;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.port.incoming.CompleteRefundReturnContinuationInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.domain.returns.ReturnStatus;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = EcommerceApplication.class)
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "payment.reconciliation.enabled=false",
                "payment.refund-return-continuation.retry-backoff-ms=600000",
                "payment.refund-return-continuation.max-attempts=3" })
class RefundReturnContinuationFairnessPostgresIntegrationTest {

    private static final String ORDER_NUMBER = "S22-BACKLOG-ORDER";
    private static final String HEALTHY = "S22-HEALTHY-51";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;
    @MockitoBean
    private CompleteRefundReturnContinuationInPort completionInPort;
    @Autowired
    private PaymentReconciliationArbitrator arbitrator;
    @Autowired
    private PaymentReconciliationEntityRepository reconciliationRepository;
    @Autowired
    private PaymentTransactionEntityRepository paymentRepository;
    @Autowired
    private PaymentRefundEntityRepository refundRepository;
    @Autowired
    private ReturnRequestEntityRepository returnRepository;
    @Autowired
    private ManageOrderInPort manageOrderInPort;
    @Autowired
    private ManagePaymentInPort managePaymentInPort;
    @Autowired
    private ManageRefundReturnContinuationOutPort continuationOutPort;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void fiftyPoisonContinuationsMustNotStarveHealthyItemFiftyOne() {
        seedBacklog();
        final AtomicBoolean healthyCompleted = new AtomicBoolean();
        doAnswer(invocation -> {
            final String number = invocation.getArgument(0);
            if (number.startsWith("S22-POISON-")) {
                throw new IllegalStateException("permanent poison");
            }
            if (HEALTHY.equals(number)) {
                healthyCompleted.set(true);
            }
            return null;
        }).when(completionInPort).complete(startsWith("S22-"));

        final PaymentReconciliationScheduler scheduler = scheduler();
        scheduler.reconcileDueOperations();
        scheduler.reconcileDueOperations();

        assertThat(healthyCompleted).as("healthy 51st continuation must progress by the second scheduler tick").isTrue();
    }

    private void seedBacklog() {
        final var returns = new ArrayList<ReturnRequestEntity>();
        for (int index = 0; index < 51; index++) {
            final String number = index < 50 ? "S22-POISON-" + String.format("%02d", index) : HEALTHY;
            final Instant created = Instant.parse("2026-09-23T08:00:00Z").plusMillis(index);
            returns.add(
                    ReturnRequestEntity.builder()
                            .returnNumber(number)
                            .orderNumber(ORDER_NUMBER)
                            .sku("SKU-S22")
                            .quantity(1)
                            .reason("fairness")
                            .status(ReturnStatus.APPROVED)
                            .requestedDate(created)
                            .decidedDate(created)
                            .refundAmount(BigDecimal.ONE)
                            .build());
        }
        returnRepository.saveAllAndFlush(returns);
        for (int index = 0; index < 51; index++) {
            final String number = index < 50 ? "S22-POISON-" + String.format("%02d", index) : HEALTHY;
            continuationOutPort.start(number, number, ORDER_NUMBER, BigDecimal.ONE);
        }
    }

    private PaymentReconciliationScheduler scheduler() {
        return new PaymentReconciliationScheduler(
                arbitrator,
                reconciliationRepository,
                paymentRepository,
                refundRepository,
                manageOrderInPort,
                managePaymentInPort,
                continuationOutPort,
                completionInPort);
    }
}
