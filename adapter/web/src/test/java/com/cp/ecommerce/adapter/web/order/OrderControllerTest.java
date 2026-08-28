package com.cp.ecommerce.adapter.web.order;

import java.util.Optional;
import java.util.function.Supplier;

import com.cp.ecommerce.adapter.common.exception.RateLimitExceededException;
import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.common.utils.CustomerBuilder;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.web.order.mapper.OrderWebMapper;
import com.cp.ecommerce.adapter.web.order.metrics.OrderMetrics;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderDetailsResource;
import com.cp.ecommerce.adapter.web.utils.OrderResourceBuilder;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;

/**
 * Test class checking order page controller's behavior and order page API response.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String ORDER_ENDPOINT = "/api/order";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_KEY_VALUE = "client-key-1";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient PlaceOrderUseCase placeOrderUseCase;

    @MockitoBean
    private transient ManageOrderUseCase manageOrderUseCase;

    @MockitoBean
    private transient OrderWebMapper orderWebMapper;

    @MockitoBean
    private transient OrderMetrics orderMetrics;

    @MockitoBean
    private transient RateLimitedExecutor rateLimitedExecutor;

    @BeforeEach
    void stubRateLimiterToRunActionsThrough() {

        given(rateLimitedExecutor.callRateLimited(anyString(), any())).willAnswer(invocation -> {
            final Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });
    }

    @Test
    void shouldPlaceOrderSuccessfully() throws Exception {

        given(orderWebMapper.mapToDomainObject(any())).willReturn(Optional.ofNullable(OrderBuilder.mockOrder()));
        given(placeOrderUseCase.placeOrder(any(), isNull())).willReturn(new PlaceOrderResult(TEST_ORDER_NUMBER, true));
        this.mockMvc.perform(post(ORDER_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        verify(placeOrderUseCase, atLeastOnce()).placeOrder(any(), isNull());
        verify(orderMetrics, atLeastOnce()).recordOrderPlaced();
    }

    @Test
    void shouldPassIdempotencyKeyHeaderToUseCase() throws Exception {

        given(orderWebMapper.mapToDomainObject(any())).willReturn(Optional.ofNullable(OrderBuilder.mockOrder()));
        given(placeOrderUseCase.placeOrder(any(), eq(IDEMPOTENCY_KEY_VALUE)))
                .willReturn(new PlaceOrderResult(TEST_ORDER_NUMBER, true));

        this.mockMvc
                .perform(
                        post(ORDER_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                                .header(IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY_VALUE)
                                .content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isCreated());

        verify(placeOrderUseCase).placeOrder(any(), eq(IDEMPOTENCY_KEY_VALUE));
    }

    @Test
    void shouldNotRecordMetricWhenOrderWasNotNewlyPlaced() throws Exception {

        given(orderWebMapper.mapToDomainObject(any())).willReturn(Optional.ofNullable(OrderBuilder.mockOrder()));
        given(placeOrderUseCase.placeOrder(any(), eq(IDEMPOTENCY_KEY_VALUE)))
                .willReturn(new PlaceOrderResult(TEST_ORDER_NUMBER, false));

        this.mockMvc
                .perform(
                        post(ORDER_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                                .header(IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY_VALUE)
                                .content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isCreated());

        verify(orderMetrics, never()).recordOrderPlaced();
    }

    @Test
    void shouldRespondWith429WhenRateLimitExceeded() throws Exception {

        given(orderWebMapper.mapToDomainObject(any())).willReturn(Optional.ofNullable(OrderBuilder.mockOrder()));
        willThrow(new RateLimitExceededException("Rate limit exceeded for 'placeOrder'", null)).given(rateLimitedExecutor)
                .callRateLimited(anyString(), any());

        this.mockMvc.perform(post(ORDER_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Rate Limit Exceeded"));

        verify(placeOrderUseCase, never()).placeOrder(any(), any());
        verify(orderMetrics, never()).recordOrderPlaced();
    }

    @Test
    void shouldThrowMissingDataExceptionForEmptyOptional() throws Exception {

        given(orderWebMapper.mapToDomainObject(any())).willReturn(Optional.empty());
        this.mockMvc.perform(post(ORDER_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Order data is missing"));

        verify(placeOrderUseCase, never()).placeOrder(any(), any());
        verify(orderWebMapper, atMostOnce()).mapToDomainObject(any());
        verify(orderMetrics, never()).recordOrderPlaced();
    }

    @Test
    void shouldResponseWith404IfOrderDoesntExist() throws Exception {

        given(manageOrderUseCase.findOrder(any())).willReturn(null);
        this.mockMvc.perform(get(ORDER_ENDPOINT + "/" + TEST_ORDER_NUMBER)).andExpect(status().isNotFound());
    }

    @Test
    void shouldThrowMissingDataExceptionWhenMapToResourceReturnsEmpty() throws Exception {

        final Order order = OrderBuilder.mockOrder();
        given(manageOrderUseCase.findOrder(TEST_ORDER_NUMBER)).willReturn(order);
        given(orderWebMapper.mapToResource(order)).willReturn(Optional.empty());

        this.mockMvc.perform(get(ORDER_ENDPOINT + "/" + TEST_ORDER_NUMBER))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Order data is missing"));
    }

    @Test
    void shouldResponseWithExpectedOrder() throws Exception {

        final Order order = OrderBuilder.mockOrder();
        given(manageOrderUseCase.findOrder(TEST_ORDER_NUMBER)).willReturn(order);
        given(orderWebMapper.mapToResource(order)).willReturn(Optional.of(mockOrderDetailsResource()));

        this.mockMvc.perform(get(ORDER_ENDPOINT + "/" + TEST_ORDER_NUMBER))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/hal+json"))
                .andExpect(jsonPath("$.orderNumber").value(TEST_ORDER_NUMBER))
                .andExpect(jsonPath("$.customer.fullName").value(CustomerBuilder.TEST_FULL_NAME))
                .andExpect(jsonPath("$._links.self.href", endsWith(ORDER_ENDPOINT + "/" + TEST_ORDER_NUMBER)));
    }

    private OrderDetailsResource mockOrderDetailsResource() {

        final CustomerResource customer = CustomerResource.builder()
                .fullName(CustomerBuilder.TEST_FULL_NAME)
                .email(CustomerBuilder.TEST_EMAIL)
                .phone(CustomerBuilder.TEST_PHONE_NUMBER)
                .street(CustomerBuilder.TEST_STREET_ADDRESS)
                .postalCode(CustomerBuilder.TEST_POSTAL_CODE)
                .city(CustomerBuilder.TEST_CITY)
                .countryCode(CustomerBuilder.TEST_COUNTRY_CODE)
                .build();
        return OrderDetailsResource.builder()
                .orderNumber(TEST_ORDER_NUMBER)
                .status(OrderStatus.CONFIRMED)
                .remarks(OrderBuilder.TEST_REMARKS)
                .customer(customer)
                .build();
    }

    private String createJsonResource() throws Exception {

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.writeValueAsString(OrderResourceBuilder.mockOrderResource());
    }

}
