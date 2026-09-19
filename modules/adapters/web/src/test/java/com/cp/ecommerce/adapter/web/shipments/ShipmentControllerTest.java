package com.cp.ecommerce.adapter.web.shipments;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.ShipmentConflictException;
import com.cp.ecommerce.adapter.common.utils.CustomerBuilder;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.common.utils.ShipmentBuilder;
import com.cp.ecommerce.adapter.web.exception.GlobalExceptionHandler;
import com.cp.ecommerce.adapter.web.shipments.mapper.ShipmentWebMapper;
import com.cp.ecommerce.adapter.web.shipments.resource.CreateShipmentResource;
import com.cp.ecommerce.adapter.web.shipments.resource.ShipmentResource;
import com.cp.ecommerce.application.shipment.ShipmentService;
import com.cp.ecommerce.application.shipment.ShipmentWorkflow;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.incoming.AdvanceShipmentStatusInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.CreateShipmentInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.GetShipmentInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.ListShipmentsInPort;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class checking shipment controller's behavior and API responses.
 */
@WebMvcTest(ShipmentController.class)
@Import({ GlobalExceptionHandler.class, ShipmentService.class })
class ShipmentControllerTest {

    private static final String SHIPMENTS_ENDPOINT = "/api/shipments";
    private static final String ADVANCE_PATH_SUFFIX = "/advance";

    @Autowired
    private transient MockMvc mockMvc;

    @Autowired
    private transient ShipmentWorkflow shipmentWorkflow;

    @MockitoBean
    private transient CreateShipmentInPort createShipmentInPort;

    @MockitoBean
    private transient AdvanceShipmentStatusInPort advanceShipmentStatusInPort;

    @MockitoBean
    private transient GetShipmentInPort getShipmentInPort;

    @MockitoBean
    private transient ListShipmentsInPort listShipmentsInPort;

    @MockitoBean
    private transient ManageOrderUseCase manageOrderUseCase;

    @MockitoBean
    private transient SendNotificationInPort sendNotificationInPort;

    @MockitoBean
    private transient ShipmentWebMapper shipmentWebMapper;

