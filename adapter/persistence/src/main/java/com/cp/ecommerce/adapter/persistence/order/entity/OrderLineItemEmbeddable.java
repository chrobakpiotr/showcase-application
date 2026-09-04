package com.cp.ecommerce.adapter.persistence.order.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embeddable representation of an {@code OrderLineItem} - stored via {@code OrderEntity}'s {@code @ElementCollection}, not as
 * an independent entity with its own repository, mirroring {@code CartLineItemEmbeddable} (ADR 0027): a line item has no
 * identity or lifecycle outside its owning order (see ADR 0029).
 */
@Embeddable
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderLineItemEmbeddable {

    @Column(name = "SKU", length = 40, nullable = false)
    private String sku;

    @Column(name = "PRODUCT_NAME", length = 200, nullable = false)
    private String productName;

    @Column(name = "UNIT_PRICE", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "QUANTITY", nullable = false)
    private int quantity;

}
