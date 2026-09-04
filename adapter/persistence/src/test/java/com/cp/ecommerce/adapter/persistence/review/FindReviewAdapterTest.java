package com.cp.ecommerce.adapter.persistence.review;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ReviewBuilder;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntityRepository;
import com.cp.ecommerce.adapter.persistence.review.mapper.ReviewPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ReviewEntityBuilder;
import com.cp.ecommerce.domain.review.Review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_ID;

/**
 * Test class for {@link FindReviewAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindReviewAdapterTest {

    @InjectMocks
    private transient FindReviewAdapter findReviewAdapter;

    @Mock
    private transient ReviewEntityRepository reviewEntityRepository;

    @Mock
    private transient ReviewPersistenceMapper reviewPersistenceMapper;

    @Test
    void shouldFindReviewById() {

        final var entity = ReviewEntityBuilder.mockReviewEntity();
        final Review review = ReviewBuilder.mockReview();
        doReturn(Optional.of(entity)).when(reviewEntityRepository).findById(TEST_REVIEW_ID);
        doReturn(Optional.of(review)).when(reviewPersistenceMapper).mapToDomainObject(entity);

        final Review result = findReviewAdapter.find(TEST_REVIEW_ID);

        assertEquals(review, result);
    }

    @Test
    void shouldReturnNullWhenReviewNotFound() {

        doReturn(Optional.empty()).when(reviewEntityRepository).findById(TEST_REVIEW_ID);

        final Review result = findReviewAdapter.find(TEST_REVIEW_ID);

        assertNull(result);
    }

}
