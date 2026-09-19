package com.cp.ecommerce.adapter.web.returns;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.ReturnQuantityConflictException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotApprovableException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotRefundableException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotRejectableException;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder;
import com.cp.ecommerce.adapter.web.returns.mapper.ReturnWebMapper;
import com.cp.ecommerce.adapter.web.returns.resource.RequestReturnResource;
import com.cp.ecommerce.adapter.web.returns.resource.ReturnRequestResource;
import com.cp.ecommerce.application.returns.ReturnService;
import com.cp.ecommerce.application.returns.ReturnWorkflow;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ListReturnsInPort;
import com.cp.ecommerce.domain.returns.port.incoming.RequestReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * Test class checking return controller's behavior and API responses.
 */
@WebMvcTest(ReturnController.class)
@Import(ReturnService.class)
class ReturnControllerTest {

    private static final String RETURNS_ENDPOINT = "/api/returns";
    private static final String APPROVE_ENDPOINT = "/approve";
    private static final String REJECT_ENDPOINT = "/reject";
    private static final String STATUS_JSON_PATH = "$.status";

    @Autowired
    private transient MockMvc mockMvc;

    @Autowired
    private transient ReturnWorkflow returnWorkflow;

    @MockitoBean
    private transient RequestReturnInPort requestReturnInPort;

    @MockitoBean
    private transient GetReturnInPort getReturnInPort;

    @MockitoBean
    private transient ListReturnsInPort listReturnsInPort;

    @MockitoBean
    private transient ReturnModerationInPort returnModerationInPort;

    @MockitoBean
    private transient ManageOrderUseCase manageOrderUseCase;

    @MockitoBean
    private transient ManagePaymentInPort managePaymentInPort;

    @MockitoBean
    private transient SendNotificationInPort sendNotificationInPort;

    @MockitoBean
    private transient ReturnWebMapper returnWebMapper;

