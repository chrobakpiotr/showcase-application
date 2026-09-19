package com.cp.ecommerce.adapter.web.recommendation;

import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.web.recommendation.mapper.RecommendationWebMapper;
import com.cp.ecommerce.adapter.web.recommendation.resource.ProductRecommendationsResource;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;
import com.cp.ecommerce.domain.recommendation.port.incoming.RecommendProductsInPort;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Operator-only endpoint for AI-powered personalized product recommendations.
 *
 * <p>
 * ADR 0044 requires {@code ORDER_READ}: the input is an arbitrary customer e-mail and the recommendation context is derived
 * from customer purchase/review history, while this showcase has no customer identity/ownership model.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Personalized recommendations", description = "AI-assisted product recommendations grounded in customer history")
public class RecommendationController {

    private static final String RECOMMEND_PRODUCTS_RATE_LIMITER = "recommendProducts";

    private final RecommendProductsInPort recommendProductsInPort;

    private final RecommendationWebMapper recommendationWebMapper;

    private final RateLimitedExecutor rateLimitedExecutor;

    @GetMapping
    @Operation(
            summary = "Get personalized product recommendations",
            description = "Generates a short list of catalog recommendations for a customer e-mail by combining existing order "
                    + "history, review history and active catalog candidates. Returns an empty list with assistantAvailable=false "
                    + "when the AI feature is disabled or unavailable.")
    @ApiResponse(
            responseCode = "200",
            description = "Recommendations produced (or an unavailable fallback)",
            content = @Content(schema = @Schema(implementation = ProductRecommendationsResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Email is missing, blank or invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "429",
            description = "Too many recommendation requests; retry after a short delay",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ProductRecommendationsResource recommend(@RequestParam("email") final String email) {

        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is missing");
        }
        final RecommendationRequest request = RecommendationRequest.builder().customerEmail(email).build();
        request.assertValidationsEmpty();

        return recommendationWebMapper
                .mapToResource(
                        rateLimitedExecutor.callRateLimited(
                                RECOMMEND_PRODUCTS_RATE_LIMITER,
                                () -> recommendProductsInPort.recommendProducts(request)))
                .orElseThrow(() -> new IllegalStateException("Failed to map product recommendations to response"));
    }

}
