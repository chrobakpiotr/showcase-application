package com.cp.ecommerce.adapter.web.wishlist;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ProductBuilder;
import com.cp.ecommerce.adapter.common.utils.WishlistBuilder;
import com.cp.ecommerce.adapter.web.wishlist.mapper.WishlistWebMapper;
import com.cp.ecommerce.adapter.web.wishlist.resource.WishlistItemResource;
import com.cp.ecommerce.adapter.web.wishlist.resource.WishlistResource;
import com.cp.ecommerce.domain.cart.port.incoming.ManageCartInPort;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort;
import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.port.incoming.CreateWishlistInPort;
import com.cp.ecommerce.domain.wishlist.port.incoming.GetWishlistInPort;
import com.cp.ecommerce.domain.wishlist.port.incoming.ManageWishlistInPort;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.cp.ecommerce.adapter.common.utils.WishlistBuilder.TEST_WISHLIST_ID;
import static com.cp.ecommerce.adapter.common.utils.WishlistBuilder.TEST_WISHLIST_SKU;

/**
 * Test class checking wishlist controller's behavior and API responses.
 */
@WebMvcTest(WishlistController.class)
class WishlistControllerTest {

    private static final String WISHLIST_ENDPOINT = "/api/wishlist";

    private static final String WISHLIST_BY_ID_ENDPOINT = WISHLIST_ENDPOINT + "/" + TEST_WISHLIST_ID;

    private static final String ITEMS_ENDPOINT = WISHLIST_BY_ID_ENDPOINT + "/items";

    private static final String ITEM_BY_SKU_ENDPOINT = ITEMS_ENDPOINT + "/" + TEST_WISHLIST_SKU;

    private static final String MOVE_TO_CART_ENDPOINT = ITEM_BY_SKU_ENDPOINT + "/move-to-cart";

    private static final String WISHLIST_ID_JSON_PATH = "$.wishlistId";

    private static final String MOVE_TO_CART_JSON = "{\"cartId\":\"CART-1\"}";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient CreateWishlistInPort createWishlistInPort;

    @MockitoBean
    private transient GetWishlistInPort getWishlistInPort;

    @MockitoBean
    private transient ManageWishlistInPort manageWishlistInPort;

    @MockitoBean
    private transient ManageProductInPort manageProductInPort;

    @MockitoBean
    private transient ManageCartInPort manageCartInPort;

    @MockitoBean
    private transient WishlistWebMapper wishlistWebMapper;