    @Test
    void shouldListReturns() throws Exception {

        final ReturnRequest returnRequest = ReturnRequestBuilder.mockReturnRequest();
        given(listReturnsInPort.listReturns()).willReturn(List.of(returnRequest));
        given(returnWebMapper.mapToResource(returnRequest)).willReturn(Optional.of(mockReturnRequestResource()));

        mockMvc.perform(get(RETURNS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$._embedded.returnRequestResourceList[0].returnNumber")
                                .value(ReturnRequestBuilder.TEST_RETURN_NUMBER))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/hal+json")));
    }

    @Test
    void shouldListPendingReturns() throws Exception {

        final ReturnRequest returnRequest = ReturnRequestBuilder.mockReturnRequest();
        given(listReturnsInPort.listPendingReturns()).willReturn(List.of(returnRequest));
        given(returnWebMapper.mapToResource(returnRequest)).willReturn(Optional.of(mockReturnRequestResource()));

        mockMvc.perform(get(RETURNS_ENDPOINT + "/pending"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$._embedded.returnRequestResourceList[0]._links.approve.href",
                                endsWith("/api/returns/RETURN-1234/approve")));
    }

    @Test
    void shouldListReturnsForOrder() throws Exception {

        final ReturnRequest returnRequest = ReturnRequestBuilder.mockReturnRequest();
        given(listReturnsInPort.listReturnsForOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER)).willReturn(List.of(returnRequest));
        given(returnWebMapper.mapToResource(returnRequest)).willReturn(Optional.of(mockReturnRequestResource()));

        mockMvc.perform(get(RETURNS_ENDPOINT + "/order/" + ReturnRequestBuilder.TEST_ORDER_NUMBER))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$._embedded.returnRequestResourceList[0].orderNumber")
                                .value(ReturnRequestBuilder.TEST_ORDER_NUMBER));
    }

    @Test
    void shouldGetReturn() throws Exception {

        final ReturnRequest returnRequest = ReturnRequestBuilder.mockReturnRequest();
        given(getReturnInPort.getReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(returnRequest);
        given(returnWebMapper.mapToResource(returnRequest)).willReturn(Optional.of(mockReturnRequestResource()));

        mockMvc.perform(get(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnNumber").value(ReturnRequestBuilder.TEST_RETURN_NUMBER));
    }

    @Test
    void shouldReturnNotFoundWhenReturnDoesNotExist() throws Exception {

        given(getReturnInPort.getReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(null);

        mockMvc.perform(get(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER)).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateReturnRequest() throws Exception {

        final Order order = OrderBuilder.mockOrder();
        final ReturnRequest created = ReturnRequestBuilder.mockReturnRequest();
        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER)).willReturn(orderWithNumber(order));
        given(
                requestReturnInPort.requestReturn(
                        eq(ReturnRequestBuilder.TEST_ORDER_NUMBER),
                        eq(OrderBuilder.TEST_ORDER_LINE_ITEM_SKU),
                        eq(1),
                        eq(OrderBuilder.TEST_ORDER_LINE_ITEM_QUANTITY),
                        eq(ReturnRequestBuilder.TEST_REASON),
                        eq(OrderBuilder.TEST_ORDER_LINE_ITEM_UNIT_PRICE)))
                .willReturn(created);
        given(returnWebMapper.mapToResource(created)).willReturn(Optional.of(mockReturnRequestResource()));

        mockMvc.perform(
                post(RETURNS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                requestReturnJson(
                                        ReturnRequestBuilder.TEST_ORDER_NUMBER,
                                        OrderBuilder.TEST_ORDER_LINE_ITEM_SKU,
                                        1,
                                        ReturnRequestBuilder.TEST_REASON)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnNumber").value(ReturnRequestBuilder.TEST_RETURN_NUMBER));
    }

    @Test
    void shouldRejectCreateWhenPayloadMissing() throws Exception {

        mockMvc.perform(post(RETURNS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(requestReturnInPort, never()).requestReturn(any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void shouldRejectCreateWhenQuantityIsInvalid() throws Exception {

        mockMvc.perform(
                post(RETURNS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                requestReturnJson(
                                        ReturnRequestBuilder.TEST_ORDER_NUMBER,
                                        OrderBuilder.TEST_ORDER_LINE_ITEM_SKU,
                                        0,
                                        ReturnRequestBuilder.TEST_REASON)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectCreateWhenReasonIsBlank() throws Exception {

        mockMvc.perform(
                post(RETURNS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                requestReturnJson(
                                        ReturnRequestBuilder.TEST_ORDER_NUMBER,
                                        OrderBuilder.TEST_ORDER_LINE_ITEM_SKU,
                                        1,
                                        " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectCreateWhenQuantityIsNull() {

        final ReturnController controller = new ReturnController(
                getReturnInPort,
                returnWorkflow,
                listReturnsInPort,
                returnWebMapper);

        assertThatThrownBy(
                () -> controller.requestReturn(
                        RequestReturnResource.builder()
                                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                                .sku(OrderBuilder.TEST_ORDER_LINE_ITEM_SKU)
                                .quantity(null)
                                .reason(ReturnRequestBuilder.TEST_REASON)
                                .build()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectCreateWhenRequestBodyIsMissing() {

        final ReturnController controller = new ReturnController(
                getReturnInPort,
                returnWorkflow,
                listReturnsInPort,
                returnWebMapper);

        assertThatThrownBy(() -> controller.requestReturn(null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectCreateWhenSkuIsMissing() {

        final ReturnController controller = new ReturnController(
                getReturnInPort,
                returnWorkflow,
                listReturnsInPort,
                returnWebMapper);

        assertThatThrownBy(
                () -> controller.requestReturn(
                        RequestReturnResource.builder()
                                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                                .sku(null)
                                .quantity(1)
                                .reason(ReturnRequestBuilder.TEST_REASON)
                                .build()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectCreateWhenReasonIsMissing() {

        final ReturnController controller = new ReturnController(
                getReturnInPort,
                returnWorkflow,
                listReturnsInPort,
                returnWebMapper);

        assertThatThrownBy(
                () -> controller.requestReturn(
                        RequestReturnResource.builder()
                                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                                .sku(OrderBuilder.TEST_ORDER_LINE_ITEM_SKU)
                                .quantity(1)
                                .reason(null)
                                .build()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldReturnNotFoundWhenOrderDoesNotExist() throws Exception {

        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER)).willReturn(null);

        mockMvc.perform(
                post(RETURNS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                requestReturnJson(
                                        ReturnRequestBuilder.TEST_ORDER_NUMBER,
                                        OrderBuilder.TEST_ORDER_LINE_ITEM_SKU,
                                        1,
                                        ReturnRequestBuilder.TEST_REASON)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreateWhenOrderIsNotConfirmed() throws Exception {

        final Order cancelledOrder = Order.builder()
                .remarks(OrderBuilder.TEST_REMARKS)
                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                .created(OrderBuilder.mockOrder().getCreated())
                .customer(OrderBuilder.mockOrder().getCustomer())
                .items(List.of(OrderBuilder.mockOrderLineItem()))
                .status(OrderStatus.CANCELLED)
                .paymentMethod(OrderBuilder.TEST_PAYMENT_METHOD)
                .build();
        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER)).willReturn(cancelledOrder);

        mockMvc.perform(
                post(RETURNS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                requestReturnJson(
                                        ReturnRequestBuilder.TEST_ORDER_NUMBER,
                                        OrderBuilder.TEST_ORDER_LINE_ITEM_SKU,
                                        1,
                                        ReturnRequestBuilder.TEST_REASON)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenSkuIsNotPartOfOrder() throws Exception {

        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));

        mockMvc.perform(
                post(RETURNS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                requestReturnJson(
                                        ReturnRequestBuilder.TEST_ORDER_NUMBER,
                                        "SKU-999",
                                        1,
                                        ReturnRequestBuilder.TEST_REASON)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictWhenAtomicEntitlementCheckFails() throws Exception {

        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));
        given(
                requestReturnInPort.requestReturn(
                        eq(ReturnRequestBuilder.TEST_ORDER_NUMBER),
                        eq(OrderBuilder.TEST_ORDER_LINE_ITEM_SKU),
                        eq(1),
                        eq(OrderBuilder.TEST_ORDER_LINE_ITEM_QUANTITY),
                        eq(ReturnRequestBuilder.TEST_REASON),
                        eq(OrderBuilder.TEST_ORDER_LINE_ITEM_UNIT_PRICE)))
                .willThrow(new ReturnQuantityConflictException(0));

        mockMvc.perform(
                post(RETURNS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                requestReturnJson(
                                        ReturnRequestBuilder.TEST_ORDER_NUMBER,
                                        OrderBuilder.TEST_ORDER_LINE_ITEM_SKU,
                                        1,
                                        ReturnRequestBuilder.TEST_REASON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Return Quantity Conflict"));
    }

    @Test
    void shouldPassOrderedQuantityToAtomicReturnPort() throws Exception {

        final ReturnRequest created = ReturnRequestBuilder.mockReturnRequest();
        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));
        given(
                requestReturnInPort.requestReturn(
                        eq(ReturnRequestBuilder.TEST_ORDER_NUMBER),
                        eq(OrderBuilder.TEST_ORDER_LINE_ITEM_SKU),
                        eq(2),
                        eq(OrderBuilder.TEST_ORDER_LINE_ITEM_QUANTITY),
                        eq(ReturnRequestBuilder.TEST_REASON),
                        eq(BigDecimal.valueOf(59.98))))
                .willReturn(created);
        given(returnWebMapper.mapToResource(created)).willReturn(Optional.of(mockReturnRequestResource()));

        mockMvc.perform(
                post(RETURNS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                requestReturnJson(
                                        ReturnRequestBuilder.TEST_ORDER_NUMBER,
                                        OrderBuilder.TEST_ORDER_LINE_ITEM_SKU,
                                        2,
                                        ReturnRequestBuilder.TEST_REASON)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldNotUseWholeOrderRefundForPartialReturn() throws Exception {

        final ReturnRequest approved = TestReturnRequests.approved();
        final ReturnRequest refunded = TestReturnRequests.refunded();
        given(getReturnInPort.getReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.markRefunded(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(refunded);
        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));
        given(returnWebMapper.mapToResource(refunded)).willReturn(Optional.of(resourceWithStatus(ReturnStatus.REFUNDED)));

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + APPROVE_ENDPOINT))
                .andExpect(status().isOk());

        verify(managePaymentInPort, never()).refundPayment(ReturnRequestBuilder.TEST_ORDER_NUMBER);
    }

    @Test
    void shouldApproveReturnAndRefundPayment() throws Exception {

        final ReturnRequest approved = TestReturnRequests.approved();
        final ReturnRequest refunded = TestReturnRequests.refunded();
        given(getReturnInPort.getReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.markRefunded(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(refunded);
        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));
        given(returnWebMapper.mapToResource(refunded)).willReturn(Optional.of(resourceWithStatus(ReturnStatus.REFUNDED)));

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + APPROVE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath(STATUS_JSON_PATH).value("REFUNDED"));

        verify(managePaymentInPort).refundPayment(
                ReturnRequestBuilder.TEST_ORDER_NUMBER,
                ReturnRequestBuilder.TEST_RETURN_NUMBER,
                ReturnRequestBuilder.TEST_REFUND_AMOUNT);
        verify(sendNotificationInPort).sendNotification(
                "test@test.com",
                NotificationType.RETURN_REFUNDED,
                "Return " + ReturnRequestBuilder.TEST_RETURN_NUMBER + " refunded",
                "Your return request " + ReturnRequestBuilder.TEST_RETURN_NUMBER + " was refunded.");
    }

    @Test
    void shouldApproveAlreadyRefundedReturnWithoutRefundingPaymentAgain() throws Exception {

        final ReturnRequest refunded = TestReturnRequests.refunded();
        given(getReturnInPort.getReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(refunded);
        given(returnModerationInPort.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(refunded);
        given(returnModerationInPort.markRefunded(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(refunded);
        given(returnWebMapper.mapToResource(refunded)).willReturn(Optional.of(resourceWithStatus(ReturnStatus.REFUNDED)));

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + APPROVE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath(STATUS_JSON_PATH).value("REFUNDED"));

        verify(managePaymentInPort, never()).refundPayment(any());
        verify(sendNotificationInPort, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void shouldRejectReturn() throws Exception {

        final ReturnRequest rejected = TestReturnRequests.rejected();
        given(getReturnInPort.getReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER))
                .willReturn(ReturnRequestBuilder.mockReturnRequest());
        given(returnModerationInPort.rejectReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(rejected);
        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));
        given(returnWebMapper.mapToResource(rejected)).willReturn(Optional.of(resourceWithStatus(ReturnStatus.REJECTED)));

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + REJECT_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath(STATUS_JSON_PATH).value("REJECTED"));

        verify(sendNotificationInPort).sendNotification(
                "test@test.com",
                NotificationType.RETURN_REJECTED,
                "Return " + ReturnRequestBuilder.TEST_RETURN_NUMBER + " rejected",
                "Your return request " + ReturnRequestBuilder.TEST_RETURN_NUMBER + " was rejected.");
    }

    @Test
    void shouldRejectAlreadyRejectedReturnWithoutSendingNotificationAgain() throws Exception {

        final ReturnRequest rejected = TestReturnRequests.rejected();
        given(getReturnInPort.getReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(rejected);
        given(returnModerationInPort.rejectReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(rejected);
        given(returnWebMapper.mapToResource(rejected)).willReturn(Optional.of(resourceWithStatus(ReturnStatus.REJECTED)));

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + REJECT_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath(STATUS_JSON_PATH).value("REJECTED"));

        verify(sendNotificationInPort, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void shouldReturnNotFoundWhenApprovingUnknownReturn() throws Exception {

        given(returnModerationInPort.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(null);

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + APPROVE_ENDPOINT))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenRefundedReturnCannotBeReloaded() throws Exception {

        final ReturnRequest approved = TestReturnRequests.approved();
        given(returnModerationInPort.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.markRefunded(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(null);

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + APPROVE_ENDPOINT))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenOrderCannotBeLoadedForRefundNotification() throws Exception {

        final ReturnRequest approved = TestReturnRequests.approved();
        final ReturnRequest refunded = TestReturnRequests.refunded();
        given(getReturnInPort.getReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.markRefunded(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(refunded);
        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER)).willReturn(null);

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + APPROVE_ENDPOINT))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldThrowWhenRefundedReturnCannotBeReloadedInDirectInvocation() {

        final ReturnController controller = new ReturnController(
                getReturnInPort,
                returnWorkflow,
                listReturnsInPort,
                returnWebMapper);
        final ReturnRequest approved = TestReturnRequests.approved();
        given(returnModerationInPort.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.markRefunded(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(null);
        given(manageOrderUseCase.findOrder(ReturnRequestBuilder.TEST_ORDER_NUMBER))
                .willReturn(orderWithNumber(OrderBuilder.mockOrder()));

        assertThatThrownBy(() -> controller.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldReturnNotFoundWhenRejectingUnknownReturn() throws Exception {

        given(returnModerationInPort.rejectReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(null);

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + REJECT_ENDPOINT))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictWhenReturnCannotBeApproved() throws Exception {

        given(returnModerationInPort.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER))
                .willThrow(new ReturnRequestNotApprovableException("cannot approve"));

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + APPROVE_ENDPOINT))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnConflictWhenReturnCannotBeRejected() throws Exception {

        given(returnModerationInPort.rejectReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER))
                .willThrow(new ReturnRequestNotRejectableException("cannot reject"));

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + REJECT_ENDPOINT))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnConflictWhenReturnCannotBeMarkedRefunded() throws Exception {

        final ReturnRequest approved = TestReturnRequests.approved();
        given(returnModerationInPort.approveReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.markRefunded(ReturnRequestBuilder.TEST_RETURN_NUMBER))
                .willThrow(new ReturnRequestNotRefundableException("cannot refund"));

        mockMvc.perform(post(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER + APPROVE_ENDPOINT))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldThrowTechnicalProblemWhenMappingReturnResourceReturnsEmpty() throws Exception {

        final ReturnRequest returnRequest = ReturnRequestBuilder.mockReturnRequest();
        given(getReturnInPort.getReturn(ReturnRequestBuilder.TEST_RETURN_NUMBER)).willReturn(returnRequest);
        given(returnWebMapper.mapToResource(returnRequest)).willReturn(Optional.empty());

        mockMvc.perform(get(RETURNS_ENDPOINT + "/" + ReturnRequestBuilder.TEST_RETURN_NUMBER))
                .andExpect(status().isInternalServerError());
    }

    private static String requestReturnJson(
            final String orderNumber,
            final String sku,
            final int quantity,
            final String reason) {

        return "{\"orderNumber\":\"" + orderNumber + "\",\"sku\":\"" + sku + "\",\"quantity\":" + quantity + ",\"reason\":\""
                + reason + "\"}";
    }

    private static Order orderWithNumber(final Order order) {

        final OrderLineItem item = order.getItems().getFirst();
        return Order.builder()
                .remarks(order.getRemarks())
                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                .created(order.getCreated())
                .customer(order.getCustomer())
                .items(List.of(item))
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .couponCode(order.getCouponCode())
                .discountAmount(order.getDiscountAmount())
                .build();
    }

    private static ReturnRequestResource mockReturnRequestResource() {

        return resourceWithStatus(ReturnStatus.REQUESTED);
    }

    private static ReturnRequestResource resourceWithStatus(final ReturnStatus status) {

        return ReturnRequestResource.builder()
                .returnNumber(ReturnRequestBuilder.TEST_RETURN_NUMBER)
                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                .sku(ReturnRequestBuilder.TEST_SKU)
                .quantity(ReturnRequestBuilder.TEST_QUANTITY)
                .reason(ReturnRequestBuilder.TEST_REASON)
                .status(status.name())
                .requestedDate(ReturnRequestBuilder.TEST_REQUESTED_DATE)
                .decidedDate(ReturnRequestBuilder.TEST_DECIDED_DATE)
                .refundAmount(ReturnRequestBuilder.TEST_REFUND_AMOUNT)
                .build();
    }

    private static final class TestReturnRequests {

        private static ReturnRequest approved() {

            return ReturnRequest.builder()
                    .returnNumber(ReturnRequestBuilder.TEST_RETURN_NUMBER)
                    .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                    .sku(ReturnRequestBuilder.TEST_SKU)
                    .quantity(ReturnRequestBuilder.TEST_QUANTITY)
                    .reason(ReturnRequestBuilder.TEST_REASON)
                    .status(ReturnStatus.APPROVED)
                    .requestedDate(ReturnRequestBuilder.TEST_REQUESTED_DATE)
                    .decidedDate(ReturnRequestBuilder.TEST_DECIDED_DATE)
                    .refundAmount(ReturnRequestBuilder.TEST_REFUND_AMOUNT)
                    .build();
        }

        private static ReturnRequest refunded() {

            return ReturnRequest.builder()
                    .returnNumber(ReturnRequestBuilder.TEST_RETURN_NUMBER)
                    .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                    .sku(ReturnRequestBuilder.TEST_SKU)
                    .quantity(ReturnRequestBuilder.TEST_QUANTITY)
                    .reason(ReturnRequestBuilder.TEST_REASON)
                    .status(ReturnStatus.REFUNDED)
                    .requestedDate(ReturnRequestBuilder.TEST_REQUESTED_DATE)
                    .decidedDate(ReturnRequestBuilder.TEST_DECIDED_DATE)
                    .refundAmount(ReturnRequestBuilder.TEST_REFUND_AMOUNT)
                    .build();
        }

        private static ReturnRequest rejected() {

            return ReturnRequest.builder()
                    .returnNumber(ReturnRequestBuilder.TEST_RETURN_NUMBER)
                    .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                    .sku(ReturnRequestBuilder.TEST_SKU)
                    .quantity(ReturnRequestBuilder.TEST_QUANTITY)
                    .reason(ReturnRequestBuilder.TEST_REASON)
                    .status(ReturnStatus.REJECTED)
                    .requestedDate(ReturnRequestBuilder.TEST_REQUESTED_DATE)
                    .decidedDate(ReturnRequestBuilder.TEST_DECIDED_DATE)
                    .refundAmount(ReturnRequestBuilder.TEST_REFUND_AMOUNT)
                    .build();
        }

    }

}
