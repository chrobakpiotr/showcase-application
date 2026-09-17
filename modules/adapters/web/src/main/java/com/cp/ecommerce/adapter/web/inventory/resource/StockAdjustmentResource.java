package com.cp.ecommerce.adapter.web.inventory.resource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for every stock-mutating endpoint ({@code receive}/{@code reserve}/{@code release}/{@code fulfill}).
 */
public record StockAdjustmentResource(@Schema(example = "10") Integer quantity) {

}
