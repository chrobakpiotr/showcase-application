package com.cp.ecommerce.adapter.web.wishlist;

import com.cp.ecommerce.adapter.web.wishlist.mapper.WishlistWebMapper;
import com.cp.ecommerce.adapter.web.wishlist.resource.AddWishlistItemResource;
import com.cp.ecommerce.adapter.web.wishlist.resource.MoveWishlistItemToCartResource;
import com.cp.ecommerce.adapter.web.wishlist.resource.WishlistResource;
import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.port.incoming.ManageCartInPort;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort;
import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.WishlistItem;
import com.cp.ecommerce.domain.wishlist.port.incoming.CreateWishlistInPort;
import com.cp.ecommerce.domain.wishlist.port.incoming.GetWishlistInPort;
import com.cp.ecommerce.domain.wishlist.port.incoming.ManageWishlistInPort;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * Controller serving the functionality of the customer-facing wishlist API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/wishlist")
@Tag(name = "Wishlist", description = "Creating and managing an anonymous, session-based wishlist")
public class WishlistController {

    private static final String INVALID_SKU_MESSAGE = "sku is required";

    private static final String INVALID_CART_ID_MESSAGE = "cartId is required";

    private final CreateWishlistInPort createWishlistInPort;

    private final GetWishlistInPort getWishlistInPort;

    private final ManageWishlistInPort manageWishlistInPort;

    private final ManageProductInPort manageProductInPort;

    private final ManageCartInPort manageCartInPort;

    private final WishlistWebMapper wishlistWebMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a new, empty wishlist")
    @ApiResponse(
            responseCode = "201",
            description = "The newly created, empty wishlist",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = WishlistResource.class)))
    public WishlistResource createWishlist() {

        return toResource(createWishlistInPort.createWishlist());
    }

    @GetMapping("/{wishlistId}")
    @Operation(summary = "Get a wishlist by id")
    @ApiResponse(
            responseCode = "200",
            description = "The wishlist",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = WishlistResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No wishlist exists for the given id",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public WishlistResource getWishlist(@PathVariable("wishlistId") final String wishlistId) {

        return toResourceOrNotFound(getWishlistInPort.getWishlist(wishlistId));
    }

    @PostMapping("/{wishlistId}/items")
    @Operation(summary = "Add an item to the wishlist")
    public WishlistResource addItem(
            @PathVariable("wishlistId") final String wishlistId,
            @RequestBody final AddWishlistItemResource item) {

        final String sku = requireSku(item);
        final Product product = requireProduct(sku);
        return toResourceOrNotFound(manageWishlistInPort.addItem(wishlistId, sku, product.getName()));
    }

    @DeleteMapping("/{wishlistId}/items/{sku}")
    @Operation(summary = "Remove a wishlist item")
    public WishlistResource removeItem(
            @PathVariable("wishlistId") final String wishlistId,
            @PathVariable("sku") final String sku) {

        return toResourceOrNotFound(manageWishlistInPort.removeItem(wishlistId, sku));
    }

    @PostMapping("/{wishlistId}/items/{sku}/move-to-cart")
    @Operation(summary = "Move a wishlist item into an existing cart")
    public WishlistResource moveToCart(
            @PathVariable("wishlistId") final String wishlistId,
            @PathVariable("sku") final String sku,
            @RequestBody final MoveWishlistItemToCartResource resource) {

        final String cartId = requireCartId(resource);
        final Wishlist wishlist = getExistingWishlist(wishlistId);
        requireWishlistItem(wishlist, sku);
        final Product product = requireProduct(sku);
        final Cart cart = manageCartInPort.addItem(cartId, sku, product.getName(), product.getUnitPrice(), 1);
        if (cart == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No cart exists for the given id");
        }
        return toResource(manageWishlistInPort.removeItem(wishlistId, sku));
    }

    private String requireSku(final AddWishlistItemResource item) {

        if (item == null || item.sku() == null || item.sku().isBlank()) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_SKU_MESSAGE);
        }
        return item.sku();
    }

    private String requireCartId(final MoveWishlistItemToCartResource resource) {

        if (resource == null || resource.cartId() == null || resource.cartId().isBlank()) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_CART_ID_MESSAGE);
        }
        return resource.cartId();
    }

    private Product requireProduct(final String sku) {

        final Product product = manageProductInPort.findProduct(sku);
        if (product == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No product exists for the given sku");
        }
        return product;
    }

    private Wishlist getExistingWishlist(final String wishlistId) {

        final Wishlist wishlist = getWishlistInPort.getWishlist(wishlistId);
        if (wishlist == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No wishlist exists for the given id");
        }
        return wishlist;
    }

    private void requireWishlistItem(final Wishlist wishlist, final String sku) {

        final boolean present = wishlist.getItems().stream().map(WishlistItem::getSku).anyMatch(sku::equals);
        if (!present) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No wishlist item exists for the given sku");
        }
    }

    private WishlistResource toResourceOrNotFound(final Wishlist wishlist) {

        if (wishlist == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No wishlist exists for the given id");
        }
        return toResource(wishlist);
    }

    private WishlistResource toResource(final Wishlist wishlist) {

        return wishlistWebMapper.mapToResource(wishlist)
                .orElseThrow(() -> new TechnicalProblemException("Wishlist data is missing"));
    }

}
