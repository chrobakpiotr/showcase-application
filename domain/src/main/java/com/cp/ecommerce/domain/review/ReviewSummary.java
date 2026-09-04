package com.cp.ecommerce.domain.review;

import java.math.BigDecimal;

/**
 * Aggregate rating for a SKU, computed from its {@link ReviewStatus#APPROVED} reviews only - pending/rejected reviews never
 * influence the number a customer sees on a product listing.
 *
 * @param sku the SKU this summary is for.
 * @param averageRating the mean {@link Review#getRating()} across every approved review, or {@link BigDecimal#ZERO} if there
 *            are none yet.
 * @param reviewCount the number of approved reviews the average was computed from.
 */
public record ReviewSummary(String sku, BigDecimal averageRating, long reviewCount) {

}
