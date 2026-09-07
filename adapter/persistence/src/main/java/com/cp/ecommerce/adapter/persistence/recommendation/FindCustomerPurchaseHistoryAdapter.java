package com.cp.ecommerce.adapter.persistence.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderLineItemEmbeddable;
import com.cp.ecommerce.domain.recommendation.PurchasedProductProfile;
import com.cp.ecommerce.domain.recommendation.port.outgoing.FindCustomerPurchaseHistoryOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Reads a customer's recent purchased products by email from existing order history.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindCustomerPurchaseHistoryAdapter implements FindCustomerPurchaseHistoryOutPort {

    private final OrderEntityRepository orderEntityRepository;

    @Override
    public List<PurchasedProductProfile> findPurchasedProducts(final String customerEmail) {

        final List<OrderEntity> recentOrders = orderEntityRepository.findTop10ByCustomerEmailOrderByCreatedDesc(customerEmail);
        final Map<String, PurchasedProductProfile> aggregated = new LinkedHashMap<>();
        for (OrderEntity order : recentOrders) {
            for (OrderLineItemEmbeddable item : order.getItems()) {
                aggregated.merge(
                        item.getSku(),
                        PurchasedProductProfile.builder()
                                .sku(item.getSku())
                                .productName(item.getProductName())
                                .totalQuantity(item.getQuantity())
                                .build(),
                        (left, right) -> PurchasedProductProfile.builder()
                                .sku(left.getSku())
                                .productName(left.getProductName())
                                .totalQuantity(left.getTotalQuantity() + right.getTotalQuantity())
                                .build());
            }
        }
        return List.copyOf(aggregated.values());
    }

}
