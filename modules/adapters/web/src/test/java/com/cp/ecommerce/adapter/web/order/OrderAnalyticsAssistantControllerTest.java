package com.cp.ecommerce.adapter.web.order;

import java.time.Duration;
import java.util.function.Supplier;

import com.cp.ecommerce.adapter.common.exception.RateLimitExceededException;
import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.web.order.resource.AnalyticsQuestionResource;
import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.usecase.AskAnalyticsQuestionUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class checking {@link OrderAnalyticsAssistantController}'s behavior and API response.
 */
@WebMvcTest(OrderAnalyticsAssistantController.class)
class OrderAnalyticsAssistantControllerTest {

    private static final String ASK_ENDPOINT = "/api/order/analytics/ask";

    private final transient ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient AskAnalyticsQuestionUseCase askAnalyticsQuestionUseCase;

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
    void shouldAnswerQuestionSuccessfully() throws Exception {

        given(askAnalyticsQuestionUseCase.askQuestion(any(), eq("conversation-1"))).willReturn(
                AnalyticsAnswer.builder()
                        .answer("14 order(s) were placed between 2024-01-01 and 2024-01-31 (inclusive, UTC).")
                        .assistantAvailable(true)
                        .build());

        this.mockMvc
                .perform(
                        post(ASK_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson("How many orders were placed in January 2024?", "conversation-1")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        jsonPath("$.answer")
                                .value("14 order(s) were placed between 2024-01-01 and 2024-01-31 (inclusive, UTC)."))
                .andExpect(jsonPath("$.assistantAvailable").value(true));
    }

    @Test
    void shouldReturn400WhenQuestionIsBlank() throws Exception {

        this.mockMvc.perform(post(ASK_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestJson(" ", null)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Domain Validation Error"));
    }

    @Test
    void shouldReturn400WhenQuestionIsMissing() throws Exception {

        this.mockMvc.perform(post(ASK_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Question is missing"));
    }

    @Test
    void shouldRespondWith429WhenRateLimitExceeded() throws Exception {

        willThrow(new RateLimitExceededException("Rate limit exceeded for 'askAnalyticsQuestion'", Duration.ofSeconds(1), null))
                .given(rateLimitedExecutor)
                .callRateLimited(anyString(), any());

        this.mockMvc
                .perform(
                        post(ASK_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson("How many orders were placed today?", null)))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().longValue(HttpHeaders.RETRY_AFTER, 1))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(askAnalyticsQuestionUseCase, never()).askQuestion(any(), any());
    }

    private String requestJson(final String question, final String conversationId) throws Exception {

        return objectMapper.writeValueAsString(new AnalyticsQuestionResource(question, conversationId));
    }

}
