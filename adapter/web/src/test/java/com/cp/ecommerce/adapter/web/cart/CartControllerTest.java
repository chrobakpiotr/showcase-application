package com.cp.ecommerce.adapter.web.cart;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CartBuilder;
import com.cp.ecommerce.adapter.common.utils.ProductBuilder;
import com.cp.ecommerce.adapter.web.cart.mapper.CartWebMapper;
import com.cp.ecommerce.adapter.web.cart.resource.CartLineItemResource;
import com.cp.ecommerce.adapter.web.cart.resource.CartResource;
import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.port.incoming.CreateCartInPort;
import com.cp.ecommerce.domain.cart.port.incoming.GetCartInPort;
import com.cp.ecommerce.domain.cart.port.incoming.ManageCartInPort;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_ID;
import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_SKU;

/**
 * Test class checking cart controller's behavior and API responses.
 */
@WebMvcTest(CartController.class)
class CartControllerTest {

    private static final String CART_ENDPOINT = "/api/cart";

    private static final String CART_BY_ID_ENDPOINT = CART_ENDPOINT + "/" + TEST_CART_ID;

    private static final String ITEMS_ENDPOINT = CART_BY_ID_ENDPOINT + "/items";

    private static final String ITEM_BY_SKU_ENDPOINT = ITEMS_ENDPOINT + "/" + TEST_CART_SKU;

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient CreateCartInPort createCartInPort;

    @MockitoBean
    private transient GetCartInPort getCartInPort;

    @MockitoBean
    private transient ManageCartInPort manageCartInPort;

    @MockitoBean
    private transient ManageProductInPort manageProductInPort;

    @MockitoBean
    private transient CartWebMapper cartWebMapper;

    @Test
    void shouldCreateCart() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(createCartInPort.createCart()).willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.of(mockCartResource()));

        mockMvc.perform(post(CART_ENDPOINT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cartId").value(TEST_CART_ID));
    }

    @Test
    void shouldGetCart() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(getCartInPort.getCart(TEST_CART_ID)).willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.of(mockCartResource()));

        mockMvc.perform(get(CART_BY_ID_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(TEST_CART_ID));
    }

    @Test
    void shouldReturnNotFoundWhenGettingUnknownCart() throws Exception {

        given(getCartInPort.getCart(TEST_CART_ID)).willReturn(null);

        mockMvc.perform(get(CART_BY_ID_ENDPOINT)).andExpect(status().isNotFound());
    }

    @Test
    void shouldAddItemResolvingProductServerSide() throws Exception {

        final Product product = ProductBuilder.mockProduct();
        final Cart cart = CartBuilder.mockCart();
        given(manageProductInPort.findProduct(TEST_CART_SKU)).willReturn(product);
        given(manageCartInPort.addItem(TEST_CART_ID, TEST_CART_SKU, product.getName(), product.getUnitPrice(), 2))
                .willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.of(mockCartResource()));

        mockMvc.perform(post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(addItemJson(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(TEST_CART_ID));
    }

    @Test
    void shouldReturnNotFoundWhenAddingItemToUnknownCart() throws Exception {

        final Product product = ProductBuilder.mockProduct();
        given(manageProductInPort.findProduct(TEST_CART_SKU)).willReturn(product);
        given(manageCartInPort.addItem(TEST_CART_ID, TEST_CART_SKU, product.getName(), product.getUnitPrice(), 2))
                .willReturn(null);

        mockMvc.perform(post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(addItemJson(2)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenAddingItemWithUnknownSku() throws Exception {

        given(manageProductInPort.findProduct(TEST_CART_SKU)).willReturn(null);

        mockMvc.perform(post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(addItemJson(2)))
                .andExpect(status().isNotFound());
        verify(manageCartInPort, never()).addItem(anyString(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void shouldRejectAddItemWithMissingSku() throws Exception {

        mockMvc.perform(post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isBadRequest());
        verify(manageProductInPort, never()).findProduct(anyString());
    }

    @Test
    void shouldRejectAddItemWithMissingQuantity() throws Exception {

        mockMvc.perform(
                post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"" + TEST_CART_SKU + "\"}"))
                .andExpect(status().isBadRequest());
        verify(manageCartInPort, never()).addItem(anyString(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void shouldRejectAddItemWithZeroQuantity() throws Exception {

        mockMvc.perform(post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(addItemJson(0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateItemQuantity() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(manageCartInPort.updateItemQuantity(TEST_CART_ID, TEST_CART_SKU, 5)).willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.of(mockCartResource()));

        mockMvc.perform(put(ITEM_BY_SKU_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectUpdateItemQuantityWithZeroQuantity() throws Exception {

        mockMvc.perform(put(ITEM_BY_SKU_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        verify(manageCartInPort, never()).updateItemQuantity(anyString(), anyString(), anyInt());
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingItemQuantityForUnknownCart() throws Exception {

        given(manageCartInPort.updateItemQuantity(TEST_CART_ID, TEST_CART_SKU, 5)).willReturn(null);

        mockMvc.perform(put(ITEM_BY_SKU_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRemoveItem() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(manageCartInPort.removeItem(TEST_CART_ID, TEST_CART_SKU)).willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.of(mockCartResource()));

        mockMvc.perform(delete(ITEM_BY_SKU_ENDPOINT)).andExpect(status().isOk());
    }

    @Test
    void shouldReturnNotFoundWhenRemovingItemFromUnknownCart() throws Exception {

        given(manageCartInPort.removeItem(TEST_CART_ID, TEST_CART_SKU)).willReturn(null);

        mockMvc.perform(delete(ITEM_BY_SKU_ENDPOINT)).andExpect(status().isNotFound());
    }

    @Test
    void shouldClearCart() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(manageCartInPort.clearCart(TEST_CART_ID)).willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.of(mockCartResource()));

        mockMvc.perform(delete(CART_BY_ID_ENDPOINT)).andExpect(status().isOk());
    }

    @Test
    void shouldReturnNotFoundWhenClearingUnknownCart() throws Exception {

        given(manageCartInPort.clearCart(TEST_CART_ID)).willReturn(null);

        mockMvc.perform(delete(CART_BY_ID_ENDPOINT)).andExpect(status().isNotFound());
    }

    @Test
    void shouldThrowTechnicalProblemWhenMapToResourceReturnsEmpty() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(getCartInPort.getCart(TEST_CART_ID)).willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.empty());

        mockMvc.perform(get(CART_BY_ID_ENDPOINT)).andExpect(status().isInternalServerError());
    }

    private static String addItemJson(final int quantity) {

        return "{\"sku\":\"" + TEST_CART_SKU + "\",\"quantity\":" + quantity + "}";
    }

    private static CartResource mockCartResource() {

        return CartResource.builder()
                .cartId(TEST_CART_ID)
                .items(
                        java.util.List.of(
                                CartLineItemResource.builder()
                                        .sku(TEST_CART_SKU)
                                        .productName(CartBuilder.TEST_CART_PRODUCT_NAME)
                                        .unitPrice(CartBuilder.TEST_CART_UNIT_PRICE)
                                        .quantity(CartBuilder.TEST_CART_QUANTITY)
                                        .subtotal(CartBuilder.TEST_CART_UNIT_PRICE.multiply(java.math.BigDecimal.valueOf(2)))
                                        .build()))
                .total(CartBuilder.TEST_CART_UNIT_PRICE.multiply(java.math.BigDecimal.valueOf(2)))
                .itemCount(CartBuilder.TEST_CART_QUANTITY)
                .build();
    }

}
