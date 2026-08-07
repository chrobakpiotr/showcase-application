package com.cp.ecommerce.adapter.web.order;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.web.order.mapper.OrderWebMapper;
import com.cp.ecommerce.adapter.web.order.metrics.OrderMetrics;
import com.cp.ecommerce.adapter.web.utils.OrderResourceBuilder;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @Test
    void shouldPlaceOrderSuccessfully() throws Exception {

        given(orderWebMapper.mapToDomainObject(any())).willReturn(Optional.ofNullable(OrderBuilder.mockOrder()));
        this.mockMvc.perform(post(ORDER_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        verify(placeOrderUseCase, atLeastOnce()).placeOrder(any());
        verify(orderMetrics, atLeastOnce()).recordOrderPlaced();
    }

    @Test
    void shouldThrowMissingDataExceptionForEmptyOptional() throws Exception {

        given(orderWebMapper.mapToDomainObject(any())).willReturn(Optional.empty());
        this.mockMvc.perform(post(ORDER_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Order data is missing"));

        verify(placeOrderUseCase, never()).placeOrder(null);
        verify(orderWebMapper, atMostOnce()).mapToDomainObject(any());
        verify(orderMetrics, never()).recordOrderPlaced();
    }

    @Test
    void shouldResponseWith404IfOrderDoesntExist() throws Exception {

        given(manageOrderUseCase.findOrder(any())).willReturn(null);
        this.mockMvc.perform(get(ORDER_ENDPOINT + "/" + TEST_ORDER_NUMBER)).andExpect(status().isNotFound());
    }

    @Test
    void shouldResponseWithExpectedOrder() throws Exception {

        given(manageOrderUseCase.findOrder(TEST_ORDER_NUMBER)).willReturn(OrderBuilder.mockOrder());
        this.mockMvc.perform(get(ORDER_ENDPOINT + "/" + TEST_ORDER_NUMBER))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderNumber").value(TEST_ORDER_NUMBER));
    }

    private String createJsonResource() throws Exception {

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.writeValueAsString(OrderResourceBuilder.mockOrderResource());
    }

}
