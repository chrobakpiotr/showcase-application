package com.cp.ecommerce.domain.cart.usecase;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.CartLineItem;
import com.cp.ecommerce.domain.cart.port.outgoing.FindCartOutPort;
import com.cp.ecommerce.domain.cart.port.outgoing.GenerateCartIdOutPort;
import com.cp.ecommerce.domain.cart.port.outgoing.SaveCartOutPort;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageCartUseCaseMutationTest {

    private static final String CART_ID = "CART-1";
    private static final String SKU = "SKU-1";

    @Mock
    private FindCartOutPort findCartOutPort;
    @Mock
    private SaveCartOutPort saveCartOutPort;
    @Mock
    private GenerateCartIdOutPort generateCartIdOutPort;

    private ManageCartUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageCartUseCase(findCartOutPort, saveCartOutPort, generateCartIdOutPort);
    }

    @Test
    void shouldValidateGeneratedCartBeforeSaving() {
        given(generateCartIdOutPort.generate()).willReturn(null);

        assertThatThrownBy(useCase::createCart).isInstanceOf(DomainObjectValidationException.class);
        verify(saveCartOutPort, never()).save(any());
    }

    @Test
    void shouldClearCouponWhenAddingNewItemAndPreserveVersion() {
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        given(findCartOutPort.find(CART_ID)).willReturn(cartWithCoupon(List.of()));

        final Cart result = useCase.addItem(CART_ID, SKU, "New name", new BigDecimal("12.50"), 2);

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSku()).isEqualTo(SKU);
            assertThat(item.getProductName()).isEqualTo("New name");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("12.50");
            assertThat(item.getQuantity()).isEqualTo(2);
        });
        assertThat(result.getCouponCode()).isNull();
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(result.getVersion()).isEqualTo(7);
        assertThat(result.getUpdated()).isNotNull();
    }

    @Test
    void shouldClearCouponWhenChangingExistingItemQuantity() {
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final CartLineItem item = item(SKU, "Old", "5.00", 2);
        given(findCartOutPort.find(CART_ID)).willReturn(cartWithCoupon(List.of(item)));

        final Cart result = useCase.updateItemQuantity(CART_ID, SKU, 4);

        assertThat(result.getItems()).singleElement().satisfies(updated -> {
            assertThat(updated.getSku()).isEqualTo(SKU);
            assertThat(updated.getProductName()).isEqualTo("Old");
            assertThat(updated.getUnitPrice()).isEqualByComparingTo("5.00");
            assertThat(updated.getQuantity()).isEqualTo(4);
        });
        assertThat(result.getCouponCode()).isNull();
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(result.getVersion()).isEqualTo(7);
    }

    @Test
    void shouldPreserveCouponWhenRemovingAbsentSku() {
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Cart existing = cartWithCoupon(List.of(item(SKU, "Product", "5.00", 1)));
        given(findCartOutPort.find(CART_ID)).willReturn(existing);

        final Cart result = useCase.removeItem(CART_ID, "OTHER");

        assertThat(result.getItems()).containsExactlyElementsOf(existing.getItems());
        assertThat(result.getCouponCode()).isEqualTo("SAVE10");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("3.00");
        assertThat(result.getVersion()).isEqualTo(7);
    }

    @Test
    void shouldClearCouponWhenRemovingExistingSku() {
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        given(findCartOutPort.find(CART_ID)).willReturn(cartWithCoupon(List.of(item(SKU, "Product", "5.00", 1))));

        final Cart result = useCase.removeItem(CART_ID, SKU);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getCouponCode()).isNull();
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("0");
    }

    @Test
    void shouldPreserveCouponWhenClearingAlreadyEmptyCart() {
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        given(findCartOutPort.find(CART_ID)).willReturn(cartWithCoupon(List.of()));

        final Cart result = useCase.clearCart(CART_ID);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getCouponCode()).isEqualTo("SAVE10");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("3.00");
    }

    @Test
    void shouldClearCouponWhenClearingNonEmptyCart() {
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        given(findCartOutPort.find(CART_ID)).willReturn(cartWithCoupon(List.of(item(SKU, "Product", "5.00", 1))));

        final Cart result = useCase.clearCart(CART_ID);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getCouponCode()).isNull();
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("0");
    }

    @Test
    void shouldApplyAndRemoveCouponWhilePreservingItemsAndVersion() {
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final CartLineItem item = item(SKU, "Product", "5.00", 2);
        final Cart existing = Cart.builder().cartId(CART_ID).items(List.of(item)).version(7).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);

        final Cart applied = useCase.applyCoupon(CART_ID, "SAVE10", new BigDecimal("1.25"));

        assertThat(applied.getItems()).containsExactly(item);
        assertThat(applied.getCouponCode()).isEqualTo("SAVE10");
        assertThat(applied.getDiscountAmount()).isEqualByComparingTo("1.25");
        assertThat(applied.getVersion()).isEqualTo(7);
        assertThat(applied.getUpdated()).isNotNull();

        given(findCartOutPort.find(CART_ID)).willReturn(applied);
        final Cart removed = useCase.removeCoupon(CART_ID);

        assertThat(removed.getItems()).containsExactly(item);
        assertThat(removed.getCouponCode()).isNull();
        assertThat(removed.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(removed.getVersion()).isEqualTo(7);
    }

    @Test
    void shouldRejectInvalidNestedItemBeforeSave() {
        final Cart existing = Cart.builder().cartId(CART_ID).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);

        assertThatThrownBy(() -> useCase.addItem(CART_ID, SKU, "Product", BigDecimal.ZERO, 1))
                .isInstanceOf(DomainObjectValidationException.class);

        verify(saveCartOutPort, never()).save(any());
    }

    private static Cart cartWithCoupon(final List<CartLineItem> items) {
        return Cart.builder()
                .cartId(CART_ID)
                .items(items)
                .couponCode("SAVE10")
                .discountAmount(new BigDecimal("3.00"))
                .version(7)
                .build();
    }

    private static CartLineItem item(final String sku, final String productName, final String price, final int quantity) {
        return CartLineItem.builder()
                .sku(sku)
                .productName(productName)
                .unitPrice(new BigDecimal(price))
                .quantity(quantity)
                .build();
    }
}
