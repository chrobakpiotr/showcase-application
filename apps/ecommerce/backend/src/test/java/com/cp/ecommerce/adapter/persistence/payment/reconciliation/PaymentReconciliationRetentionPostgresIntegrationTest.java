package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.application.EcommerceApplication;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;
import com.cp.ecommerce.domain.payment.PaymentStatus;

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
                "outbox.publisher.enabled=false",
                "payment.reconciliation.enabled=false",
                "payment.reconciliation.retention.enabled=false" })
class PaymentReconciliationRetentionPostgresIntegrationTest {

    private static final Instant CUTOFF = Instant.parse("2026-06-22T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private PaymentReconciliationRetentionManager retentionManager;

    @Autowired
    private PaymentReconciliationEntityRepository reconciliationRepository;

    @Autowired
    private PaymentTransactionEntityRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldDeleteCompletedEvidenceOnlyInBoundedBatchesBeyondReplayHorizon() {

        final String suffix = UUID.randomUUID().toString().replace("-", "");
        final String oldOne = operationId("OLD1", suffix);
        final String oldTwo = operationId("OLD2", suffix);
        final String oldThree = operationId("OLD3", suffix);
        final String recent = operationId("RECENT", suffix);
        final String manualReview = operationId("MANUAL", suffix);

        saveCompleted(oldOne, "ORD1" + suffix, CUTOFF.minusSeconds(300));
        saveCompleted(oldTwo, "ORD2" + suffix, CUTOFF.minusSeconds(200));
        saveCompleted(oldThree, "ORD3" + suffix, CUTOFF.minusSeconds(100));
        saveCompleted(recent, "ORD4" + suffix, CUTOFF.plusSeconds(1));
        saveManualReview(manualReview, "ORD5" + suffix, CUTOFF.minusSeconds(10_000));

        assertThat(retentionManager.purgeCompletedBefore(CUTOFF, 2)).isEqualTo(2);

        assertThat(reconciliationRepository.findById(oldOne)).isEmpty();
        assertThat(reconciliationRepository.findById(oldTwo)).isEmpty();
        assertThat(reconciliationRepository.findById(oldThree)).isPresent();
        assertThat(reconciliationRepository.findById(recent)).isPresent();
        assertThat(reconciliationRepository.findById(manualReview)).isPresent();

        assertThat(retentionManager.purgeCompletedBefore(CUTOFF, 2)).isEqualTo(1);

        assertThat(reconciliationRepository.findById(oldThree)).isEmpty();
        assertThat(reconciliationRepository.findById(recent)).isPresent();
        assertThat(reconciliationRepository.findById(manualReview)).isPresent();

        final Integer indexCount = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes " + "where schemaname = 'test_db' "
                        + "and lower(indexname) = 'idx_payment_reconciliation_retention'",
                Integer.class);
        assertThat(indexCount).isEqualTo(1);
    }

    private void saveCompleted(final String operationId, final String orderNumber, final Instant completed) {

        savePayment(orderNumber);
        reconciliationRepository.saveAndFlush(
                PaymentReconciliationEntity.builder()
                        .operationId(operationId)
                        .orderNumber(orderNumber)
                        .operationType(PaymentProviderOperationType.CAPTURE)
                        .status(PaymentReconciliationStatus.COMPLETED)
                        .attempts(0)
                        .nextAttemptDate(completed)
                        .created(completed.minusSeconds(1))
                        .completed(completed)
                        .build());
    }

    private void saveManualReview(final String operationId, final String orderNumber, final Instant completedLikeAge) {

        savePayment(orderNumber);
        reconciliationRepository.saveAndFlush(
                PaymentReconciliationEntity.builder()
                        .operationId(operationId)
                        .orderNumber(orderNumber)
                        .operationType(PaymentProviderOperationType.CAPTURE)
                        .status(PaymentReconciliationStatus.MANUAL_REVIEW)
                        .attempts(5)
                        .nextAttemptDate(completedLikeAge)
                        .created(completedLikeAge)
                        .lastError("operator evidence required")
                        .build());
    }

    private void savePayment(final String orderNumber) {

        paymentRepository.saveAndFlush(
                PaymentTransactionEntity.builder()
                        .orderNumber(orderNumber)
                        .amount(BigDecimal.TEN)
                        .refundedAmount(BigDecimal.ZERO)
                        .method(PaymentMethod.CARD)
                        .status(PaymentStatus.CAPTURED)
                        .gatewayReference("gw-" + orderNumber)
                        .created(CUTOFF.minusSeconds(20_000))
                        .build());
    }

    private static String operationId(final String prefix, final String suffix) {
        return prefix + "-" + suffix;
    }
}
