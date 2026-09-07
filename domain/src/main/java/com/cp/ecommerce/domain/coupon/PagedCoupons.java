package com.cp.ecommerce.domain.coupon;

import java.util.List;

/**
 * Paginated coupon list.
 */
public record PagedCoupons(List<Coupon> content, int page, int size, long totalElements, int totalPages) {

}
