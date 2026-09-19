package com.cp.ecommerce.adapter.web.cart;

import java.time.Instant;

import com.cp.ecommerce.adapter.web.cart.mapper.CartWebMapper;
import com.cp.ecommerce.adapter.web.cart.resource.AddCartItemResource;
import com.cp.ecommerce.adapter.web.cart.resource.ApplyCouponResource;
import com.cp.ecommerce.adapter.web.cart.resource.CartResource;
import com.cp.ecommerce.adapter.web.cart.resource.UpdateCartItemQuantityResource;
import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.port.incoming.CreateCartInPort;
import com.cp.ecommerce.domain.cart.port.incoming.GetCartInPort;
import com.cp.ecommerce.domain.cart.port.incoming.ManageCartInPort;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort;
import com.cp.ecommerce.domain.coupon.CouponDiscount;
import com.cp.ecommerce.domain.coupon.port.incoming.PreviewCouponInPort;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controller serving the functionality of the customer-facing shopping-cart API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cart")
@Tag(name = "Shopping Cart", description = "Creating and managing an anonymous, session-based shopping cart")
public class CartController {

    private static final String INVALID_QUANTITY_MESSAGE = "quantity is required and must be greater than zero";

    private static final String INVALID_SKU_MESSAGE = "sku is required";

    private static final String INVALID_COUPON_MESSAGE = "code is required";

    private final CreateCartInPort createCartInPort;

    private final GetCartInPort getCartInPort;

    private final ManageCartInPort manageCartInPort;

    private final ManageProductInPort manageProductInPort;

    private final PreviewCouponInPort previewCouponInPort;

    private final CartWebMapper cartWebMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a new, empty shopping cart")
    @ApiResponse(
            responseCode = "201",
            description = "The newly created, empty cart",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CartResource.class)))
    public CartResource createCart() {

        return toResource(createCartInPort.createCart());
    }

    @GetMapping("/{cartId}")
    @Operation(summary = "Get a cart by id")
    @ApiResponse(
            responseCode = "200",
            description = "The cart",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CartResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No cart exists for the given id",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public CartResource getCart(@PathVariable("cartId") final String cartId) {

        return toResourceOrNotFound(getCartInPort.getCart(cartId));
    }

    @PostMapping("/{cartId}/items")
    @Operation(summary = "Add an item to the cart")
    public CartResource addItem(@PathVariable("cartId") final String cartId, @RequestBody final AddCartItemResource item) {

        final String sku = requireSku(item);
        final int quantity = requirePositiveQuantity(item.quantity());
        final Product product = requireProduct(sku);
        return toResourceOrNotFound(manageCartInPort.addItem(cartId, sku, product.getName(), product.getUnitPrice(), quantity));
    }

    @PutMapping("/{cartId}/items/{sku}")
    @Operation(summary = "Update a line item's quantity")
    public CartResource updateItemQuantity(
            @PathVariable("cartId") final String cartId,
            @PathVariable("sku") final String sku,
            @RequestBody final UpdateCartItemQuantityResource resource) {

        return toResourceOrNotFound(
                manageCartInPort.updateItemQuantity(cartId, sku, requirePositiveQuantity(resource.quantity())));
    }

    @DeleteMapping("/{cartId}/items/{sku}")
    @Operation(summary = "Remove a line item")
    public CartResource removeItem(@PathVariable("cartId") final String cartId, @PathVariable("sku") final String sku) {

        return toResourceOrNotFound(manageCartInPort.removeItem(cartId, sku));
    }

    @DeleteMapping("/{cartId}")
    @Operation(summary = "Empty the cart")
    public CartResource clearCart(@PathVariable("cartId") final String cartId) {

        return toResourceOrNotFound(manageCartInPort.clearCart(cartId));
    }

    @PostMapping("/{cartId}/coupon")
    @Operation(summary = "Attach a coupon to the cart")
    public CartResource applyCoupon(
            @PathVariable("cartId") final String cartId,
            @RequestBody final ApplyCouponResource resource) {

        final Cart cart = getExistingCart(cartId);
        final CouponDiscount discount = previewCouponInPort.previewCoupon(
                requireCouponCode(resource),
                cart.getSubtotal(),
                Instant.ofEpochMilli(Instant.now().toEpochMilli()));
        if (discount == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found");
        }
        return toResource(manageCartInPort.applyCoupon(cartId, discount.code(), discount.discountAmount()));
    }

    @DeleteMapping("/{cartId}/coupon")
    @Operation(summary = "Remove the cart's coupon preview")
    public CartResource removeCoupon(@PathVariable("cartId") final String cartId) {

        return toResourceOrNotFound(manageCartInPort.removeCoupon(cartId));
    }

    private String requireSku(final AddCartItemResource item) {

        if (item.sku() == null || item.sku().isBlank()) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_SKU_MESSAGE);
        }
        return item.sku();
    }

    private int requirePositiveQuantity(final Integer quantity) {

        if (quantity == null || quantity <= 0) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_QUANTITY_MESSAGE);
        }
        return quantity;
    }

    private String requireCouponCode(final ApplyCouponResource resource) {

        if (resource == null || resource.code() == null || resource.code().isBlank()) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_COUPON_MESSAGE);
        }
        return resource.code();
    }

    private Product requireProduct(final String sku) {

        final Product product = manageProductInPort.findProduct(sku);
        if (product == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No product exists for the given sku");
        }
        return product;
    }

    private Cart getExistingCart(final String cartId) {

        final Cart cart = getCartInPort.getCart(cartId);
        if (cart == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No cart exists for the given id");
        }
        return cart;
    }

    private CartResource toResourceOrNotFound(final Cart cart) {

        if (cart == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No cart exists for the given id");
        }
        return toResource(cart);
    }

    private CartResource toResource(final Cart cart) {

        return cartWebMapper.mapToResource(cart).orElseThrow(() -> new TechnicalProblemException("Cart data is missing"));
    }

}
