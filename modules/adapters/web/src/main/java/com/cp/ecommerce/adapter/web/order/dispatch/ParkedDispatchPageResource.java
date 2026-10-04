package com.cp.ecommerce.adapter.web.order.dispatch;

import java.util.List;

public record ParkedDispatchPageResource(List<ParkedDispatchResource> content, int page, int size, long totalElements,
        int totalPages, Long oldestAgeSeconds) {
}
