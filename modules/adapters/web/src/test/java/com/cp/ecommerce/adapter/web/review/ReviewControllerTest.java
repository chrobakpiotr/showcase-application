package com.cp.ecommerce.adapter.web.review;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ReviewBuilder;
import com.cp.ecommerce.adapter.web.review.mapper.ReviewSummaryWebMapper;
import com.cp.ecommerce.adapter.web.review.mapper.ReviewWebMapper;
import com.cp.ecommerce.adapter.web.review.resource.ReviewResource;
import com.cp.ecommerce.adapter.web.review.resource.ReviewSummaryResource;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewSummary;
import com.cp.ecommerce.domain.review.port.incoming.GetProductReviewsInPort;
import com.cp.ecommerce.domain.review.port.incoming.SubmitReviewInPort;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_AUTHOR_NAME;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_COMMENT;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_ID;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_RATING;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_SKU;

/**
 * Test class checking review controller's behavior and API responses.
 */
@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    private static final String REVIEWS_ENDPOINT = "/api/reviews";

    private static final String PRODUCT_REVIEWS_ENDPOINT = REVIEWS_ENDPOINT + "/product/" + TEST_REVIEW_SKU;

    private static final String PRODUCT_REVIEW_SUMMARY_ENDPOINT = PRODUCT_REVIEWS_ENDPOINT + "/summary";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient SubmitReviewInPort submitReviewInPort;

    @MockitoBean
    private transient GetProductReviewsInPort getProductReviewsInPort;

    @MockitoBean
    private transient ReviewWebMapper reviewWebMapper;

    @MockitoBean
    private transient ReviewSummaryWebMapper reviewSummaryWebMapper;

    @Test
    void shouldSubmitReview() throws Exception {

        final Review review = ReviewBuilder.mockReview();
        given(
                submitReviewInPort
                        .submitReview(TEST_REVIEW_SKU, TEST_REVIEW_AUTHOR_NAME, TEST_REVIEW_RATING, TEST_REVIEW_COMMENT))
                .willReturn(review);
        given(reviewWebMapper.mapToResource(review)).willReturn(Optional.of(mockReviewResource()));

        mockMvc.perform(
                post(REVIEWS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                submitReviewJson(
                                        TEST_REVIEW_SKU,
                                        TEST_REVIEW_AUTHOR_NAME,
                                        TEST_REVIEW_RATING,
                                        TEST_REVIEW_COMMENT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").value(TEST_REVIEW_ID));
    }

    @Test
    void shouldRejectSubmitReviewWithMissingSku() throws Exception {

        mockMvc.perform(
                post(REVIEWS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(submitReviewJson(null, TEST_REVIEW_AUTHOR_NAME, TEST_REVIEW_RATING, TEST_REVIEW_COMMENT)))
                .andExpect(status().isBadRequest());
        verify(submitReviewInPort, never()).submitReview(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void shouldRejectSubmitReviewWithMissingAuthorName() throws Exception {

        mockMvc.perform(
                post(REVIEWS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(submitReviewJson(TEST_REVIEW_SKU, null, TEST_REVIEW_RATING, TEST_REVIEW_COMMENT)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectSubmitReviewWithMissingComment() throws Exception {

        mockMvc.perform(
                post(REVIEWS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(submitReviewJson(TEST_REVIEW_SKU, TEST_REVIEW_AUTHOR_NAME, TEST_REVIEW_RATING, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectSubmitReviewWithMissingRating() throws Exception {

        mockMvc.perform(
                post(REVIEWS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(submitReviewJson(TEST_REVIEW_SKU, TEST_REVIEW_AUTHOR_NAME, null, TEST_REVIEW_COMMENT)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectSubmitReviewWithRatingBelowMinimum() throws Exception {

        mockMvc.perform(
                post(REVIEWS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(submitReviewJson(TEST_REVIEW_SKU, TEST_REVIEW_AUTHOR_NAME, 0, TEST_REVIEW_COMMENT)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectSubmitReviewWithRatingAboveMaximum() throws Exception {

        mockMvc.perform(
                post(REVIEWS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(submitReviewJson(TEST_REVIEW_SKU, TEST_REVIEW_AUTHOR_NAME, 6, TEST_REVIEW_COMMENT)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldListApprovedReviews() throws Exception {

        final Review review = ReviewBuilder.mockReview();
        given(getProductReviewsInPort.listApprovedReviews(TEST_REVIEW_SKU)).willReturn(List.of(review));
        given(reviewWebMapper.mapToResource(review)).willReturn(Optional.of(mockReviewResource()));

        mockMvc.perform(get(PRODUCT_REVIEWS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewId").value(TEST_REVIEW_ID));
    }

    @Test
    void shouldReturnEmptyListWhenNoApprovedReviewsExist() throws Exception {

        given(getProductReviewsInPort.listApprovedReviews(TEST_REVIEW_SKU)).willReturn(List.of());

        mockMvc.perform(get(PRODUCT_REVIEWS_ENDPOINT)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldGetReviewSummary() throws Exception {

        final ReviewSummary summary = new ReviewSummary(TEST_REVIEW_SKU, BigDecimal.valueOf(4.5), 2L);
        given(getProductReviewsInPort.getSummary(TEST_REVIEW_SKU)).willReturn(summary);
        given(reviewSummaryWebMapper.mapToResource(summary)).willReturn(
                Optional.of(
                        ReviewSummaryResource.builder()
                                .sku(TEST_REVIEW_SKU)
                                .averageRating(BigDecimal.valueOf(4.5))
                                .reviewCount(2L)
                                .build()));

        mockMvc.perform(get(PRODUCT_REVIEW_SUMMARY_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(TEST_REVIEW_SKU))
                .andExpect(jsonPath("$.reviewCount").value(2));
    }

    @Test
    void shouldThrowTechnicalProblemWhenMappingReviewResourceReturnsEmpty() throws Exception {

        final Review review = ReviewBuilder.mockReview();
        given(
                submitReviewInPort
                        .submitReview(TEST_REVIEW_SKU, TEST_REVIEW_AUTHOR_NAME, TEST_REVIEW_RATING, TEST_REVIEW_COMMENT))
                .willReturn(review);
        given(reviewWebMapper.mapToResource(review)).willReturn(Optional.empty());

        mockMvc.perform(
                post(REVIEWS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(
                                submitReviewJson(
                                        TEST_REVIEW_SKU,
                                        TEST_REVIEW_AUTHOR_NAME,
                                        TEST_REVIEW_RATING,
                                        TEST_REVIEW_COMMENT)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldThrowTechnicalProblemWhenMappingReviewSummaryResourceReturnsEmpty() throws Exception {

        final ReviewSummary summary = new ReviewSummary(TEST_REVIEW_SKU, BigDecimal.valueOf(4.5), 2L);
        given(getProductReviewsInPort.getSummary(TEST_REVIEW_SKU)).willReturn(summary);
        given(reviewSummaryWebMapper.mapToResource(summary)).willReturn(Optional.empty());

        mockMvc.perform(get(PRODUCT_REVIEW_SUMMARY_ENDPOINT)).andExpect(status().isInternalServerError());
    }

    private static String submitReviewJson(
            final String sku,
            final String authorName,
            final Integer rating,
            final String comment) {

        return "{\"sku\":" + jsonString(sku) + ",\"authorName\":" + jsonString(authorName) + ",\"rating\":"
                + (rating == null ? "null" : rating) + ",\"comment\":" + jsonString(comment) + "}";
    }

    private static String jsonString(final String value) {

        return value == null ? "null" : "\"" + value + "\"";
    }

    private static ReviewResource mockReviewResource() {

        return ReviewResource.builder()
                .reviewId(TEST_REVIEW_ID)
                .sku(TEST_REVIEW_SKU)
                .authorName(TEST_REVIEW_AUTHOR_NAME)
                .rating(TEST_REVIEW_RATING)
                .comment(TEST_REVIEW_COMMENT)
                .status("PENDING")
                .build();
    }

}