    @Test
    void shouldCreateWishlist() throws Exception {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        given(createWishlistInPort.createWishlist()).willReturn(wishlist);
        given(wishlistWebMapper.mapToResource(wishlist)).willReturn(Optional.of(mockWishlistResource()));

        mockMvc.perform(post(WISHLIST_ENDPOINT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(WISHLIST_ID_JSON_PATH).value(TEST_WISHLIST_ID));
    }

    @Test
    void shouldGetWishlist() throws Exception {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        given(getWishlistInPort.getWishlist(TEST_WISHLIST_ID)).willReturn(wishlist);
        given(wishlistWebMapper.mapToResource(wishlist)).willReturn(Optional.of(mockWishlistResource()));

        mockMvc.perform(get(WISHLIST_BY_ID_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath(WISHLIST_ID_JSON_PATH).value(TEST_WISHLIST_ID));
    }

    @Test
    void shouldReturnNotFoundWhenGettingUnknownWishlist() throws Exception {

        given(getWishlistInPort.getWishlist(TEST_WISHLIST_ID)).willReturn(null);

        mockMvc.perform(get(WISHLIST_BY_ID_ENDPOINT)).andExpect(status().isNotFound());
    }

    @Test
    void shouldAddItemResolvingProductServerSide() throws Exception {

        final Product product = ProductBuilder.mockProduct();
        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        given(manageProductInPort.findProduct(TEST_WISHLIST_SKU)).willReturn(product);
        given(manageWishlistInPort.addItem(TEST_WISHLIST_ID, TEST_WISHLIST_SKU, product.getName())).willReturn(wishlist);
        given(wishlistWebMapper.mapToResource(wishlist)).willReturn(Optional.of(mockWishlistResource()));

        mockMvc.perform(post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(addItemJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(WISHLIST_ID_JSON_PATH).value(TEST_WISHLIST_ID));
    }

    @Test
    void shouldReturnNotFoundWhenAddingItemToUnknownWishlist() throws Exception {

        final Product product = ProductBuilder.mockProduct();
        given(manageProductInPort.findProduct(TEST_WISHLIST_SKU)).willReturn(product);
        given(manageWishlistInPort.addItem(TEST_WISHLIST_ID, TEST_WISHLIST_SKU, product.getName())).willReturn(null);

        mockMvc.perform(post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(addItemJson()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenAddingItemWithUnknownSku() throws Exception {

        given(manageProductInPort.findProduct(TEST_WISHLIST_SKU)).willReturn(null);

        mockMvc.perform(post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(addItemJson()))
                .andExpect(status().isNotFound());
        verify(manageWishlistInPort, never()).addItem(anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectAddItemWithMissingSku() throws Exception {

        mockMvc.perform(post(ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(manageProductInPort, never()).findProduct(anyString());
    }

    @Test
    void shouldRemoveItem() throws Exception {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        given(manageWishlistInPort.removeItem(TEST_WISHLIST_ID, TEST_WISHLIST_SKU)).willReturn(wishlist);
        given(wishlistWebMapper.mapToResource(wishlist)).willReturn(Optional.of(mockWishlistResource()));

        mockMvc.perform(delete(ITEM_BY_SKU_ENDPOINT)).andExpect(status().isOk());
    }

    @Test
    void shouldReturnNotFoundWhenRemovingItemFromUnknownWishlist() throws Exception {

        given(manageWishlistInPort.removeItem(TEST_WISHLIST_ID, TEST_WISHLIST_SKU)).willReturn(null);

        mockMvc.perform(delete(ITEM_BY_SKU_ENDPOINT)).andExpect(status().isNotFound());
    }

    @Test
    void shouldMoveItemToCart() throws Exception {

        final Product product = ProductBuilder.mockProduct();
        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        final Wishlist emptiedWishlist = Wishlist.builder()
                .wishlistId(wishlist.getWishlistId())
                .updated(wishlist.getUpdated())
                .version(wishlist.getVersion())
                .build();
        given(getWishlistInPort.getWishlist(TEST_WISHLIST_ID)).willReturn(wishlist);
        given(manageProductInPort.findProduct(TEST_WISHLIST_SKU)).willReturn(product);
        given(manageCartInPort.addItem("CART-1", TEST_WISHLIST_SKU, product.getName(), product.getUnitPrice(), 1))
                .willReturn(com.cp.ecommerce.adapter.common.utils.CartBuilder.mockCart());
        given(manageWishlistInPort.removeItem(TEST_WISHLIST_ID, TEST_WISHLIST_SKU)).willReturn(emptiedWishlist);
        given(wishlistWebMapper.mapToResource(emptiedWishlist)).willReturn(
                Optional.of(
                        WishlistResource.builder()
                                .wishlistId(TEST_WISHLIST_ID)
                                .items(java.util.List.of())
                                .itemCount(0)
                                .build()));

        mockMvc.perform(post(MOVE_TO_CART_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(MOVE_TO_CART_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void shouldReturnNotFoundWhenMovingItemFromUnknownWishlist() throws Exception {

        mockMvc.perform(post(MOVE_TO_CART_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(MOVE_TO_CART_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenMovingMissingWishlistItem() throws Exception {

        final Wishlist wishlist = Wishlist.builder().wishlistId(TEST_WISHLIST_ID).build();
        given(getWishlistInPort.getWishlist(TEST_WISHLIST_ID)).willReturn(wishlist);

        mockMvc.perform(post(MOVE_TO_CART_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(MOVE_TO_CART_JSON))
                .andExpect(status().isNotFound());
        verify(manageCartInPort, never()).addItem(anyString(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void shouldRejectMoveToCartWithMissingCartId() throws Exception {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        given(getWishlistInPort.getWishlist(TEST_WISHLIST_ID)).willReturn(wishlist);

        mockMvc.perform(post(MOVE_TO_CART_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(manageCartInPort, never()).addItem(anyString(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void shouldReturnNotFoundWhenTargetCartDoesNotExist() throws Exception {

        final Product product = ProductBuilder.mockProduct();
        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        given(getWishlistInPort.getWishlist(TEST_WISHLIST_ID)).willReturn(wishlist);
        given(manageProductInPort.findProduct(TEST_WISHLIST_SKU)).willReturn(product);
        given(manageCartInPort.addItem("CART-1", TEST_WISHLIST_SKU, product.getName(), product.getUnitPrice(), 1))
                .willReturn(null);

        mockMvc.perform(post(MOVE_TO_CART_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(MOVE_TO_CART_JSON))
                .andExpect(status().isNotFound());
        verify(manageWishlistInPort, never()).removeItem(anyString(), anyString());
    }

    @Test
    void shouldThrowTechnicalProblemWhenMapToResourceReturnsEmpty() throws Exception {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        given(getWishlistInPort.getWishlist(TEST_WISHLIST_ID)).willReturn(wishlist);
        given(wishlistWebMapper.mapToResource(wishlist)).willReturn(Optional.empty());

        mockMvc.perform(get(WISHLIST_BY_ID_ENDPOINT)).andExpect(status().isInternalServerError());
    }

    private static String addItemJson() {

        return "{\"sku\":\"" + TEST_WISHLIST_SKU + "\"}";
    }

    private static WishlistResource mockWishlistResource() {

        return WishlistResource.builder()
                .wishlistId(TEST_WISHLIST_ID)
                .items(
                        java.util.List.of(
                                WishlistItemResource.builder()
                                        .sku(TEST_WISHLIST_SKU)
                                        .productName(WishlistBuilder.TEST_WISHLIST_PRODUCT_NAME)
                                        .addedDate(WishlistBuilder.TEST_WISHLIST_ADDED_DATE)
                                        .build()))
                .itemCount(1)
                .build();
    }

}
