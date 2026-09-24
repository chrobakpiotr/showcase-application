package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.application.returns.ReturnWorkflow;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = { "outbox.publisher.enabled=false", "payment.reconciliation.enabled=false" })
class RefundContinuationCrashPointPostgresIntegrationTest {

    private static final String ORDER_NUMBER = "S22-ORDER-CRASH";
    private static final BigDecimal REFUND = new BigDecimal("10.00");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;
    @MockitoSpyBean
    private ManageRefundReturnContinuationOutPort continuationOutPort;
    @Autowired
    private ReturnWorkflow returnWorkflow;
    @Autowired
    private ReturnRequestEntityRepository returnRepository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void approvalAndDurableContinuationMustRollbackTogether() {
        final String number = "S22-RMA-ROLLBACK";
        returnRepository.saveAndFlush(entity(number, REFUND, ReturnStatus.REQUESTED));
        doThrow(new IllegalStateException("simulated continuation persistence failure")).when(continuationOutPort)
                .start(number, number, ORDER_NUMBER, REFUND);
        assertThatThrownBy(() -> returnWorkflow.approveReturn(number)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated continuation");
        assertThat(returnRepository.findByReturnNumber(number).getStatus()).isEqualTo(ReturnStatus.REQUESTED);
    }

    @Test
    void mappingWithoutRefundRowMustRemainDiscoverable() {
        final String number = "S22-RMA-MAPPING-ONLY";
        returnRepository.saveAndFlush(entity(number, REFUND, ReturnStatus.APPROVED));
        continuationOutPort.start(number, number, ORDER_NUMBER, REFUND);
        assertThat(continuationOutPort.findRecoverableReturnNumbers(100)).contains(number);
    }

    @Test
    void zeroValueApprovalMustRollbackWithNotificationFailure() {
        final String number = "S22-RMA-ZERO";
        returnRepository.saveAndFlush(entity(number, BigDecimal.ZERO, ReturnStatus.REQUESTED));
        assertThatThrownBy(() -> returnWorkflow.approveReturn(number)).isInstanceOf(RuntimeException.class);
        assertThat(returnRepository.findByReturnNumber(number).getStatus()).isEqualTo(ReturnStatus.REQUESTED);
    }

    private static ReturnRequestEntity entity(final String number, final BigDecimal amount, final ReturnStatus status) {
        return ReturnRequestEntity.builder()
                .returnNumber(number)
                .orderNumber(ORDER_NUMBER)
                .sku("SKU-S22")
                .quantity(1)
                .reason("S22 crash point")
                .status(status)
                .requestedDate(Instant.parse("2026-09-23T08:00:00Z"))
                .decidedDate(status == ReturnStatus.APPROVED ? Instant.parse("2026-09-23T08:01:00Z") : null)
                .refundAmount(amount)
                .build();
    }
}
