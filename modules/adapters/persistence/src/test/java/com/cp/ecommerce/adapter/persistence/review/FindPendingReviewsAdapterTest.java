package com.cp.ecommerce.adapter.persistence.review;

import java.util.List;

import com.cp.ecommerce.adapter.common.utils.ReviewBuilder;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntity;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntityRepository;
import com.cp.ecommerce.adapter.persistence.review.mapper.ReviewPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ReviewEntityBuilder;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import static java.util.Optional.empty;
import static java.util.Optional.of;

/**
 * Test class for {@link FindPendingReviewsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindPendingReviewsAdapterTest {

    @InjectMocks
    private transient FindPendingReviewsAdapter findPendingReviewsAdapter;

    @Mock
    private transient ReviewEntityRepository reviewEntityRepository;

    @Mock
    private transient ReviewPersistenceMapper reviewPersistenceMapper;

    @Test
    void shouldFindPendingReviews() {

        final ReviewEntity entity = ReviewEntityBuilder.mockReviewEntity();
        final Review review = ReviewBuilder.mockReview();
        doReturn(List.of(entity)).when(reviewEntityRepository).findByStatusOrderByCreatedDateAsc(ReviewStatus.PENDING);
        doReturn(of(review)).when(reviewPersistenceMapper).mapToDomainObject(entity);

        final List<Review> result = findPendingReviewsAdapter.findPending();

        assertEquals(List.of(review), result);
    }

    @Test
    void shouldThrowWhenMappingFails() {

        final ReviewEntity entity = ReviewEntityBuilder.mockReviewEntity();
        doReturn(List.of(entity)).when(reviewEntityRepository).findByStatusOrderByCreatedDateAsc(ReviewStatus.PENDING);
        doReturn(empty()).when(reviewPersistenceMapper).mapToDomainObject(entity);

        assertThrows(IllegalStateException.class, () -> findPendingReviewsAdapter.findPending());
    }

}
