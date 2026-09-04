package com.cp.ecommerce.adapter.web.review;

import java.util.List;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.review.mapper.ReviewWebMapper;
import com.cp.ecommerce.adapter.web.review.resource.ReviewResource;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.port.incoming.ReviewModerationInPort;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controller serving the back-office review moderation queue.
 * <p>
 * Unlike {@link ReviewController}'s fully public endpoints, every endpoint here requires an operator role (see
 * {@code WebSecurityConfiguration} and ADR 0028) - this application's first hybrid public/gated bounded context.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/reviews/moderation")
@Tag(name = "Review Moderation", description = "Back-office approval/rejection of submitted reviews")
public class ReviewModerationController {

    private final ReviewModerationInPort reviewModerationInPort;

    private final ReviewWebMapper reviewWebMapper;

    @GetMapping("/pending")
    @Operation(summary = "List reviews awaiting moderation", description = "Oldest first. Empty list if there are none.")
    @ApiResponse(
            responseCode = "200",
            description = "Pending reviews",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = ReviewResource.class))))
    public List<ReviewResource> listPendingReviews() {

        return reviewModerationInPort.listPendingReviews().stream().map(this::toResource).toList();
    }

    @PostMapping("/{reviewId}/approve")
    @Operation(
            summary = "Approve a review",
            description = "Idempotent: approving an already-approved (or previously rejected) review simply (re)sets its status.")
    @ApiResponse(
            responseCode = "200",
            description = "The approved review",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ReviewResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No review exists for the given id",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ReviewResource approveReview(@PathVariable("reviewId") final String reviewId) {

        return toResourceOrNotFound(reviewModerationInPort.approveReview(reviewId));
    }

    @PostMapping("/{reviewId}/reject")
    @Operation(summary = "Reject a review", description = "Idempotent, same reasoning as approve.")
    @ApiResponse(
            responseCode = "200",
            description = "The rejected review",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ReviewResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No review exists for the given id",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ReviewResource rejectReview(@PathVariable("reviewId") final String reviewId) {

        return toResourceOrNotFound(reviewModerationInPort.rejectReview(reviewId));
    }

    private ReviewResource toResourceOrNotFound(final Review review) {

        if (review == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No review exists for the given id");
        }
        return toResource(review);
    }

    private ReviewResource toResource(final Review review) {

        return reviewWebMapper.mapToResource(review).orElseThrow(() -> new TechnicalProblemException("Review data is missing"));
    }

}
