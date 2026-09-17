package com.cp.ecommerce.adapter.web.assistant;

import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.web.assistant.resource.SupportAnswerResource;
import com.cp.ecommerce.adapter.web.assistant.resource.SupportQuestionResource;
import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;
import com.cp.ecommerce.domain.assistant.usecase.AskSupportQuestionUseCase;

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
 * Controller serving the AI customer-support assistant (see ADR 0020).
 *
 * <p>
 * Unlike {@code OrderController}, every endpoint here is public/unauthenticated by design ({@code WebSecurityConfiguration}'s
 * catch-all {@code anyRequest().permitAll()} covers any path outside {@code /api/order/**}): a customer-support chat should be
 * usable by visitors who haven't signed in, not just authenticated back-office operators.
 * </p>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/support-assistant")
@Tag(name = "Support Assistant", description = "AI-assisted, policy-grounded answers to customer support questions")
public class SupportAssistantController {

    private static final String ASK_SUPPORT_QUESTION_RATE_LIMITER = "askSupportQuestion";

    private final AskSupportQuestionUseCase askSupportQuestionUseCase;

    private final RateLimitedExecutor rateLimitedExecutor;

    @PostMapping("/questions")
    @Operation(
            summary = "Ask the AI support assistant a question",
            description = "Answers a free-text customer question, grounded in bundled platform policy documents and, when "
                    + "relevant, a live lookup of a specific order. Never invents capabilities the platform doesn't have; "
                    + "falls back to a fixed message rather than an error when the feature is disabled or unavailable.")
    @ApiResponse(
            responseCode = "200",
            description = "Answer produced (or a fallback message if the assistant is disabled/unavailable - see "
                    + "assistantAvailable)",
            content = @Content(schema = @Schema(implementation = SupportAnswerResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Question is missing, blank or too long",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "429",
            description = "Too many support-assistant requests; retry after a short delay",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public SupportAnswerResource askQuestion(@RequestBody final SupportQuestionResource resource) {

        if (resource == null || resource.question() == null) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is missing");
        }
        final SupportQuestion question = SupportQuestion.builder().question(resource.question()).build();
        question.assertValidationsEmpty();

        final SupportAnswer answer = rateLimitedExecutor.callRateLimited(
                ASK_SUPPORT_QUESTION_RATE_LIMITER,
                () -> askSupportQuestionUseCase.askQuestion(question, resource.conversationId()));

        return SupportAnswerResource.builder()
                .answer(answer.getAnswer())
                .assistantAvailable(answer.isAssistantAvailable())
                .build();
    }

}
