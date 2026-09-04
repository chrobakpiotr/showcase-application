package com.cp.ecommerce.adapter.persistence.cart.mapper;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.cart.entity.CartEntity;
import com.cp.ecommerce.adapter.persistence.cart.entity.CartLineItemEmbeddable;
import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.CartLineItem;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link Cart} object into/from entity object.
 */
@Component
public class CartPersistenceMapper implements PersistenceMapper<Cart, CartEntity> {

    @Override
    public Optional<CartEntity> mapToEntity(final Cart cart) {

        return ofNullable(cart).map(
                domain -> CartEntity.builder()
                        .cartId(domain.getCartId())
                        .items(domain.getItems().stream().map(this::mapItemToEmbeddable).toList())
                        .updated(domain.getUpdated())
                        .version(domain.getVersion())
                        .build());
    }

    @Override
    public Optional<Cart> mapToDomainObject(final CartEntity entity) {

        return ofNullable(entity).map(
                cartEntity -> Cart.builder()
                        .cartId(cartEntity.getCartId())
                        .items(mapItemsToDomainObjects(cartEntity.getItems()))
                        .updated(cartEntity.getUpdated())
                        .version(cartEntity.getVersion())
                        .build());
    }

    private CartLineItemEmbeddable mapItemToEmbeddable(final CartLineItem item) {

        return CartLineItemEmbeddable.builder()
                .sku(item.getSku())
                .productName(item.getProductName())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .build();
    }

    private List<CartLineItem> mapItemsToDomainObjects(final List<CartLineItemEmbeddable> items) {

        return items.stream()
                .map(
                        item -> CartLineItem.builder()
                                .sku(item.getSku())
                                .productName(item.getProductName())
                                .unitPrice(item.getUnitPrice())
                                .quantity(item.getQuantity())
                                .build())
                .toList();
    }

}
