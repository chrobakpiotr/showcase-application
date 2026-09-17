package com.cp.ecommerce.adapter.web.order;

import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.web.order.resource.AnalyticsAnswerResource;
import com.cp.ecommerce.adapter.web.order.resource.AnalyticsQuestionResource;
import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;
import com.cp.ecommerce.domain.order.usecase.AskAnalyticsQuestionUseCase;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controller serving the AI ops-analytics assistant (see ADR 0021), a tool-calling-only companion to
 * {@link OrderAnalyticsController}'s raw read model for operators who'd rather ask a free-text question than build their own
 * query.
 *
 * <p>
 * Mapped under {@code /api/order/analytics/ask} rather than a new top-level path: unlike the customer-facing support assistant,
 * this feature is for authenticated back-office operators only, so it belongs behind the existing {@code /api/order/**}
 * security matchers rather than being publicly reachable. Since the request body is a free-text question, this must be a
 * {@code POST}; {@code WebSecurityConfiguration} carves out a specific, narrower rule so it only requires {@code ORDER_READ}
 * (this endpoint never mutates anything) instead of falling through to the general {@code POST /api/order/**} rule's
 * {@code ORDER_WRITE} requirement.
 * </p>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/order/analytics")
@Tag(name = "Order analytics assistant", description = "AI-assisted, tool-calling answers to operator ops-analytics questions")
public class OrderAnalyticsAssistantController {

    private static final String ASK_ANALYTICS_QUESTION_RATE_LIMITER = "askAnalyticsQuestion";

    private final AskAnalyticsQuestionUseCase askAnalyticsQuestionUseCase;

    private final RateLimitedExecutor rateLimitedExecutor;

    @PostMapping("/ask")
    @Operation(
            summary = "Ask the AI ops-analytics assistant a question",
            description = "Answers a free-text operator question about order volumes or remarks-triage classification "
                    + "counts, grounded in live tool calls into the existing order-analytics and remarks-classification "
                    + "read models - never invents figures. Falls back to a fixed message rather than an error when the "
                    + "feature is disabled or unavailable.")
    @ApiResponse(
            responseCode = "200",
            description = "Answer produced (or a fallback message if the assistant is disabled/unavailable - see "
                    + "assistantAvailable)",
            content = @Content(schema = @Schema(implementation = AnalyticsAnswerResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Question is missing, blank or too long",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "429",
            description = "Too many analytics-assistant requests; retry after a short delay",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public AnalyticsAnswerResource askQuestion(@RequestBody final AnalyticsQuestionResource resource) {

        if (resource == null || resource.question() == null) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is missing");
        }
        final AnalyticsQuestion question = AnalyticsQuestion.builder().question(resource.question()).build();
        question.assertValidationsEmpty();

        final AnalyticsAnswer answer = rateLimitedExecutor.callRateLimited(
                ASK_ANALYTICS_QUESTION_RATE_LIMITER,
                () -> askAnalyticsQuestionUseCase.askQuestion(question, resource.conversationId()));

        return AnalyticsAnswerResource.builder()
                .answer(answer.getAnswer())
                .assistantAvailable(answer.isAssistantAvailable())
                .build();
    }

}
