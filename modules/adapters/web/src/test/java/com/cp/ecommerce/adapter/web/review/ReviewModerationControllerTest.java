package com.cp.ecommerce.adapter.web.review;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ReviewBuilder;
import com.cp.ecommerce.adapter.web.review.mapper.ReviewWebMapper;
import com.cp.ecommerce.adapter.web.review.resource.ReviewResource;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.port.incoming.ReviewModerationInPort;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_ID;

/**
 * Test class checking review moderation controller's behavior and API responses.
 */
@WebMvcTest(ReviewModerationController.class)
class ReviewModerationControllerTest {

    private static final String MODERATION_ENDPOINT = "/api/reviews/moderation";

    private static final String PENDING_ENDPOINT = MODERATION_ENDPOINT + "/pending";

    private static final String APPROVE_ENDPOINT = MODERATION_ENDPOINT + "/" + TEST_REVIEW_ID + "/approve";

    private static final String REJECT_ENDPOINT = MODERATION_ENDPOINT + "/" + TEST_REVIEW_ID + "/reject";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient ReviewModerationInPort reviewModerationInPort;

    @MockitoBean
    private transient ReviewWebMapper reviewWebMapper;

    @Test
    void shouldListPendingReviews() throws Exception {

        final Review review = ReviewBuilder.mockReview();
        given(reviewModerationInPort.listPendingReviews()).willReturn(List.of(review));
        given(reviewWebMapper.mapToResource(review)).willReturn(Optional.of(mockReviewResource()));

        mockMvc.perform(get(PENDING_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewId").value(TEST_REVIEW_ID));
    }

    @Test
    void shouldApproveReview() throws Exception {

        final Review review = ReviewBuilder.mockReview();
        given(reviewModerationInPort.approveReview(TEST_REVIEW_ID)).willReturn(review);
        given(reviewWebMapper.mapToResource(review)).willReturn(Optional.of(mockReviewResource()));

        mockMvc.perform(post(APPROVE_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(TEST_REVIEW_ID));
    }

    @Test
    void shouldReturnNotFoundWhenApprovingUnknownReview() throws Exception {

        given(reviewModerationInPort.approveReview(TEST_REVIEW_ID)).willReturn(null);

        mockMvc.perform(post(APPROVE_ENDPOINT)).andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectReview() throws Exception {

        final Review review = ReviewBuilder.mockReview();
        given(reviewModerationInPort.rejectReview(TEST_REVIEW_ID)).willReturn(review);
        given(reviewWebMapper.mapToResource(review)).willReturn(Optional.of(mockReviewResource()));

        mockMvc.perform(post(REJECT_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(TEST_REVIEW_ID));
    }

    @Test
    void shouldReturnNotFoundWhenRejectingUnknownReview() throws Exception {

        given(reviewModerationInPort.rejectReview(TEST_REVIEW_ID)).willReturn(null);

        mockMvc.perform(post(REJECT_ENDPOINT)).andExpect(status().isNotFound());
    }

    @Test
    void shouldThrowTechnicalProblemWhenMappingReviewResourceReturnsEmpty() throws Exception {

        final Review review = ReviewBuilder.mockReview();
        given(reviewModerationInPort.approveReview(TEST_REVIEW_ID)).willReturn(review);
        given(reviewWebMapper.mapToResource(review)).willReturn(Optional.empty());

        mockMvc.perform(post(APPROVE_ENDPOINT)).andExpect(status().isInternalServerError());
    }

    private static ReviewResource mockReviewResource() {

        return ReviewResource.builder().reviewId(TEST_REVIEW_ID).build();
    }

}
