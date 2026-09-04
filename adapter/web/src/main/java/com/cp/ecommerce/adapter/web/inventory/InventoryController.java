package com.cp.ecommerce.adapter.web.inventory;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.inventory.mapper.StockLevelWebMapper;
import com.cp.ecommerce.adapter.web.inventory.resource.StockAdjustmentResource;
import com.cp.ecommerce.adapter.web.inventory.resource.StockLevelResource;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.incoming.GetStockLevelInPort;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controller serving the functionality of the {@link StockLevel} inventory API.
 * <p>
 * This is a back-office API only (no customer-facing Angular UI): stock is browsed/adjusted by operators and other bounded
 * contexts (e.g. shopping cart/order placement checking availability), not end customers directly. {@code INVENTORY_READ}/
 * {@code INVENTORY_WRITE} mirror the same back-office authorization model as {@code CATALOG_READ}/{@code CATALOG_WRITE} (see
 * ADR 0017).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Browsing and adjusting SKU stock levels")
public class InventoryController {

    private static final String INVALID_QUANTITY_MESSAGE = "quantity is required and must be greater than zero";

    private final GetStockLevelInPort getStockLevelInPort;

    private final ManageStockInPort manageStockInPort;

    private final StockLevelWebMapper stockLevelWebMapper;

    @GetMapping("/{sku}")
    @Operation(
            summary = "Get the current stock level of a SKU",
            description = "Never 404s: a SKU that has never been received is reported as zero on-hand/reserved.")
    @ApiResponse(
            responseCode = "200",
            description = "Current stock level",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StockLevelResource.class)))
    public StockLevelResource getStockLevel(@PathVariable("sku") final String sku) {

        return toResource(getStockLevelInPort.getStockLevel(sku));
    }

    @PostMapping("/{sku}/receive")
    @Operation(summary = "Receive stock", description = "Increases on-hand quantity, e.g. a goods-receipt event.")
    @ApiResponse(
            responseCode = "200",
            description = "Updated stock level",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StockLevelResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "quantity is missing or not positive",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public StockLevelResource receiveStock(
            @PathVariable("sku") final String sku,
            @RequestBody final StockAdjustmentResource adjustment) {

        return toResource(manageStockInPort.receiveStock(sku, requirePositiveQuantity(adjustment)));
    }

    @PostMapping("/{sku}/reserve")
    @Operation(
            summary = "Reserve stock",
            description = "Reserves quantity against currently available stock, e.g. a cart checkout/order placement.")
    @ApiResponse(
            responseCode = "200",
            description = "Updated stock level",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StockLevelResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "quantity is missing or not positive",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Insufficient available stock, or a concurrent update conflict after exhausting retries",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public StockLevelResource reserveStock(
            @PathVariable("sku") final String sku,
            @RequestBody final StockAdjustmentResource adjustment) {

        return toResource(manageStockInPort.reserveStock(sku, requirePositiveQuantity(adjustment)));
    }

    @PostMapping("/{sku}/release")
    @Operation(
            summary = "Release reserved stock",
            description = "Releases a previously made reservation without touching on-hand quantity, e.g. a cancelled order.")
    @ApiResponse(
            responseCode = "200",
            description = "Updated stock level",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StockLevelResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "quantity is missing or not positive",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public StockLevelResource releaseStock(
            @PathVariable("sku") final String sku,
            @RequestBody final StockAdjustmentResource adjustment) {

        return toResource(manageStockInPort.releaseStock(sku, requirePositiveQuantity(adjustment)));
    }

    @PostMapping("/{sku}/fulfill")
    @Operation(
            summary = "Fulfill reserved stock",
            description = "Decreases both on-hand and reserved quantity, e.g. once an order actually ships.")
    @ApiResponse(
            responseCode = "200",
            description = "Updated stock level",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = StockLevelResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "quantity is missing or not positive",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Insufficient reserved stock, or a concurrent update conflict after exhausting retries",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public StockLevelResource fulfillStock(
            @PathVariable("sku") final String sku,
            @RequestBody final StockAdjustmentResource adjustment) {

        return toResource(manageStockInPort.fulfillStock(sku, requirePositiveQuantity(adjustment)));
    }

    private int requirePositiveQuantity(final StockAdjustmentResource adjustment) {

        if (adjustment == null || adjustment.quantity() == null || adjustment.quantity() <= 0) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_QUANTITY_MESSAGE);
        }
        return adjustment.quantity();
    }

    private StockLevelResource toResource(final StockLevel stockLevel) {

        return stockLevelWebMapper.mapToResource(stockLevel)
                .orElseThrow(() -> new TechnicalProblemException("Stock level data is missing"));
    }

}
