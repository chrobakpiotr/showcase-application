package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.cp.ecommerce.adapter.amqp.order.MessageListener;
import com.cp.ecommerce.adapter.amqp.order.SendOrderMessageAdapter;
import com.cp.ecommerce.adapter.amqp.order.mapper.OrderMessageMapper;
import com.cp.ecommerce.adapter.common.configuration.GsonConfiguration;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.fulfillment.OrderFulfillmentReceiptEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OrderPlacementBestEffortTail;
import com.cp.ecommerce.adapter.persistence.order.outbox.OrderPlacementSagaOrchestrator;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntity;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.OrderMessagePublishOutcome;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.GetResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionOperations;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.QUEUE_NAME;
import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.ROUTING_KEY;
import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.TOPIC_EXCHANGE_NAME;

@SpringBootTest(classes = EcommerceApplication.class)
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "service.rabbitmq.enabled=false",
                "outbox.publisher.enabled=false",
                "payment.reconciliation.enabled=false",
                "notification.retry.enabled=false",
                "order.cancellation.recovery.poll-interval-ms=3600000" })
class RabbitMqFulfillmentDeliveryIntegrationTest {

    private static final String TAKEOVER_OWNER = "rabbit-takeover-owner";
    private static final Instant CREATED = Instant.parse("2026-09-24T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"))
            .withAdminUser("sa")
            .withAdminPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private OutboxEventEntityRepository outboxRepository;

    @Autowired
    private OrderFulfillmentReceiptEntityRepository receiptRepository;

    @Autowired
    private PaymentTransactionEntityRepository paymentRepository;

    @Autowired
    private MessageListener messageListener;

    @Autowired
    private TransactionOperations transactionOperations;

    private final Gson gson = new GsonConfiguration().gson();

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetDatabaseState() {
        receiptRepository.deleteAll();
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void acceptedPublishCanBeRepublishedAfterOwnerLossWithoutDuplicateReceiptOrFinanceMutation() throws Exception {

        final Order order = uniqueOrder();
        final String operationId = "ORDER-FULFILLMENT:" + order.getOrderNumber();
        final String gatewayReference = seedCapturedPayment(order);
        final OutboxEventEntity event = outboxRepository.saveAndFlush(
                OutboxEventEntity.builder()
                        .orderNumber(order.getOrderNumber())
                        .status(OutboxEventStatus.PENDING)
                        .createdDate(CREATED)
                        .nextAttemptDate(Instant.EPOCH)
                        .build());

        final ManageOrderInPort manageOrder = mock(ManageOrderInPort.class);
        final OrderPlacementBestEffortTail bestEffortTail = mock(OrderPlacementBestEffortTail.class);
        final CancelOrderInPort cancelOrder = mock(CancelOrderInPort.class);
        final ManageStockInPort manageStock = mock(ManageStockInPort.class);
        final ManagePaymentInPort managePayment = mock(ManagePaymentInPort.class);
        final AtomicInteger publishCalls = new AtomicInteger();
        final List<String> publishedOperationIds = new ArrayList<>();

        when(manageOrder.findOrder(order.getOrderNumber())).thenReturn(order);
        when(managePayment.capturePayment(eq(order.getOrderNumber()), eq(order.getTotal()), eq(order.getPaymentMethod())))
                .thenReturn(
                        PaymentTransaction.builder()
                                .orderNumber(order.getOrderNumber())
                                .status(PaymentStatus.CAPTURED)
                                .build());

        try (PublisherHarness publisher = publisherHarness()) {
            publisher.purge();

            final SendMessageInPort realBrokerSender = (publishedOrder, stableOperationId) -> {
                publishedOperationIds.add(stableOperationId);
                final OrderMessagePublishOutcome outcome = publisher.adapter().send(publishedOrder, stableOperationId);
                if (publishCalls.incrementAndGet() == 1) {
                    forcePlacementTakeover(event.getId());
                }
                return outcome;
            };

            final OrderPlacementSagaOrchestrator orchestrator = new OrderPlacementSagaOrchestrator(
                    outboxRepository,
                    manageOrder,
                    realBrokerSender,
                    bestEffortTail,
                    cancelOrder,
                    manageStock,
                    managePayment,
                    transactionOperations,
                    new SagaMetrics(new SimpleMeterRegistry()),
                    Clock.systemUTC());

            orchestrator.publishPendingEvents();

            final OutboxEventEntity afterOwnerLoss = outboxRepository.findById(event.getId()).orElseThrow();
            assertThat(afterOwnerLoss.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
            assertThat(afterOwnerLoss.getClaimId()).isEqualTo(TAKEOVER_OWNER);

            expireTakeoverLease(event.getId());
            orchestrator.publishPendingEvents();

            assertThat(publishedOperationIds).containsExactly(operationId, operationId);
            assertThat(outboxRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.SENT);

            consumeAndAckTwoCopies(operationId);
        }

        assertSingleReceipt(operationId);
        assertCapturedPaymentUnchanged(order.getOrderNumber(), gatewayReference);
        verifyNoInteractions(cancelOrder);
        verify(managePayment, never()).refundPayment(order.getOrderNumber());
    }

    @Test
    void committedReceiptMustSurviveAckLossAndBrokerRedeliveryWithoutFinanceMutation() throws Exception {

        final Order order = uniqueOrder();
        final String operationId = "ORDER-FULFILLMENT:" + order.getOrderNumber();
        final String gatewayReference = seedCapturedPayment(order);

        try (PublisherHarness publisher = publisherHarness()) {
            publisher.purge();

            assertThat(publisher.adapter().send(order, operationId)).isEqualTo(OrderMessagePublishOutcome.ACCEPTED);

            try (Connection firstConnection = rawConnection(); Channel firstChannel = firstConnection.createChannel()) {
                final GetResponse firstDelivery = firstChannel.basicGet(QUEUE_NAME, false);
                assertThat(firstDelivery).isNotNull();
                assertThat(firstDelivery.getEnvelope().isRedeliver()).isFalse();

                messageListener.receiveMessage(body(firstDelivery.getBody()));
                assertSingleReceipt(operationId);

                // Intentionally close the connection without ACK: the durable receipt is already committed,
                // but RabbitMQ must requeue this unacked delivery.
            }

            final CountDownLatch redeliveryCommitted = new CountDownLatch(1);
            final AtomicReference<Delivery> redelivery = new AtomicReference<>();
            final AtomicReference<Throwable> callbackFailure = new AtomicReference<>();

            try (Connection secondConnection = rawConnection(); Channel secondChannel = secondConnection.createChannel()) {
                secondChannel.basicConsume(QUEUE_NAME, false, (consumerTag, delivery) -> {
                    try {
                        redelivery.set(delivery);
                        messageListener.receiveMessage(body(delivery.getBody()));
                        secondChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (Throwable failure) {
                        callbackFailure.set(failure);
                    } finally {
                        redeliveryCommitted.countDown();
                    }
                }, consumerTag -> {
                });

                assertThat(redeliveryCommitted.await(10, TimeUnit.SECONDS))
                        .as("RabbitMQ should redeliver the unacked committed message after reconnect")
                        .isTrue();
            }

            assertThat(callbackFailure.get()).isNull();
            assertThat(redelivery.get()).isNotNull();
            assertThat(redelivery.get().getEnvelope().isRedeliver()).isTrue();
        }

        assertSingleReceipt(operationId);
        assertCapturedPaymentUnchanged(order.getOrderNumber(), gatewayReference);
    }

    private PublisherHarness publisherHarness() {

        final CachingConnectionFactory connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);

        final RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        final Queue queue = new Queue(QUEUE_NAME, true);
        final TopicExchange exchange = new TopicExchange(TOPIC_EXCHANGE_NAME, true, false);
        rabbitAdmin.declareExchange(exchange);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY));

        return new PublisherHarness(
                connectionFactory,
                rabbitAdmin,
                new SendOrderMessageAdapter(new OrderMessageMapper(), rabbitTemplate, gson));
    }

    private Connection rawConnection() throws Exception {

        final com.rabbitmq.client.ConnectionFactory connectionFactory = new com.rabbitmq.client.ConnectionFactory();
        connectionFactory.setHost(RABBIT.getHost());
        connectionFactory.setPort(RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        return connectionFactory.newConnection();
    }

    private void consumeAndAckTwoCopies(final String operationId) throws Exception {

        try (Connection connection = rawConnection(); Channel channel = connection.createChannel()) {
            for (int delivery = 0; delivery < 2; delivery++) {
                final GetResponse response = channel.basicGet(QUEUE_NAME, false);
                assertThat(response).isNotNull();

                final OrderMessage wireMessage = gson.fromJson(body(response.getBody()), OrderMessage.class);
                assertThat(wireMessage.operationId()).isEqualTo(operationId);

                messageListener.receiveMessage(body(response.getBody()));
                channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
            }

            assertThat(channel.basicGet(QUEUE_NAME, true)).isNull();
        }
    }

    private void forcePlacementTakeover(final Long eventId) {

        transactionOperations.executeWithoutResult(status -> outboxRepository.findByIdForUpdate(eventId).ifPresent(locked -> {
            locked.setStatus(OutboxEventStatus.PROCESSING);
            locked.setClaimId(TAKEOVER_OWNER);
            locked.setClaimUntil(Clock.systemUTC().instant().plusSeconds(60));
            outboxRepository.save(locked);
        }));
    }

    private void expireTakeoverLease(final Long eventId) {

        transactionOperations.executeWithoutResult(status -> outboxRepository.findByIdForUpdate(eventId).ifPresent(locked -> {
            locked.setClaimUntil(Instant.EPOCH);
            outboxRepository.save(locked);
        }));
    }

    private String seedCapturedPayment(final Order order) {

        final String gatewayReference = "gw-" + UUID.randomUUID();
        paymentRepository.saveAndFlush(
                PaymentTransactionEntity.builder()
                        .orderNumber(order.getOrderNumber())
                        .amount(order.getTotal())
                        .refundedAmount(BigDecimal.ZERO)
                        .status(PaymentStatus.CAPTURED)
                        .gatewayReference(gatewayReference)
                        .created(CREATED)
                        .build());
        return gatewayReference;
    }

    private void assertCapturedPaymentUnchanged(final String orderNumber, final String gatewayReference) {

        final PaymentTransactionEntity persisted = paymentRepository.findById(orderNumber).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(persisted.getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(persisted.getGatewayReference()).isEqualTo(gatewayReference);
        assertThat(paymentRepository.count()).isEqualTo(1L);
    }

    private void assertSingleReceipt(final String operationId) {

        assertThat(receiptRepository.findById(operationId)).isPresent();
        assertThat(receiptRepository.findAll().stream().filter(receipt -> operationId.equals(receipt.getOperationId())).count())
                .isEqualTo(1L);
    }

    private static Order uniqueOrder() throws Exception {

        final Order template = OrderBuilder.mockOrder();
        return Order.builder()
                .remarks(template.getRemarks())
                .orderNumber("RB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                .stockReservationId(template.getStockReservationId())
                .created(CREATED)
                .customer(
                        Customer.builder()
                                .id(1001L)
                                .contact(template.getCustomer().getContact())
                                .address(template.getCustomer().getAddress())
                                .build())
                .items(template.getItems())
                .status(template.getStatus())
                .paymentMethod(template.getPaymentMethod())
                .couponCode(template.getCouponCode())
                .discountAmount(template.getDiscountAmount())
                .build();
    }

    private static String body(final byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record PublisherHarness(CachingConnectionFactory connectionFactory, RabbitAdmin rabbitAdmin,
            SendOrderMessageAdapter adapter) implements AutoCloseable {

        void purge() {
            rabbitAdmin.purgeQueue(QUEUE_NAME, false);
        }

        @Override
        public void close() {
            connectionFactory.destroy();
        }
    }
}
