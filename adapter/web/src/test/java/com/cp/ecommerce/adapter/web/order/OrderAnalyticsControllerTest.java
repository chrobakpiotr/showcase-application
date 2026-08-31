package com.cp.ecommerce.adapter.web.order;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.web.order.mapper.OrderAnalyticsWebMapper;
import com.cp.ecommerce.adapter.web.order.resource.OrderAnalyticsResource;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.usecase.FindRecentOrderAnalyticsUseCase;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class checking order-analytics controller's behavior and API response.
 */
@WebMvcTest(OrderAnalyticsController.class)
class OrderAnalyticsControllerTest {

    private static final String RECENT_ENDPOINT = "/api/order/analytics/recent";

    private static final String TEST_ORDER_NUMBER = "ORD-1001";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient FindRecentOrderAnalyticsUseCase findRecentOrderAnalyticsUseCase;

    @MockitoBean
    private transient OrderAnalyticsWebMapper orderAnalyticsWebMapper;

    @Test
    void shouldReturnRecentAnalyticsWithDefaultLimit() throws Exception {

        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                TEST_ORDER_NUMBER,
                1001L,
                new Date(),
                new Date());
        final OrderAnalyticsResource resource = OrderAnalyticsResource.builder()
                .orderNumber(TEST_ORDER_NUMBER)
                .customerId(1001L)
                .orderPlacedDate(projection.orderPlacedDate())
                .consumedDate(projection.consumedDate())
                .build();
        given(findRecentOrderAnalyticsUseCase.findRecent(20)).willReturn(List.of(projection));
        given(orderAnalyticsWebMapper.mapToResource(projection)).willReturn(Optional.of(resource));

        this.mockMvc.perform(get(RECENT_ENDPOINT))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/hal+json"))
                .andExpect(jsonPath("$._embedded.orderAnalyticsResourceList[0].orderNumber").value(TEST_ORDER_NUMBER))
                .andExpect(jsonPath("$._embedded.orderAnalyticsResourceList[0].customerId").value(1001))
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    void shouldReturnEmptyListWhenNoAnalyticsRecordedYet() throws Exception {

        given(findRecentOrderAnalyticsUseCase.findRecent(20)).willReturn(List.of());

        this.mockMvc.perform(get(RECENT_ENDPOINT))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/hal+json"))
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    void shouldUseCustomLimitWhenProvided() throws Exception {

        given(findRecentOrderAnalyticsUseCase.findRecent(5)).willReturn(List.of());

        this.mockMvc.perform(get(RECENT_ENDPOINT).param("limit", "5")).andDo(print()).andExpect(status().isOk());

        verify(findRecentOrderAnalyticsUseCase).findRecent(5);
    }

    @Test
    void shouldRejectLimitBelowOne() throws Exception {

        this.mockMvc.perform(get(RECENT_ENDPOINT).param("limit", "0"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(findRecentOrderAnalyticsUseCase, never()).findRecent(anyInt());
    }

    @Test
    void shouldRejectLimitAboveMax() throws Exception {

        this.mockMvc.perform(get(RECENT_ENDPOINT).param("limit", "101"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(findRecentOrderAnalyticsUseCase, never()).findRecent(anyInt());
    }

    @Test
    void shouldRejectWhenMappingFails() throws Exception {

        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                TEST_ORDER_NUMBER,
                1001L,
                new Date(),
                new Date());
        given(findRecentOrderAnalyticsUseCase.findRecent(20)).willReturn(List.of(projection));
        given(orderAnalyticsWebMapper.mapToResource(any())).willReturn(Optional.empty());

        this.mockMvc.perform(get(RECENT_ENDPOINT)).andDo(print()).andExpect(status().is5xxServerError());
    }

}
