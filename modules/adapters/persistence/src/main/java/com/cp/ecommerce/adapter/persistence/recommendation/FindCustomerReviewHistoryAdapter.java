package com.cp.ecommerce.adapter.persistence.recommendation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntity;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntityRepository;
import com.cp.ecommerce.domain.recommendation.CustomerReviewProfile;
import com.cp.ecommerce.domain.recommendation.port.outgoing.FindCustomerReviewHistoryOutPort;
import com.cp.ecommerce.domain.review.ReviewStatus;

import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * Reads a customer's review history by first resolving the display names previously used on that customer's own orders.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindCustomerReviewHistoryAdapter implements FindCustomerReviewHistoryOutPort {

    private final OrderEntityRepository orderEntityRepository;

    private final ReviewEntityRepository reviewEntityRepository;

    @Override
    public List<CustomerReviewProfile> findReviews(final String customerEmail) {

        final Set<String> authorNames = orderEntityRepository.findTop10ByCustomerEmailOrderByCreatedDesc(customerEmail)
                .stream()
                .map(OrderEntity::getCustomer)
                .filter(customer -> customer != null && StringUtils.hasText(customer.getFullName()))
                .map(customer -> customer.getFullName().trim())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (authorNames.isEmpty()) {
            return List.of();
        }
        return reviewEntityRepository.findByAuthorNameInAndStatusOrderByCreatedDateDesc(authorNames, ReviewStatus.APPROVED)
                .stream()
                .map(this::toProfile)
                .toList();
    }

    private CustomerReviewProfile toProfile(final ReviewEntity entity) {

        return CustomerReviewProfile.builder()
                .sku(entity.getSku())
                .rating(entity.getRating())
                .comment(entity.getComment())
                .build();
    }

}
