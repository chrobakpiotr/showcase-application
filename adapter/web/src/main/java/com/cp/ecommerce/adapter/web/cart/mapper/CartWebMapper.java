package com.cp.ecommerce.adapter.web.cart.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.cart.resource.CartLineItemResource;
import com.cp.ecommerce.adapter.web.cart.resource.CartResource;
import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.CartLineItem;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link Cart} domain object to its web resource.
 */
@Component
public class CartWebMapper implements WebResponseMapper<Cart, CartResource> {

    @Override
    public Optional<CartResource> mapToResource(final Cart cart) {

        return Optional.ofNullable(cart)
                .map(
                        domain -> CartResource.builder()
                                .cartId(domain.getCartId())
                                .items(domain.getItems().stream().map(this::mapItemToResource).toList())
                                .total(domain.getTotal())
                                .itemCount(domain.getItemCount())
                                .build());
    }

    private CartLineItemResource mapItemToResource(final CartLineItem item) {

        return CartLineItemResource.builder()
                .sku(item.getSku())
                .productName(item.getProductName())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .build();
    }

}
