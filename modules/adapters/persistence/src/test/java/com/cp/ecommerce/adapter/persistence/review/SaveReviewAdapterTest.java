package com.cp.ecommerce.adapter.persistence.review;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ReviewBuilder;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntity;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link SaveReviewAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveReviewAdapterTest {

    @InjectMocks
    private transient SaveReviewAdapter saveReviewAdapter;

    @Mock
    private transient ReviewEntityRepository reviewEntityRepository;

    @Mock
    private transient ReviewPersistenceMapper reviewPersistenceMapper;

    @Test
    void shouldSaveAndReturnMappedReview() {

        final Review review = ReviewBuilder.mockReview();
        final ReviewEntity mappedEntity = ReviewEntityBuilder.mockReviewEntity();
        doReturn(Optional.of(mappedEntity)).when(reviewPersistenceMapper).mapToEntity(eq(review));
        doReturn(mappedEntity).when(reviewEntityRepository).save(mappedEntity);
        doReturn(Optional.of(review)).when(reviewPersistenceMapper).mapToDomainObject(mappedEntity);

        final Review result = saveReviewAdapter.save(review);

        assertEquals(review, result);
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final Review review = ReviewBuilder.mockReview();
        doReturn(Optional.empty()).when(reviewPersistenceMapper).mapToEntity(eq(review));

        assertThrows(IllegalStateException.class, () -> saveReviewAdapter.save(review));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final Review review = ReviewBuilder.mockReview();
        final ReviewEntity mappedEntity = ReviewEntityBuilder.mockReviewEntity();
        doReturn(Optional.of(mappedEntity)).when(reviewPersistenceMapper).mapToEntity(eq(review));
        doReturn(mappedEntity).when(reviewEntityRepository).save(mappedEntity);
        doReturn(Optional.empty()).when(reviewPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> saveReviewAdapter.save(review));
    }

}
