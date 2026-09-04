package com.cp.ecommerce.adapter.web.cart;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.cart.mapper.CartWebMapper;
import com.cp.ecommerce.adapter.web.cart.resource.AddCartItemResource;
import com.cp.ecommerce.adapter.web.cart.resource.CartResource;
import com.cp.ecommerce.adapter.web.cart.resource.UpdateCartItemQuantityResource;
import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.port.incoming.CreateCartInPort;
import com.cp.ecommerce.domain.cart.port.incoming.GetCartInPort;
import com.cp.ecommerce.domain.cart.port.incoming.ManageCartInPort;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort;

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
 * <p>
 * Unlike Catalog/Inventory, this is a genuinely public, unauthenticated API (see {@code WebSecurityConfiguration} and ADR
 * 0027): this application has no persisted customer-account concept, so a cart is identified purely by its generated
 * {@code cartId}, which the frontend is expected to hold onto (e.g. local storage) across requests.
 * <p>
 * SKU price/name resolution is deliberately performed here, not in the domain: {@link #addItem} looks up the authoritative,
 * current {@link Product} via {@link ManageProductInPort#findProduct(String)} before delegating to {@link ManageCartInPort}, so
 * the cart bounded context itself never depends on {@code catalog.Product} (see ADR 0027).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cart")
@Tag(name = "Shopping Cart", description = "Creating and managing an anonymous, session-based shopping cart")
public class CartController {

    private static final String INVALID_QUANTITY_MESSAGE = "quantity is required and must be greater than zero";

    private static final String INVALID_SKU_MESSAGE = "sku is required";

    private final CreateCartInPort createCartInPort;

    private final GetCartInPort getCartInPort;

    private final ManageCartInPort manageCartInPort;

    private final ManageProductInPort manageProductInPort;

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
    @Operation(
            summary = "Add an item to the cart",
            description = "Resolves sku to its authoritative current name/price server-side; if the sku is already in the cart, "
                    + "its quantity is increased and its snapshot refreshed rather than adding a duplicate line.")
    @ApiResponse(
            responseCode = "200",
            description = "Updated cart",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CartResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "sku/quantity is missing or invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No cart exists for the given id",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "A concurrent update conflict",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public CartResource addItem(@PathVariable("cartId") final String cartId, @RequestBody final AddCartItemResource item) {

        final String sku = requireSku(item);
        final int quantity = requirePositiveQuantity(item.quantity());
        final Product product = manageProductInPort.findProduct(sku);

        return toResourceOrNotFound(manageCartInPort.addItem(cartId, sku, product.getName(), product.getUnitPrice(), quantity));
    }

    @PutMapping("/{cartId}/items/{sku}")
    @Operation(
            summary = "Update a line item's quantity",
            description = "A no-op (cart returned unchanged) if the sku is not currently in the cart.")
    @ApiResponse(
            responseCode = "200",
            description = "Updated cart",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CartResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "quantity is missing or not positive",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No cart exists for the given id",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public CartResource updateItemQuantity(
            @PathVariable("cartId") final String cartId,
            @PathVariable("sku") final String sku,
            @RequestBody final UpdateCartItemQuantityResource resource) {

        final int quantity = requirePositiveQuantity(resource.quantity());

        return toResourceOrNotFound(manageCartInPort.updateItemQuantity(cartId, sku, quantity));
    }

    @DeleteMapping("/{cartId}/items/{sku}")
    @Operation(summary = "Remove a line item", description = "A no-op if the sku is not currently in the cart.")
    @ApiResponse(
            responseCode = "200",
            description = "Updated cart",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CartResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No cart exists for the given id",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public CartResource removeItem(@PathVariable("cartId") final String cartId, @PathVariable("sku") final String sku) {

        return toResourceOrNotFound(manageCartInPort.removeItem(cartId, sku));
    }

    @DeleteMapping("/{cartId}")
    @Operation(summary = "Empty the cart")
    @ApiResponse(
            responseCode = "200",
            description = "Updated (now empty) cart",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CartResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No cart exists for the given id",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public CartResource clearCart(@PathVariable("cartId") final String cartId) {

        return toResourceOrNotFound(manageCartInPort.clearCart(cartId));
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
