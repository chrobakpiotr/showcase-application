package com.cp.ecommerce.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.cp.ecommerce.adapter.persistence.order.fulfillment.OrderFulfillmentReceiptEntity;
import com.cp.ecommerce.adapter.persistence.order.fulfillment.OrderFulfillmentReceiptEntityRepository;
import com.cp.ecommerce.domain.order.OrderFulfillmentReceiptOutcome;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.port.incoming.ReceiveOrderMessageInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;

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

@SpringBootTest(classes = EcommerceApplication.class)
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "payment.reconciliation.enabled=false",
                "notification.retry.enabled=false",
                "order.cancellation.recovery.poll-interval-ms=3600000" })
class OrderFulfillmentReceiptPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private ReceiveOrderMessageInPort receiveOrderMessageInPort;

    @Autowired
    private OrderFulfillmentReceiptEntityRepository repository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void concurrentRedeliveryMustCreateOneDurableFulfillmentReceipt() throws Exception {

        final String operationId = "ORDER-FULFILLMENT:" + UUID.randomUUID();
        final OrderMessage message = message(operationId, "ORD-" + UUID.randomUUID());
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        final List<OrderFulfillmentReceiptOutcome> outcomes;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return receiveOrderMessageInPort.receive(message);
            });
            final var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return receiveOrderMessageInPort.receive(message);
            });
            ready.await();
            start.countDown();
            outcomes = List.of(first.get(), second.get());
        }

        assertThat(outcomes)
                .containsExactlyInAnyOrder(OrderFulfillmentReceiptOutcome.RECORDED, OrderFulfillmentReceiptOutcome.REPLAYED);

        final OrderFulfillmentReceiptEntity persisted = repository.findById(operationId).orElseThrow();
        assertThat(persisted.getOrderNumber()).isEqualTo(message.orderNumber());
        assertThat(persisted.getCustomerId()).isEqualTo(message.customerId());
        assertThat(persisted.getMessageCreated()).isEqualTo(message.created());
        assertThat(repository.findAll().stream().filter(receipt -> operationId.equals(receipt.getOperationId())).count())
                .isEqualTo(1L);
    }

    @Test
    void operationIdReuseWithDifferentPayloadMustFailClosed() {

        final String operationId = "ORDER-FULFILLMENT:" + UUID.randomUUID();
        final OrderMessage original = message(operationId, "ORD-" + UUID.randomUUID());
        final OrderMessage conflicting = message(operationId, "ORD-" + UUID.randomUUID());

        assertThat(receiveOrderMessageInPort.receive(original)).isEqualTo(OrderFulfillmentReceiptOutcome.RECORDED);

        assertThatThrownBy(() -> receiveOrderMessageInPort.receive(conflicting))
                .isInstanceOf(ApplicationConflictException.class)
                .hasMessageContaining(operationId);

        assertThat(repository.findById(operationId)).isPresent();
    }

    private static OrderMessage message(final String operationId, final String orderNumber) {
        return new OrderMessage(
                OrderMessage.SCHEMA_VERSION,
                operationId,
                Instant.parse("2026-09-24T12:00:00Z"),
                1001L,
                orderNumber);
    }
}
