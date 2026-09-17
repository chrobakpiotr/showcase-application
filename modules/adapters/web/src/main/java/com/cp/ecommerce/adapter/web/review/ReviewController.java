package com.cp.ecommerce.adapter.web.review;

import java.util.List;

import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.review.mapper.ReviewSummaryWebMapper;
import com.cp.ecommerce.adapter.web.review.mapper.ReviewWebMapper;
import com.cp.ecommerce.adapter.web.review.resource.ReviewResource;
import com.cp.ecommerce.adapter.web.review.resource.ReviewSummaryResource;
import com.cp.ecommerce.adapter.web.review.resource.SubmitReviewResource;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewSummary;
import com.cp.ecommerce.domain.review.port.incoming.GetProductReviewsInPort;
import com.cp.ecommerce.domain.review.port.incoming.SubmitReviewInPort;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
 * Controller serving the customer-facing review API: submitting a review and reading a product's approved reviews/aggregate
 * rating.
 * <p>
 * Unlike Cart (fully public) or Order/Catalog/Inventory (fully role-gated), this is this application's first hybrid API: this
 * controller's endpoints are all genuinely public/unauthenticated, while {@code ReviewModerationController}'s are role-gated -
 * see {@code WebSecurityConfiguration} and ADR 0028.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/reviews")
@Tag(name = "Reviews", description = "Submitting and reading customer product reviews")
public class ReviewController {

    private static final String INVALID_RATING_MESSAGE = "rating is required and must be between 1 and 5";

    private final SubmitReviewInPort submitReviewInPort;

    private final GetProductReviewsInPort getProductReviewsInPort;

    private final ReviewWebMapper reviewWebMapper;

    private final ReviewSummaryWebMapper reviewSummaryWebMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Submit a new product review",
            description = "The review starts out PENDING and is not publicly visible until approved by an operator.")
    @ApiResponse(
            responseCode = "201",
            description = "The newly submitted, pending review",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ReviewResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "sku/authorName/rating/comment is missing or invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ReviewResource submitReview(@RequestBody final SubmitReviewResource resource) {

        final String sku = requireNonBlank(resource.sku(), ValidationConstants.INVALID_REVIEW_SKU);
        final String authorName = requireNonBlank(resource.authorName(), ValidationConstants.INVALID_REVIEW_AUTHOR_NAME);
        final int rating = requireValidRating(resource.rating());
        final String comment = requireNonBlank(resource.comment(), ValidationConstants.INVALID_REVIEW_COMMENT);

        return toResource(submitReviewInPort.submitReview(sku, authorName, rating, comment));
    }

    @GetMapping("/product/{sku}")
    @Operation(summary = "List a product's approved reviews", description = "Newest first. Empty list if there are none yet.")
    @ApiResponse(
            responseCode = "200",
            description = "Approved reviews for the given sku",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = ReviewResource.class))))
    public List<ReviewResource> listApprovedReviews(@PathVariable("sku") final String sku) {

        return getProductReviewsInPort.listApprovedReviews(sku).stream().map(this::toResource).toList();
    }

    @GetMapping("/product/{sku}/summary")
    @Operation(
            summary = "Get a product's aggregate rating",
            description = "Zero average/count if the product has no approved reviews yet.")
    @ApiResponse(
            responseCode = "200",
            description = "The aggregate rating",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ReviewSummaryResource.class)))
    public ReviewSummaryResource getReviewSummary(@PathVariable("sku") final String sku) {

        return toResource(getProductReviewsInPort.getSummary(sku));
    }

    private String requireNonBlank(final String value, final String message) {

        if (value == null || value.isBlank()) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private int requireValidRating(final Integer rating) {

        if (rating == null || rating < 1 || rating > 5) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_RATING_MESSAGE);
        }
        return rating;
    }

    private ReviewResource toResource(final Review review) {

        return reviewWebMapper.mapToResource(review).orElseThrow(() -> new TechnicalProblemException("Review data is missing"));
    }

    private ReviewSummaryResource toResource(final ReviewSummary reviewSummary) {

        return reviewSummaryWebMapper.mapToResource(reviewSummary)
                .orElseThrow(() -> new TechnicalProblemException("Review summary data is missing"));
    }

}
