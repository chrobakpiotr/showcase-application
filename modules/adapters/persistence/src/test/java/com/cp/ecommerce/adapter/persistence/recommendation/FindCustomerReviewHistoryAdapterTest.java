package com.cp.ecommerce.adapter.persistence.recommendation;

import java.util.Date;
import java.util.List;
import java.util.Set;

import com.cp.ecommerce.adapter.persistence.customer.entity.CustomerEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntity;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntityRepository;
import com.cp.ecommerce.domain.review.ReviewStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FindCustomerReviewHistoryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindCustomerReviewHistoryAdapterTest {

    private static final String CUSTOMER_EMAIL = "john.doe@test.com";

    private static final String PRIMARY_NAME = "John Doe";

    @Mock
    private transient OrderEntityRepository orderEntityRepository;

    @Mock
    private transient ReviewEntityRepository reviewEntityRepository;

    @InjectMocks
    private transient FindCustomerReviewHistoryAdapter adapter;

    @Test
    void shouldResolveReviewsUsingDistinctCustomerNamesFromOrders() {

        when(orderEntityRepository.findTop10ByCustomerEmailOrderByCreatedDesc(CUSTOMER_EMAIL))
                .thenReturn(List.of(order(PRIMARY_NAME), order(PRIMARY_NAME), order("Johnny Doe")));
        when(
                reviewEntityRepository.findByAuthorNameInAndStatusOrderByCreatedDateDesc(
                        Set.of(PRIMARY_NAME, "Johnny Doe"),
                        ReviewStatus.APPROVED))
                .thenReturn(List.of(review("SKU-1", 5, "Great")));

        final var result = adapter.findReviews(CUSTOMER_EMAIL);

        assertThat(result).singleElement().satisfies(review -> {
            assertThat(review.getSku()).isEqualTo("SKU-1");
            assertThat(review.getRating()).isEqualTo(5);
            assertThat(review.getComment()).isEqualTo("Great");
        });
        verify(reviewEntityRepository)
                .findByAuthorNameInAndStatusOrderByCreatedDateDesc(Set.of(PRIMARY_NAME, "Johnny Doe"), ReviewStatus.APPROVED);
    }

    @Test
    void shouldReturnEmptyWhenNoCustomerNameCanBeResolved() {

        when(orderEntityRepository.findTop10ByCustomerEmailOrderByCreatedDesc(CUSTOMER_EMAIL))
                .thenReturn(List.of(order(" "), order(null), orderWithNullCustomer()));

        assertThat(adapter.findReviews(CUSTOMER_EMAIL)).isEmpty();
        verifyNoInteractions(reviewEntityRepository);
    }

    private OrderEntity order(final String fullName) {

        final CustomerEntity customer = new CustomerEntity();
        customer.setEmail(CUSTOMER_EMAIL);
        customer.setFullName(fullName);
        final OrderEntity order = new OrderEntity();
        order.setCreated(new Date());
        order.setCustomer(customer);
        return order;
    }

    private OrderEntity orderWithNullCustomer() {

        final OrderEntity order = new OrderEntity();
        order.setCreated(new Date());
        return order;
    }

    private ReviewEntity review(final String sku, final int rating, final String comment) {

        final ReviewEntity review = new ReviewEntity();
        review.setReviewId("REVIEW-1");
        review.setSku(sku);
        review.setAuthorName(PRIMARY_NAME);
        review.setRating(rating);
        review.setComment(comment);
        review.setStatus(ReviewStatus.APPROVED);
        review.setCreatedDate(new Date());
        return review;
    }

}
