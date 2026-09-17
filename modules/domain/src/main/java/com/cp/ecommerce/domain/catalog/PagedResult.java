package com.cp.ecommerce.domain.catalog;

import java.util.List;

/**
 * A single page of catalog results, together with enough metadata for a caller to render pagination controls (e.g. HATEOAS
 * first/prev/next/last links) without depending on any persistence-technology paging type.
 *
 * <p>
 * Deliberately not shared with {@code com.cp.ecommerce.domain.order.PagedResult}, despite being structurally identical: this
 * bounded context intentionally does not depend on the order bounded context's package for even a trivial generic wrapper -
 * bounded contexts stay independently deployable/extractable in principle, even though today they share one deployment unit.
 *
 * @param <T> type of the paged content.
 */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

}
