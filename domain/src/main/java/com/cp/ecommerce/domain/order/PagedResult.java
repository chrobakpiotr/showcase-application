package com.cp.ecommerce.domain.order;

import java.util.List;

/**
 * A single page of results, together with enough metadata for a caller to render pagination controls (e.g. HATEOAS
 * first/prev/next/last links) without depending on any persistence-technology paging type.
 *
 * @param <T> type of the paged content.
 */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

}
