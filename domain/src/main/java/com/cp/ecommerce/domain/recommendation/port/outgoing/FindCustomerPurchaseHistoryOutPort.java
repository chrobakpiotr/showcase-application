package com.cp.ecommerce.domain.recommendation.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.recommendation.PurchasedProductProfile;

/**
 * Outgoing port for reading a customer's recent purchased products by e-mail.
 */
public interface FindCustomerPurchaseHistoryOutPort {

    List<PurchasedProductProfile> findPurchasedProducts(String customerEmail);

}
