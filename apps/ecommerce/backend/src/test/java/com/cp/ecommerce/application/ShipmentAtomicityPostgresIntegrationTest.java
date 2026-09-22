package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cp.ecommerce.adapter.persistence.inventory.entity.StockReservationEntity;
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
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.incoming.CreateShipmentInPort;

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
class ShipmentAtomicityPostgresIntegrationTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("20.00");

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
    private ManageOrderUseCase manageOrderUseCase;

    @Autowired
    private CreateShipmentInPort createShipmentInPort;

    @Autowired
    private ShipmentWorkflow shipmentWorkflow;

    @Autowired
    private ShipmentEntityRepository shipmentEntityRepository;

    @Autowired
    private StockReservationEntityRepository stockReservationEntityRepository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void legacyDispatchMustRollbackShipmentAndEarlierSkuWhenLaterSkuFails() {

        final String firstSku = shortSku("B04A");
        final String secondSku = shortSku("B04B");

        catalogProductFixture.ensureActiveProduct(firstSku, "B04 first", UNIT_PRICE);
        catalogProductFixture.ensureActiveProduct(secondSku, "B04 second", UNIT_PRICE);
        manageStockInPort.receiveStock(firstSku, 1);
        manageStockInPort.receiveStock(secondSku, 1);

        final String orderNumber = orderController.placeOrder(orderRequest(firstSku, secondSku), UUID.randomUUID().toString())
                .orderNumber();

        final Order order = manageOrderUseCase.findOrder(orderNumber);
        assertThat(order).isNotNull();
        assertThat(order.getStockReservationId()).isNotBlank();

        final String reservationId = order.getStockReservationId();
        final String firstKey = reservationId + ":" + firstSku;
        final String secondKey = reservationId + ":" + secondSku;

        final StockReservationEntity firstBefore = stockReservationEntityRepository.findById(firstKey).orElseThrow();
        final StockReservationEntity secondBefore = stockReservationEntityRepository.findById(secondKey).orElseThrow();

        assertThat(firstBefore.getStatus()).isEqualTo(StockReservationStatus.RESERVED);
        assertThat(secondBefore.getStatus()).isEqualTo(StockReservationStatus.RESERVED);

        final Shipment shipment = createShipmentInPort.createShipment(orderNumber, "B04-CARRIER");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PENDING);

        secondBefore.setStatus(StockReservationStatus.RELEASED);
        stockReservationEntityRepository.saveAndFlush(secondBefore);

        assertThatThrownBy(() -> shipmentWorkflow.advanceShipment(shipment.getShipmentNumber()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only RESERVED stock can be fulfilled");

        assertThat(shipmentEntityRepository.findByShipmentNumber(shipment.getShipmentNumber()).getStatus())
                .as("legacy dispatch must roll back shipment state when a later SKU cannot be fulfilled")
                .isEqualTo(ShipmentStatus.PENDING);

        assertThat(stockReservationEntityRepository.findById(firstKey).orElseThrow().getStatus())
                .as("legacy dispatch must roll back earlier SKU fulfillment when a later SKU fails")
                .isEqualTo(StockReservationStatus.RESERVED);
    }

    private static OrderResource orderRequest(final String firstSku, final String secondSku) {

        return new OrderResource(
                "B04 shipment atomicity",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                new CustomerResource(
                        "B04 Buyer",
                        compactUuid() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(
                        new OrderLineItemResource(firstSku, "B04 first", UNIT_PRICE, 1, null),
                        new OrderLineItemResource(secondSku, "B04 second", UNIT_PRICE, 1, null)),
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