    @Test
    void shouldListShipments() throws Exception {

        final Shipment shipment = ShipmentBuilder.mockShipment();
        given(listShipmentsInPort.listShipments()).willReturn(List.of(shipment));
        given(shipmentWebMapper.mapToResource(shipment)).willReturn(Optional.of(toResource(shipment)));

        mockMvc.perform(get(SHIPMENTS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$._embedded.shipmentResourceList[0].shipmentNumber")
                                .value(ShipmentBuilder.TEST_SHIPMENT_NUMBER))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/hal+json")));
    }

    @Test
    void shouldListShipmentsForOrder() throws Exception {

        final Shipment shipment = ShipmentBuilder.mockShipment();
        given(listShipmentsInPort.listShipmentsForOrder(ShipmentBuilder.TEST_ORDER_NUMBER)).willReturn(List.of(shipment));
        given(shipmentWebMapper.mapToResource(shipment)).willReturn(Optional.of(toResource(shipment)));

        mockMvc.perform(get(SHIPMENTS_ENDPOINT + "/order/" + ShipmentBuilder.TEST_ORDER_NUMBER))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$._embedded.shipmentResourceList[0].orderNumber").value(ShipmentBuilder.TEST_ORDER_NUMBER));
    }

    @Test
    void shouldListShipmentsByStatus() throws Exception {

        final Shipment shipment = ShipmentBuilder.mockShipment();
        given(listShipmentsInPort.listShipmentsByStatus(ShipmentStatus.PENDING)).willReturn(List.of(shipment));
        given(shipmentWebMapper.mapToResource(shipment)).willReturn(Optional.of(toResource(shipment)));

        mockMvc.perform(get(SHIPMENTS_ENDPOINT + "/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$._embedded.shipmentResourceList[0]._links['advance-status'].href",
                                endsWith(SHIPMENTS_ENDPOINT + "/SHIP-1234/advance")));
    }

    @Test
    void shouldGetShipment() throws Exception {

        final Shipment shipment = ShipmentBuilder.mockShipment();
        given(getShipmentInPort.getShipment(ShipmentBuilder.TEST_SHIPMENT_NUMBER)).willReturn(shipment);
        given(shipmentWebMapper.mapToResource(shipment)).willReturn(Optional.of(toResource(shipment)));

        mockMvc.perform(get(SHIPMENTS_ENDPOINT + "/" + ShipmentBuilder.TEST_SHIPMENT_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentNumber").value(ShipmentBuilder.TEST_SHIPMENT_NUMBER));
    }

    @Test
    void shouldReturnNotFoundWhenShipmentDoesNotExist() throws Exception {

        given(getShipmentInPort.getShipment(ShipmentBuilder.TEST_SHIPMENT_NUMBER)).willReturn(null);

        mockMvc.perform(get(SHIPMENTS_ENDPOINT + "/" + ShipmentBuilder.TEST_SHIPMENT_NUMBER)).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateShipment() throws Exception {

        final Order order = orderWithNumber(OrderBuilder.mockOrder());
        final Shipment created = ShipmentBuilder.mockShipment();
        given(manageOrderUseCase.findOrder(ShipmentBuilder.TEST_ORDER_NUMBER)).willReturn(order);
        given(createShipmentInPort.createShipment(ShipmentBuilder.TEST_ORDER_NUMBER, ShipmentBuilder.TEST_CARRIER))
                .willReturn(created);
        given(shipmentWebMapper.mapToResource(created)).willReturn(Optional.of(toResource(created)));

        mockMvc.perform(
                post(SHIPMENTS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentJson(ShipmentBuilder.TEST_ORDER_NUMBER, ShipmentBuilder.TEST_CARRIER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipmentNumber").value(ShipmentBuilder.TEST_SHIPMENT_NUMBER));
    }

    @Test
    void shouldRejectCreateWhenPayloadMissing() throws Exception {

        mockMvc.perform(post(SHIPMENTS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(createShipmentInPort, never()).createShipment(anyString(), anyString());
    }

    @Test
    void shouldRejectCreateWhenRequestBodyIsMissing() {

        final ShipmentController controller = new ShipmentController(
                getShipmentInPort,
                shipmentWorkflow,
                listShipmentsInPort,
                shipmentWebMapper);

        assertThatThrownBy(() -> controller.createShipment(null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectCreateWhenCarrierIsMissing() {

        final ShipmentController controller = new ShipmentController(
                getShipmentInPort,
                shipmentWorkflow,
                listShipmentsInPort,
                shipmentWebMapper);

        assertThatThrownBy(
                () -> controller.createShipment(
                        CreateShipmentResource.builder().orderNumber(ShipmentBuilder.TEST_ORDER_NUMBER).carrier(null).build()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectCreateWhenOrderNumberIsBlank() {

        final ShipmentController controller = new ShipmentController(
                getShipmentInPort,
                shipmentWorkflow,
                listShipmentsInPort,
                shipmentWebMapper);

        assertThatThrownBy(
                () -> controller.createShipment(
                        CreateShipmentResource.builder().orderNumber(" ").carrier(ShipmentBuilder.TEST_CARRIER).build()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldReturnNotFoundWhenOrderDoesNotExist() throws Exception {

        given(manageOrderUseCase.findOrder(ShipmentBuilder.TEST_ORDER_NUMBER)).willReturn(null);

        mockMvc.perform(
                post(SHIPMENTS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentJson(ShipmentBuilder.TEST_ORDER_NUMBER, ShipmentBuilder.TEST_CARRIER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreateWhenOrderIsNotConfirmed() throws Exception {

        final Order cancelledOrder = Order.builder()
                .remarks(OrderBuilder.TEST_REMARKS)
                .orderNumber(ShipmentBuilder.TEST_ORDER_NUMBER)
                .created(OrderBuilder.mockOrder().getCreated())
                .customer(OrderBuilder.mockOrder().getCustomer())
                .items(List.of(OrderBuilder.mockOrderLineItem()))
                .status(OrderStatus.CANCELLED)
                .paymentMethod(OrderBuilder.TEST_PAYMENT_METHOD)
                .build();
        given(manageOrderUseCase.findOrder(ShipmentBuilder.TEST_ORDER_NUMBER)).willReturn(cancelledOrder);

        mockMvc.perform(
                post(SHIPMENTS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentJson(ShipmentBuilder.TEST_ORDER_NUMBER, ShipmentBuilder.TEST_CARRIER)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnConflictWhenShipmentAlreadyExistsForOrder() throws Exception {

        given(manageOrderUseCase.findOrder(ShipmentBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));
        given(createShipmentInPort.createShipment(ShipmentBuilder.TEST_ORDER_NUMBER, ShipmentBuilder.TEST_CARRIER))
                .willThrow(new ShipmentConflictException("already exists"));

        mockMvc.perform(
                post(SHIPMENTS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentJson(ShipmentBuilder.TEST_ORDER_NUMBER, ShipmentBuilder.TEST_CARRIER)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAdvanceShipmentAndSendDispatchedNotification() throws Exception {

        final Shipment dispatched = Shipment.builder()
                .shipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER)
                .orderNumber(ShipmentBuilder.TEST_ORDER_NUMBER)
                .carrier(ShipmentBuilder.TEST_CARRIER)
                .trackingNumber(ShipmentBuilder.TEST_TRACKING_NUMBER)
                .status(ShipmentStatus.DISPATCHED)
                .createdDate(ShipmentBuilder.TEST_CREATED_DATE)
                .dispatchedDate(ShipmentBuilder.TEST_DISPATCHED_DATE)
                .estimatedDeliveryDate(ShipmentBuilder.TEST_ESTIMATED_DELIVERY_DATE)
                .build();
        given(advanceShipmentStatusInPort.advanceShipmentStatus(ShipmentBuilder.TEST_SHIPMENT_NUMBER)).willReturn(dispatched);
        given(manageOrderUseCase.findOrder(ShipmentBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));
        given(shipmentWebMapper.mapToResource(dispatched)).willReturn(Optional.of(toResource(dispatched)));

        mockMvc.perform(post(SHIPMENTS_ENDPOINT + "/" + ShipmentBuilder.TEST_SHIPMENT_NUMBER + ADVANCE_PATH_SUFFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        verify(sendNotificationInPort).sendNotification(
                eq(CustomerBuilder.TEST_EMAIL),
                eq(NotificationType.SHIPMENT_DISPATCHED),
                eq("Shipment SHIP-1234 dispatched"),
                eq("Your shipment SHIP-1234 was dispatched. Tracking number: DHL-TRACK-1234."));
    }

    @Test
    void shouldAdvanceShipmentAndSendDeliveredNotification() throws Exception {

        final Shipment delivered = Shipment.builder()
                .shipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER)
                .orderNumber(ShipmentBuilder.TEST_ORDER_NUMBER)
                .carrier(ShipmentBuilder.TEST_CARRIER)
                .trackingNumber(ShipmentBuilder.TEST_TRACKING_NUMBER)
                .status(ShipmentStatus.DELIVERED)
                .createdDate(ShipmentBuilder.TEST_CREATED_DATE)
                .dispatchedDate(ShipmentBuilder.TEST_DISPATCHED_DATE)
                .estimatedDeliveryDate(ShipmentBuilder.TEST_ESTIMATED_DELIVERY_DATE)
                .deliveredDate(ShipmentBuilder.TEST_DELIVERED_DATE)
                .build();
        given(advanceShipmentStatusInPort.advanceShipmentStatus(ShipmentBuilder.TEST_SHIPMENT_NUMBER)).willReturn(delivered);
        given(manageOrderUseCase.findOrder(ShipmentBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));
        given(shipmentWebMapper.mapToResource(delivered)).willReturn(Optional.of(toResource(delivered)));

        mockMvc.perform(post(SHIPMENTS_ENDPOINT + "/" + ShipmentBuilder.TEST_SHIPMENT_NUMBER + ADVANCE_PATH_SUFFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        verify(sendNotificationInPort).sendNotification(
                eq(CustomerBuilder.TEST_EMAIL),
                eq(NotificationType.SHIPMENT_DELIVERED),
                eq("Shipment SHIP-1234 delivered"),
                eq("Your shipment SHIP-1234 was delivered."));
    }

    @Test
    void shouldAdvanceShipmentWithoutSendingNotificationForInTransit() throws Exception {

        final Shipment inTransit = Shipment.builder()
                .shipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER)
                .orderNumber(ShipmentBuilder.TEST_ORDER_NUMBER)
                .carrier(ShipmentBuilder.TEST_CARRIER)
                .trackingNumber(ShipmentBuilder.TEST_TRACKING_NUMBER)
                .status(ShipmentStatus.IN_TRANSIT)
                .createdDate(ShipmentBuilder.TEST_CREATED_DATE)
                .dispatchedDate(ShipmentBuilder.TEST_DISPATCHED_DATE)
                .estimatedDeliveryDate(ShipmentBuilder.TEST_ESTIMATED_DELIVERY_DATE)
                .build();
        given(advanceShipmentStatusInPort.advanceShipmentStatus(ShipmentBuilder.TEST_SHIPMENT_NUMBER)).willReturn(inTransit);
        given(shipmentWebMapper.mapToResource(inTransit)).willReturn(Optional.of(toResource(inTransit)));

        mockMvc.perform(post(SHIPMENTS_ENDPOINT + "/" + ShipmentBuilder.TEST_SHIPMENT_NUMBER + ADVANCE_PATH_SUFFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        verify(sendNotificationInPort, never())
                .sendNotification(anyString(), eq(NotificationType.SHIPMENT_DISPATCHED), anyString(), anyString());
        verify(sendNotificationInPort, never())
                .sendNotification(anyString(), eq(NotificationType.SHIPMENT_DELIVERED), anyString(), anyString());
    }

    @Test
    void shouldReturnNotFoundWhenAdvancingUnknownShipment() throws Exception {

        given(advanceShipmentStatusInPort.advanceShipmentStatus(ShipmentBuilder.TEST_SHIPMENT_NUMBER)).willReturn(null);

        mockMvc.perform(post(SHIPMENTS_ENDPOINT + "/" + ShipmentBuilder.TEST_SHIPMENT_NUMBER + ADVANCE_PATH_SUFFIX))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenOrderCannotBeLoadedForNotification() throws Exception {

        final Shipment dispatched = Shipment.builder()
                .shipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER)
                .orderNumber(ShipmentBuilder.TEST_ORDER_NUMBER)
                .carrier(ShipmentBuilder.TEST_CARRIER)
                .trackingNumber(ShipmentBuilder.TEST_TRACKING_NUMBER)
                .status(ShipmentStatus.DISPATCHED)
                .createdDate(ShipmentBuilder.TEST_CREATED_DATE)
                .dispatchedDate(ShipmentBuilder.TEST_DISPATCHED_DATE)
                .estimatedDeliveryDate(ShipmentBuilder.TEST_ESTIMATED_DELIVERY_DATE)
                .build();
        given(advanceShipmentStatusInPort.advanceShipmentStatus(ShipmentBuilder.TEST_SHIPMENT_NUMBER)).willReturn(dispatched);
        given(manageOrderUseCase.findOrder(ShipmentBuilder.TEST_ORDER_NUMBER)).willReturn(null);

        mockMvc.perform(post(SHIPMENTS_ENDPOINT + "/" + ShipmentBuilder.TEST_SHIPMENT_NUMBER + ADVANCE_PATH_SUFFIX))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnInternalServerErrorWhenShipmentCannotBeMapped() throws Exception {

        final Shipment shipment = ShipmentBuilder.mockShipment();
        given(getShipmentInPort.getShipment(ShipmentBuilder.TEST_SHIPMENT_NUMBER)).willReturn(shipment);
        given(shipmentWebMapper.mapToResource(shipment)).willReturn(Optional.empty());

        mockMvc.perform(get(SHIPMENTS_ENDPOINT + "/" + ShipmentBuilder.TEST_SHIPMENT_NUMBER))
                .andExpect(status().isInternalServerError());
    }

    private static ShipmentResource toResource(final Shipment shipment) {

        return ShipmentResource.builder()
                .shipmentNumber(shipment.getShipmentNumber())
                .orderNumber(shipment.getOrderNumber())
                .carrier(shipment.getCarrier())
                .trackingNumber(shipment.getTrackingNumber())
                .status(shipment.getStatus().name())
                .dispatchedDate(shipment.getDispatchedDate())
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .deliveredDate(shipment.getDeliveredDate())
                .createdDate(shipment.getCreatedDate())
                .build();
    }

    private static String createShipmentJson(final String orderNumber, final String carrier) {

        return "{\"orderNumber\":\"" + orderNumber + "\",\"carrier\":\"" + carrier + "\"}";
    }

    private static Order orderWithNumber(final Order order) {

        return Order.builder()
                .remarks(order.getRemarks())
                .orderNumber(ShipmentBuilder.TEST_ORDER_NUMBER)
                .created(order.getCreated())
                .customer(order.getCustomer())
                .items(order.getItems())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .build();
    }

}
