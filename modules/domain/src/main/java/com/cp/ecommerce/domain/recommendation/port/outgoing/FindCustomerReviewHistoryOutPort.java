package com.cp.ecommerce.domain.recommendation.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.recommendation.CustomerReviewProfile;

/**
 * Outgoing port for reading a customer's review history.
 */
public interface FindCustomerReviewHistoryOutPort {

    List<CustomerReviewProfile> findReviews(String customerEmail);

}
