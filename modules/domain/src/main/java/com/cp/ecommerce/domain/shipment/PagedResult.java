package com.cp.ecommerce.domain.shipment;

import java.util.List;

/** Framework-independent page result owned by this bounded context. */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
