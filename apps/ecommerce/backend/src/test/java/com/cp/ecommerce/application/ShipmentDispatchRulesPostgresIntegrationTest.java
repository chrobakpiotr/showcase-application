package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cp.ecommerce.adapter.persistence.inventory.entity.StockReservationEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockReservationStatus;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.application.shipment.ShipmentWorkflow;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
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

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000",
                "resilience4j.ratelimiter.instances.cancelOrder.limit-for-period=1000" })
class ShipmentDispatchRulesPostgresIntegrationTest {

    private static final BigDecimal PRICE = new BigDecimal("20.00");
    private static final BigDecimal PARTIAL_REFUND = new BigDecimal("5.00");

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
    private ManagePaymentInPort managePaymentInPort;

    @Autowired
    private ManageOrderUseCase manageOrderUseCase;

    @Autowired
    private ShipmentWorkflow shipmentWorkflow;

    @Autowired
    private ShipmentEntityRepository shipmentRepository;

    @Autowired
    private StockReservationEntityRepository stockReservationRepository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void legacyDispatchMustRejectPartiallyRefundedPaymentWithoutMutatingShipmentOrStock() {
        final String sku = shortSku("S24A");
        catalogProductFixture.ensureActiveProduct(sku, "S22-04a dispatch rules", PRICE);
        manageStockInPort.receiveStock(sku, 1);

        final String orderNumber = orderController.placeOrder(orderRequest(sku), UUID.randomUUID().toString()).orderNumber();
        managePaymentInPort.capturePayment(orderNumber, PRICE, PaymentMethod.CARD);

        final Order order = manageOrderUseCase.findOrder(orderNumber);
        assertThat(order).isNotNull();
        assertThat(order.getStockReservationId()).isNotBlank();

        final Shipment shipment = shipmentWorkflow.createShipment(orderNumber, "S22-04A-CARRIER");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PENDING);

        assertThat(managePaymentInPort.refundPayment(orderNumber, "S24-REF-" + compactUuid(), PARTIAL_REFUND).getStatus())
                .isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        final String reservationKey = order.getStockReservationId() + ":" + sku;
        assertThat(stockReservationRepository.findById(reservationKey).orElseThrow().getStatus())
                .isEqualTo(StockReservationStatus.RESERVED);

        assertThatThrownBy(() -> shipmentWorkflow.advanceShipment(shipment.getShipmentNumber()))
                .as("legacy dispatch must enforce the same CAPTURED-payment rule as operation-aware dispatch")
                .isInstanceOf(ApplicationConflictException.class)
                .hasMessageContaining("Only CAPTURED orders can be dispatched");

        assertThat(shipmentRepository.findByShipmentNumber(shipment.getShipmentNumber()).getStatus())
                .as("rejected legacy dispatch must roll back shipment transition")
                .isEqualTo(ShipmentStatus.PENDING);
        assertThat(stockReservationRepository.findById(reservationKey).orElseThrow().getStatus())
                .as("rejected legacy dispatch must keep reservation stock untouched")
                .isEqualTo(StockReservationStatus.RESERVED);
    }

    private static OrderResource orderRequest(final String sku) {
        return new OrderResource(
                "S22-04a dispatch rules",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                new CustomerResource(
                        "S22-04a Buyer",
                        compactUuid() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "S22-04a dispatch rules", PRICE, 1, null)),
                PaymentMethod.CARD,
                null);
    }

    private static String shortSku(final String prefix) {
        return prefix + "-" + compactUuid();
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
