package com.cp.ecommerce.adapter.persistence.cart.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.cart.Cart;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link Cart} in database.
 *
 * <p>
 * Uses the cart id directly as its primary key rather than a surrogate id, mirroring {@code StockLevelEntity} (ADR 0026).
 * {@link #items} is a JPA {@code @ElementCollection}, not a full child entity/repository: line items have no identity or
 * lifecycle independent of their owning cart (see {@link CartLineItemEmbeddable}). {@link #version} backs optimistic locking,
 * exactly like {@code StockLevelEntity.version} - see {@code SaveCartAdapter}.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "CART")
public class CartEntity {

    @Id
    @Column(name = "CART_ID", length = 40, nullable = false)
    private String cartId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "CART_LINE_ITEM", joinColumns = @JoinColumn(name = "CART_ID"))
    @Builder.Default
    private List<CartLineItemEmbeddable> items = new ArrayList<>();

    @Column(name = "UPDATED_DATE", nullable = false)
    private Date updated;

    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

}
