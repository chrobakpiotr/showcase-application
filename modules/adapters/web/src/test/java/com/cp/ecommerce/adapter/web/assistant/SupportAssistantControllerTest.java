package com.cp.ecommerce.adapter.web.assistant;

import java.time.Duration;
import java.util.function.Supplier;

import com.cp.ecommerce.adapter.common.exception.RateLimitExceededException;
import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.web.assistant.resource.SupportQuestionResource;
import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.usecase.AskSupportQuestionUseCase;
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
 * Test class checking {@link SupportAssistantController}'s behavior and API response.
 */
@WebMvcTest(SupportAssistantController.class)
class SupportAssistantControllerTest {

    private static final String QUESTIONS_ENDPOINT = "/api/support-assistant/questions";

    private final transient ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient AskSupportQuestionUseCase askSupportQuestionUseCase;

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

        given(askSupportQuestionUseCase.askQuestion(any(), eq("conversation-1"))).willReturn(
                SupportAnswer.builder().answer("You can cancel from the order page.").assistantAvailable(true).build());

        this.mockMvc
                .perform(
                        post(QUESTIONS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson("Can I cancel my order?", "conversation-1")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer").value("You can cancel from the order page."))
                .andExpect(jsonPath("$.assistantAvailable").value(true));
    }

    @Test
    void shouldReturn400WhenQuestionIsBlank() throws Exception {

        this.mockMvc.perform(post(QUESTIONS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestJson(" ", null)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Domain Validation Error"));
    }

    @Test
    void shouldReturn400WhenQuestionIsMissing() throws Exception {

        this.mockMvc.perform(post(QUESTIONS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Question is missing"));
    }

    @Test
    void shouldRespondWith429WhenRateLimitExceeded() throws Exception {

        willThrow(new RateLimitExceededException("Rate limit exceeded for 'askSupportQuestion'", Duration.ofSeconds(1), null))
                .given(rateLimitedExecutor)
                .callRateLimited(anyString(), any());

        this.mockMvc
                .perform(
                        post(QUESTIONS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson("Can I cancel my order?", null)))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().longValue(HttpHeaders.RETRY_AFTER, 1))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(askSupportQuestionUseCase, never()).askQuestion(any(), any());
    }

    private String requestJson(final String question, final String conversationId) throws Exception {

        return objectMapper.writeValueAsString(new SupportQuestionResource(question, conversationId));
    }

}
