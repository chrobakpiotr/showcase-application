package com.cp.ecommerce.adapter.persistence.inventory.entity;

import com.cp.ecommerce.domain.inventory.StockLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link StockLevel} in database.
 *
 * <p>
 * Uses the product SKU directly as its primary key rather than a surrogate id - there is exactly one stock row per SKU, so a
 * separate technical id would only add an unused indirection. {@link #version} backs JPA's optimistic locking: Hibernate
 * automatically includes it in the {@code UPDATE ... WHERE ID = ? AND VERSION = ?} clause and increments it on every successful
 * write, so a concurrent write against a stale version affects zero rows and is reported back as an optimistic locking failure
 * (see {@code SaveStockLevelAdapter}).
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "STOCK_LEVEL")
public class StockLevelEntity {

    @Id
    @Column(name = "SKU", length = 40, nullable = false)
    private String sku;

    @Column(name = "QUANTITY_ON_HAND", nullable = false)
    private int quantityOnHand;

    @Column(name = "QUANTITY_RESERVED", nullable = false)
    private int quantityReserved;

    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

}
