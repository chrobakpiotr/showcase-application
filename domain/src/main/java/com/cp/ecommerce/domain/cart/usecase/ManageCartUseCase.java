package com.cp.ecommerce.domain.cart.usecase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.CartLineItem;
import com.cp.ecommerce.domain.cart.port.incoming.CreateCartInPort;
import com.cp.ecommerce.domain.cart.port.incoming.GetCartInPort;
import com.cp.ecommerce.domain.cart.port.incoming.ManageCartInPort;
import com.cp.ecommerce.domain.cart.port.outgoing.FindCartOutPort;
import com.cp.ecommerce.domain.cart.port.outgoing.GenerateCartIdOutPort;
import com.cp.ecommerce.domain.cart.port.outgoing.SaveCartOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for creating shopping carts and mutating their line items.
 */
@UseCase
@RequiredArgsConstructor
public class ManageCartUseCase implements CreateCartInPort, GetCartInPort, ManageCartInPort {

    private final FindCartOutPort findCartOutPort;

    private final SaveCartOutPort saveCartOutPort;

    private final GenerateCartIdOutPort generateCartIdOutPort;

    @Override
    public Cart createCart() {

        final Cart cart = Cart.builder().cartId(generateCartIdOutPort.generate()).updated(new Date()).build();
        cart.assertValidationsEmpty();
        return saveCartOutPort.save(cart);
    }

    @Override
    public Cart getCart(final String cartId) {

        return findCartOutPort.find(cartId);
    }

    @Override
    public Cart addItem(
            final String cartId,
            final String sku,
            final String productName,
            final BigDecimal unitPrice,
            final int quantity) {

        final Cart existing = findCartOutPort.find(cartId);
        if (existing == null) {

            return null;
        }
        final List<CartLineItem> items = new ArrayList<>(existing.getItems());
        final Optional<CartLineItem> currentItem = findItem(items, sku);
        currentItem.ifPresentOrElse(item -> {

            items.remove(item);
            items.add(
                    CartLineItem.builder()
                            .sku(sku)
                            .productName(productName)
                            .unitPrice(unitPrice)
                            .quantity(item.getQuantity() + quantity)
                            .build());
        },
                () -> items.add(
                        CartLineItem.builder()
                                .sku(sku)
                                .productName(productName)
                                .unitPrice(unitPrice)
                                .quantity(quantity)
                                .build()));
        return persist(existing, items);
    }

    @Override
    public Cart updateItemQuantity(final String cartId, final String sku, final int quantity) {

        final Cart existing = findCartOutPort.find(cartId);
        if (existing == null) {

            return null;
        }
        final List<CartLineItem> items = new ArrayList<>(existing.getItems());
        final Optional<CartLineItem> currentItem = findItem(items, sku);
        if (currentItem.isEmpty()) {

            return existing;
        }
        items.remove(currentItem.get());
        items.add(
                CartLineItem.builder()
                        .sku(sku)
                        .productName(currentItem.get().getProductName())
                        .unitPrice(currentItem.get().getUnitPrice())
                        .quantity(quantity)
                        .build());
        return persist(existing, items);
    }

    @Override
    public Cart removeItem(final String cartId, final String sku) {

        final Cart existing = findCartOutPort.find(cartId);
        if (existing == null) {

            return null;
        }
        final List<CartLineItem> items = new ArrayList<>(existing.getItems());
        items.removeIf(item -> item.getSku().equals(sku));
        return persist(existing, items);
    }

    @Override
    public Cart clearCart(final String cartId) {

        final Cart existing = findCartOutPort.find(cartId);
        if (existing == null) {

            return null;
        }
        return persist(existing, List.of());
    }

    private Optional<CartLineItem> findItem(final List<CartLineItem> items, final String sku) {

        return items.stream().filter(item -> item.getSku().equals(sku)).findFirst();
    }

    private Cart persist(final Cart existing, final List<CartLineItem> items) {

        final Cart mutated = Cart.builder()
                .cartId(existing.getCartId())
                .items(items)
                .updated(new Date())
                .version(existing.getVersion())
                .build();
        mutated.assertValidationsEmpty();
        return saveCartOutPort.save(mutated);
    }

}
