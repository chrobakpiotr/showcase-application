package com.cp.ecommerce.adapter.web.recommendation;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.web.recommendation.mapper.RecommendationWebMapper;
import com.cp.ecommerce.domain.recommendation.ProductRecommendation;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;
import com.cp.ecommerce.domain.recommendation.port.incoming.RecommendProductsInPort;
import com.cp.ecommerce.foundation.exception.RateLimitExceededException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link RecommendationController}.
 */
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    private static final String ENDPOINT = "/api/recommendations";

    private static final String CUSTOMER_EMAIL = "john.doe@test.com";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient RecommendProductsInPort recommendProductsInPort;

    @MockitoBean
    private transient RecommendationWebMapper recommendationWebMapper;

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
    void shouldReturnRecommendationsSuccessfully() throws Exception {

        final ProductRecommendations domain = ProductRecommendations.builder()
                .recommendations(
                        List.of(ProductRecommendation.builder().sku("SKU-1").productName("Mouse").reason("Good fit.").build()))
                .assistantAvailable(true)
                .build();
        final RecommendationRequest request = RecommendationRequest.builder().customerEmail(CUSTOMER_EMAIL).build();
        given(recommendProductsInPort.recommendProducts(request)).willReturn(domain);
        given(recommendationWebMapper.mapToResource(domain)).willReturn(
                java.util.Optional.of(
                        com.cp.ecommerce.adapter.web.recommendation.resource.ProductRecommendationsResource.builder()
                                .assistantAvailable(true)
                                .recommendations(
                                        List.of(
                                                com.cp.ecommerce.adapter.web.recommendation.resource.ProductRecommendationResource
                                                        .builder()
                                                        .sku("SKU-1")
                                                        .productName("Mouse")
                                                        .reason("Good fit.")
                                                        .build()))
                                .build()));

        mockMvc.perform(get(ENDPOINT).param("email", CUSTOMER_EMAIL))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.assistantAvailable").value(true))
                .andExpect(jsonPath("$.recommendations[0].sku").value("SKU-1"));
    }

    @Test
    void shouldReturn400WhenEmailIsBlank() throws Exception {

        mockMvc.perform(get(ENDPOINT).param("email", " "))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Domain Validation Error"));

        verify(recommendProductsInPort, never()).recommendProducts(any());
    }

    @Test
    void shouldReturn400WhenEmailParameterIsMissing() throws Exception {

        mockMvc.perform(get(ENDPOINT)).andDo(print()).andExpect(status().isBadRequest());

        verify(recommendProductsInPort, never()).recommendProducts(any());
    }

    @Test
    void shouldThrowBadRequestWhenControllerReceivesNullEmail() {

        final RecommendationController controller = new RecommendationController(
                recommendProductsInPort,
                recommendationWebMapper,
                rateLimitedExecutor);

        assertThatThrownBy(() -> controller.recommend(null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("Email is missing");
    }

    @Test
    void shouldThrowWhenRecommendationsCannotBeMappedToAResource() {

        final ProductRecommendations domain = ProductRecommendations.builder()
                .recommendations(List.of())
                .assistantAvailable(true)
                .build();
        given(recommendProductsInPort.recommendProducts(any(RecommendationRequest.class))).willReturn(domain);
        given(recommendationWebMapper.mapToResource(domain)).willReturn(java.util.Optional.empty());
        final RecommendationController controller = new RecommendationController(
                recommendProductsInPort,
                recommendationWebMapper,
                rateLimitedExecutor);

        assertThatThrownBy(() -> controller.recommend(CUSTOMER_EMAIL)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to map product recommendations to response");
    }

    @Test
    void shouldRespondWith429WhenRateLimitExceeded() throws Exception {

        willThrow(new RateLimitExceededException("Rate limit exceeded for 'recommendProducts'", Duration.ofSeconds(1), null))
                .given(rateLimitedExecutor)
                .callRateLimited(anyString(), any());

        mockMvc.perform(get(ENDPOINT).param("email", CUSTOMER_EMAIL))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().longValue(HttpHeaders.RETRY_AFTER, 1))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(recommendProductsInPort, never()).recommendProducts(any());
    }

}
