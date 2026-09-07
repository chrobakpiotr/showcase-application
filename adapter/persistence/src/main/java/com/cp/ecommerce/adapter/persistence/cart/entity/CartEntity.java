package com.cp.ecommerce.adapter.persistence.cart.entity;

import java.math.BigDecimal;
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
    @Column(name = "CART_ID", length = 41, nullable = false)
    private String cartId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "CART_LINE_ITEM", joinColumns = @JoinColumn(name = "CART_ID"))
    @Builder.Default
    private List<CartLineItemEmbeddable> items = new ArrayList<>();

    @Column(name = "COUPON_CODE", length = 30)
    private String couponCode;

    @Column(name = "DISCOUNT_AMOUNT", nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "UPDATED_DATE", nullable = false)
    private Date updated;

    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

}
