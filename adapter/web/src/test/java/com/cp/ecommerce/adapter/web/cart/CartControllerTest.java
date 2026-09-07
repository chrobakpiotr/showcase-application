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
import com.cp.ecommerce.domain.coupon.CouponDiscount;
import com.cp.ecommerce.domain.coupon.port.incoming.PreviewCouponInPort;

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

    private static final String CART_COUPON_ENDPOINT = CART_BY_ID_ENDPOINT + "/coupon";

    private static final String TEST_COUPON_CODE = "SAVE10";
    private static final String CART_ID_JSON_PATH = "$.cartId";
    private static final String COUPON_CODE_JSON_PATH = "$.couponCode";
    private static final String APPLY_COUPON_JSON = "{\"code\":\"SAVE10\"}";

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
    private transient PreviewCouponInPort previewCouponInPort;

    @MockitoBean
    private transient CartWebMapper cartWebMapper;

    @Test
    void shouldCreateCart() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(createCartInPort.createCart()).willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.of(mockCartResource()));

        mockMvc.perform(post(CART_ENDPOINT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(CART_ID_JSON_PATH).value(TEST_CART_ID));
    }

    @Test
    void shouldGetCart() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(getCartInPort.getCart(TEST_CART_ID)).willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.of(mockCartResource()));

        mockMvc.perform(get(CART_BY_ID_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CART_ID_JSON_PATH).value(TEST_CART_ID));
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
                .andExpect(jsonPath(CART_ID_JSON_PATH).value(TEST_CART_ID));
    }

    @Test
    void shouldApplyCoupon() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        final Cart discountedCart = Cart.builder()
                .cartId(cart.getCartId())
                .items(cart.getItems())
                .couponCode(TEST_COUPON_CODE)
                .discountAmount(java.math.BigDecimal.TEN)
                .updated(cart.getUpdated())
                .version(cart.getVersion())
                .build();
        given(getCartInPort.getCart(TEST_CART_ID)).willReturn(cart);
        given(
                previewCouponInPort.previewCoupon(
                        org.mockito.ArgumentMatchers.eq(TEST_COUPON_CODE),
                        org.mockito.ArgumentMatchers.eq(cart.getSubtotal()),
                        any()))
                .willReturn(new CouponDiscount(TEST_COUPON_CODE, java.math.BigDecimal.TEN));
        given(manageCartInPort.applyCoupon(TEST_CART_ID, TEST_COUPON_CODE, java.math.BigDecimal.TEN))
                .willReturn(discountedCart);
        given(cartWebMapper.mapToResource(discountedCart)).willReturn(Optional.of(mockDiscountedCartResource()));

        mockMvc.perform(post(CART_COUPON_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(APPLY_COUPON_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath(COUPON_CODE_JSON_PATH).value(TEST_COUPON_CODE));
    }

    @Test
    void shouldRejectApplyCouponWithMissingCode() throws Exception {

        given(getCartInPort.getCart(TEST_CART_ID)).willReturn(CartBuilder.mockCart());

        mockMvc.perform(post(CART_COUPON_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRemoveCoupon() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(manageCartInPort.removeCoupon(TEST_CART_ID)).willReturn(cart);
        given(cartWebMapper.mapToResource(cart)).willReturn(Optional.of(mockCartResource()));

        mockMvc.perform(delete(CART_COUPON_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CART_ID_JSON_PATH).value(TEST_CART_ID));
    }

    @Test
    void shouldReturnNotFoundWhenCouponDoesNotExist() throws Exception {

        final Cart cart = CartBuilder.mockCart();
        given(getCartInPort.getCart(TEST_CART_ID)).willReturn(cart);
        given(
                previewCouponInPort.previewCoupon(
                        org.mockito.ArgumentMatchers.eq(TEST_COUPON_CODE),
                        org.mockito.ArgumentMatchers.eq(cart.getSubtotal()),
                        any()))
                .willReturn(null);

        mockMvc.perform(post(CART_COUPON_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(APPLY_COUPON_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenApplyingCouponToUnknownCart() throws Exception {

        mockMvc.perform(post(CART_COUPON_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(APPLY_COUPON_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenRemovingCouponFromUnknownCart() throws Exception {

        given(manageCartInPort.removeCoupon(TEST_CART_ID)).willReturn(null);

        mockMvc.perform(delete(CART_COUPON_ENDPOINT)).andExpect(status().isNotFound());
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

    private static CartResource mockDiscountedCartResource() {

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
                .subtotal(CartBuilder.TEST_CART_UNIT_PRICE.multiply(java.math.BigDecimal.valueOf(2)))
                .couponCode(TEST_COUPON_CODE)
                .discountAmount(java.math.BigDecimal.TEN)
                .total(
                        CartBuilder.TEST_CART_UNIT_PRICE.multiply(java.math.BigDecimal.valueOf(2))
                                .subtract(java.math.BigDecimal.TEN))
                .itemCount(CartBuilder.TEST_CART_QUANTITY)
                .build();
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
