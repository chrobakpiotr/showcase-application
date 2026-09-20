package com.cp.ecommerce.domain.cart.usecase;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.CartLineItem;
import com.cp.ecommerce.domain.cart.port.outgoing.FindCartOutPort;
import com.cp.ecommerce.domain.cart.port.outgoing.GenerateCartIdOutPort;
import com.cp.ecommerce.domain.cart.port.outgoing.SaveCartOutPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ManageCartUseCaseMutationWave3Test {

    private static final String CART_ID = "CART-1";

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
    void shouldFindTargetSkuInsteadOfBlindlySelectingFirstItem() {
        final CartLineItem first = item("SKU-FIRST", "First", "3.00", 1);
        final CartLineItem target = item("SKU-TARGET", "Target", "5.00", 2);
        final Cart existing = Cart.builder().cartId(CART_ID).items(List.of(first, target)).version(4).build();

        given(findCartOutPort.find(CART_ID)).willReturn(existing);
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Cart result = useCase.updateItemQuantity(CART_ID, "SKU-TARGET", 7);

        assertThat(result.getItems()).filteredOn(item -> item.getSku().equals("SKU-FIRST")).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("First");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("3.00");
            assertThat(item.getQuantity()).isEqualTo(1);
        });

        assertThat(result.getItems()).filteredOn(item -> item.getSku().equals("SKU-TARGET")).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("Target");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("5.00");
            assertThat(item.getQuantity()).isEqualTo(7);
        });
    }

    private static CartLineItem item(final String sku, final String name, final String unitPrice, final int quantity) {
        return CartLineItem.builder()
                .sku(sku)
                .productName(name)
                .unitPrice(new BigDecimal(unitPrice))
                .quantity(quantity)
                .build();
    }
}
