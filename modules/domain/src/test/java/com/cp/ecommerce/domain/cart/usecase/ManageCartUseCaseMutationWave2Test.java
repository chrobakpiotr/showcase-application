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
class ManageCartUseCaseMutationWave2Test {

    private static final String CART = "CART-1";

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
    void shouldReturnNullForCouponOperationsOnMissingCart() {
        given(findCartOutPort.find(CART)).willReturn(null);

        assertThat(useCase.applyCoupon(CART, "SAVE10", BigDecimal.ONE)).isNull();
        assertThat(useCase.removeCoupon(CART)).isNull();
        verify(saveCartOutPort, never()).save(any());
    }

    @Test
    void shouldValidateApplyCouponResultBeforeSave() {
        final Cart invalid = invalidCart();
        given(findCartOutPort.find(CART)).willReturn(invalid);

        assertThatThrownBy(() -> useCase.applyCoupon(CART, "SAVE10", BigDecimal.ONE))
                .isInstanceOf(DomainObjectValidationException.class);
        verify(saveCartOutPort, never()).save(any());
    }

    @Test
    void shouldValidateRemoveCouponResultBeforeSave() {
        final Cart invalid = invalidCart();
        given(findCartOutPort.find(CART)).willReturn(invalid);

        assertThatThrownBy(() -> useCase.removeCoupon(CART)).isInstanceOf(DomainObjectValidationException.class);
        verify(saveCartOutPort, never()).save(any());
    }

    private static Cart invalidCart() {
        final CartLineItem invalidItem = CartLineItem.builder()
                .sku(" ")
                .productName("Product")
                .unitPrice(BigDecimal.ONE)
                .quantity(1)
                .build();
        return Cart.builder().cartId(CART).items(List.of(invalidItem)).version(3).build();
    }
}
