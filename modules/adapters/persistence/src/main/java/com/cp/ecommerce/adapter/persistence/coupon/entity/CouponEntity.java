package com.cp.ecommerce.adapter.persistence.coupon.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.DiscountType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link Coupon} in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "COUPON")
public class CouponEntity {

    @Id
    @Column(name = "CODE", length = 30, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "DISCOUNT_TYPE", length = 20, nullable = false)
    private DiscountType discountType;

    @Column(name = "DISCOUNT_VALUE", nullable = false)
    private BigDecimal discountValue;

    @Column(name = "MINIMUM_ORDER_AMOUNT")
    private BigDecimal minimumOrderAmount;

    @Column(name = "MAX_REDEMPTIONS")
    private Integer maxRedemptions;

    @Column(name = "REDEMPTION_COUNT", nullable = false)
    private int redemptionCount;

    @Column(name = "EXPIRES_AT")
    private Instant expiresAt;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active;

    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

}
