package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OrderCancellationRedriveCommandEntity;
import com.cp.ecommerce.adapter.persistence.order.outbox.OrderCancellationRedriveCommandEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntity;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveCommand;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.incoming.RedriveOrderCancellationInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.foundation.exception.OrderCancellationRedriveConflictException;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000",
                "resilience4j.ratelimiter.instances.cancelOrder.limit-for-period=1000" })
class OrderCancellationManualReviewRedrivePostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private OrderController orderController;

    @Autowired
    private CatalogProductFixture catalogProductFixture;

    @Autowired
    private ManageStockInPort manageStockInPort;

    @Autowired
    private RedriveOrderCancellationInPort redriveOrderCancellationInPort;

    @Autowired
    private OutboxEventEntityRepository outboxEventEntityRepository;

    @Autowired
    private OrderCancellationRedriveCommandEntityRepository commandRepository;

    @Autowired
    private OrderEntityRepository orderEntityRepository;

    @Autowired
    private Clock clock;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldDurablyAuditReplayAndFenceManualReviewRedrive() {

        final String sku = "RDR-" + compactUuid();
        catalogProductFixture.ensureActiveProduct(sku, "redrive fixture", BigDecimal.TEN);
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = orderController.placeOrder(request(sku), UUID.randomUUID().toString()).orderNumber();

        final OrderEntity order = orderEntityRepository.getOrderEntityByOrderNumber(orderNumber);
        order.setStatus(OrderStatus.CANCELLED);
        orderEntityRepository.save(order);

        final OutboxEventEntity event = outboxEventEntityRepository.findAll()
                .stream()
                .filter(candidate -> orderNumber.equals(candidate.getOrderNumber()))
                .findFirst()
                .orElseThrow();
        event.setStatus(OutboxEventStatus.MANUAL_REVIEW);
        event.setCancellationAttempts(10);
        event.setCancellationLastError("provider outcome still unknown");
        event.setCancellationClaimId(null);
        event.setCancellationClaimUntil(null);
        event.setClaimId(null);
        event.setClaimUntil(null);
        outboxEventEntityRepository.saveAndFlush(event);

        final String commandId = "cmd-" + compactUuid();
        final OrderCancellationRedriveCommand command = new OrderCancellationRedriveCommand(
                commandId,
                orderNumber,
                "operator-a",
                "incident-456");

        assertThat(redriveOrderCancellationInPort.redrive(command)).isEqualTo(OrderCancellationRedriveOutcome.REQUEUED);

        final OutboxEventEntity requeued = reloadEvent(orderNumber);
        assertThat(requeued.getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
        assertThat(requeued.getCancellationAttempts()).isZero();
        assertThat(requeued.getCancellationNextAttemptDate()).isNotNull();
        assertThat(requeued.getCancellationClaimId()).isNull();
        assertThat(requeued.getCancellationClaimUntil()).isNull();
        assertThat(requeued.getCancellationLastError()).isNull();

        final OrderCancellationRedriveCommandEntity audit = commandRepository.findById(commandId).orElseThrow();
        assertThat(audit.getOrderNumber()).isEqualTo(orderNumber);
        assertThat(audit.getActor()).isEqualTo("operator-a");
        assertThat(audit.getReason()).isEqualTo("incident-456");
        assertThat(audit.getPreviousError()).isEqualTo("provider outcome still unknown");
        assertThat(audit.getStatus()).isEqualTo(OrderCancellationRedriveOutcome.REQUEUED);

        assertThat(redriveOrderCancellationInPort.redrive(command)).isEqualTo(OrderCancellationRedriveOutcome.REPLAYED);
        assertThat(commandRepository.findById(commandId)).isPresent();

        assertThatThrownBy(
                () -> redriveOrderCancellationInPort
                        .redrive(new OrderCancellationRedriveCommand(commandId, orderNumber, "operator-a", "different-reason")))
                .isInstanceOf(OrderCancellationRedriveConflictException.class)
                .hasMessageContaining("different command data");

        assertThatThrownBy(
                () -> redriveOrderCancellationInPort.redrive(
                        new OrderCancellationRedriveCommand(
                                "cmd-" + compactUuid(),
                                orderNumber,
                                "operator-a",
                                "second live command")))
                .isInstanceOf(OrderCancellationRedriveConflictException.class)
                .hasMessageContaining("not parked in MANUAL_REVIEW");

        final OutboxEventEntity fenced = reloadEvent(orderNumber);
        fenced.setStatus(OutboxEventStatus.MANUAL_REVIEW);
        fenced.setCancellationLastError("manual review again");
        fenced.setCancellationClaimId("stale-owner");
        fenced.setCancellationClaimUntil(clock.instant().plusSeconds(60));
        outboxEventEntityRepository.saveAndFlush(fenced);

        assertThatThrownBy(
                () -> redriveOrderCancellationInPort.redrive(
                        new OrderCancellationRedriveCommand(
                                "cmd-" + compactUuid(),
                                orderNumber,
                                "operator-a",
                                "must not steal lease")))
                .isInstanceOf(OrderCancellationRedriveConflictException.class)
                .hasMessageContaining("ownership marker");
    }

    private OutboxEventEntity reloadEvent(final String orderNumber) {
        return outboxEventEntityRepository.findAll()
                .stream()
                .filter(candidate -> orderNumber.equals(candidate.getOrderNumber()))
                .findFirst()
                .orElseThrow();
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static OrderResource request(final String sku) {
        return new OrderResource(
                "S22-07c2 cancellation redrive",
                Instant.now(),
                new CustomerResource(
                        "Redrive Operator Test",
                        UUID.randomUUID() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "redrive fixture", BigDecimal.TEN, 1, null)),
                PaymentMethod.CARD,
                null);
    }
}
