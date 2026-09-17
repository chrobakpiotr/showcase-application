package com.cp.ecommerce.adapter.persistence.recommendation;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.persistence.customer.entity.CustomerEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderLineItemEmbeddable;
import com.cp.ecommerce.domain.recommendation.PurchasedProductProfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FindCustomerPurchaseHistoryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindCustomerPurchaseHistoryAdapterTest {

    @Mock
    private transient OrderEntityRepository orderEntityRepository;

    @InjectMocks
    private transient FindCustomerPurchaseHistoryAdapter adapter;

    @Test
    void shouldAggregateQuantitiesPerSkuAcrossRecentOrders() {

        when(orderEntityRepository.findTop10ByCustomerEmailOrderByCreatedDesc("john.doe@test.com")).thenReturn(
                List.of(order(item("SKU-1", "Mouse", 1), item("SKU-2", "Keyboard", 2)), order(item("SKU-1", "Mouse", 3))));

        final List<PurchasedProductProfile> result = adapter.findPurchasedProducts("john.doe@test.com");

        assertThat(result).containsExactly(
                PurchasedProductProfile.builder().sku("SKU-1").productName("Mouse").totalQuantity(4).build(),
                PurchasedProductProfile.builder().sku("SKU-2").productName("Keyboard").totalQuantity(2).build());
    }

    private OrderEntity order(final OrderLineItemEmbeddable... items) {

        final CustomerEntity customer = new CustomerEntity();
        customer.setEmail("john.doe@test.com");
        customer.setFullName("John Doe");
        final OrderEntity order = new OrderEntity();
        order.setCreated(new Date());
        order.setCustomer(customer);
        order.setItems(List.of(items));
        return order;
    }

    private OrderLineItemEmbeddable item(final String sku, final String name, final int quantity) {

        final OrderLineItemEmbeddable item = new OrderLineItemEmbeddable();
        item.setSku(sku);
        item.setProductName(name);
        item.setQuantity(quantity);
        return item;
    }

}
